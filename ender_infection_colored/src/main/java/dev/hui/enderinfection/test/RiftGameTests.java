package dev.hui.enderinfection.test;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.rift.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import com.mojang.authlib.GameProfile;
import java.util.*;

@GameTestHolder("ender_rift_checks")
@PrefixGameTestTemplate(false)
public final class RiftGameTests {
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=240,batch="rift_attack")
    public static void earlyEndermanTelegraphsPhasesAndHits(GameTestHelper h){phaseAndHit(h,InfectedKind.EARLY_ENDERMAN);}
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=240,batch="rift_attack")
    public static void evolvedEndermanTelegraphsPhasesAndHits(GameTestHelper h){phaseAndHit(h,InfectedKind.ENDERMAN);}
    private static void phaseAndHit(GameTestHelper h,InfectedKind kind){
        floor(h);var mob=unit(h,kind,10,16);var prey=prey(h,21,16);boolean[] warning={false};
        h.succeedWhen(()->{
            warning[0]|=mob.getPersistentData().getInt("RiftPhaseTicks")>0;
            h.assertTrue(warning[0]&&mob.getPersistentData().getInt("RiftPhaseCount")>0&&prey.getHealth()<prey.getMaxHealth(),
                "No telegraphed phase strike: "+kind+" warning="+warning[0]+" phases="+mob.getPersistentData().getInt("RiftPhaseCount")+" health="+prey.getHealth()+" distance="+mob.distanceTo(prey));
            mob.discard();prey.discard();
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=180,batch="rift_interrupt")
    public static void actualSuppressorUseInterruptsWindupAndCostsDurability(GameTestHelper h){
        floor(h);var mob=unit(h,InfectedKind.ENDERMAN,10,16);var prey=prey(h,21,16);var player=player(h,10,18);
        var stack=new ItemStack(RiftContent.SUPPRESSOR.get());player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        boolean[] used={false};int[] after={0};
        h.succeedWhen(()->{
            if(!used[0]){
                h.assertTrue(mob.getPersistentData().getInt("RiftPhaseTicks")>0,"Waiting for phase windup");
                var result=RiftContent.SUPPRESSOR.get().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
                h.assertTrue(result.getResult().consumesAction(),"Suppressor did not activate");
                h.assertTrue(stack.getDamageValue()==1&&player.getCooldowns().isOnCooldown(RiftContent.SUPPRESSOR.get()),"Missing item cost/cooldown");
                h.assertTrue(mob.getPersistentData().getInt("RiftPhaseTicks")==0&&RiftCombat.suppressed(mob),"Phase was not cancelled");
                used[0]=true;after[0]=mob.tickCount;
            }
            h.assertTrue(mob.tickCount-after[0]>=30,"Checking cancelled phase stays cancelled");
            h.assertTrue(mob.getPersistentData().getInt("RiftPhaseCount")==0,"Suppressed mob still teleported");mob.discard();prey.discard();
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=90,batch="rift_budget")
    public static void anchorRechargePaysAssignedMotherExactly(GameTestHelper h){
        floor(h);var core=unit(h,InfectedKind.CORE,8,12);var n=new CompoundTag();core.addAdditionalSaveData(n);n.putInt("ColonyBiomass",20);n.putInt("EmergenceTicks",40);n.putBoolean("ExpansionNodeCreated",true);core.readAdditionalSaveData(n);
        var a=anchor(h,12,12,0);a.assignMother(core);
        h.succeedWhen(()->{
            h.assertTrue(a.energy()==1,"Waiting for one paid transfer: "+a.energy());
            h.assertTrue(core.colonyBiomass()==19,"Mother ledger mismatch: "+core.colonyBiomass());
            h.assertTrue(!a.spend(2)&&a.energy()==1,"Insufficient buffer was spent");a.addEnergy(1);
            h.assertTrue(a.spend(2)&&a.energy()==0,"Successful ability charge was not paid exactly");core.discard();
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=260,batch="rift_suppression")
    public static void anchorSuppressionPersistsAndExpires(GameTestHelper h){
        floor(h);var a=anchor(h,12,12,20);a.suppress(200);var saved=a.saveWithoutMetadata();
        var restored=new RiftAnchorEntity(a.getBlockPos(),a.getBlockState());restored.setLevel(h.getLevel());restored.load(saved);
        h.assertTrue(restored.energy()==20&&restored.suppressed()&&!restored.active(),"Suppression/energy did not survive NBT");
        h.runAfterDelay(205,()->{h.assertTrue(a.active()&&a.energy()==20,"Suppression did not expire without losing stored energy");h.succeed();});
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=90,batch="rift_shield")
    public static void suppressorOpensGuardAndIncreasesRealDamage(GameTestHelper h){
        floor(h);var a=anchor(h,14,16,20);var guard=unit(h,InfectedKind.ANCHOR_GUARD,17,16);guard.setNoAi(true);RiftCombat.bind(guard,a.getBlockPos());
        var attacker=prey(h,22,16);var player=player(h,14,14);
        float initial=guard.getHealth();guard.hurt(guard.damageSources().mobAttack(attacker),10);float protectedLoss=initial-guard.getHealth();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RiftContent.SUPPRESSOR.get()));
        RiftContent.SUPPRESSOR.get().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(!a.active()&&a.suppressed()&&guard.riftExposed(),"Anchor/guard did not expose");
        guard.invulnerableTime=0;float before=guard.getHealth();guard.hurt(guard.damageSources().mobAttack(attacker),10);
        h.assertTrue(before-guard.getHealth()>protectedLoss*1.8,"Exposed guard did not take more damage");guard.discard();attacker.discard();h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=360,batch="rift_leash")
    public static void guardHitsNearbyPreyThenReturnsInsteadOfChasingOutOfRange(GameTestHelper h){
        floor(h);
        // Keep natural cave gravel from burying the guard or obstructing its return route during this fixture.
        for(int x=0;x<48;x++)for(int z=0;z<48;z++){
            h.setBlock(new BlockPos(x,8,z),Blocks.GLASS);
            for(int y=1;y<8;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var arena=new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(0,1,0))),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(48,9,48))));
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,arena).forEach(net.minecraft.world.entity.Entity::discard);
        var a=anchor(h,12,16,20);var guard=unit(h,InfectedKind.ANCHOR_GUARD,12,20);RiftCombat.bind(guard,a.getBlockPos());var prey=prey(h,20,20);
        boolean[] returning={false};Mob[] far={null};
        h.succeedWhen(()->{
            if(!returning[0]){
                h.assertTrue(prey.getHealth()<prey.getMaxHealth(),"Guard did not attack nearby prey");
                prey.discard();guard.setTarget(null);var p=h.absolutePos(new BlockPos(30,1,16));guard.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);far[0]=prey(h,36,16);returning[0]=true;
            }
            h.assertTrue(guard.getTarget()!=far[0],"Guard abandoned its anchor to chase distant prey");
            h.assertTrue(guard.position().distanceToSqr(Vec3.atCenterOf(a.getBlockPos()))<26,"Guard did not return to anchor: "+guard.position()+" home="+a.getBlockPos()+" path="+guard.getNavigation().getPath()+" task="+guard.getPersistentData().getString("ColonyTask")+" target="+guard.getTarget()+" entityTick="+guard.tickCount+" ground="+h.getLevel().getBlockState(guard.blockPosition().below()));
            guard.discard();far[0].discard();
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=60,batch="rift_landing")
    public static void phaseLandingRejectsAirWallsLiquidAndUnloadedChunks(GameTestHelper h){
        floor(h);var mob=unit(h,InfectedKind.ENDERMAN,10,12);mob.setNoAi(true);
        var safe=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16,1,12)));h.assertTrue(RiftCombat.safeLanding(mob,safe),"Flat safe landing rejected");
        h.setBlock(new BlockPos(16,0,12),Blocks.AIR);h.assertTrue(!RiftCombat.safeLanding(mob,safe),"Air landing accepted");h.setBlock(new BlockPos(16,0,12),Blocks.STONE);
        h.setBlock(new BlockPos(16,1,12),Blocks.WATER);h.assertTrue(!RiftCombat.safeLanding(mob,safe),"Liquid landing accepted");h.setBlock(new BlockPos(16,1,12),Blocks.AIR);
        for(int y=1;y<6;y++)h.setBlock(new BlockPos(13,y,12),Blocks.STONE);
        h.assertTrue(!RiftCombat.safeLanding(mob,safe),"Phase crossed a solid wall");
        h.assertTrue(!RiftCombat.safeLanding(mob,new Vec3(1000000,70,1000000)),"Unloaded landing accepted");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(1000000>>4,1000000>>4)==null,"Landing probe force-loaded a chunk");mob.discard();h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=60,batch="rift_empty_pulse")
    public static void emptyPulseDoesNotConsumeItem(GameTestHelper h){
        floor(h);var p=player(h,12,12);var stack=new ItemStack(RiftContent.SUPPRESSOR.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        RiftContent.SUPPRESSOR.get().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(stack.getDamageValue()==0&&!p.getCooldowns().isOnCooldown(RiftContent.SUPPRESSOR.get()),"Empty pulse consumed durability/cooldown");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=60,batch="rift_fixture")
    public static void outpostHasPaidAnchorThreeSpecialistsAndCountermeasureLoot(GameTestHelper h){
        floor(h);var center=h.absolutePos(new BlockPos(24,1,24));var pos=RiftOutpost.place(h.getLevel(),center);
        var a=(RiftAnchorEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(a.active()&&a.energy()==24,"Outpost anchor not initialized");
        // Newly spawned entity sections may become queryable on the next server tick.
        h.succeedWhen(()->{var units=h.getLevel().getEntitiesOfClass(InfectedMob.class,new net.minecraft.world.phys.AABB(center).inflate(12));
            long specialists=units.stream().filter(RiftCombat::unit).count();
            h.assertTrue(units.size()==4&&specialists==3,"Missing outpost defenders: total="+units.size()+" specialists="+specialists);
            h.assertTrue(h.getLevel().getBlockEntity(center.offset(0,0,-8)) instanceof ChestBlockEntity chest&&chest.getItem(0).is(RiftContent.SUPPRESSOR.get()),"No countermeasure in entrance chest");units.forEach(InfectedMob::discard);});
    }
    @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=60,batch="rift_site")
    public static void outpostRejectsOccupiedSiteWithoutChangingIt(GameTestHelper h){
        floor(h);var center=h.absolutePos(new BlockPos(24,1,24));var chest=center.offset(2,0,2);h.getLevel().setBlock(chest,Blocks.CHEST.defaultBlockState(),3);
        boolean rejected=false;try{RiftOutpost.place(h.getLevel(),center);}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected&&h.getLevel().getBlockState(chest).is(Blocks.CHEST)&&h.getLevel().getBlockState(center).isAir(),"Occupied site was partially overwritten");h.succeed();
    }
    private static RiftAnchorEntity anchor(GameTestHelper h,int x,int z,int energy){var p=h.absolutePos(new BlockPos(x,1,z));h.getLevel().setBlock(p,RiftContent.ANCHOR.get().defaultBlockState(),3);var a=(RiftAnchorEntity)h.getLevel().getBlockEntity(p);a.addEnergy(energy);return a;}
    private static InfectedMob unit(GameTestHelper h,InfectedKind kind,int x,int z){var m=Objects.requireNonNull(InfectedContent.TYPES.get(kind).get().create(h.getLevel()));var p=h.absolutePos(new BlockPos(x,1,z));m.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);h.getLevel().addFreshEntity(m);return m;}
    private static Mob prey(GameTestHelper h,int x,int z){var m=EntityType.COW.create(h.getLevel());var p=h.absolutePos(new BlockPos(x,1,z));m.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);m.setNoAi(true);m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);m.setHealth(500);h.getLevel().addFreshEntity(m);return m;}
    private static FakePlayer player(GameTestHelper h,int x,int z){var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"RiftTest"));p.setGameMode(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(x,1,z));p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);return p;}
    private static void floor(GameTestHelper h){for(int x=0;x<48;x++)for(int z=0;z<48;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);}
}
