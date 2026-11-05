package dev.hui.enderinfection.institute;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.ContainmentWarden;
import dev.hui.enderinfection.warden.WardenContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Three-process restart/unload acceptance; enabled only by an explicit Gradle test property. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class WardenPersistenceCheck {
    private static WardenPersistenceCheck check;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String stage;
    private final BlockPos origin=new BlockPos(1100000,-40,1100000);
    private final WardenEncounter.Definition definition;
    private final Set<ChunkPos> chunks=new LinkedHashSet<>();
    private final Path receipt=Path.of("warden-identity.txt");
    private ServerPlayer actor;
    private io.netty.channel.embedded.EmbeddedChannel transport;
    private int ticks,step,unloadedAt;
    private boolean finished;
    private CompoundTag paused;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        String stage=System.getProperty("ender_infection.wardenPersistenceCheck","");
        if(stage.isEmpty())return;
        try{
            if(check==null)check=new WardenPersistenceCheck(event.getServer(),stage);
            if(!check.finished)check.advance();
        }catch(Exception|AssertionError error){error.printStackTrace();System.err.println("WARDEN_PERSISTENCE_FAIL "+stage+" "+error);if(check!=null)check.stop(false);else event.getServer().halt(false);}
    }
    private WardenPersistenceCheck(MinecraftServer server,String stage) {
        this.server=server;level=server.overworld();this.stage=stage;definition=WardenEncounter.definition(level).orElseThrow();
        server.setDifficulty(Difficulty.NORMAL,true);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        for(int x=(origin.getX()+definition.min().getX())>>4;x<=(origin.getX()+definition.max().getX()-1)>>4;x++)
            for(int z=(origin.getZ()+definition.min().getZ())>>4;z<=(origin.getZ()+definition.max().getZ()-1)>>4;z++)chunks.add(new ChunkPos(x,z));
        force(true);
        if(stage.equals("seed")) {
            require(OperationsData.get(level).find(origin)==null,"Seed world was reused");
            for(var pos:BlockPos.betweenClosed(origin.offset(definition.min()).below(),origin.offset(definition.max()).offset(-1,-1,-1)))
                if(pos.getY()==origin.getY()+definition.min().getY()-1)level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            var targets=new ArrayList<>(definition.consoles());targets.add(definition.control());
            for(var target:targets){var at=origin.offset(target.pos());level.setBlock(at,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target.block())).defaultBlockState(),3);((OperationsControlEntity)level.getBlockEntity(at)).bind(target.pos());}
            InstituteOperations.registerFacility(level,origin);var f=facility();f.repaired=true;f.containmentPowered=true;f.analyzed=true;f.separated=true;f.archiveClaimed=true;f.incident.phase=ContainmentIncident.Phase.COMPLETE;OperationsData.get(level).setDirty();
        }
        attach();
    }
    private OperationsData.Facility facility(){return Objects.requireNonNull(OperationsData.get(level).find(origin),"Persisted facility missing");}
    private ContainmentWarden boss(){var id=facility().warden.identity;return id!=null&&level.getEntity(id) instanceof ContainmentWarden mob?mob:null;}
    private boolean loaded(){return chunks.stream().allMatch(cp->level.getChunkSource().getChunkNow(cp.x,cp.z)!=null&&level.areEntitiesLoaded(cp.toLong()));}
    private void force(boolean value){for(var cp:chunks){level.setChunkForced(cp.x,cp.z,value);if(value)level.getChunk(cp.x,cp.z);}}
    private void attach(){
        actor=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"WardenProbe"),net.minecraft.server.level.ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);transport=new io.netty.channel.embedded.EmbeddedChannel(connection);
        actor.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,actor,net.minecraft.server.network.CommonListenerCookie.createInitial(actor.getGameProfile(),false));actor.setGameMode(GameType.SURVIVAL);actor.setNoGravity(true);actor.getAbilities().invulnerable=true;
        var at=origin.offset(definition.control().approach());actor.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);level.addNewPlayer(actor);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(InstituteOperations.item("research_keycard")));
    }
    private void detach(){if(actor!=null){level.removePlayerImmediately(actor,Entity.RemovalReason.DISCARDED);actor=null;}if(transport!=null){transport.finishAndReleaseAll();transport=null;}}
    private InteractionResult use(WardenEncounter.Target target){var at=origin.offset(target.approach());actor.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);return InstituteOperations.useControl(level,origin.offset(target.pos()),actor,target.role());}
    private List<String> identities(){var p=facility().warden;return List.of(p.identity.toString(),p.run.toString());}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void advance()throws Exception{
        require(++ticks<2400,"Persistence timeout stage="+stage+" step="+step);
        var p=facility().warden;var mob=boss();if(mob!=null)mob.setNoAi(true);
        if(stage.equals("seed")){
            if(!loaded())return;
            if(p.phase==WardenEncounter.Phase.IDLE){require(use(definition.control()).consumesAction(),"Actual start failed");return;}
            if(p.phase==WardenEncounter.Phase.ACTIVE&&mob!=null){mob.setHealth(231);require(use(definition.consoles().get(0)).consumesAction()&&mob.consoleMask()==1,"First console failed");Files.write(receipt,identities());stop(true);}return;
        }
        require(Files.readAllLines(receipt).equals(identities()),"Restart replaced boss/run identity");
        if(stage.equals("claimed")){
            if(!loaded())return;
            require(p.phase==WardenEncounter.Phase.DEFEATED&&p.claimed&&p.spawned,"Completed/claimed state lost on restart");
            require((mob==null||!mob.isAlive())&&use(definition.control())==InteractionResult.FAIL,"Restart respawned boss or reissued reward");stop(true);return;
        }
        require(stage.equals("resume"),"Unknown stage");
        if(step==0){
            if(!loaded()||mob==null)return;
            require(p.phase==WardenEncounter.Phase.ACTIVE&&mob.getHealth()==231&&mob.consoleMask()==1,"Active entity health/consoles/progress lost on restart");
            require(level.getEntitiesOfClass(ContainmentWarden.class,definition.bounds(origin)).size()==1,"Duplicate boss after restart");
            System.out.println("WARDEN_ACTIVE_RESTART_PASS "+identities()+" health="+mob.getHealth()+" consoles="+mob.consoleMask());
            paused=p.save();detach();force(false);step=1;return;
        }
        if(step==1){
            require(p.save().equals(paused),"Unloaded progress changed");
            if(chunks.stream().anyMatch(cp->level.getChunkSource().getChunkNow(cp.x,cp.z)!=null||level.areEntitiesLoaded(cp.toLong())))return;
            System.out.println("WARDEN_CHUNKS_UNLOADED_PASS");unloadedAt=ticks;step=2;return;
        }
        if(step==2){require(p.save().equals(paused),"Unloaded encounter advanced");if(ticks-unloadedAt<80)return;force(true);attach();step=3;return;}
        if(step==3){
            if(!loaded()||mob==null)return;
            require(p.save().equals(paused)&&mob.getHealth()==231&&mob.consoleMask()==1,"Chunk reload replaced/reset entity or progress");
            require(level.getEntitiesOfClass(ContainmentWarden.class,definition.bounds(origin)).size()==1,"Chunk reload duplicated entity");System.out.println("WARDEN_CHUNKS_RELOADED_PASS");
            require(use(definition.consoles().get(1)).consumesAction()&&mob.exposed(),"Persisted first console did not combine with second");
            mob.hurt(level.damageSources().generic(),100000);require(p.phase==WardenEncounter.Phase.DEFEATED,"Death not recorded");
            actor.getInventory().clearContent();require(use(definition.control()).consumesAction()&&p.claimed,"Reward claim failed");
            require(actor.getInventory().countItem(WardenContent.CORE.get())==1&&use(definition.control())==InteractionResult.FAIL,"Core reward duplicated or missing");stop(true);
        }
    }
    private void stop(boolean success){finished=true;detach();server.saveEverything(false,true,true);if(success)System.out.println("WARDEN_PERSISTENCE_PASS "+stage);server.halt(false);}
}
