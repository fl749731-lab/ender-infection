package dev.hui.enderinfection.test;

import dev.hui.enderinfection.entity.ContainmentWarden;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.institute.*;
import dev.hui.enderinfection.warden.WardenContent;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("ender_warden_checks")
@PrefixGameTestTemplate(false)
public final class WardenGameTests {
    private static final class Scene implements AutoCloseable {
        final GameTestHelper h;final LabOperationsGameTests.Fixture f;final WardenEncounter.Definition d;final ServerPlayer player;
        final Difficulty difficulty;final List<Entity> spawned=new ArrayList<>();boolean closed,freeze=true;int stage;
        Scene(GameTestHelper h) {
            this.h=h;f=LabOperationsGameTests.Fixture.create(h);d=WardenEncounter.definition(f.level).orElseThrow();difficulty=f.level.getDifficulty();f.level.getServer().setDifficulty(Difficulty.NORMAL,true);
            for(var p:BlockPos.betweenClosed(f.origin.offset(d.min()).below(),f.origin.offset(d.max()).offset(-1,-1,-1)))
                if(p.getY()==f.origin.getY()+d.min().getY()-1)f.put(p,Blocks.STONE.defaultBlockState());
            put(d.control());for(var target:d.consoles())put(target);
            InstituteOperations.registerFacility(f.level,f.origin);
            player=f.player();player.getAbilities().invulnerable=true;point(d.control());f.level.addNewPlayer(player);
        }
        void put(WardenEncounter.Target target) {
            var at=f.origin.offset(target.pos());var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(target.block()));
            f.put(at,block.defaultBlockState());((OperationsControlEntity)f.level.getBlockEntity(at)).bind(target.pos());
        }
        void point(WardenEncounter.Target target) { var p=f.origin.offset(target.approach());player.setPos(p.getX()+.5,p.getY(),p.getZ()+.5); }
        CompoundTag saved() { return OperationsData.get(f.level).save(new CompoundTag()); }
        CompoundTag facility(CompoundTag root) { for(var v:root.getList("Facilities",10)){var t=(CompoundTag)v;if(t.getLong("Origin")==f.origin.asLong())return t;}throw new IllegalStateException("Missing fixture facility"); }
        void ready() {
            var root=saved();var tag=facility(root);
            for(String key:List.of("Repaired","ContainmentPowered","Analyzed","Separated","ArchiveClaimed"))tag.putBoolean(key,true);
            var incident=new CompoundTag();incident.putString("Phase","COMPLETE");tag.put("E8Incident",incident);
            f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(root));card();
        }
        void card() { player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("ender_infection","research_keycard")))); }
        WardenEncounter.Status status() { return WardenEncounter.status(f.level,f.origin.offset(d.control().pos())).orElseThrow(); }
        ContainmentWarden boss() { return status().identity()==null?null:f.level.getEntity(status().identity()) instanceof ContainmentWarden mob?mob:null; }
        InteractionResult use(WardenEncounter.Target target) { point(target);return InstituteOperations.useControl(f.level,f.origin.offset(target.pos()),player,target.role()); }
        boolean start() { return use(d.control()).consumesAction(); }
        int count(Item item) { int result=0;for(int i=0;i<36;i++)if(player.getInventory().getItem(i).is(item))result+=player.getInventory().getItem(i).getCount();return result; }
        void run(Consumer<Scene> step) {
            h.runAfterDelay(700,()->{if(!closed){var status=status();close();h.fail("Warden scene timed out: "+status);}});
            h.onEachTick(()->{if(closed)return;try{if(boss()!=null&&freeze)boss().setNoAi(true);step.accept(this);}catch(RuntimeException|Error error){close();throw error;}});
        }
        boolean active() { if(status().phase()==WardenEncounter.Phase.IDLE)start();return status().phase()==WardenEncounter.Phase.ACTIVE&&boss()!=null; }
        ContainmentWarden standalone(double distance) {
            freeze=false;player.setGameMode(GameType.SPECTATOR);var at=Vec3.atBottomCenterOf(f.origin.offset(d.spawn()));var mob=WardenContent.WARDEN.get().create(f.level);mob.moveTo(at.x,at.y,at.z,0,0);
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);f.level.addFreshEntity(mob);spawned.add(mob);
            var target=EntityType.IRON_GOLEM.create(f.level);target.setNoAi(true);target.moveTo(at.x,at.y,at.z+distance,180,0);target.setOnGround(true);f.level.addFreshEntity(target);spawned.add(target);mob.setTarget(target);return mob;
        }
        void success() { close();h.succeed(); }
        @Override public void close() {
            if(closed)return;closed=true;
            try{var mob=boss();if(mob!=null)mob.discard();for(var entity:spawned)entity.discard();f.level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
            finally{try{f.close();}finally{f.level.getServer().setDifficulty(difficulty,true);}}
        }
    }
    private static Scene ready(GameTestHelper h) { var s=new Scene(h);s.ready();return s; }

    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_start")
    public static void researchArchiveIncidentAndSurvivalGuardTheActualBoundTerminal(GameTestHelper h) {
        var s=new Scene(h);s.card();h.assertTrue(!s.start()&&s.status().phase()==WardenEncounter.Phase.IDLE,"Research prerequisites ignored");s.ready();
        s.player.setGameMode(GameType.CREATIVE);h.assertTrue(!s.start(),"Creative started a facility encounter");s.player.setGameMode(GameType.SURVIVAL);
        s.run(x->{if(!x.active())return;h.assertTrue(x.boss().getHealth()==360&&x.status().spawned(),"Wrong boss or missing durable spawn flag");x.success();});
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_reward")
    public static void sharedRewardIsAtomicOnceOnlyAndSurvivesSavedDataReload(GameTestHelper h) {
        var s=ready(h);s.run(x->{if(!x.active())return;var id=x.status().identity();x.boss().hurt(x.f.level.damageSources().generic(),100000);
            h.assertTrue(x.status().phase()==WardenEncounter.Phase.DEFEATED,"Actual death did not complete encounter");
            for(int i=0;i<36;i++)x.player.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            h.assertTrue(!x.start()&&!x.status().rewardClaimed(),"Full inventory consumed the reward");
            x.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(x.saved()));x.player.getInventory().clearContent();
            h.assertTrue(x.start()&&x.count(WardenContent.CORE.get())==1&&x.status().rewardClaimed(),"Reward lost on reload");
            h.assertTrue(!x.start()&&x.count(WardenContent.CORE.get())==1&&x.status().identity().equals(id),"Reward or identity duplicated");
            x.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(x.saved()));h.assertTrue(!x.start(),"Claim flag lost on reload");x.success();
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_auto_target")
    public static void boundBossAcquiresAndHitsASurvivalPlayerWithoutManualTargetAssignment(GameTestHelper h) {
        var s=ready(h);s.freeze=false;
        s.run(x->{if(!x.active())return;var mob=x.boss();
            if(x.stage==0){x.stage=1;x.player.getAbilities().invulnerable=false;var at=Vec3.atBottomCenterOf(x.f.origin.offset(x.d.spawn()));x.player.setPos(at.x,at.y,at.z-3);}
            if(mob.attack()==ContainmentWarden.Attack.CLAW&&mob.attackTicks()>=18){
                h.assertTrue(mob.getTarget()==x.player&&x.player.getHealth()<x.player.getMaxHealth(),"Facility boss failed to acquire and hit a real survival player");x.success();
            }
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_consoles")
    public static void independentConsolesExposeCoreAndPersistTheirCooldown(GameTestHelper h) {
        var s=ready(h);s.run(x->{if(x.stage!=0||!x.active())return;x.stage=1;var mob=x.boss();
            for(int i=0;i<2;i++)h.assertTrue(mob.visualConsoleAnchor(i).orElseThrow().equals(x.f.origin.offset(x.d.consoles().get(i).pos())),"Console VFX anchor ignored server definition");
            h.assertTrue(x.use(x.d.consoles().get(0)).consumesAction()&&!mob.exposed(),"One console opened core");
            h.assertTrue(x.use(x.d.consoles().get(0))==InteractionResult.FAIL&&!mob.exposed(),"Same console discharged twice");
            h.assertTrue(x.use(x.d.consoles().get(1)).consumesAction()&&mob.exposed()&&mob.consoleMask()==3,"Two consoles failed to expose core");
            h.assertTrue(mob.visualConsoles()==3&&mob.visualClock()==0,"Console visual snapshot missed exposure/cancellation");
            var tag=new CompoundTag();mob.addAdditionalSaveData(tag);mob.readAdditionalSaveData(tag);
            h.assertTrue(mob.visualConsoles()==3&&mob.visualClock()==0&&!mob.visualActive()&&mob.visualConsoleAnchor(1).isPresent(),"Reload invented a live hit or lost visual anchors");
            h.assertTrue(mob.exposed()&&mob.consoleCooldown()==300&&!mob.activateConsole(0),"Exposure/cooldown lost on entity load");
            mob.hurt(x.f.level.damageSources().generic(),10);h.assertTrue(mob.getHealth()<350,"Exposed damage modifier missing");
            x.freeze=false;mob.setNoAi(false);h.runAfterDelay(110,()->{if(x.closed)return;try{
                h.assertTrue(!mob.exposed()&&mob.consoleMask()==0&&mob.visualConsoles()==0&&mob.consoleCooldown()>180&&mob.consoleCooldown()<=200&&!mob.activateConsole(0),"Core did not close or consoles ignored cooldown");x.success();
            }catch(RuntimeException|Error e){x.close();throw e;}});
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_second_player")
    public static void secondPlayerCannotAllocateAnotherBossDuringTheWarning(GameTestHelper h) {
        var s=ready(h);s.run(x->{if(x.status().phase()==WardenEncounter.Phase.IDLE){x.start();return;}
            var id=x.status().identity();var before=x.saved();var other=x.f.player();other.setPos(x.player.getX(),x.player.getY(),x.player.getZ());other.setItemInHand(InteractionHand.MAIN_HAND,x.player.getMainHandItem().copy());
            InstituteOperations.useControl(x.f.level,x.f.origin.offset(x.d.control().pos()),other,"terminal");
            h.assertTrue(x.saved().equals(before)&&x.status().identity().equals(id),"Second player refreshed warning or identity");x.success();});
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_recovery")
    public static void missingSavedBossNeverRespawnsOrAwardsVictoryAndStaleIdentityIsRetired(GameTestHelper h) {
        var s=ready(h);s.run(x->{if(x.stage==0&&x.active()) {
                x.stage=1;var old=x.boss();var tag=new CompoundTag();old.save(tag);var id=old.getUUID();old.discard();
                x.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(x.saved()));
                h.runAfterDelay(140,()->{if(x.closed)return;try{
                    h.assertTrue(x.status().phase()==WardenEncounter.Phase.RECOVERY&&x.status().identity().equals(id)&&!x.status().rewardClaimed()&&x.boss()==null,"Missing identity replaced or rewarded");
                    x.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);x.player.setShiftKeyDown(true);h.assertTrue(x.start(),"Recovery reset failed");x.player.setShiftKeyDown(false);
                    var stale=WardenContent.WARDEN.get().create(x.f.level);stale.load(tag);h.assertTrue(!x.f.level.addFreshEntity(stale),"Stale bound identity re-entered world");x.success();
                }catch(RuntimeException|Error e){x.close();throw e;}});
            }});
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_pause")
    public static void absentPlayersAndPeacefulPauseWithoutDeletingOrDefeatingTheBoss(GameTestHelper h) {
        var s=ready(h);s.run(x->{if(x.stage==0){if(!x.start())return;x.stage=1;x.player.setGameMode(GameType.SPECTATOR);var before=x.saved();
                h.runAfterDelay(80,()->{if(x.closed)return;h.assertTrue(x.saved().equals(before)&&!x.status().spawned(),"Absent players advanced warning");x.player.setGameMode(GameType.SURVIVAL);});
            }else if(x.stage==1&&x.active()){x.stage=2;var id=x.status().identity();x.f.level.getServer().setDifficulty(Difficulty.PEACEFUL,true);var unbound=x.standalone(3);
                h.runAfterDelay(60,()->{if(x.closed)return;try{h.assertTrue(x.boss()!=null&&x.boss().isAlive()&&x.status().phase()==WardenEncounter.Phase.ACTIVE&&x.status().identity().equals(id)&&x.status().paused(),"Peaceful deleted/defeated/duplicated the boss");h.assertTrue(unbound.isAlive()&&!unbound.fighting()&&unbound.attack()==ContainmentWarden.Attack.NONE&&!unbound.visualActive()&&unbound.visualClock()==0,"Unbound Warden attacked in Peaceful");x.success();}catch(RuntimeException|Error e){x.close();throw e;}});
            }});
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=150,batch="warden_old_data")
    public static void oldOrMalformedSavedProgressDefaultsWithoutInventingVictory(GameTestHelper h) {
        try(var s=ready(h)){
            var root=s.saved();s.facility(root).remove("F8Warden");s.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(root));
            h.assertTrue(s.status().phase()==WardenEncounter.Phase.IDLE&&!s.status().spawned(),"Old world invented a boss");
            var malformed=new CompoundTag();malformed.putString("Phase","UNKNOWN_FUTURE_PHASE");s.facility(root).put("F8Warden",malformed);s.f.level.getDataStorage().set(OperationsData.NAME,OperationsData.load(root));
            h.assertTrue(s.status().phase()==WardenEncounter.Phase.RECOVERY&&!s.status().rewardClaimed(),"Malformed data became a victory");h.succeed();
        }
    }
    private static void attack(GameTestHelper h,ContainmentWarden.Attack expected,double distance,int impact) {
        var s=ready(h);var mob=s.standalone(distance);var victim=mob.getTarget();float health=victim.getHealth();
        s.run(x->{if(mob.attack()!=expected)return;
            h.assertTrue(mob.visualSequence()>0&&mob.visualClock()==mob.attackTicks()&&mob.visualActive(),"Visual clock/round diverged from live AI");
            h.assertTrue(Math.abs(mob.visualAim())<.01,"Locked visual axis diverged from target direction");
            if(mob.attackTicks()<impact)h.assertTrue(victim.getHealth()==health,"Damage before telegraph finished");
            if(mob.attackTicks()>=impact+1){h.assertTrue(victim.getHealth()<health,"AI attack failed to damage target at its event time");x.success();}
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_claw")
    public static void clawAiTelegraphsBeforeItsSeventeenthTickHit(GameTestHelper h) { attack(h,ContainmentWarden.Attack.CLAW,3,17); }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_stomp")
    public static void stompAiTelegraphsBeforeItsTwentySecondTickGroundHit(GameTestHelper h) { attack(h,ContainmentWarden.Attack.STOMP,5,22); }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_burst")
    public static void burstAiFiresTheCenterRayAtItsTwentySixthTick(GameTestHelper h) { attack(h,ContainmentWarden.Attack.BURST,9,26); }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_wall")
    public static void wallRaisedDuringWindupPreventsTheClawHit(GameTestHelper h) {
        var s=ready(h);var mob=s.standalone(3.8);var victim=mob.getTarget();float health=victim.getHealth();
        s.run(x->{if(mob.attack()!=ContainmentWarden.Attack.CLAW)return;
            if(x.stage==0){x.stage=1;var center=mob.blockPosition().south(2);for(var p:BlockPos.betweenClosed(center.offset(-2,0,0),center.offset(2,4,0)))x.f.put(p,Blocks.STONE.defaultBlockState());}
            if(mob.attackTicks()>=19){h.assertTrue(victim.getHealth()==health,"Claw hit through a wall");x.success();}
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_dodge")
    public static void leavingClawReachDuringWindupAvoidsDamage(GameTestHelper h) {
        var s=ready(h);var mob=s.standalone(3);var victim=mob.getTarget();float health=victim.getHealth();
        s.run(x->{if(mob.attack()!=ContainmentWarden.Attack.CLAW)return;
            if(x.stage==0){x.stage=1;victim.setPos(mob.getX(),mob.getY(),mob.getZ()+7);}
            if(mob.attackTicks()>=19){h.assertTrue(victim.getHealth()==health,"Claw hit a target that left its reach");x.success();}
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_jump")
    public static void jumpingDuringStompWindupAvoidsGroundDamage(GameTestHelper h) {
        var s=ready(h);var mob=s.standalone(5);var victim=mob.getTarget();float health=victim.getHealth();
        s.run(x->{if(mob.attack()!=ContainmentWarden.Attack.STOMP)return;
            if(x.stage==0){x.stage=1;victim.setPos(victim.getX(),victim.getY()+1.5,victim.getZ());victim.setOnGround(false);}
            if(mob.attackTicks()>=24){h.assertTrue(victim.getHealth()==health,"Stomp hit an airborne target");x.success();}
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=150,batch="warden_artifact")
    public static void coreRewardAppliesFiniteVisibleSuppressionAndCannotIgnoreCooldown(GameTestHelper h) {
        var s=ready(h);
            var p=Vec3.atBottomCenterOf(s.f.origin.offset(s.d.spawn()));s.player.setPos(p.x,p.y,p.z);
            var infected=InfectedContent.TYPES.get(InfectedKind.FORAGER).get().create(s.f.level);infected.setNoAi(true);infected.moveTo(p.x+3,p.y,p.z,0,0);s.f.level.addFreshEntity(infected);s.spawned.add(infected);
            var other=EntityType.COW.create(s.f.level);other.moveTo(p.x-3,p.y,p.z,0,0);s.f.level.addFreshEntity(other);s.spawned.add(other);
            var item=WardenContent.CORE.get();s.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
            s.run(x->{if(!s.f.level.getEntitiesOfClass(LivingEntity.class,s.player.getBoundingBox().inflate(8)).contains(infected))return;
            h.assertTrue(item.use(s.f.level,s.player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"Core item failed");
            h.assertTrue(infected.hasEffect(MobEffects.WEAKNESS)&&infected.getEffect(MobEffects.WEAKNESS).getDuration()==120&&!other.hasEffect(MobEffects.WEAKNESS),"Core effect duration/target selection wrong");
            h.assertTrue(item.use(s.f.level,s.player,InteractionHand.MAIN_HAND).getResult()==InteractionResult.FAIL&&s.player.getMainHandItem().getCount()==1,"Core ignored cooldown or was consumed");s.success();});
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=750,batch="warden_real_room")
    public static void actualF8TemplatesBindThreeReachableControlsAndSpawnTheModelSizedBoss(GameTestHelper h) {
        var s=ready(h);var min=s.f.origin.offset(s.d.min()).below();var max=s.f.origin.offset(s.d.max()).offset(-1,-1,-1);var clip=new BoundingBox(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ());int tiles=0;
        for(var tile:CentralInstitute.tiles())if(tile.x()<80&&tile.x()+tile.width()>40&&tile.z()<60&&tile.z()+tile.length()>36){var at=s.f.origin.offset(tile.x(),tile.y(),tile.z());h.assertTrue(CentralInstitute.template(s.f.level,tile).placeInWorld(s.f.level,at,at,CentralInstitute.settings(clip),s.f.level.random,18),"Actual F8 template placement failed");tiles++;}
        h.assertTrue(tiles==6,"F8 should span six real tiles");
        s.run(x->{if(!x.active())return;h.assertTrue(x.boss().getBbHeight()==3.4F&&x.f.level.noCollision(x.boss()),"Actual arena clips boss");for(var t:x.d.consoles()){h.assertTrue(x.f.level.getBlockEntity(x.f.origin.offset(t.pos())) instanceof OperationsControlEntity control&&control.boundOrigin().filter(x.f.origin::equals).isPresent(),"Real console lost binding");}x.success();});
    }
    @GameTest(template="empty64",templateNamespace="ender_warden_checks",timeoutTicks=150,batch="warden_no_force")
    public static void statusAndMissingControlDoNotLoadAnUnloadedArena(GameTestHelper h) {
        try(var f=LabOperationsGameTests.Fixture.create(h)) {
            var unloadedOrigin=f.origin.offset(8192,0,8192);InstituteOperations.registerFacility(f.level,unloadedOrigin);var d=WardenEncounter.definition(f.level).orElseThrow();var at=unloadedOrigin.offset(d.control().pos());
            h.assertTrue(f.level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null,"No-force fixture already loaded");
            int chunks=f.level.getChunkSource().getLoadedChunksCount();var player=f.player();
            for(int i=0;i<4;i++){WardenEncounter.status(f.level,at);InstituteOperations.useControl(f.level,at,player,"terminal");WardenEncounter.tick(f.level,OperationsData.get(f.level));}
            h.assertTrue(chunks==f.level.getChunkSource().getLoadedChunksCount()&&f.level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)==null,"Boss lookup silently loaded arena");h.succeed();
        }
    }
}
