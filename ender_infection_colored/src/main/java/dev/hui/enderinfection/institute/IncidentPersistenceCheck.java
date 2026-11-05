package dev.hui.enderinfection.institute;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.LabDetailBlock;
import dev.hui.enderinfection.entity.InfectedMob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in dedicated-server acceptance fixture, entirely inside the Gradle build directory. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class IncidentPersistenceCheck {
    private static IncidentPersistenceCheck check;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String stage;
    private final BlockPos origin=new BlockPos(1000000,-40,1000000);
    private final ContainmentIncident.Definition definition;
    private final Set<ChunkPos> chunks=new LinkedHashSet<>();
    private final Path receipt=Path.of("incident-identities.txt");
    private ServerPlayer actor;
    private io.netty.channel.embedded.EmbeddedChannel transport;
    private int ticks,step,unloadedAt;
    private boolean finished;
    private net.minecraft.nbt.CompoundTag paused;

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        String stage=System.getProperty("ender_infection.incidentPersistenceCheck","");
        if(stage.isEmpty()||event.phase!=TickEvent.Phase.END)return;
        if(check==null)check=new IncidentPersistenceCheck(event.getServer(),stage);
        if(check.finished)return;
        try{check.advance();}catch(Exception|AssertionError error){error.printStackTrace();System.err.println("INCIDENT_PERSISTENCE_FAIL "+stage+" "+error);check.stop(false);}
    }
    private IncidentPersistenceCheck(MinecraftServer server,String stage){
        this.server=server;this.level=server.overworld();this.stage=stage;
        definition=ContainmentIncident.definition(level).orElseThrow();
        server.setDifficulty(Difficulty.NORMAL,true);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=(origin.getX()+definition.min().getX())>>4;x<=(origin.getX()+definition.max().getX()-1)>>4;x++)
            for(int z=(origin.getZ()+definition.min().getZ())>>4;z<=(origin.getZ()+definition.max().getZ()-1)>>4;z++)chunks.add(new ChunkPos(x,z));
        force(true);
        if(stage.equals("seed")){
            require(OperationsData.get(level).find(origin)==null,"Seed world was reused");
            for(var pos:BlockPos.betweenClosed(origin.offset(definition.min()).below(),origin.offset(definition.max()).offset(-1,-1,-1)))
                if(pos.getY()==origin.getY()+definition.min().getY()-1)level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            var targets=new ArrayList<>(definition.faults());targets.add(definition.control());
            for(var target:targets){var pos=origin.offset(target.pos());level.setBlock(pos,BuiltInRegistries.BLOCK.get(new ResourceLocation(target.block())).defaultBlockState(),3);if(level.getBlockEntity(pos) instanceof OperationsControlEntity be)be.bind(target.pos());}
            for(var pos:definition.alarms())level.setBlock(origin.offset(pos),Blocks.REDSTONE_LAMP.defaultBlockState(),3);
            InstituteOperations.registerFacility(level,origin);var f=facility();f.repaired=true;f.containmentPowered=true;f.analyzed=true;f.separated=true;OperationsData.get(level).setDirty();
        }
        attach();
    }
    private OperationsData.Facility facility(){return Objects.requireNonNull(OperationsData.get(level).find(origin),"Persisted facility missing");}
    private void force(boolean enabled){for(var cp:chunks){level.setChunkForced(cp.x,cp.z,enabled);if(enabled)level.getChunk(cp.x,cp.z);}}
    private boolean loaded(){return chunks.stream().allMatch(cp->level.getChunkSource().getChunkNow(cp.x,cp.z)!=null&&level.areEntitiesLoaded(cp.toLong()));}
    private void attach(){
        var profile=new GameProfile(UUID.randomUUID(),"IncidentProbe");actor=new ServerPlayer(server,level,profile);
        var connection=new Connection(PacketFlow.SERVERBOUND);transport=new io.netty.channel.embedded.EmbeddedChannel(connection);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor);
        actor.setGameMode(GameType.SURVIVAL);actor.setNoGravity(true);actor.getAbilities().invulnerable=true;
        var at=origin.offset(definition.control().approach());actor.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);level.addNewPlayer(actor);
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(InstituteOperations.item("research_keycard")));
    }
    private void detach(){if(actor!=null){level.removePlayerImmediately(actor,Entity.RemovalReason.DISCARDED);actor=null;}if(transport!=null){transport.finishAndReleaseAll();transport=null;}}
    private net.minecraft.world.InteractionResult control(){return InstituteOperations.useControl(level,origin.offset(definition.control().pos()),actor,"terminal");}
    private List<InfectedMob> alive(){return Arrays.stream(facility().incident.identities).filter(Objects::nonNull).map(level::getEntity).filter(e->e instanceof InfectedMob&&e.isAlive()).map(e->(InfectedMob)e).toList();}
    private List<String> identities(){return Arrays.stream(facility().incident.identities).map(UUID::toString).toList();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void fixture(String role,ItemStack held,boolean shift){
        var target=definition.faults().stream().filter(t->t.role().equals(role)).findFirst().orElseThrow();var at=origin.offset(target.pos());var approach=origin.offset(target.approach());actor.setPos(approach.getX()+.5,approach.getY(),approach.getZ()+.5);actor.setItemInHand(InteractionHand.MAIN_HAND,held);actor.setShiftKeyDown(shift);
        require(((LabDetailBlock)level.getBlockState(at).getBlock()).use(level.getBlockState(at),level,at,actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false)).consumesAction(),"Actual repair interaction failed: "+role);actor.setShiftKeyDown(false);
    }
    private void advance() throws Exception {
        require(++ticks<2400,"Persistence probe timed out stage="+stage+" step="+step);
        for(var mob:alive())mob.setNoAi(true);
        var p=facility().incident;
        if(stage.equals("seed")){
            if(!loaded())return;
            if(p.phase==ContainmentIncident.Phase.IDLE){require(control().consumesAction(),"Actual start interaction failed");return;}
            if(Integer.bitCount(p.spawned)==3){require(alive().size()==3,"Seed did not create three real entities");Files.write(receipt,identities());stop(true);}return;
        }
        require(Files.readAllLines(receipt).equals(identities()),"Restart changed the reserved six identities");
        if(stage.equals("claimed")){
            if(!loaded())return;
            require(p.phase==ContainmentIncident.Phase.COMPLETE&&p.spawned==63&&p.resolved==63&&p.faults==0&&p.claimed,"Completed progress was lost across restart");
            require(alive().isEmpty()&&control()==InteractionResult.FAIL,"Restart refilled monsters or duplicated reward");stop(true);return;
        }
        require(stage.equals("resume"),"Unknown probe stage");
        if(step==0){
            if(!loaded()||alive().size()!=3)return;
            require(p.spawned==7&&p.resolved==0&&p.phase==ContainmentIncident.Phase.WAVE_ONE,"Active progress lost on restart");
            System.out.println("INCIDENT_ACTIVE_RESTART_PASS "+identities());paused=p.save();detach();force(false);step=1;return;
        }
        if(step==1){
            require(p.save().equals(paused),"Unloaded encounter changed without an eligible player");
            if(chunks.stream().anyMatch(cp->level.getChunkSource().getChunkNow(cp.x,cp.z)!=null||level.areEntitiesLoaded(cp.toLong())))return;
            System.out.println("INCIDENT_CHUNKS_UNLOADED_PASS "+chunks);unloadedAt=ticks;step=2;return;
        }
        if(step==2){require(p.save().equals(paused),"Unloaded timer/quota advanced");if(ticks-unloadedAt<80)return;force(true);attach();step=3;return;}
        if(step==3){if(!loaded()||alive().size()!=3)return;require(p.save().equals(paused),"Chunk reload changed phase/identities/quota");System.out.println("INCIDENT_CHUNKS_RELOADED_PASS");step=4;return;}
        if((p.phase==ContainmentIncident.Phase.WAVE_ONE&&p.spawned==7)||(p.phase==ContainmentIncident.Phase.WAVE_TWO&&p.spawned==63))for(var mob:alive())mob.hurt(level.damageSources().generic(),100000F);
        if(p.phase==ContainmentIncident.Phase.ISOLATION){
            fixture("cooling",new ItemStack(Items.COPPER_INGOT,2),false);fixture("sensor",new ItemStack(Items.REDSTONE),false);fixture("valve",ItemStack.EMPTY,true);
            actor.getInventory().clearContent();require(control().consumesAction()&&p.claimed,"Reward claim failed after real restart");require(control()==InteractionResult.FAIL,"Reward repeated after real restart");stop(true);
        }
    }
    private void stop(boolean success){finished=true;detach();server.saveEverything(false,true,true);if(success)System.out.println("INCIDENT_PERSISTENCE_PASS "+stage);server.halt(false);}
}
