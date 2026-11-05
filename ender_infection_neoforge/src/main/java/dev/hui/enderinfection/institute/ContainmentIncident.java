package dev.hui.enderinfection.institute;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** One bounded E8 encounter per facility. Missing/unloaded enemies never authorize replacements. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class ContainmentIncident {
    public enum Phase { IDLE, WARNING, WAVE_ONE, INTERMISSION, WAVE_TWO, ISOLATION, COMPLETE, RECOVERY }
    public record Status(Phase phase,int spawned,int resolved,int faults,boolean rewardClaimed,boolean paused) {}
    public record Target(String role,BlockPos pos,BlockPos approach,String block) {}
    public record Definition(BlockPos min,BlockPos max,Target control,List<Target> faults,List<BlockPos> spawns,List<BlockPos> alarms) {
        public AABB bounds(BlockPos origin) {return new AABB(origin.getX()+min.getX(),origin.getY()+min.getY(),origin.getZ()+min.getZ(),origin.getX()+max.getX(),origin.getY()+max.getY(),origin.getZ()+max.getZ());}
    }
    public static final class Progress {
        Phase phase=Phase.IDLE;
        UUID run;
        final UUID[] identities=new UUID[6];
        final BlockPos[] lastPositions=new BlockPos[6];
        int spawned,resolved,faults,clock;
        boolean claimed;
        boolean active(){return phase!=Phase.IDLE&&phase!=Phase.COMPLETE&&phase!=Phase.RECOVERY;}
        CompoundTag save(){
            var t=new CompoundTag();t.putString("Phase",phase.name());if(run!=null)t.putUUID("Run",run);
            t.putInt("Spawned",spawned);t.putInt("Resolved",resolved);t.putInt("Faults",faults);t.putInt("Clock",clock);t.putBoolean("Claimed",claimed);
            var slots=new ListTag();for(int i=0;i<6;i++)if(identities[i]!=null){var s=new CompoundTag();s.putInt("Slot",i);s.putUUID("Identity",identities[i]);if(lastPositions[i]!=null)s.putLong("Position",lastPositions[i].asLong());slots.add(s);}t.put("Slots",slots);return t;
        }
        static Progress load(CompoundTag t){
            var p=new Progress();if(t.isEmpty())return p;
            try{p.phase=Phase.valueOf(t.getString("Phase"));}catch(IllegalArgumentException bad){p.phase=Phase.RECOVERY;}
            p.run=t.hasUUID("Run")?t.getUUID("Run"):null;p.spawned=t.getInt("Spawned")&63;p.resolved=t.getInt("Resolved")&p.spawned;
            p.faults=t.getInt("Faults")&7;p.clock=Math.max(0,Math.min(60,t.getInt("Clock")));p.claimed=t.getBoolean("Claimed");
            for(var entry:t.getList("Slots",10)){var s=(CompoundTag)entry;int slot=s.getInt("Slot");if(slot>=0&&slot<6&&s.hasUUID("Identity")){p.identities[slot]=s.getUUID("Identity");if(s.contains("Position",4))p.lastPositions[slot]=BlockPos.of(s.getLong("Position"));}}
            if(p.claimed)p.phase=Phase.COMPLETE;
            if(p.phase!=Phase.IDLE&&p.phase!=Phase.COMPLETE&&(p.run==null||java.util.Arrays.stream(p.identities).anyMatch(java.util.Objects::isNull)))p.phase=Phase.RECOVERY;
            if(p.phase==Phase.IDLE&&p.spawned!=0)p.phase=Phase.RECOVERY;
            return p;
        }
    }
    private static final Map<Object,Optional<Definition>> DEFINITIONS=new WeakHashMap<>();
    private static final InfectedKind[] KINDS={InfectedKind.FORAGER,InfectedKind.EARLY_ZOMBIE,InfectedKind.THROAT_SPITTER,
            InfectedKind.FORAGER,InfectedKind.SHIELD_SENTINEL,InfectedKind.EARLY_SKELETON};
    private static final String RUN="InstituteIncidentRun",ORIGIN="InstituteIncidentOrigin",SLOT="InstituteIncidentSlot";
    private ContainmentIncident(){}
    private static BlockPos pos(JsonElement value){var a=value.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    private static Target target(JsonElement value){var o=value.getAsJsonObject();return new Target(o.get("role").getAsString(),pos(o.get("pos")),pos(o.get("approach_pos")),o.get("block").getAsString());}
    public static Optional<Definition> definition(ServerLevel level){
        Object resources=level.getServer().getResourceManager();var old=DEFINITIONS.get(resources);if(old!=null)return old;
        try(var reader=level.getServer().getResourceManager().getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID,"central_institute.json")).openAsReader()){
            var root=JsonParser.parseReader(reader).getAsJsonObject();if(!root.has("incidents")||!root.getAsJsonObject("incidents").has("E8")){DEFINITIONS.put(resources,Optional.empty());return Optional.empty();}
            var o=root.getAsJsonObject("incidents").getAsJsonObject("E8");var faults=new ArrayList<Target>();var spawns=new ArrayList<BlockPos>();var alarms=new ArrayList<BlockPos>();
            o.getAsJsonArray("faults").forEach(v->faults.add(target(v)));o.getAsJsonArray("spawn_points").forEach(v->spawns.add(pos(v)));o.getAsJsonArray("alarm_lights").forEach(v->alarms.add(pos(v)));
            var d=new Definition(pos(o.get("bounds_min")),pos(o.get("bounds_max")),target(o.get("control")),List.copyOf(faults),List.copyOf(spawns),List.copyOf(alarms));
            if(spawns.size()!=3||faults.size()!=3||alarms.size()!=2||!faults.stream().map(Target::role).collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of("cooling","sensor","valve")))throw new IllegalArgumentException("Invalid E8 incident definition");
            if(d.min.getX()>=d.max.getX()||d.min.getY()>=d.max.getY()||d.min.getZ()>=d.max.getZ()
                    ||!InstituteOperations.validOffset(d.min)||!InstituteOperations.validOffset(d.max.offset(-1,-1,-1))
                    ||spawns.stream().distinct().count()!=3||alarms.stream().distinct().count()!=2)
                throw new IllegalArgumentException("Invalid E8 incident bounds");
            var cells=new ArrayList<BlockPos>(spawns);cells.addAll(alarms);cells.add(d.control.pos);cells.add(d.control.approach);
            for(var fault:faults){cells.add(fault.pos);cells.add(fault.approach);}
            if(cells.stream().anyMatch(cell->!d.bounds(BlockPos.ZERO).contains(Vec3.atCenterOf(cell))))throw new IllegalArgumentException("E8 incident target outside room");
            var result=Optional.of(d);DEFINITIONS.put(resources,result);return result;
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private static OperationsData.Facility facilityAt(ServerLevel level,BlockPos at){
        for(var f:OperationsData.get(level).facilities.values())if(InstituteOperations.validOffset(at.subtract(f.origin)))return f;return null;
    }
    public static Optional<Status> status(ServerLevel level,BlockPos at){
        var f=facilityAt(level,at);var d=definition(level);if(f==null||d.isEmpty())return Optional.empty();var p=f.incident;
        return Optional.of(new Status(p.phase,Integer.bitCount(p.spawned),Integer.bitCount(p.resolved),p.faults,p.claimed,p.active()&&!canAdvance(level,f,d.get())));
    }
    public static boolean localPower(ServerLevel level,OperationsData.Facility f,BlockPos relative){
        var d=definition(level);return d.isEmpty()||relative.equals(d.get().control.pos)||!f.incident.active()||(f.incident.faults&1)==0||!d.get().bounds(BlockPos.ZERO).contains(Vec3.atCenterOf(relative));
    }
    public static boolean isControl(ServerLevel level,BlockPos at){
        var f=facilityAt(level,at);var d=definition(level);return f!=null&&d.isPresent()&&at.equals(f.origin.offset(d.get().control.pos));
    }
    private static InteractionResult tell(ServerPlayer player,String key,boolean success,Object... args){player.displayClientMessage(Component.translatable("message.ender_infection.incident."+key,args),false);return success?InteractionResult.CONSUME:InteractionResult.FAIL;}
    private static boolean boundControl(ServerLevel level,OperationsData.Facility f,Definition d){
        var at=f.origin.offset(d.control.pos);if(level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null)return false;
        return level.getBlockEntity(at) instanceof OperationsControlEntity control&&control.boundOrigin().filter(f.origin::equals).isPresent()&&control.role().equals("terminal");
    }
    public static InteractionResult useControl(ServerLevel level,BlockPos at,ServerPlayer player){
        var f=facilityAt(level,at);var definition=definition(level);if(f==null||definition.isEmpty())return InteractionResult.PASS;var d=definition.get();var p=f.incident;
        if(!at.equals(f.origin.offset(d.control.pos))||!boundControl(level,f,d))return tell(player,"unbound",false);
        if(p.phase==Phase.IDLE){
            if(!player.getMainHandItem().is(InstituteOperations.item("research_keycard")))return tell(player,"start_hint",false);
            if(player.isSpectator()||player.isCreative()||!player.isAlive())return tell(player,"survival_required",false);
            if(!f.repaired||!f.containmentPowered||!f.analyzed||!f.separated)return tell(player,"prerequisites",false);
            if(level.getDifficulty()==Difficulty.PEACEFUL||!loaded(level,f,d))return tell(player,"wait",false);
            p.run=UUID.randomUUID();for(int i=0;i<6;i++)p.identities[i]=UUID.nameUUIDFromBytes((f.origin.asLong()+"/"+p.run+"/"+i).getBytes(StandardCharsets.UTF_8));
            p.phase=Phase.WARNING;p.faults=7;p.clock=0;changed(level,f,"started");return tell(player,"started",true);
        }
        if(p.phase==Phase.COMPLETE){
            if(p.claimed)return tell(player,"already_claimed",false);
            if(!f.repaired||!f.containmentPowered)return tell(player,"reward_power",false);
            if(!InstituteOperations.transact(player,Map.of(),List.of(new ItemStack(InstituteOperations.item("research_record")),new ItemStack(InstituteOperations.item("void_crystal")))))return tell(player,"inventory_full",false);
            p.claimed=true;changed(level,f,"reward_claimed");InstituteOperations.synchronizeInventory(player);return tell(player,"reward",true);
        }
        return tell(player,"status",true,Component.translatable("message.ender_infection.incident.phase."+p.phase.name().toLowerCase(java.util.Locale.ROOT)),Integer.bitCount(p.resolved),Integer.bitCount(p.spawned),Integer.bitCount(p.faults));
    }
    public static InteractionResult useFixture(ServerLevel level,BlockPos at,ServerPlayer player){
        var f=facilityAt(level,at);var definition=definition(level);if(f==null||definition.isEmpty()||!f.incident.active())return InteractionResult.PASS;var d=definition.get();
        Target target=null;for(var t:d.faults)if(at.equals(f.origin.offset(t.pos)))target=t;
        if(target==null)return InteractionResult.PASS;
        var state=level.getBlockState(at);if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(target.block))return InteractionResult.PASS;
        int bit=switch(target.role){case "cooling"->1;case "sensor"->2;default->4;};var p=f.incident;
        if((p.faults&bit)==0)return tell(player,"fixed",true);
        if(bit==4){
            if(p.phase!=Phase.ISOLATION||(p.faults&3)!=0)return tell(player,"valve_wait",false);
            if(!player.isShiftKeyDown()||!player.getMainHandItem().isEmpty())return tell(player,"valve_hint",false);
        }else{
            var cost=bit==1?Map.of(Items.COPPER_INGOT,2):Map.of(Items.REDSTONE,1);
            if(!player.getMainHandItem().is(bit==1?Items.COPPER_INGOT:Items.REDSTONE)||!InstituteOperations.transact(player,cost,List.of()))return tell(player,bit==1?"cooling_cost":"sensor_cost",false);
        }
        p.faults&=~bit;if(bit==4)p.phase=Phase.COMPLETE;
        changed(level,f,"repaired:"+target.role);InstituteOperations.synchronizeInventory(player);
        level.playSound(null,at,SoundEvents.BEACON_ACTIVATE,SoundSource.BLOCKS,.5F,1.2F);
        return tell(player,bit==4?"complete":"repaired",true,Component.translatable("message.ender_infection.incident.fixture."+target.role));
    }
    private static void changed(ServerLevel level,OperationsData.Facility f,String reason){
        var data=OperationsData.get(level);data.setDirty();data.restartRefresh(f.origin);InstituteOperations.incidentChanged(level,f,"incident:E8:"+reason);
    }
    private static boolean loaded(ServerLevel level,OperationsData.Facility f,Definition d){
        for(int x=(f.origin.getX()+d.min.getX())>>4;x<=(f.origin.getX()+d.max.getX()-1)>>4;x++)for(int z=(f.origin.getZ()+d.min.getZ())>>4;z<=(f.origin.getZ()+d.max.getZ()-1)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null||!level.areEntitiesLoaded(ChunkPos.asLong(x,z)))return false;
        for(int i=0;i<6;i++)if((f.incident.spawned&~f.incident.resolved&(1<<i))!=0&&f.incident.lastPositions[i]!=null){var cp=new ChunkPos(f.incident.lastPositions[i]);if(level.getChunkSource().getChunkNow(cp.x,cp.z)==null||!level.areEntitiesLoaded(cp.toLong()))return false;}
        return true;
    }
    private static boolean canAdvance(ServerLevel level,OperationsData.Facility f,Definition d){
        return level.getDifficulty()!=Difficulty.PEACEFUL&&loaded(level,f,d)&&level.players().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&d.bounds(f.origin).inflate(2).contains(p.position()));
    }
    private static boolean tagged(Entity e,OperationsData.Facility f,int slot){
        var tag=e.getPersistentData();return e.getUUID().equals(f.incident.identities[slot])&&tag.hasUUID(RUN)&&tag.getUUID(RUN).equals(f.incident.run)&&tag.getLong(ORIGIN)==f.origin.asLong()&&tag.getInt(SLOT)==slot;
    }
    private static void spawnWave(ServerLevel level,OperationsData.Facility f,Definition d,int first){
        var p=f.incident;
        for(int slot=first;slot<first+3;slot++){
            if((p.spawned&(1<<slot))!=0)continue;
            var existing=level.getEntity(p.identities[slot]);
            if(existing!=null){if(tagged(existing,f,slot)){p.spawned|=1<<slot;p.lastPositions[slot]=existing.blockPosition();OperationsData.get(level).setDirty();}continue;}
            var mob=InfectedContent.TYPES.get(KINDS[slot]).get().create(level);if(mob==null)continue;
            var at=f.origin.offset(d.spawns.get(slot%3));mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);
            if(!level.noCollision(mob)){mob.discard();continue;}
            mob.setUUID(p.identities[slot]);mob.setPersistenceRequired();mob.restrictTo(BlockPos.containing(d.bounds(f.origin).getCenter()),12);
            var saved=new CompoundTag();mob.addAdditionalSaveData(saved);saved.putInt("EmergenceTicks",0);mob.readAdditionalSaveData(saved);
            var tag=mob.getPersistentData();tag.putUUID(RUN,p.run);tag.putLong(ORIGIN,f.origin.asLong());tag.putInt(SLOT,slot);
            p.spawned|=1<<slot;p.lastPositions[slot]=at;OperationsData.get(level).setDirty();
            if(!level.addFreshEntity(mob)){p.spawned&=~(1<<slot);p.lastPositions[slot]=null;OperationsData.get(level).setDirty();}
        }
    }
    private static void visuals(ServerLevel level,OperationsData.Facility f,Definition d){
        boolean alarm=f.incident.active()&&((level.getGameTime()/20)%2==0);
        for(var offset:d.alarms){var at=f.origin.offset(offset);var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);if(chunk==null)continue;var state=chunk.getBlockState(at);
            if(state.is(Blocks.REDSTONE_LAMP)&&state.getValue(BlockStateProperties.LIT)!=alarm)level.setBlock(at,state.setValue(BlockStateProperties.LIT,alarm),18);}
        if(boundControl(level,f,d))((OperationsControlEntity)level.getBlockEntity(f.origin.offset(d.control.pos))).updatePowered(f.repaired&&f.containmentPowered);
        if(f.incident.active()&&level.getGameTime()%100==0&&loaded(level,f,d))level.playSound(null,f.origin.offset(d.control.pos),SoundEvents.BEACON_DEACTIVATE,SoundSource.BLOCKS,.7F,.6F);
    }
    public static void tick(ServerLevel level,OperationsData data){
        if(level.getGameTime()%20!=0)return;var definition=definition(level);if(definition.isEmpty())return;var d=definition.get();
        for(var f:data.facilities.values()){
            var p=f.incident;visuals(level,f,d);if(!p.active())continue;
            if(level.getDifficulty()==Difficulty.PEACEFUL){
                for(int i=0;i<6;i++)if((p.spawned&~p.resolved&(1<<i))!=0){var e=level.getEntity(p.identities[i]);if(e!=null&&tagged(e,f,i))e.discard();}
                if(p.resolved!=p.spawned){p.resolved=p.spawned;data.setDirty();}continue;
            }
            if(!canAdvance(level,f,d))continue;
            for(int i=0;i<6;i++)if((p.spawned&~p.resolved&(1<<i))!=0){var e=level.getEntity(p.identities[i]);if(e!=null&&tagged(e,f,i)){
                if(!d.bounds(f.origin).inflate(1).contains(e.position())){var at=f.origin.offset(d.spawns.get(i%3));var box=e.getBoundingBox().move(Vec3.atBottomCenterOf(at).subtract(e.position()));if(level.noCollision(e,box))e.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);}
                if(!e.blockPosition().equals(p.lastPositions[i])){p.lastPositions[i]=e.blockPosition();data.setDirty();}
            }}
            switch(p.phase){
                case WARNING,INTERMISSION->{p.clock+=20;data.setDirty();if(p.clock>=60){p.clock=0;p.phase=p.phase==Phase.WARNING?Phase.WAVE_ONE:Phase.WAVE_TWO;changed(level,f,"wave_started");spawnWave(level,f,d,p.phase==Phase.WAVE_ONE?0:3);}}
                case WAVE_ONE->{spawnWave(level,f,d,0);if((p.resolved&7)==7){p.phase=Phase.INTERMISSION;p.clock=0;changed(level,f,"first_wave_cleared");}}
                case WAVE_TWO->{spawnWave(level,f,d,3);if(p.resolved==63){p.phase=Phase.ISOLATION;changed(level,f,"isolate");}}
                default->{}
            }
        }
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent event){
        var entity=event.getEntity();if(!(entity.level() instanceof ServerLevel level)||!entity.getPersistentData().hasUUID(RUN))return;
        var f=OperationsData.get(level).find(BlockPos.of(entity.getPersistentData().getLong(ORIGIN)));int slot=entity.getPersistentData().getInt(SLOT);
        if(f!=null&&slot>=0&&slot<6&&f.incident.run!=null&&tagged(entity,f,slot)&&(f.incident.spawned&(1<<slot))!=0){f.incident.resolved|=1<<slot;OperationsData.get(level).setDirty();}
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        var entity=event.getEntity();if(!(event.getLevel() instanceof ServerLevel level)||!entity.getPersistentData().hasUUID(RUN))return;
        var f=OperationsData.get(level).find(BlockPos.of(entity.getPersistentData().getLong(ORIGIN)));int slot=entity.getPersistentData().getInt(SLOT);
        if(f!=null&&slot>=0&&slot<6&&f.incident.run!=null&&tagged(entity,f,slot)&&(f.incident.resolved&(1<<slot))!=0){event.setCanceled(true);entity.discard();}
    }
}
