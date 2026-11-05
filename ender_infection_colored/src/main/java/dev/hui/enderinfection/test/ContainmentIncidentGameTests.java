package dev.hui.enderinfection.test;

import dev.hui.enderinfection.institute.*;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.design.LabDetailBlock;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("ender_incident_checks")
@PrefixGameTestTemplate(false)
public final class ContainmentIncidentGameTests {
    private static Item item(String key){return BuiltInRegistries.ITEM.get(new ResourceLocation("ender_infection",key));}
    private static Block block(String key){return BuiltInRegistries.BLOCK.get(new ResourceLocation(key));}
    private static final class Encounter implements AutoCloseable {
        final GameTestHelper h;final LabOperationsGameTests.Fixture f;final ContainmentIncident.Definition d;final ServerPlayer actor;
        final Difficulty difficulty;boolean closed;int stage;
        Encounter(GameTestHelper h){
            this.h=h;f=LabOperationsGameTests.Fixture.create(h);d=ContainmentIncident.definition(f.level).orElseThrow();difficulty=f.level.getDifficulty();
            f.level.getServer().setDifficulty(Difficulty.NORMAL,true);
            for(var at:BlockPos.betweenClosed(f.origin.offset(d.min()).below(),f.origin.offset(d.max()).offset(-1,-1,-1))){
                if(at.getY()==f.origin.getY()+d.min().getY()-1)f.put(at,Blocks.STONE.defaultBlockState());
            }
            put(d.control());for(var target:d.faults())put(target);
            for(var offset:d.alarms())f.put(f.origin.offset(offset),Blocks.REDSTONE_LAMP.defaultBlockState());
            actor=f.player();actor.getAbilities().invulnerable=true;actor.setPos(control().getX()+1.5,control().getY(),control().getZ()+.5);f.level.addNewPlayer(actor);
        }
        void put(ContainmentIncident.Target target){var at=f.origin.offset(target.pos());f.put(at,block(target.block()).defaultBlockState());if(f.level.getBlockEntity(at) instanceof OperationsControlEntity be)be.bind(target.pos());}
        BlockPos control(){return f.origin.offset(d.control().pos());}
        ContainmentIncident.Status state(){return ContainmentIncident.status(f.level,control()).orElseThrow();}
        CompoundTag saved(){return OperationsData.get(f.level).save(new CompoundTag());}
        CompoundTag facilityTag(CompoundTag root){for(var entry:root.getList("Facilities",10)){var t=(CompoundTag)entry;if(t.getLong("Origin")==f.origin.asLong())return t;}throw new IllegalStateException("Fixture was not registered");}
        void ready(){
            var root=saved();var facility=facilityTag(root);facility.putBoolean("Repaired",true);facility.putBoolean("ContainmentPowered",true);facility.putBoolean("ServicePowered",false);facility.putBoolean("Analyzed",true);facility.putBoolean("Separated",true);
            f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(root));actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("research_keycard")));
        }
        boolean start(){return ((OperationsControlBlock)f.level.getBlockState(control()).getBlock()).use(f.level.getBlockState(control()),f.level,control(),actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(control()),Direction.NORTH,control(),false)).consumesAction();}
        InteractionResult use(String role,ServerPlayer player){
            var t=d.faults().stream().filter(x->x.role().equals(role)).findFirst().orElseThrow();var at=f.origin.offset(t.pos());player.setPos(f.origin.getX()+t.approach().getX()+.5,f.origin.getY()+t.approach().getY(),f.origin.getZ()+t.approach().getZ()+.5);
            return ((LabDetailBlock)f.level.getBlockState(at).getBlock()).use(f.level.getBlockState(at),f.level,at,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false));
        }
        Set<UUID> identities(){var ids=new LinkedHashSet<UUID>();for(var value:facilityTag(saved()).getCompound("E8Incident").getList("Slots",10))ids.add(((CompoundTag)value).getUUID("Identity"));return ids;}
        List<InfectedMob> alive(){var ids=identities();return f.level.getEntitiesOfClass(InfectedMob.class,d.bounds(f.origin).inflate(2)).stream().filter(e->ids.contains(e.getUUID())&&e.isAlive()).toList();}
        void freezeMobs(){for(var mob:alive())mob.setNoAi(true);}
        void killWave(){for(var mob:alive())mob.hurt(f.level.damageSources().generic(),100000F);}
        void repairs(){actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COPPER_INGOT,2));h.assertTrue(use("cooling",actor).consumesAction(),"Cooling repair failed");actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.REDSTONE));h.assertTrue(use("sensor",actor).consumesAction(),"Sensor repair failed");}
        void finishValve(){actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.setShiftKeyDown(true);h.assertTrue(use("valve",actor).consumesAction(),"Isolation valve did not reset");actor.setShiftKeyDown(false);}
        int count(ServerPlayer player,Item target){int count=0;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(target))count+=player.getInventory().getItem(i).getCount();return count;}
        void run(Consumer<Encounter> tick){
            h.runAfterDelay(800,()->{if(!closed){String diagnostic=state().toString();close();h.fail("Incident timed out: "+diagnostic);}});
            h.onEachTick(()->{if(closed)return;try{freezeMobs();tick.accept(this);}catch(RuntimeException|Error error){close();throw error;}});
        }
        boolean begun(){if(state().phase()!=ContainmentIncident.Phase.IDLE)return true;return start();}
        void success(){close();h.succeed();}
        @Override public void close(){if(closed)return;closed=true;try{for(var mob:alive())mob.discard();f.level.removePlayerImmediately(actor,Entity.RemovalReason.DISCARDED);}finally{try{f.close();}finally{f.level.getServer().setDifficulty(difficulty,true);}}}
    }
    private static Encounter prepared(GameTestHelper h){var e=new Encounter(h);e.ready();return e;}

    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_cycle")
    public static void twoFiniteWavesRepairIsolationAndRewardRemainAtomic(GameTestHelper h){
        var e=prepared(h);e.run(x->{if(!x.begun())return;var s=x.state();
            if((s.phase()==ContainmentIncident.Phase.WAVE_ONE&&s.spawned()==3)||(s.phase()==ContainmentIncident.Phase.WAVE_TWO&&s.spawned()==6))x.killWave();
            if(s.phase()==ContainmentIncident.Phase.ISOLATION){
                h.assertTrue(s.spawned()==6&&s.resolved()==6&&x.identities().size()==6,"Wave quota or identities changed");
                h.assertTrue(x.use("valve",x.actor)==InteractionResult.FAIL,"Valve closed before the fixed faults");
                x.repairs();x.finishValve();h.assertTrue(x.state().phase()==ContainmentIncident.Phase.COMPLETE,"Isolation did not finish");
                x.actor.getInventory().clearContent();h.assertTrue(x.start()&&x.state().rewardClaimed(),"Reward was not awarded");
                h.assertTrue(x.count(x.actor,item("research_record"))==1&&x.count(x.actor,item("void_crystal"))==1,"Wrong reward count");
                h.assertTrue(!x.start()&&x.count(x.actor,item("void_crystal"))==1,"Reward or experiment repeated");
                h.assertTrue(!x.f.state().servicePowered()&&x.f.state().containmentPowered(),"Recovery changed the original facility switches");x.success();
            }
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_start")
    public static void researchGuardAndTwoPlayersCannotRestartTheSameEncounter(GameTestHelper h){
        var e=new Encounter(h);e.actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("research_keycard")));h.assertTrue(!e.start()&&e.state().phase()==ContainmentIncident.Phase.IDLE,"Research prerequisites were ignored");e.ready();
        e.run(x->{if(!x.begun())return;var before=x.saved();var identities=x.identities();var other=x.f.player();other.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("research_keycard")));
            var at=x.control();InstituteOperations.useControl(x.f.level,at,other,"terminal");h.assertTrue(x.identities().equals(identities)&&x.state().spawned()==0&&x.saved().equals(before),"Second player replaced the encounter or refreshed its warning");x.success();});
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_partial")
    public static void blockedThirdSpawnRetainsTheFirstTwoAndOnlyRetriesItsOwnSlot(GameTestHelper h){
        var e=prepared(h);var blocked=e.f.origin.offset(e.d.spawns().get(2));for(var at:BlockPos.betweenClosed(blocked.offset(-1,0,-1),blocked.offset(1,2,1)))e.f.put(at,Blocks.STONE.defaultBlockState());
        e.run(x->{if(!x.begun())return;if(x.state().phase()!=ContainmentIncident.Phase.WAVE_ONE)return;
            if(x.stage==0&&x.state().spawned()==2){x.stage=1;h.runAfterDelay(60,()->{if(x.closed)return;h.assertTrue(x.state().spawned()==2&&x.alive().size()==2,"Partial failure duplicated successful spawns");for(var at:BlockPos.betweenClosed(blocked.offset(-1,0,-1),blocked.offset(1,2,1)))x.f.put(at,Blocks.AIR.defaultBlockState());});}
            else if(x.stage==1&&x.state().spawned()==3){h.assertTrue(x.alive().size()==3&&x.identities().size()==6,"Retry changed the reserved roster");x.success();}
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_missing")
    public static void absentSavedIdentityIsNeverReplacedOnReload(GameTestHelper h){
        var e=prepared(h);e.run(x->{if(!x.begun())return;if(x.stage==0&&x.state().spawned()==3){var ids=x.identities();x.alive().get(0).discard();x.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(x.saved()));x.stage=1;
                h.runAfterDelay(80,()->{if(x.closed)return;h.assertTrue(x.state().spawned()==3&&x.state().resolved()==0&&x.alive().size()==2&&x.identities().equals(ids),"Missing entity was replaced or treated as defeated");x.success();});}});
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_peaceful")
    public static void peacefulRetiresSpentSlotsAndNeverRefillsTheirQuota(GameTestHelper h){
        var e=prepared(h);e.run(x->{if(!x.begun())return;if(x.stage==0&&x.state().spawned()==3){x.stage=1;x.f.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);h.runAfterDelay(60,()->{if(x.closed)return;h.assertTrue(x.state().spawned()==3&&x.state().resolved()==3&&x.alive().isEmpty(),"Peaceful failed to retire existing identities without drops");x.f.level.getServer().setDifficulty(Difficulty.NORMAL,true);});}
            if(x.stage==1&&x.state().spawned()==6){h.assertTrue(x.alive().size()==3,"Return from Peaceful refilled the first wave");x.success();}});
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_pause")
    public static void noEligiblePlayersPausesTheWarningAndDoesNotSpawn(GameTestHelper h){
        var e=prepared(h);e.run(x->{if(!x.begun())return;if(x.stage==0){x.stage=1;x.actor.setGameMode(GameType.SPECTATOR);var before=x.saved();h.runAfterDelay(100,()->{if(x.closed)return;h.assertTrue(x.state().phase()==ContainmentIncident.Phase.WARNING&&x.state().spawned()==0&&x.saved().equals(before),"Absent participants advanced or reset the warning");x.success();});}});
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_claim")
    public static void fullInventoryAndReloadCannotLoseOrDuplicateTheSharedReward(GameTestHelper h){
        var e=prepared(h);e.run(x->{if(!x.begun())return;var s=x.state();if((s.phase()==ContainmentIncident.Phase.WAVE_ONE&&s.spawned()==3)||(s.phase()==ContainmentIncident.Phase.WAVE_TWO&&s.spawned()==6))x.killWave();
            if(s.phase()==ContainmentIncident.Phase.ISOLATION){x.repairs();x.finishValve();x.actor.getInventory().clearContent();for(int i=0;i<36;i++)x.actor.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
                h.assertTrue(!x.start()&&!x.state().rewardClaimed(),"Full inventory consumed a reward");x.actor.getInventory().clearContent();h.assertTrue(x.start(),"Reward could not be claimed after making room");
                x.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(x.saved()));var other=x.f.player();h.assertTrue(InstituteOperations.useControl(x.f.level,x.control(),other,"terminal")==InteractionResult.FAIL,"Second player or reload duplicated reward");x.success();}});
    }
    @GameTest(template="empty64",templateNamespace="ender_incident_checks",timeoutTicks=850,batch="incident_reload")
    public static void activeNbtRoundTripKeepsSixFixedIdentitiesAndLocalPowerOverlay(GameTestHelper h){
        var e=prepared(h);e.run(x->{if(!x.begun())return;if(x.stage==0&&x.state().spawned()==3){var ids=x.identities();var before=x.state();var root=x.saved();var loaded=OperationsData.load(root);h.assertTrue(loaded.save(new CompoundTag()).equals(root),"Incident save/load discarded its phase or quota");x.f.level.getDataStorage().set(OperationsData.NAME,loaded);
                h.assertTrue(x.identities().equals(ids)&&x.state().spawned()==before.spawned(),"Reload allocated new identities");
                BlockPos lamp=null;
                for(var value:x.f.operations.getAsJsonArray("managed_lights")){var row=value.getAsJsonObject();var a=row.getAsJsonArray("pos");var rel=new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());if(x.d.bounds(BlockPos.ZERO).contains(Vec3.atCenterOf(rel))){lamp=x.f.origin.offset(rel);x.f.put(lamp,block(row.get("block").getAsString()).defaultBlockState());break;}}
                h.assertTrue(lamp!=null&&!dev.hui.enderinfection.api.institute.InstituteApi.isPowered(x.f.level,lamp),"Local fault did not remove E8 lamp power");
                h.assertTrue(dev.hui.enderinfection.api.institute.InstituteApi.isPowered(x.f.level,x.control()),"Emergency terminal lost its independent power");
                x.actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COPPER_INGOT,2));h.assertTrue(x.use("cooling",x.actor).consumesAction()&&x.count(x.actor,Items.COPPER_INGOT)==0,"Repair was not paid exactly once");x.actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COPPER_INGOT,2));x.use("cooling",x.actor);h.assertTrue(x.count(x.actor,Items.COPPER_INGOT)==2,"Repeated repair charged again");h.assertTrue(dev.hui.enderinfection.api.institute.InstituteApi.isPowered(x.f.level,lamp)&&x.f.state().containmentPowered()&&!x.f.state().servicePowered(),"Cooling recovery failed to restore local power or changed global switches");x.success();}});
    }
}
