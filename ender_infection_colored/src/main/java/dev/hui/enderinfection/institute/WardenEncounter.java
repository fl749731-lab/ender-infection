package dev.hui.enderinfection.institute;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.ContainmentWarden;
import dev.hui.enderinfection.warden.WardenContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One persistent F8 boss identity per run; absence never means victory or permission to respawn. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class WardenEncounter {
    public enum Phase { IDLE, WARNING, ACTIVE, DEFEATED, RECOVERY }
    public record Status(Phase phase, UUID identity, boolean spawned, boolean rewardClaimed, boolean paused,
                         float health, boolean exposed, int consoles, int consoleCooldown) {}
    public record Target(String role, BlockPos pos, BlockPos approach, String block) {}
    public record Definition(BlockPos min, BlockPos max, Target control, List<Target> consoles, BlockPos spawn) {
        public AABB bounds(BlockPos origin) { return new AABB(origin.getX()+min.getX(),origin.getY()+min.getY(),origin.getZ()+min.getZ(),origin.getX()+max.getX(),origin.getY()+max.getY(),origin.getZ()+max.getZ()); }
    }
    public static final class Progress {
        Phase phase=Phase.IDLE;
        UUID run, identity;
        BlockPos lastPosition;
        boolean spawned, claimed;
        int warningTicks, missingTicks;
        CompoundTag save() {
            var tag=new CompoundTag(); tag.putString("Phase",phase.name());
            if(run!=null)tag.putUUID("Run",run); if(identity!=null)tag.putUUID("Identity",identity);
            if(lastPosition!=null)tag.putLong("LastPosition",lastPosition.asLong());
            tag.putBoolean("Spawned",spawned);tag.putBoolean("Claimed",claimed);tag.putInt("WarningTicks",warningTicks);
            return tag;
        }
        static Progress load(CompoundTag tag) {
            var p=new Progress();if(tag.isEmpty())return p;
            try{p.phase=Phase.valueOf(tag.getString("Phase"));}catch(IllegalArgumentException invalid){p.phase=Phase.RECOVERY;}
            p.run=tag.hasUUID("Run")?tag.getUUID("Run"):null;p.identity=tag.hasUUID("Identity")?tag.getUUID("Identity"):null;
            p.lastPosition=tag.contains("LastPosition",4)?BlockPos.of(tag.getLong("LastPosition")):null;
            p.spawned=tag.getBoolean("Spawned");p.claimed=tag.getBoolean("Claimed");p.warningTicks=Math.max(0,Math.min(60,tag.getInt("WarningTicks")));
            if(p.claimed)p.phase=Phase.DEFEATED;
            else if((p.phase==Phase.WARNING||p.phase==Phase.ACTIVE)&&(p.run==null||p.identity==null)
                    ||p.phase==Phase.ACTIVE&&!p.spawned||p.phase==Phase.IDLE&&p.spawned)p.phase=Phase.RECOVERY;
            return p;
        }
    }
    private static final Map<Object,Optional<Definition>> DEFINITIONS=new WeakHashMap<>();
    private WardenEncounter() {}
    private static BlockPos vector(JsonElement value) { var a=value.getAsJsonArray();return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()); }
    private static Target target(JsonElement value) { var o=value.getAsJsonObject();return new Target(o.get("role").getAsString(),vector(o.get("pos")),vector(o.get("approach_pos")),o.get("block").getAsString()); }
    public static Optional<Definition> definition(ServerLevel level) {
        var resources=level.getServer().getResourceManager();var old=DEFINITIONS.get(resources);if(old!=null)return old;
        try(var reader=resources.getResourceOrThrow(new ResourceLocation(EnderInfection.MOD_ID,"central_institute.json")).openAsReader()) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(!root.has("bosses")||!root.getAsJsonObject("bosses").has("F8")){DEFINITIONS.put(resources,Optional.empty());return Optional.empty();}
            var object=root.getAsJsonObject("bosses").getAsJsonObject("F8");var consoles=new ArrayList<Target>();
            object.getAsJsonArray("consoles").forEach(value->consoles.add(target(value)));
            var d=new Definition(vector(object.get("bounds_min")),vector(object.get("bounds_max")),target(object.get("control")),List.copyOf(consoles),vector(object.get("spawn_pos")));
            if(!InstituteOperations.validOffset(d.min)||!InstituteOperations.validOffset(d.max.offset(-1,-1,-1))
                    ||d.min.getX()>=d.max.getX()||d.min.getY()>=d.max.getY()||d.min.getZ()>=d.max.getZ()||consoles.size()!=2)
                throw new IllegalArgumentException("Invalid F8 boss bounds or consoles");
            var cells=new ArrayList<BlockPos>(List.of(d.control.pos,d.control.approach,d.spawn));
            for(var c:consoles){cells.add(c.pos);cells.add(c.approach);}
            if(cells.stream().anyMatch(p->!d.bounds(BlockPos.ZERO).contains(Vec3.atCenterOf(p)))||consoles.get(0).pos.equals(consoles.get(1).pos))
                throw new IllegalArgumentException("F8 encounter target outside room or duplicated");
            var result=Optional.of(d);DEFINITIONS.put(resources,result);return result;
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private static OperationsData.Facility facilityAt(ServerLevel level,BlockPos at) {
        for(var f:OperationsData.get(level).facilities.values())if(InstituteOperations.validOffset(at.subtract(f.origin)))return f;return null;
    }
    private static ContainmentWarden boss(ServerLevel level,OperationsData.Facility f) {
        var p=f.warden;if(p.identity==null)return null;
        var e=level.getEntity(p.identity);
        return e instanceof ContainmentWarden w&&w.facilityOrigin().filter(f.origin::equals).isPresent()&&w.encounterRun().filter(run->run.equals(p.run)).isPresent()?w:null;
    }
    public static Optional<Status> status(ServerLevel level,BlockPos at) {
        var f=facilityAt(level,at);var d=definition(level);if(f==null||d.isEmpty())return Optional.empty();var p=f.warden;var mob=boss(level,f);
        return Optional.of(new Status(p.phase,p.identity,p.spawned,p.claimed,(p.phase==Phase.WARNING||p.phase==Phase.ACTIVE)&&!canAdvance(level,f,d.get()),
            mob==null?0:mob.getHealth(),mob!=null&&mob.exposed(),mob==null?0:mob.consoleMask(),mob==null?0:mob.consoleCooldown()));
    }
    public static boolean inArena(ContainmentWarden mob,Vec3 at) {
        if(!(mob.level() instanceof ServerLevel level)||mob.facilityOrigin().isEmpty())return false;
        return definition(level).map(d->d.bounds(mob.facilityOrigin().get()).contains(at)).orElse(false);
    }
    public static boolean canFight(ContainmentWarden mob) {
        if(!(mob.level() instanceof ServerLevel level)||mob.facilityOrigin().isEmpty())return false;
        var f=OperationsData.get(level).find(mob.facilityOrigin().get());var d=definition(level);
        return f!=null&&f.warden.phase==Phase.ACTIVE&&boss(level,f)==mob&&d.isPresent()&&canAdvance(level,f,d.get());
    }
    private static boolean loaded(ServerLevel level,OperationsData.Facility f,Definition d) {
        for(int x=(f.origin.getX()+d.min.getX())>>4;x<=(f.origin.getX()+d.max.getX()-1)>>4;x++)
            for(int z=(f.origin.getZ()+d.min.getZ())>>4;z<=(f.origin.getZ()+d.max.getZ()-1)>>4;z++)
                if(level.getChunkSource().getChunkNow(x,z)==null||!level.areEntitiesLoaded(ChunkPos.asLong(x,z)))return false;
        if(f.warden.spawned&&f.warden.lastPosition!=null){var cp=new ChunkPos(f.warden.lastPosition);if(level.getChunkSource().getChunkNow(cp.x,cp.z)==null||!level.areEntitiesLoaded(cp.toLong()))return false;}
        return true;
    }
    private static boolean canAdvance(ServerLevel level,OperationsData.Facility f,Definition d) {
        return f.repaired&&f.containmentPowered&&level.getDifficulty()!=Difficulty.PEACEFUL&&loaded(level,f,d)
            &&level.players().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&d.bounds(f.origin).contains(p.position()));
    }
    private static boolean validControl(ServerLevel level,OperationsData.Facility f,Target target) {
        var at=f.origin.offset(target.pos);var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);if(chunk==null)return false;
        return BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(at).getBlock()).toString().equals(target.block)
            &&chunk.getBlockEntity(at) instanceof OperationsControlEntity control&&control.role().equals(target.role)
            &&control.boundOrigin().filter(f.origin::equals).isPresent();
    }
    private static InteractionResult tell(ServerPlayer player,String key,boolean success,Object... args) {
        player.displayClientMessage(Component.translatable("message.ender_infection.warden."+key,args),false);
        return success?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    public static boolean isControl(ServerLevel level,BlockPos at) {
        var f=facilityAt(level,at);var d=definition(level);if(f==null||d.isEmpty())return false;
        return at.equals(f.origin.offset(d.get().control.pos))||d.get().consoles.stream().anyMatch(t->at.equals(f.origin.offset(t.pos)));
    }
    public static InteractionResult useControl(ServerLevel level,BlockPos at,ServerPlayer player) {
        var f=facilityAt(level,at);var definition=definition(level);if(f==null||definition.isEmpty())return InteractionResult.PASS;
        var d=definition.get();Target target=at.equals(f.origin.offset(d.control.pos))?d.control:null;int console=-1;
        for(int i=0;i<2;i++)if(at.equals(f.origin.offset(d.consoles.get(i).pos))){target=d.consoles.get(i);console=i;}
        if(target==null)return InteractionResult.PASS;
        if(!validControl(level,f,target)||player.distanceToSqr(Vec3.atCenterOf(at))>64)return tell(player,"unbound",false);
        var p=f.warden;
        if(console>=0) {
            if(p.phase!=Phase.ACTIVE||!canAdvance(level,f,d))return tell(player,"console_wait",false);
            if(!player.getMainHandItem().is(InstituteOperations.item("research_keycard")))return tell(player,"card",false);
            var mob=boss(level,f);if(mob==null||!mob.isAlive())return tell(player,"wait",false);
            if(!mob.activateConsole(console))return tell(player,mob.consoleCooldown()>0?"console_cooldown":"console_used",false,(mob.consoleCooldown()+19)/20);
            changed(level,f,"console:"+console);return tell(player,mob.exposed()?"exposed":"console_armed",true);
        }
        if(p.phase==Phase.DEFEATED) {
            if(p.claimed)return tell(player,"claimed",false);
            if(!f.repaired||!f.containmentPowered)return tell(player,"power",false);
            if(!InstituteOperations.transact(player,Map.of(),List.of(new ItemStack(WardenContent.CORE.get()),new ItemStack(InstituteOperations.item("void_crystal"),4))))return tell(player,"inventory_full",false);
            p.claimed=true;changed(level,f,"reward_claimed");InstituteOperations.synchronizeInventory(player);return tell(player,"reward",true);
        }
        if(p.phase==Phase.RECOVERY) {
            if(!player.isShiftKeyDown()||!player.getMainHandItem().isEmpty())return tell(player,"recovery",false);
            if(!loaded(level,f,d)||boss(level,f)!=null)return tell(player,"wait",false);
            f.warden=new Progress();changed(level,f,"recovered");return tell(player,"recovered",true);
        }
        if(p.phase!=Phase.IDLE)return tell(player,"status",true,Component.translatable("message.ender_infection.warden.phase."+p.phase.name().toLowerCase(java.util.Locale.ROOT)));
        if(!player.getMainHandItem().is(InstituteOperations.item("research_keycard")))return tell(player,"card",false);
        if(player.isCreative()||player.isSpectator()||!player.isAlive())return tell(player,"survival",false);
        if(!f.repaired||!f.containmentPowered||!f.analyzed||!f.separated||!f.archiveClaimed||f.incident.phase!=ContainmentIncident.Phase.COMPLETE)return tell(player,"prerequisites",false);
        if(!canAdvance(level,f,d))return tell(player,"wait",false);
        p.run=UUID.randomUUID();p.identity=UUID.randomUUID();p.phase=Phase.WARNING;p.warningTicks=0;p.spawned=false;p.lastPosition=f.origin.offset(d.spawn);
        changed(level,f,"started");return tell(player,"started",true);
    }
    private static void changed(ServerLevel level,OperationsData.Facility f,String reason) {
        OperationsData.get(level).setDirty();InstituteOperations.incidentChanged(level,f,"boss:F8:"+reason);
    }
    public static void defeated(ContainmentWarden mob) {
        if(!(mob.level() instanceof ServerLevel level)||mob.facilityOrigin().isEmpty())return;
        var f=OperationsData.get(level).find(mob.facilityOrigin().get());
        if(f!=null&&f.warden.phase==Phase.ACTIVE&&f.warden.spawned&&boss(level,f)==mob){f.warden.phase=Phase.DEFEATED;changed(level,f,"defeated");}
    }
    public static void tick(ServerLevel level,OperationsData data) {
        if(level.getGameTime()%20!=0)return;var definition=definition(level);if(definition.isEmpty())return;var d=definition.get();
        for(var f:data.facilities.values()) {
            var p=f.warden;for(var target:List.of(d.control,d.consoles.get(0),d.consoles.get(1)))if(validControl(level,f,target))
                ((OperationsControlEntity)level.getBlockEntity(f.origin.offset(target.pos))).updatePowered(f.repaired&&f.containmentPowered);
            if(p.phase!=Phase.WARNING&&p.phase!=Phase.ACTIVE||!canAdvance(level,f,d))continue;
            if(p.phase==Phase.WARNING&&!p.spawned) {
                p.warningTicks=Math.min(60,p.warningTicks+20);data.setDirty();if(p.warningTicks<60)continue;
                if(level.getEntity(p.identity)!=null){p.phase=Phase.RECOVERY;changed(level,f,"identity_collision");continue;}
                var mob=WardenContent.WARDEN.get().create(level);if(mob==null)continue;var at=f.origin.offset(d.spawn);
                mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,180,0);mob.setUUID(p.identity);mob.bindFacility(f.origin,p.run);
                if(!level.noCollision(mob)){mob.discard();continue;}
                p.spawned=true;p.phase=Phase.ACTIVE;p.lastPosition=at;data.setDirty();
                if(!level.addFreshEntity(mob)){p.spawned=false;p.phase=Phase.WARNING;data.setDirty();}else changed(level,f,"spawned");
            } else if(p.phase==Phase.ACTIVE) {
                var mob=boss(level,f);
                if(mob==null) { p.missingTicks+=20;if(p.missingTicks>=100){p.phase=Phase.RECOVERY;changed(level,f,"missing_identity");}continue; }
                p.missingTicks=0;
                if(!d.bounds(f.origin).deflate(1,0,1).contains(mob.position())) {
                    var at=Vec3.atBottomCenterOf(f.origin.offset(d.spawn));
                    if(level.noCollision(mob,mob.getBoundingBox().move(at.subtract(mob.position()))))mob.teleportTo(at.x,at.y,at.z);
                }
                if(!mob.blockPosition().equals(p.lastPosition)){p.lastPosition=mob.blockPosition();data.setDirty();}
            }
        }
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof ContainmentWarden mob)||mob.facilityOrigin().isEmpty())return;
        var f=OperationsData.get(level).find(mob.facilityOrigin().get());
        if(f==null||f.warden.phase!=Phase.ACTIVE||!f.warden.spawned||!mob.getUUID().equals(f.warden.identity)
                ||mob.encounterRun().filter(run->run.equals(f.warden.run)).isEmpty()){event.setCanceled(true);mob.discard();}
    }
}
