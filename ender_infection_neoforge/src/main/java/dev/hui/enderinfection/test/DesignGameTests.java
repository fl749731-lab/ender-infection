package dev.hui.enderinfection.test;

import dev.hui.enderinfection.*;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.entity.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.world.entity.EntityType;

@GameTestHolder("ender_infection")
@PrefixGameTestTemplate(false)
public final class DesignGameTests {
    private static void floor(GameTestHelper h){for(int x=0;x<36;x++)for(int z=0;z<32;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);}
    private static InfectedMob unit(GameTestHelper h,InfectedKind kind,int x,int z){var m=InfectedContent.TYPES.get(kind).get().create(h.getLevel());m.moveTo(h.absolutePos(new BlockPos(x,1,z)),0,0);m.setNoAi(true);h.getLevel().addFreshEntity(m);return m;}
    @GameTest(template="empty64",timeoutTicks=60,batch="design_registry")
    public static void everyDesignUnitHasDistinctDimensionsAndAttributes(GameTestHelper h){
        floor(h);int i=0;for(InfectedKind k:InfectedKind.values())if(k.designed()){
            var e=unit(h,k,2+(i%7)*4,3+(i/7)*12);h.assertTrue(e.getMaxHealth()==k.health&&Math.abs(e.getBbWidth()-k.width)<.001,"Wrong attributes/dimensions: "+k);h.assertTrue(FactionAI.infected(e)&&InfectedContent.EGGS.get(k).isBound(),"Missing faction/egg: "+k);e.discard();i++;
        }
        h.assertTrue(i==14,"Expected 14 new specialists");h.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=60,batch="design_fixture")
    public static void fixturesRotateReserveSpaceOpenAndBreakTogether(GameTestHelper h){
        floor(h);var level=h.getLevel();var block=(LabFixtureBlock)DesignContent.BLOCKS_BY_KEY.get("lab_door").get();var p=h.absolutePos(new BlockPos(6,1,6));var s=block.defaultBlockState().setValue(LabFixtureBlock.FACING,Direction.EAST);
        level.setBlock(p,s,3);block.fillParts(level,p,s);h.assertTrue(level.getBlockEntity(p) instanceof LabFixtureEntity,"Missing fixture BE");
        h.assertTrue(level.getBlockState(p.above()).getBlock() instanceof LabPartBlock,"Door upper part missing");
        h.assertTrue(!level.getBlockState(p).getCollisionShape(level,p).isEmpty(),"Closed door has no collision");
        level.setBlock(p,s.setValue(LabFixtureBlock.OPEN,true),3);var upperShape=level.getBlockState(p.above()).getCollisionShape(level,p.above());
        h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(upperShape,net.minecraft.world.phys.shapes.Shapes.box(.3,.2,.3,.7,.8,.7),net.minecraft.world.phys.shapes.BooleanOp.AND),"Opened door still blocks the passage");
        h.assertTrue(!upperShape.isEmpty(),"Opened door lost its frame collision");
        level.destroyBlock(p.above(),false);h.assertTrue(level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir(),"Breaking upper part orphaned root");h.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=60,batch="design_wide_fixture")
    public static void wideFixturesKeepLocalOffsetsWhenFacingWest(GameTestHelper h){
        floor(h);var level=h.getLevel();var block=(LabFixtureBlock)DesignContent.BLOCKS_BY_KEY.get("lab_workbench_eroded").get();var p=h.absolutePos(new BlockPos(6,1,6));var s=block.defaultBlockState().setValue(LabFixtureBlock.FACING,Direction.WEST);level.setBlock(p,s,3);block.fillParts(level,p,s);
        int parts=0;for(int x=-1;x<=1;x++)for(int y=0;y<2;y++)if(x!=0||y!=0){var cell=LabFixtureBlock.cell(p,Direction.WEST,x,y);var state=level.getBlockState(cell);h.assertTrue(state.getBlock() instanceof LabPartBlock&&LabPartBlock.anchor(cell,state).equals(p),"Rotated part points at wrong root");parts++;}
        h.assertTrue(parts==5,"Wrong footprint");level.destroyBlock(p,false);h.assertTrue(level.getBlockState(p.north()).isAir(),"Side part survived root destruction");h.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=90,batch="design_shield")
    public static void shieldBlocksFrontButNotBack(GameTestHelper h){
        floor(h);var shield=unit(h,InfectedKind.SHIELD_SENTINEL,8,8);shield.setYRot(0);var attacker=EntityType.ZOMBIE.create(h.getLevel());attacker.moveTo(shield.getX(),shield.getY(),shield.getZ()+3,0,0);attacker.setNoAi(true);h.getLevel().addFreshEntity(attacker);
        float front=DesignBehavior.filterDamage(shield,shield.damageSources().mobAttack(attacker),20);attacker.setPos(shield.getX(),shield.getY(),shield.getZ()-3);float back=DesignBehavior.filterDamage(shield,shield.damageSources().mobAttack(attacker),20);
        h.assertTrue(front<back*.5&&back==20,"Directional shield has no front/back distinction");shield.discard();attacker.discard();h.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="design_economy")
    public static void specialistBirthsPayTheirOwnMotherAndStopWithoutFunds(GameTestHelper h){
        floor(h);var core=unit(h,InfectedKind.CORE,16,16);core.setNoAi(false);core.receiveColonyBiomass(500);int before=core.colonyBiomass();
        h.assertTrue(DesignBehavior.spawn(core,core,InfectedKind.FORAGER,h.getLevel()),"Funded mother failed to recruit");
        h.assertTrue(core.colonyBiomass()==before-DesignBehavior.price(InfectedKind.FORAGER),"Recruitment cost missing");
        core.spendBiomass(core.colonyBiomass());h.assertTrue(!DesignBehavior.spawn(core,core,InfectedKind.FORAGER,h.getLevel()),"Zero reserve produced free unit");
        h.getLevel().getEntitiesOfClass(InfectedMob.class,core.getBoundingBox().inflate(30)).forEach(InfectedMob::discard);h.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="design_projectile")
    public static void crystalProjectileDamagesEnemiesAndSpitterPoisons(GameTestHelper h){
        floor(h);var caster=unit(h,InfectedKind.THROAT_SPITTER,4,6);var target=EntityType.COW.create(h.getLevel());target.moveTo(h.absolutePos(new BlockPos(10,1,6)),0,0);target.setNoAi(true);h.getLevel().addFreshEntity(target);float before=target.getHealth();
        DesignProjectile.fire(h.getLevel(),caster,target.getEyePosition(),7,true);
        h.runAfterDelay(25,()->{h.assertTrue(target.getHealth()<before,"Projectile did not hurt target");h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.POISON),"Spore shot missing poison");caster.discard();target.discard();h.succeed();});
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="design_structure")
    public static void nativeLaboratoryHasFunctionalSuppliesAndBiomeRegistry(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,1,2));var spec=dev.hui.enderinfection.worldgen.ResearchLabPlacement.SPECS.get(0);
        h.assertTrue(dev.hui.enderinfection.worldgen.ResearchLabPlacement.place(level,p),"Native lab placement failed");int chests=0;
        for(var at:BlockPos.betweenClosed(p,p.offset(spec.width()-1,spec.height()-1,spec.length()-1)))if(level.getBlockEntity(at) instanceof ChestBlockEntity)chests++;
        h.assertTrue(chests==spec.rooms(),"Missing themed room supplies");h.assertTrue(level.getBlockState(p.offset(spec.entrance())).getBlock() instanceof LabFixtureBlock,"Missing entrance");h.succeed();
    }
}
