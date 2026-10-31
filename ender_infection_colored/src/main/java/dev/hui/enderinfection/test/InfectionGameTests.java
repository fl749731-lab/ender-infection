package dev.hui.enderinfection.test;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ender_infection")
@PrefixGameTestTemplate(false)
public final class InfectionGameTests {
    @GameTest(template="empty64",timeoutTicks=190,batch="ai_node_alarm")
    public static void brokenNodeRecallsHuntersToTheObjectiveWithoutUnseenTargetTracking(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,40,0,10);var level=helper.getLevel();
        var core=fundedCore(helper,new BlockPos(4,1,4),0,1);
        var hunter=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(30,1,4),core,"hunter");
        var prey=net.minecraft.world.entity.EntityType.COW.create(level);placeMob(helper,prey,new BlockPos(37,1,4));prey.setNoAi(true);prey.setInvulnerable(true);level.addFreshEntity(prey);hunter.setTarget(prey);
        for(int z=0;z<=10;z++)for(int y=1;y<=4;y++)helper.setBlock(new BlockPos(10,y,z),Blocks.STONE);
        BlockPos nodePos=new BlockPos(24,1,4);helper.setBlock(nodePos,Blocks.AIR);helper.setBlock(nodePos,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(nodePos));node.setOwner(core.getUUID());core.observeExpansion(node);
        var fake=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"NodeAlarmTest"));fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);placeMobPlayer(helper,fake,new BlockPos(25,1,4));
        helper.runAfterDelay(45,()->{
            helper.assertTrue(!core.hasLineOfSight(fake),"Node alarm test did not hide the player from the mother");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,helper.absolutePos(nodePos),InfectedContent.NEST.get().defaultBlockState(),fake));helper.setBlock(nodePos,Blocks.AIR);
            helper.assertTrue(hunter.getTarget()==null&&!hunter.getPersistentData().hasUUID("CommandTarget")&&BlockPos.of(hunter.getPersistentData().getLong("SearchPos")).equals(helper.absolutePos(nodePos)),"Node alarm disclosed the unseen player instead of the damaged objective");
            helper.runAfterDelay(100,()->{
                helper.assertTrue(hunter.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(nodePos)))<64,"Node recall: hunter "+hunter.position()+", objective "+helper.absolutePos(nodePos)+", target "+(hunter.getTarget()==null?"none":hunter.getTarget().getType())+", path done "+hunter.getNavigation().isDone());fake.discard();clearWideBattlefield(helper);helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=360,batch="ai_hunting")
    public static void hunterFindsDistantPreyKillsItAndFeedsItsOwnMother(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,42,0,8);
        var owner=fundedCore(helper,new BlockPos(1,1,4),100,1);var foreign=fundedCore(helper,new BlockPos(38,1,1),500,1);
        var hunter=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(2,1,4),owner,"hunter");
        var prey=net.minecraft.world.entity.EntityType.COW.create(helper.getLevel());placeMob(helper,prey,new BlockPos(37,1,4));prey.setNoAi(true);prey.setHealth(3);helper.getLevel().addFreshEntity(prey);
        double distance=hunter.distanceToSqr(prey);
        helper.runAfterDelay(60,()->helper.assertTrue(hunter.getTarget()==prey&&hunter.distanceToSqr(prey)<distance,"Hunter did not acquire/approach distant prey: target="+hunter.getTarget()+", pos="+hunter.position()+", task="+hunter.getPersistentData().getString("TaskReason")));
        helper.runAfterDelay(310,()->{
            helper.assertTrue(!prey.isAlive()&&owner.colonyBiomass()==105&&foreign.colonyBiomass()==500,"Distant hunt failed to kill/feed its assigned mother: prey "+prey.getHealth()+" at "+prey.position()+", hunter "+hunter.position()+", alive="+hunter.isAlive()+", target="+hunter.getTarget()+", task="+hunter.getPersistentData().getString("TaskReason")+", pathDone="+hunter.getNavigation().isDone()+", stocks "+owner.colonyBiomass()+" / "+foreign.colonyBiomass());
            clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=220,batch="ai_recall")
    public static void motherDamageInterruptsHuntingAndPhysicallyRecallsOwnedUnits(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,40,0,8);
        var owner=fundedCore(helper,new BlockPos(4,1,4),0,1);var foreign=fundedCore(helper,new BlockPos(34,1,1),0,1);
        var hunter=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(30,1,4),owner,"hunter");
        var outsider=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(30,1,1),foreign,"hunter");
        var prey=net.minecraft.world.entity.EntityType.COW.create(helper.getLevel());placeMob(helper,prey,new BlockPos(37,1,4));prey.setNoAi(true);prey.setInvulnerable(true);helper.getLevel().addFreshEntity(prey);hunter.setTarget(prey);outsider.setTarget(prey);
        helper.runAfterDelay(45,()->{
            var attacker=net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());placeMob(helper,attacker,new BlockPos(8,1,4));attacker.setNoAi(true);attacker.setInvulnerable(true);helper.getLevel().addFreshEntity(attacker);
            owner.setInvulnerable(false);owner.hurt(helper.getLevel().damageSources().mobAttack(attacker),1);owner.setInvulnerable(true);
            helper.assertTrue(hunter.getTarget()==attacker&&hunter.getPersistentData().getUUID("DefendingMother").equals(owner.getUUID()),"Core injury did not immediately interrupt its hunter's prey order");
            helper.assertTrue(!outsider.getPersistentData().hasUUID("DefendingMother"),"Core commandeered another mother's hunters");
            var trace=new java.util.ArrayList<String>();for(int delay:new int[]{20,40,80})helper.runAfterDelay(delay,()->trace.add(hunter.position()+" target="+(hunter.getTarget()==null?"none":hunter.getTarget().getType())+" done="+hunter.getNavigation().isDone()+" sees="+hunter.hasLineOfSight(attacker)));
            helper.runAfterDelay(120,()->{
                helper.assertTrue(hunter.distanceToSqr(owner)<196,"Recall: hunter "+hunter.position()+", core "+owner.position()+", attacker "+attacker.position()+", target="+(hunter.getTarget()==null?"none":hunter.getTarget().getType())+", trace="+trace);
                clearWideBattlefield(helper);helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=250,batch="ai_ranged")
    public static void skeletonClearsAllyFireLaneAndBacksAwayFromCloseEnemies(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,14,0,14);
        for(int x=0;x<=14;x++)for(int z=0;z<=14;z++)helper.setBlock(new BlockPos(x,4,z),Blocks.STONE);
        var archer=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_SKELETON,new BlockPos(4,1,4),null,"hunter");
        var ally=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_COW,new BlockPos(4,1,7),null,"hunter");ally.setNoAi(true);
        var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());placeMob(helper,target,new BlockPos(4,1,10));target.setNoAi(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);helper.getLevel().addFreshEntity(target);archer.setTarget(target);
        helper.assertTrue(!dev.hui.enderinfection.entity.FactionAI.clearShot(archer,target),"Friendly blocker was not detected in the firing lane");
        helper.runAfterDelay(120,()->{
            helper.assertTrue(Math.abs(archer.getX()-helper.absolutePos(new BlockPos(4,1,4)).getX()-.5)>1&&target.getHealth()<100,"Skeleton did not sidestep its ally and land arrows: "+archer.position()+", target hp "+target.getHealth());
            target.teleportTo(archer.getX(),archer.getY(),archer.getZ()+2);archer.setTarget(target);
            helper.runAfterDelay(80,()->{
                helper.assertTrue(archer.distanceToSqr(target)>=16,"Skeleton stood at point-blank range instead of retreating");clearWideBattlefield(helper);helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=210,batch="ai_last_seen")
    public static void hunterSearchesLastSeenPositionInsteadOfTrackingThroughWalls(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,24,0,8);
        var hunter=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(2,1,4),null,"hunter");
        var prey=net.minecraft.world.entity.EntityType.COW.create(helper.getLevel());placeMob(helper,prey,new BlockPos(8,1,4));prey.setNoAi(true);prey.setInvulnerable(true);helper.getLevel().addFreshEntity(prey);hunter.setTarget(prey);
        helper.runAfterDelay(20,()->{
            for(int z=0;z<=8;z++)for(int y=1;y<=5;y++)helper.setBlock(new BlockPos(10,y,z),Blocks.STONE);
            var hidden=helper.absolutePos(new BlockPos(20,1,4));prey.teleportTo(hidden.getX()+.5,hidden.getY(),hidden.getZ()+.5);
            helper.runAfterDelay(150,()->{
                helper.assertTrue(hunter.getTarget()!=prey&&hunter.getX()<helper.absolutePos(new BlockPos(10,1,4)).getX(),"Hunter kept tracking a hidden moving target beyond the last seen point");
                helper.assertTrue(hunter.getPersistentData().contains("SearchPos")&&BlockPos.of(hunter.getPersistentData().getLong("SearchPos")).getX()<hidden.getX(),"Search order was updated to an unseen position");clearWideBattlefield(helper);helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=220,batch="ai_scouting")
    public static void idleHuntersExploreWhilePermanentGuardsStayNearHome(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,42,0,42);
        var core=fundedCore(helper,new BlockPos(21,1,21),0,1);
        var hunter=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(21,1,24),core,"hunter");
        var guard=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_COW,new BlockPos(24,1,21),core,"guard");
        // A patrol can cross home between waypoints; observe the excursion instead of one random phase.
        double[] excursion={0,0};for(int t=10;t<=170;t+=10)helper.runAfterDelay(t,()->{excursion[0]=Math.max(excursion[0],hunter.distanceToSqr(core));excursion[1]=Math.max(excursion[1],guard.distanceToSqr(core));});
        helper.runAfterDelay(180,()->{
            excursion[0]=Math.max(excursion[0],hunter.distanceToSqr(core));excursion[1]=Math.max(excursion[1],guard.distanceToSqr(core));
            helper.assertTrue(excursion[0]>144,"Idle hunter never explored beyond its home: max squared distance "+excursion[0]);
            helper.assertTrue(excursion[1]<144,"Permanent guard abandoned its home while the hunter scouted");clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ai_evolution_orders")
    public static void evolutionPreservesMotherDutyAndActiveDefenseOrder(GameTestHelper helper){
        clearWideBattlefield(helper);prepareBurstArena(helper);var core=fundedCore(helper,new BlockPos(7,1,7),0,1);
        var host=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_ZOMBIE,new BlockPos(3,1,3),core,"guard");
        host.getPersistentData().putUUID("DefendingMother",core.getUUID());host.getPersistentData().putLong("DefendUntil",helper.getLevel().getGameTime()+200);host.getPersistentData().putLong("DefendPoint",core.blockPosition().asLong());host.feedBiomass(40);
        helper.runAfterDelay(45,()->{
            var mutants=helper.getLevel().getEntitiesOfClass(dev.hui.enderinfection.entity.MutantZombie.class,core.getBoundingBox().inflate(20),e->e.getPersistentData().hasUUID("ColonyMother")&&e.getPersistentData().getUUID("ColonyMother").equals(core.getUUID()));
            helper.assertTrue(host.isRemoved()&&mutants.size()==1&&mutants.get(0).getPersistentData().getString("ColonyDuty").equals("guard")&&mutants.get(0).getPersistentData().getLong("DefendPoint")==core.blockPosition().asLong(),"Evolution discarded colony or defense orders");clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="soil_depletion")
    public static void soilNutrientsDeclinePersistAndCannotBeResetByAnotherOwner(GameTestHelper helper){
        var data=new dev.hui.enderinfection.biomass.LandNutrients();BlockPos pos=new BlockPos(123,45,678);
        int fresh=data.harvest(pos,2,1000);helper.assertTrue(fresh==240&&data.remaining(pos)==119,"Fresh land did not give full nutrients once");
        helper.assertTrue(data.harvest(pos,2,1000)==0,"Replacing a node or owner harvested the same soil interval twice");
        int second=data.harvest(pos,2,1200);helper.assertTrue(second==238&&second<fresh,"Eaten land did not decline");
        data=dev.hui.enderinfection.biomass.LandNutrients.load(data.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(data.harvest(pos,2,1200)==0&&data.harvest(pos,2,1400)==236,"Save/load refreshed soil nutrients or its harvest timer");
        for(int i=3;i<120;i++)data.harvest(pos,2,1000+i*200L);
        helper.assertTrue(data.remaining(pos)==0&&data.harvest(pos,2,100000)==0,"Exhausted land kept producing biomass");
        data=dev.hui.enderinfection.biomass.LandNutrients.load(data.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(data.harvest(pos,2,200000)==0&&data.harvest(pos.east(),2,200000)==240,"Old soil reset or a fresh frontier inherited another tile's depletion");helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="territory_income")
    public static void landIncomeScalesWithConvertedAreaAndMaturityWithoutReloadDuplicates(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.BARRIER);
        var core=fundedCore(helper,new BlockPos(7,1,7),100,3);var node=incomeNode(helper,new BlockPos(4,1,4),core,4);
        helper.assertTrue(dev.hui.enderinfection.biomass.TerritoryYield.measure(level,core).columns()==0,"Unconverted claimed radius produced land income");
        var tiles=new java.util.ArrayList<BlockPos>();
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)if((x-4)*(x-4)+(z-4)*(z-4)<=16&&(x!=4||z!=4))tiles.add(new BlockPos(x,0,z));
        for(int i=0;i<16;i++)helper.setBlock(tiles.get(i),InfectedContent.TERRAIN.get("eroded_soil").get());
        var early=dev.hui.enderinfection.biomass.TerritoryYield.measure(level,core);helper.assertTrue(early.columns()==16&&early.weight()==16&&early.nutrientCredits()==1920,"Early territory did not measure actual eroded soil");
        for(int i=0;i<16;i++)helper.setBlock(tiles.get(i),InfectedContent.TERRAIN.get("crystal_crust").get());
        helper.assertTrue(dev.hui.enderinfection.biomass.TerritoryYield.measure(level,core).nutrientCredits()==3840,"Mature land was not worth twice early erosion");
        for(int i=16;i<32;i++)helper.setBlock(tiles.get(i),InfectedContent.TERRAIN.get("crystal_crust").get());
        helper.assertTrue(forceLandHarvest(helper,core)==4&&core.territoryColumns()==32&&core.colonyBiomass()==104,"Larger mature territory did not increase the return");
        var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);core.readAdditionalSaveData(saved);core.setNoAi(false);
        helper.assertTrue(core.collectTerritoryBiomass(level)==0&&forceLandHarvest(helper,core)==0&&core.colonyBiomass()==104,"Reload or rebuilding a mother harvested the same land twice");
        helper.assertTrue(dev.hui.enderinfection.biomass.TerritoryYield.measure(level,core).nutrientCredits()<7680,"Harvested territory kept its original fertility");
        level.removeBlock(node.getBlockPos(),false);
        helper.assertTrue(forceLandHarvest(helper,core)==0&&core.territoryColumns()==0,"Land without an active supplying node kept producing income");
        core.discard();helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="territory_overlap")
    public static void overlappingNodesAndMothersDoNotDoubleCountLandOrLoadRemoteChunks(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("crystal_crust").get());
        var first=fundedCore(helper,new BlockPos(1,1,1),100,3);var second=fundedCore(helper,new BlockPos(7,1,7),400,3);
        incomeNode(helper,new BlockPos(3,1,4),first,3);incomeNode(helper,new BlockPos(5,1,4),first,3);
        int expected=0;
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(((x-3)*(x-3)+(z-4)*(z-4)<=9||(x-5)*(x-5)+(z-4)*(z-4)<=9)&&!((x==3||x==5)&&z==4))expected++;
        helper.assertTrue(dev.hui.enderinfection.biomass.TerritoryYield.measure(level,first).columns()==expected,"Same-mother overlap was counted twice");
        incomeNode(helper,new BlockPos(4,1,6),second,3);expected=0;
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(((x-3)*(x-3)+(z-4)*(z-4)<=9||(x-5)*(x-5)+(z-4)*(z-4)<=9||(x-4)*(x-4)+(z-6)*(z-6)<=9)&&!(((x==3||x==5)&&z==4)||(x==4&&z==6)))expected++;
        var a=dev.hui.enderinfection.biomass.TerritoryYield.measure(level,first);var b=dev.hui.enderinfection.biomass.TerritoryYield.measure(level,second);
        helper.assertTrue(a.columns()+b.columns()==expected&&a.columns()>0&&b.columns()>0,"Two mother wallets both claimed the same overlapping land");
        int paidA=forceLandHarvest(helper,first),paidB=forceLandHarvest(helper,second);
        helper.assertTrue(first.colonyBiomass()==100+paidA&&second.colonyBiomass()==400+paidB,"Land income entered the wrong mother's reserve");
        BlockPos remote=new BlockPos(1234567,80,1234567);helper.assertTrue(!level.hasChunkAt(remote),"Remote test chunk was already loaded");
        var saved=new net.minecraft.nbt.CompoundTag();first.addAdditionalSaveData(saved);saved.getList("ColonyNodes",4).add(net.minecraft.nbt.LongTag.valueOf(remote.asLong()));first.readAdditionalSaveData(saved);
        dev.hui.enderinfection.biomass.TerritoryYield.measure(level,first);helper.assertTrue(!level.hasChunkAt(remote),"Measuring territory force-loaded an inactive remote node");
        first.discard();second.discard();helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="legacy_canopy")
    public static void legacyBirchLeavesRecoverWithoutRepaintingKnownOrAmbiguousCrowns(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();
        var unknown=InfectedContent.TERRAIN.get("eroded_leaves").get().defaultBlockState();
        helper.setBlock(new BlockPos(2,4,2),unknown);helper.setBlock(new BlockPos(1,4,2),Blocks.BIRCH_LOG);helper.setBlock(new BlockPos(3,4,2),Blocks.OAK_LOG);
        helper.assertTrue(dev.hui.enderinfection.block.InfectedCanopyBlock.recoverLegacy(level,helper.absolutePos(new BlockPos(2,4,2)))==null,"Ambiguous touching crowns were guessed as one species");
        helper.setBlock(new BlockPos(2,4,7),unknown);helper.setBlock(new BlockPos(4,4,7),Blocks.BIRCH_LOG);
        helper.assertTrue(dev.hui.enderinfection.block.InfectedCanopyBlock.recoverLegacy(level,helper.absolutePos(new BlockPos(2,4,7)))==null,"Unconnected nearby trunk changed an isolated old leaf");
        var oak=dev.hui.enderinfection.block.InfectedCanopyBlock.from(Blocks.OAK_LEAVES.defaultBlockState(),false);
        helper.assertTrue(oak.getValue(dev.hui.enderinfection.block.InfectedCanopyBlock.KNOWN),"New oak conversion did not remember its source");
        helper.setBlock(new BlockPos(6,4,4),oak);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));fundNode(helper,node);
        for(int y=1;y<=3;y++)helper.setBlock(new BlockPos(5,y,4),Blocks.BIRCH_LOG);
        helper.setBlock(new BlockPos(5,4,4),Blocks.BIRCH_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1));
        helper.setBlock(new BlockPos(5,4,5),unknown);helper.setBlock(new BlockPos(6,4,5),InfectedContent.TERRAIN.get("infected_leaves").get());
        var recovered=dev.hui.enderinfection.block.InfectedCanopyBlock.recoverLegacy(level,helper.absolutePos(new BlockPos(5,4,5)));
        helper.assertTrue(recovered!=null&&recovered.getValue(dev.hui.enderinfection.block.InfectedCanopyBlock.WOOD)==2,"Connected birch crown was not inferred");
        for(int tick=0;tick<2400;tick++)node.serverTick(level);
        for(BlockPos leaf:new BlockPos[]{new BlockPos(5,4,5),new BlockPos(6,4,5)}){
            var state=helper.getBlockState(leaf);helper.assertTrue(state.getValue(dev.hui.enderinfection.block.InfectedCanopyBlock.WOOD)==2&&dev.hui.enderinfection.block.InfectedCanopyBlock.known(state),"Live node failed to repair legacy birch canopy "+leaf);
        }
        helper.assertTrue(helper.getBlockState(new BlockPos(6,4,4)).getValue(dev.hui.enderinfection.block.InfectedCanopyBlock.WOOD)==0,"Known oak leaf beside birch was repainted");
        clearArenaMobs(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="outpost_growth")
    public static void matureNodeBuildsOnePaidOwnedFrontierOutpostAndPersistsIt(GameTestHelper helper){
        clearArenaMobs(helper);prepareOutpostArea(helper,true);
        var core=fundedCore(helper,new BlockPos(4,1,4),200,3);BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());var level=helper.getLevel();var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());
        var tag=node.saveWithFullMetadata();tag.putInt("Radius",9);node.load(tag);node.serverTick(level);
        helper.assertTrue(core.colonyNodeCount()==1&&core.colonyBiomass()==200,"Node branched before reaching the frontier radius");
        tag=node.saveWithFullMetadata();tag.putInt("Radius",10);node.load(tag);node.serverTick(level);
        helper.assertTrue(core.colonyNodeCount()==2&&core.colonyBiomass()==192&&node.spentBiomass()==8,"Frontier outpost was missing, duplicated or not charged eight");
        var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);BlockPos child=null;
        for(var entry:saved.getList("ColonyNodes",4)){
            BlockPos pos=BlockPos.of(((net.minecraft.nbt.LongTag)entry).getAsLong());if(!pos.equals(helper.absolutePos(p)))child=pos;
        }
        helper.assertTrue(child!=null&&child.distSqr(helper.absolutePos(p))>=49&&level.getBlockEntity(child) instanceof ExpansionNodeEntity,"New node was not separated from its parent");
        helper.assertTrue(((ExpansionNodeEntity)level.getBlockEntity(child)).ownerId().equals(core.getUUID()),"Outpost did not inherit the paying mother");
        helper.assertTrue(child.getY()==helper.absolutePos(new BlockPos(0,0,0)).getY()&&level.getBlockState(child).getValue(dev.hui.enderinfection.block.InfectedNestBlock.ROOTED)&&level.getBlockState(child.above()).isAir(),"Outpost was placed above the ground instead of replacing the infected surface");
        core.readAdditionalSaveData(saved);helper.assertTrue(core.colonyNodeCount()==2,"Colony node registry did not survive reload");
        var copy=new ExpansionNodeEntity(helper.absolutePos(p),InfectedContent.NEST.get().defaultBlockState());copy.load(node.saveWithFullMetadata());
        for(int t=0;t<200;t++)copy.serverTick(level);
        helper.assertTrue(core.colonyNodeCount()==2,"Reload allowed one parent to create additional outposts");
        level.removeBlock(child,false);helper.assertTrue(core.colonyNodeCount()==1,"Removed outpost permanently occupied the colony limit");
        core.discard();helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="outpost_limits")
    public static void outpostsRequireTerrainOwnFundsAndBoundedColonyCapacity(GameTestHelper helper){
        clearArenaMobs(helper);prepareOutpostArea(helper,false);var level=helper.getLevel();
        var poor=fundedCore(helper,new BlockPos(4,1,4),16,2);var rich=fundedCore(helper,new BlockPos(1,1,1),400,2);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(poor.getUUID());
        var tag=node.saveWithFullMetadata();tag.putInt("Radius",10);node.load(tag);for(int t=0;t<100;t++)node.serverTick(level);
        helper.assertTrue(poor.colonyNodeCount()==1&&poor.colonyBiomass()==16,"Blocked uninfected frontier consumed construction funds");
        poor.spendBiomass(9);prepareOutpostArea(helper,true);tag=node.saveWithFullMetadata();tag.putInt("OutpostClock",600);node.load(tag);node.serverTick(level);
        helper.assertTrue(poor.colonyNodeCount()==1&&poor.colonyBiomass()==7&&rich.colonyBiomass()==400,"Unfunded outpost borrowed another mother's biomass");
        poor.receiveColonyBiomass(93);
        for(BlockPos site:new BlockPos[]{new BlockPos(12,1,4),new BlockPos(-4,1,4),new BlockPos(4,1,12),new BlockPos(4,1,-4),new BlockPos(12,1,12)}){
            helper.setBlock(site,Blocks.AIR);helper.setBlock(site,InfectedContent.NEST.get());var other=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(site));other.setOwner(poor.getUUID());poor.observeExpansion(other);
        }
        tag=node.saveWithFullMetadata();tag.putInt("OutpostClock",600);tag.putInt("SpreadClock",0);tag.putInt("GrowthClock",0);tag.putInt("DeepClock",0);tag.putInt("MatureClock",0);tag.putInt("FloraClock",0);tag.putInt("SpawnClock",0);node.load(tag);node.serverTick(level);
        helper.assertTrue(poor.colonyNodeCount()==6&&poor.colonyBiomass()==100,"Full colony: nodes "+poor.colonyNodeCount()+", stock "+poor.colonyBiomass());
        poor.discard();rich.discard();helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=140,batch="expansion_priority")
    public static void motherExpandsBeforeEvolvingAndKeepsWorkingCapital(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);
        var core=fundedCore(helper,new BlockPos(4,1,4),500,1);var node=matureExpansionNode(helper,core,2);core.setNoAi(false);
        helper.runAfterDelay(30,()->{
            helper.assertTrue(core.motherStage()==1&&!core.expansionReady(),"Rich mother evolved before establishing its territory");
            var data=node.saveWithFullMetadata();data.putInt("Radius",6);node.load(data);node.serverTick(helper.getLevel());
            helper.runAfterDelay(30,()->{
                helper.assertTrue(core.motherStage()==2&&!core.expansionReady(),"First territory milestone did not allow only stage two");
                var next=node.saveWithFullMetadata();next.putInt("Radius",10);node.load(next);node.serverTick(helper.getLevel());
                helper.runAfterDelay(30,()->{
                    helper.assertTrue(core.motherStage()==3&&core.colonyBiomass()>=16,"Second territory milestone did not allow stage three with working reserve");
                    clearArenaMobs(helper);helper.succeed();
                });
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="native_trees")
    public static void treeConversionPreservesSpeciesFormsAndLegacyBirchTrunks(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);
        for(int wood=0;wood<dev.hui.enderinfection.block.InfectedTimberBlock.SPECIES.size();wood++){
            String species=dev.hui.enderinfection.block.InfectedTimberBlock.SPECIES.get(wood);String suffix=wood==8||wood==9?"_stem":wood==10?"_block":"_log";
            for(boolean stripped:new boolean[]{false,true})for(var axis:net.minecraft.core.Direction.Axis.values()){
                String name=(stripped?"stripped_":"")+species+suffix;
                var original=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",name)).defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,axis);
                var eroded=dev.hui.enderinfection.block.InfectedTimberBlock.from(original,false);var mature=dev.hui.enderinfection.block.InfectedTimberBlock.from(eroded,true);
                for(var state:new net.minecraft.world.level.block.state.BlockState[]{eroded,mature})helper.assertTrue(state.getValue(dev.hui.enderinfection.block.InfectedTimberBlock.WOOD)==wood&&state.getValue(dev.hui.enderinfection.block.InfectedTimberBlock.STRIPPED)==stripped&&state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)==axis,"Tree species, stripping or axis lost: "+name);
            }
        }
        for(int wood=0;wood<dev.hui.enderinfection.block.InfectedCanopyBlock.SPECIES.size();wood++){
            String name=dev.hui.enderinfection.block.InfectedCanopyBlock.SPECIES.get(wood)+"_leaves";
            var original=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",name)).defaultBlockState();
            var eroded=dev.hui.enderinfection.block.InfectedCanopyBlock.from(original,false);helper.assertTrue(dev.hui.enderinfection.block.InfectedCanopyBlock.from(eroded,true).getValue(dev.hui.enderinfection.block.InfectedCanopyBlock.WOOD)==wood,"Canopy species was lost: "+name);
        }
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(p));fundNode(helper,node);
        helper.setBlock(new BlockPos(5,1,4),InfectedContent.TERRAIN.get("eroded_log").get());
        for(int y=2;y<=4;y++)helper.setBlock(new BlockPos(5,y,4),Blocks.BIRCH_LOG);
        helper.setBlock(new BlockPos(5,5,4),Blocks.BIRCH_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,false).setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1));
        for(int tick=0;tick<2400;tick++)node.serverTick(helper.getLevel());
        for(int y=1;y<=4;y++){
            var state=helper.getBlockState(new BlockPos(5,y,4));helper.assertTrue(state.getBlock() instanceof dev.hui.enderinfection.block.InfectedTimberBlock&&state.getValue(dev.hui.enderinfection.block.InfectedTimberBlock.WOOD)==2,"Live node lost birch bark or failed to infer a legacy trunk at height "+y);
        }
        clearArenaMobs(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="emergency_burst")
    public static void criticalMotherSpendsReserveAndGuardsItsAttacker(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);
        var core=fundedCore(helper,new BlockPos(4,1,4),100,1);
        var attacker=net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        var pos=helper.absolutePos(new BlockPos(4,1,2));attacker.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);attacker.setNoAi(true);helper.getLevel().addFreshEntity(attacker);
        core.setInvulnerable(false);core.hurt(helper.getLevel().damageSources().mobAttack(attacker),90);core.setInvulnerable(true);core.setNoAi(false);
        helper.runAfterDelay(4,()->{
            var guards=burstGuards(helper,core);
            helper.assertTrue(core.motherStage()==1&&core.colonyBiomass()==30,"Crisis failed to take priority over upgrading or did not spend seventy percent: "+core.colonyBiomass());
            helper.assertTrue(guards.size()==3,"Juvenile emergency wave should contain three defenders, got "+guards.size());
            helper.assertTrue(guards.stream().allMatch(e->e.getTarget()==attacker),"Emergency guards ignored the actual attacker");
            helper.assertTrue(core.motherState()==dev.hui.enderinfection.entity.MotherBrain.State.BURSTING,"Crisis state was not exposed");
            guards.forEach(e->e.setNoAi(true));
            var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);
            helper.assertTrue(!tag.getBoolean("BurstArmed")&&tag.getLong("BurstReadyAt")>helper.getLevel().getGameTime(),"Burst lock or cooldown was not persisted");
            core.readAdditionalSaveData(tag);core.setNoAi(false);core.receiveColonyBiomass(1000);
            helper.runAfterDelay(35,()->{
                helper.assertTrue(burstGuards(helper,core).size()==3,"Reload or new income caused another burst during the same crisis");
                core.discard();attacker.discard();guards.forEach(net.minecraft.world.entity.Entity::discard);helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=120,batch="emergency_failure")
    public static void blockedOrUnfundedEmergencyDoesNotSpendAndCanRetry(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);
        var core=fundedCore(helper,new BlockPos(4,1,4),7,1);core.setHealth(10);core.setNoAi(false);
        helper.runAfterDelay(4,()->{
            helper.assertTrue(core.colonyBiomass()==7&&burstGuards(helper,core).isEmpty(),"Unfunded mother created free defenders or spent its last reserve");
            for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)for(int y=1;y<7;y++)if(x<3||x>5||z<3||z>5||y>=3)helper.setBlock(new BlockPos(x,y,z),Blocks.STONE);
            core.receiveColonyBiomass(93);
            var waiting=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(waiting);waiting.putLong("LastRecovery",helper.getLevel().getGameTime());waiting.putLong("LastBrood",helper.getLevel().getGameTime());core.readAdditionalSaveData(waiting);core.setNoAi(false);
            helper.runAfterDelay(25,()->{
                var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);
                helper.assertTrue(core.colonyBiomass()==100&&tag.getBoolean("BurstArmed")&&burstGuards(helper,core).isEmpty(),"Blocked burst: stock "+core.colonyBiomass()+", armed "+tag.getBoolean("BurstArmed")+", guards "+burstGuards(helper,core).size()+", health "+core.getHealth());
                for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)for(int y=1;y<7;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
                int delay=(int)Math.max(2,tag.getLong("NextBurstRetry")-helper.getLevel().getGameTime()+2);
                helper.runAfterDelay(delay,()->{
                    helper.assertTrue(core.colonyBiomass()==30&&burstGuards(helper,core).size()==3,"Emergency did not retry safely after space became available");
                    burstGuards(helper,core).forEach(net.minecraft.world.entity.Entity::discard);core.discard();helper.succeed();
                });
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="emergency_cap")
    public static void matureEmergencyHasBoundedExtraCapacityAndRequiresRecovery(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);
        var core=fundedCore(helper,new BlockPos(4,1,4),400,3);
        for(int i=0;i<13;i++){
            var ally=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG).get().create(helper.getLevel());
            var p=helper.absolutePos(new BlockPos(11+i%4,1,1+i/4));ally.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);ally.setNoAi(true);ally.setNoGravity(true);helper.getLevel().addFreshEntity(ally);
        }
        core.setHealth(120);core.setNoAi(false);
        helper.runAfterDelay(4,()->{
            var guards=burstGuards(helper,core);guards.forEach(e->e.setNoAi(true));
            helper.assertTrue(guards.size()==6&&core.colonyBiomass()==120,"Mature emergency was blocked by the normal cap or exceeded its bounded six-unit wave: "+guards.size()+" / "+core.colonyBiomass());
            var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);tag.putLong("BurstReadyAt",helper.getLevel().getGameTime()-1);tag.putInt("EmergenceTicks",0);core.readAdditionalSaveData(tag);core.setNoAi(false);
            helper.runAfterDelay(4,()->{
                var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);
                helper.assertTrue(!saved.getBoolean("BurstArmed")&&burstGuards(helper,core).size()==6,"Cooldown expiry alone retriggered a low-health burst");
                core.setHealth(core.getMaxHealth());
                helper.runAfterDelay(3,()->{
                    var healed=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(healed);helper.assertTrue(healed.getBoolean("BurstArmed"),"Recovered mother did not rearm after the cooldown");
                    clearArenaMobs(helper);helper.succeed();
                });
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="economy_rules")
    public static void reservePricesNeverOverdraftAndAllRatesIncreaseWithWealth(GameTestHelper helper){
        var core=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.CORE).get().create(helper.getLevel());
        helper.assertTrue(core.colonyBiomass()==36,"Starter reserve changed without an explicit economy update");
        helper.assertTrue(!core.spendBiomass(100)&&core.colonyBiomass()==36,"Failed purchase changed the reserve");
        core.spendBiomass(36);helper.assertTrue(!core.spendForHatching(8)&&core.colonyBiomass()==0,"Empty mother hatched for free or overdrafted");
        core.receiveColonyBiomass(32);helper.assertTrue(core.spendForHatching(8)&&core.colonyBiomass()==24,"Juvenile hatch did not cost eight");
        int budget=core.expansionAllowance();helper.assertTrue(core.spendForExpansion(1)&&core.colonyBiomass()==23,"Expansion charge missing");core.refundExpansion(1,1);
        helper.assertTrue(core.colonyBiomass()==24&&core.expansionAllowance()==budget,"Failed expansion refund changed the spending allowance");
        for(int base:new int[]{300,200,160,100,40,20}){
            int previous=Integer.MAX_VALUE;
            for(int reserve:new int[]{0,20,80,240,10000}){
                int interval=dev.hui.enderinfection.biomass.ColonyEconomy.interval(base,reserve);helper.assertTrue(interval<=previous&&interval>=5,"A colony rate became slower with more biomass or exceeded its safe limit");previous=interval;
            }
        }
        helper.assertTrue(dev.hui.enderinfection.biomass.ColonyEconomy.speed(0)==1&&dev.hui.enderinfection.biomass.ColonyEconomy.speed(240)==4&&dev.hui.enderinfection.biomass.ColonyEconomy.speed(10000)==4,"Shared speed curve is not bounded at fourfold");helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="economy_nodes")
    public static void nodeChargesItsOwnMotherAndPersistsOwnership(GameTestHelper helper){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.BARRIER);
        var poor=fundedCore(helper,new BlockPos(7,1,7),0,3);var rich=fundedCore(helper,new BlockPos(1,1,7),1000,3);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(p));node.setOwner(poor.getUUID());
        for(int t=0;t<160;t++)node.serverTick(helper.getLevel());
        helper.assertTrue(node.spentBiomass()==0&&poor.colonyBiomass()==0&&rich.colonyBiomass()==1000,"Node borrowed a neighboring mother's reserve");
        poor.receiveColonyBiomass(100);for(int t=0;t<200;t++)node.serverTick(helper.getLevel());
        helper.assertTrue(poor.colonyBiomass()==100&&node.spentBiomass()==0,"Blocked noninfectable ground consumed expansion funds");
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.SANDSTONE);
        for(int t=0;t<320;t++)node.serverTick(helper.getLevel());
        helper.assertTrue(node.purchasedChanges()>0&&poor.colonyBiomass()==100-node.spentBiomass()&&rich.colonyBiomass()==1000,"Actual terrain changes were not billed exclusively to their owner");
        helper.assertTrue(poor.colonyBiomass()>=8,"Expansion consumed the mother's worker reserve");
        var saved=node.saveWithFullMetadata();var copy=new ExpansionNodeEntity(helper.absolutePos(p),InfectedContent.NEST.get().defaultBlockState());copy.load(saved);
        helper.assertTrue(copy.ownerId().equals(poor.getUUID())&&copy.spentBiomass()==node.spentBiomass(),"Node ownership or spending ledger was lost on reload");
        poor.discard();rich.discard();helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=290,batch="economy_commit")
    public static void failedHatchesAndBlockedUpgradesDoNotCharge(GameTestHelper helper){
        clearArenaMobs(helper);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(int y=1;y<6;y++)helper.setBlock(new BlockPos(x,y,z),(x<3||x>5||z<3||z>5)?Blocks.STONE:Blocks.AIR);
        }
        var core=fundedCore(helper,new BlockPos(4,1,4),36,1);core.setNoAi(false);
        helper.runAfterDelay(70,()->{
            helper.assertTrue(core.colonyBiomass()==36,"Failed hatch placement consumed biomass");
            for(int x=0;x<9;x++)for(int z=0;z<9;z++)for(int y=1;y<6;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            helper.runAfterDelay(40,()->{
                helper.assertTrue(core.colonyBiomass()==28,"Hatch balance "+core.colonyBiomass()+"; state "+core.motherState()+"; nearby infected "+helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,core.getBoundingBox().inflate(24),dev.hui.enderinfection.entity.FactionAI::infected).size());
                var area=new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,7,9)));
                helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,area,e->e!=core).forEach(net.minecraft.world.entity.Entity::discard);
                core.spendBiomass(core.colonyBiomass());core.receiveColonyBiomass(56);matureExpansionNode(helper,core,10);
                for(int x=2;x<=6;x++)for(int z=2;z<=6;z++)helper.setBlock(new BlockPos(x,3,z),Blocks.STONE);
                helper.runAfterDelay(60,()->{
                    helper.assertTrue(core.motherStage()==1&&core.colonyBiomass()==56,"Blocked upgrade spent or lost its reserved cost: "+core.colonyBiomass());
                    for(int x=2;x<=6;x++)for(int z=2;z<=6;z++)helper.setBlock(new BlockPos(x,3,z),Blocks.AIR);
                    helper.runAfterDelay(70,()->{helper.assertTrue(core.motherStage()==2&&core.colonyBiomass()==16,"Successful upgrade did not consume exactly forty and retain sixteen: "+core.colonyBiomass()+" / stage "+core.motherStage());core.discard();helper.succeed();});
                });
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=160,batch="economy_speed")
    public static void gainingBiomassShortensAnAlreadyRunningHatchWait(GameTestHelper helper){
        clearArenaMobs(helper);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);for(int y=1;y<7;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
        var slow=fundedCore(helper,new BlockPos(2,1,4),24,3);var fast=fundedCore(helper,new BlockPos(6,1,4),320,3);
        for(var core:new dev.hui.enderinfection.entity.InfectedMob[]{slow,fast}){var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);tag.putLong("LastBrood",helper.getLevel().getGameTime());core.readAdditionalSaveData(tag);core.setNoAi(false);}
        var area=new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(-8,-3,-8)),helper.absolutePos(new BlockPos(17,8,17)));
        helper.runAfterDelay(90,()->{
            var children=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,area,e->e.getPersistentData().hasUUID("ColonyMother"));
            helper.assertTrue(children.stream().anyMatch(e->e.getPersistentData().getUUID("ColonyMother").equals(fast.getUUID()))&&children.stream().noneMatch(e->e.getPersistentData().getUUID("ColonyMother").equals(slow.getUUID())),"Wealth did not accelerate incubation or independent reserves were mixed");
            children.forEach(net.minecraft.world.entity.Entity::discard);fast.setNoAi(true);slow.receiveColonyBiomass(320);
            helper.runAfterDelay(30,()->{
                var next=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,area,e->e.getPersistentData().hasUUID("ColonyMother"));
                helper.assertTrue(next.stream().anyMatch(e->e.getPersistentData().getUUID("ColonyMother").equals(slow.getUUID())),"Slow mother balance "+slow.colonyBiomass()+"; state "+slow.motherState()+"; nearby infected "+helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,slow.getBoundingBox().inflate(24),dev.hui.enderinfection.entity.FactionAI::infected).size());
                next.forEach(net.minecraft.world.entity.Entity::discard);slow.discard();fast.discard();helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=260,batch="spider_overhang")
    public static void spiderDoesNotJumpForeverUnderAnUnreachableOverhang(GameTestHelper helper){
        clearArenaMobs(helper);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);for(int y=1;y<8;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        for(int x=1;x<=7;x++){
            for(int z=4;z<=8;z++)helper.setBlock(new BlockPos(x,4,z),Blocks.STONE);
            for(int y=1;y<=4;y++)helper.setBlock(new BlockPos(x,y,4),Blocks.STONE);
        }
        var level=helper.getLevel();var spider=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_SPIDER).get().create(level);
        var pos=helper.absolutePos(new BlockPos(4,1,1));spider.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);level.addFreshEntity(spider);
        var target=net.minecraft.world.entity.EntityType.COW.create(level);pos=helper.absolutePos(new BlockPos(4,5,7));target.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);target.setNoAi(true);level.addFreshEntity(target);spider.setTarget(target);
        var resolved=new java.util.concurrent.atomic.AtomicBoolean();
        for(int delay=100;delay<=230;delay+=5)helper.runAfterDelay(delay,()->{
            if(spider.getTarget()!=target||spider.getY()>helper.absolutePos(new BlockPos(0,0,0)).getY()+2.5)resolved.set(true);
        });
        helper.runAfterDelay(240,()->{
            helper.assertTrue(resolved.get(),"Spider kept unreachable target; position "+spider.position()+", target "+target.position()+", has target "+(spider.getTarget()==target)+", wall "+spider.horizontalCollision);spider.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=230,batch="spider_climbing")
    public static void infectedSpiderCanClimbTowardElevatedTarget(GameTestHelper helper){
        clearArenaMobs(helper);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);for(int y=1;y<8;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        for(int x=1;x<=7;x++){
            for(int z=4;z<=8;z++)for(int y=1;y<=4;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        }
        var level=helper.getLevel();var spider=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_SPIDER).get().create(level);
        var pos=helper.absolutePos(new BlockPos(4,1,1));spider.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);level.addFreshEntity(spider);
        var target=net.minecraft.world.entity.EntityType.COW.create(level);pos=helper.absolutePos(new BlockPos(4,5,7));target.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);target.setNoAi(true);level.addFreshEntity(target);spider.setTarget(target);
        helper.runAfterDelay(180,()->{
            helper.assertTrue(spider.getY()>helper.absolutePos(new BlockPos(0,0,0)).getY()+2.5,"Spider position "+spider.position()+"; target "+target.position()+"; wall collision "+spider.horizontalCollision+"; path finished "+spider.getNavigation().isDone()+"; target retained "+(spider.getTarget()==target));
            spider.discard();target.discard();helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=240,batch="mother_growth")
    public static void motherGrowsFromNearbyKillsAndWaitsForSpace(GameTestHelper helper){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(int y=1;y<6;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var level=helper.getLevel();var core=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.CORE).get().create(level);
        var pos=helper.absolutePos(new BlockPos(4,1,4));core.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);core.setNoAi(false);level.addFreshEntity(core);matureExpansionNode(helper,core,10);
        helper.assertTrue(core.motherStage()==1&&core.getMaxHealth()==100&&core.getHealth()==100,"New mother did not start in healthy stage one");
        var worker=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG).get().create(level);pos=helper.absolutePos(new BlockPos(4,1,1));worker.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);worker.setNoAi(true);level.addFreshEntity(worker);
        for(int[] corner:new int[][]{{1,1},{7,1},{1,7},{7,7}}){
            var host=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);pos=helper.absolutePos(new BlockPos(corner[0],1,corner[1]));host.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);host.setNoAi(true);level.addFreshEntity(host);host.hurt(level.damageSources().mobAttack(worker),1000);
        }
        helper.runAfterDelay(70,()->{
            helper.assertTrue(core.colonyBiomass()<=76&&core.motherStage()==2&&core.getMaxHealth()==240,"Nearby zombie kills did not grow the mother into stage two");
            core.receiveColonyBiomass(176-core.colonyBiomass());
            for(int x=2;x<=6;x++)for(int z=2;z<=6;z++)helper.setBlock(new BlockPos(x,4,z),Blocks.STONE);
            helper.runAfterDelay(50,()->{
                helper.assertTrue(core.motherStage()==2,"Mother evolved through a low ceiling: stage "+core.motherStage()+", state "+core.motherState());
                var area=new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,6,9)));
                level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,area,e->e!=core).forEach(net.minecraft.world.entity.Entity::discard);
                for(int x=2;x<=6;x++)for(int z=2;z<=6;z++)helper.setBlock(new BlockPos(x,4,z),Blocks.AIR);
                helper.runAfterDelay(60,()->{
                    // Nodes now spend faster, so they can drain the upgrade reserve while the mother waits for
                    // space. Top the wallet up here: this test is about the space check, not node throughput.
                    core.receiveColonyBiomass(Math.max(0,216-core.colonyBiomass()));
                    helper.runAfterDelay(30,()->{
                        helper.assertTrue(core.motherStage()==3&&core.getMaxHealth()==480,"Mother stage after ceiling cleared: "+core.motherStage()+", stock="+core.colonyBiomass()+", ready="+core.expansionReady()+", state="+core.motherState()+", position="+core.position()+", entities="+level.getEntities(core,core.getBoundingBox().inflate(1)).stream().map(e->e.getType()+" at "+e.position()).toList());
                        var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);
                        var restored=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.CORE).get().create(level);restored.readAdditionalSaveData(tag);
                        helper.assertTrue(restored.motherStage()==3&&restored.colonyBiomass()==core.colonyBiomass()&&restored.getMaxHealth()==480,"Mother stage, biomass or attributes were lost on reload");
                        core.discard();helper.succeed();
                    });
                });
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="tactical_data")
    public static void playerMemoryPersistsDecaysAndKeepsPlayersSeparate(GameTestHelper helper){
        var data=new dev.hui.enderinfection.entity.PlayerTactics.Data();var id=java.util.UUID.randomUUID();long now=100;
        for(int i=0;i<4;i++)data.observe(id,dev.hui.enderinfection.entity.PlayerTactics.Observation.RANGED,now+i);
        helper.assertTrue(data.tactic(id,110)==dev.hui.enderinfection.entity.PlayerTactics.Tactic.ANTI_RANGED,"Repeated ranged attacks were not recognized");
        data=dev.hui.enderinfection.entity.PlayerTactics.Data.load(data.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(data.tactic(id,110)==dev.hui.enderinfection.entity.PlayerTactics.Tactic.ANTI_RANGED,"Tactical memory did not survive reload");
        helper.assertTrue(data.tactic(java.util.UUID.randomUUID(),110)==dev.hui.enderinfection.entity.PlayerTactics.Tactic.BALANCED,"One player's style affected an unknown player");
        for(int i=0;i<4;i++)data.observe(id,dev.hui.enderinfection.entity.PlayerTactics.Observation.HIGH_GROUND,120+i);
        helper.assertTrue(data.tactic(id,130)==dev.hui.enderinfection.entity.PlayerTactics.Tactic.ANTI_TOWER,"Tower tactics did not supersede ordinary ranged pressure");
        for(int i=0;i<4;i++)data.observe(id,dev.hui.enderinfection.entity.PlayerTactics.Observation.RAID,140+i);
        helper.assertTrue(data.tactic(id,150)==dev.hui.enderinfection.entity.PlayerTactics.Tactic.DEFEND_CORE,"Repeated raids did not trigger defense");
        helper.assertTrue(data.tactic(id,13000)==dev.hui.enderinfection.entity.PlayerTactics.Tactic.BALANCED,"Old tactical memory did not decay");
        for(int i=0;i<140;i++)data.observe(new java.util.UUID(0,i+1),dev.hui.enderinfection.entity.PlayerTactics.Observation.MELEE,14000+i);
        helper.assertTrue(data.save(new net.minecraft.nbt.CompoundTag()).getList("Players",10).size()<=128,"Tactical memory exceeded its bounded player count");helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=120,batch="tactical_combat")
    public static void witnessedPlayerAttacksChangeCommandAndBrood(GameTestHelper helper){
        clearWideBattlefield(helper);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);for(int y=1;y<7;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var level=helper.getLevel();var core=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.CORE).get().create(level);
        var pos=helper.absolutePos(new BlockPos(4,1,4));core.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);core.setNoAi(false);level.addFreshEntity(core);
        var guard=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_COW).get().create(level);pos=helper.absolutePos(new BlockPos(4,1,5));guard.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);guard.setNoAi(true);level.addFreshEntity(guard);
        var fake=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"StrategyTest"));fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);pos=helper.absolutePos(new BlockPos(4,1,7));fake.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        for(int damage=1;damage<=4;damage++)guard.hurt(level.damageSources().arrow(new net.minecraft.world.entity.projectile.Arrow(level,fake),fake),damage);
        helper.runAfterDelay(70,()->{
            helper.assertTrue(core.motherTactic()==dev.hui.enderinfection.entity.PlayerTactics.Tactic.ANTI_RANGED,"Witnessed ranged: mother="+core.motherTactic()+", profile="+dev.hui.enderinfection.entity.PlayerTactics.get(level).tactic(fake.getUUID(),level.getGameTime())+", target="+core.getTarget());
            var area=new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,7,9)));
            var brood=level.getEntitiesOfClass(dev.hui.enderinfection.entity.InfectedMob.class,area,e->e!=core&&e!=guard&&e.getPersistentData().hasUUID("ColonyMother")&&e.getPersistentData().getUUID("ColonyMother").equals(core.getUUID()));
            helper.assertTrue(!brood.isEmpty(),"Defending mother did not incubate counter units");
            helper.assertTrue(brood.stream().allMatch(e->e.kind==dev.hui.enderinfection.entity.InfectedKind.EARLY_SPIDER||e.kind==dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG||e.kind==dev.hui.enderinfection.entity.InfectedKind.EARLY_ENDERMAN),"Anti-ranged brood: "+brood.stream().map(e->e.kind.key).toList());
            fake.setPos(fake.getX(),fake.getY()+5,fake.getZ());
            for(int damage=5;damage<=8;damage++)guard.hurt(level.damageSources().arrow(new net.minecraft.world.entity.projectile.Arrow(level,fake),fake),damage);
            helper.runAfterDelay(25,()->{
                helper.assertTrue(core.motherTactic()==dev.hui.enderinfection.entity.PlayerTactics.Tactic.ANTI_TOWER,"Elevated player attacks did not change command to high-ground pressure");
                level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,area).forEach(net.minecraft.world.entity.Entity::discard);fake.discard();helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=80)
    public static void worldIntroductionWaitsFiveSecondsAndPersistsCompletion(GameTestHelper helper){
        var id=java.util.UUID.randomUUID();var data=dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(0);
        helper.assertTrue(data.schedule(id,30),"New world did not schedule its mother");
        helper.assertTrue(!data.ready(129)&&data.ready(130),"Mother delay must be exactly 100 game ticks");
        var restored=dev.hui.enderinfection.entity.WorldIntroduction.Data.load(data.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(restored.ready(130),"Pending introduction did not survive save/load");
        helper.assertTrue(!restored.schedule(java.util.UUID.randomUUID(),130),"Second player scheduled another introduction");
        restored.complete();restored=dev.hui.enderinfection.entity.WorldIntroduction.Data.load(restored.save(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(!restored.ready(10000)&&!restored.schedule(id,10000),"Completed world repeated the introduction");
        helper.assertTrue(!dev.hui.enderinfection.entity.WorldIntroduction.Data.forWorldAge(5000).schedule(id,5000),"Existing world got a new-world event");
        helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="host_conversion")
    public static void infectedKillsConvertEachSupportedHostOnce(GameTestHelper helper){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var level=helper.getLevel();
        var killer=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG).get().create(level);
        var pos=helper.absolutePos(new BlockPos(4,1,4));killer.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);killer.setNoAi(true);level.addFreshEntity(killer);
        net.minecraft.world.entity.EntityType<?>[] hosts={net.minecraft.world.entity.EntityType.ZOMBIE,net.minecraft.world.entity.EntityType.SKELETON,net.minecraft.world.entity.EntityType.COW,net.minecraft.world.entity.EntityType.PIG,net.minecraft.world.entity.EntityType.SHEEP,net.minecraft.world.entity.EntityType.SPIDER,net.minecraft.world.entity.EntityType.ENDERMAN};
        for(int i=0;i<hosts.length;i++){
            var host=(net.minecraft.world.entity.Mob)hosts[i].create(level);pos=helper.absolutePos(new BlockPos(1+(i%3)*3,1,1+(i/3)*3));
            host.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);host.setNoAi(true);level.addFreshEntity(host);
            host.hurt(level.damageSources().mobAttack(killer),1000);
        }
        helper.assertTrue(dev.hui.enderinfection.entity.InfectionConversion.replacementFor(killer)==null,"Infected host can recursively convert");
        helper.runAfterDelay(20,()->{
            var converted=level.getEntitiesOfClass(dev.hui.enderinfection.entity.InfectedMob.class,new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,6,9))),e->e.getPersistentData().contains("InfectedHost"));
            helper.assertTrue(converted.size()==7,"Expected exactly one converted unit for each of seven hosts, got "+converted.size());
            for(var host:hosts){
                String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(host).toString();
                helper.assertTrue(converted.stream().filter(e->e.getPersistentData().getString("InfectedHost").equals(id)&&e.kind.early()).count()==1,"Missing or duplicate early conversion for "+id);
            }
            helper.assertTrue(converted.stream().allMatch(dev.hui.enderinfection.entity.InfectedMob::emerging),"Conversion skipped infection emergence animation");
            converted.forEach(e->e.setNoAi(true));
            helper.runAfterDelay(25,()->{
                int count=level.getEntitiesOfClass(dev.hui.enderinfection.entity.InfectedMob.class,new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,6,9))),e->e.getPersistentData().contains("InfectedHost")).size();
                helper.assertTrue(count==7,"Conversion generated duplicate units on later ticks");helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=80)
    public static void plantsDoNotFreezeGroundAndNaturalBasesArePreserved(GameTestHelper helper){
        for(var bases:dev.hui.enderinfection.block.ErodedGroundBlock.BASES){
            for(int index=0;index<bases.size();index++){
                String name=bases.get(index);var original=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",name.equals("farmland_moist")?"farmland":name)).defaultBlockState();
                if(original.is(Blocks.FARMLAND))original=original.setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,name.equals("farmland_moist")?7:0);
                var eroded=dev.hui.enderinfection.block.ErodedGroundBlock.fromNatural(original);
                helper.assertTrue(eroded.getValue(dev.hui.enderinfection.block.ErodedGroundBlock.SUBSTRATE)==index,"Natural substrate not preserved: "+bases.get(index));
                helper.assertTrue(eroded.getValue(dev.hui.enderinfection.block.ErodedGroundBlock.EROSION)==0,"New terrain skipped initial erosion");
            }
        }
        var plants=new java.util.ArrayList<>(InfectedContent.PLANTS.values());
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("eroded_sand").get().defaultBlockState().setValue(dev.hui.enderinfection.block.ErodedGroundBlock.EROSION,2));
            helper.setBlock(new BlockPos(x,1,z),plants.get((x+z)%4).get());
        }
        BlockPos center=new BlockPos(4,1,4);helper.setBlock(center,InfectedContent.NEST.get());
        ExpansionNodeEntity node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(center));fundNode(helper,node);
        for(int tick=0;tick<2400;tick++)node.serverTick(helper.getLevel());
        int[] matured=new int[4];
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            if(x==4&&z==4)continue;
            helper.assertTrue(helper.getBlockState(new BlockPos(x,1,z)).is(plants.get((x+z)%4).get()),"Existing plant was removed during ground conversion");
            String key=InfectedContent.terrainKey(helper.getBlockState(new BlockPos(x,0,z)));
            if(key!=null&&!key.startsWith("eroded_"))matured[(x+z)%4]++;
        }
        for(int i=0;i<4;i++)helper.assertTrue(matured[i]>0,"Ground could not mature under "+plants.get(i).getId());
        helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=230,batch="faction_combat")
    public static void factionAttacksHostileMobsAndProtectsAllies(GameTestHelper helper){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){
            helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            helper.setBlock(new BlockPos(x,4,z),Blocks.STONE);
        }
        var level=helper.getLevel();
        var pig=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG).get().create(level);
        var ally=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.EARLY_COW).get().create(level);
        var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        var pos=helper.absolutePos(new BlockPos(1,1,4));pig.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        pos=helper.absolutePos(new BlockPos(1,1,1));ally.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        pos=helper.absolutePos(new BlockPos(6,1,4));zombie.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);zombie.setNoAi(true);
        level.addFreshEntity(pig);level.addFreshEntity(ally);level.addFreshEntity(zombie);
        helper.assertTrue(!dev.hui.enderinfection.entity.FactionAI.enemy(pig,ally),"Faction units classified as enemies");
        float health=ally.getHealth();ally.hurt(level.damageSources().mobAttack(pig),5);
        helper.assertTrue(ally.getHealth()==health,"Infected ally took friendly damage");
        helper.runAfterDelay(55,()->{
            helper.assertTrue(pig.getTarget()==zombie,"Infected ignored zombie: target="+pig.getTarget()+", pos="+pig.position()+", task="+pig.getPersistentData().getString("TaskReason"));
            helper.assertTrue(ally.getTarget()==zombie,"Nearby ally did not acquire hostile target");
            ally.setNoAi(true);
            helper.runAfterDelay(120,()->{
                helper.assertTrue(!zombie.isAlive() || zombie.getHealth()<zombie.getMaxHealth(),"Infected did not navigate to and damage hostile zombie");
                helper.assertTrue(pig.getTarget()!=ally,"Infected targeted its ally");
                helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=260)
    public static void nodeSupportsNaturalGroundAndStopsWhenRemoved(GameTestHelper helper){
        BlockPos nodePos=new BlockPos(4,1,4);
        for(Block ground:new Block[]{Blocks.SAND,Blocks.SANDSTONE,Blocks.END_STONE,Blocks.GRASS_BLOCK}){
            helper.setBlock(nodePos,Blocks.AIR);
            for(int x=0;x<9;x++)for(int z=0;z<9;z++){
                helper.setBlock(new BlockPos(x,-1,z),Blocks.STONE);helper.setBlock(new BlockPos(x,0,z),ground);
            }
            helper.setBlock(nodePos,InfectedContent.NEST.get());
            var entity=helper.getLevel().getBlockEntity(helper.absolutePos(nodePos));
            helper.assertTrue(entity instanceof ExpansionNodeEntity,"Expansion node must have a ticking block entity");
            ExpansionNodeEntity node=(ExpansionNodeEntity)entity;fundNode(helper,node);
            for(int tick=0;tick<120;tick++)node.serverTick(helper.getLevel());
            int infected=0;
            for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(InfectedContent.terrainKey(helper.getBlockState(new BlockPos(x,0,z)))!=null)infected++;
            helper.assertTrue(infected>0,"Node failed to erode "+ground.toString());
        }
        helper.setBlock(nodePos,Blocks.AIR);
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)helper.setBlock(new BlockPos(x,0,z),Blocks.SANDSTONE);
        helper.setBlock(nodePos,InfectedContent.NEST.get());
        fundNode(helper,(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(nodePos)));
        helper.runAfterDelay(100,()->{
            int automatic=0;
            for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(InfectedContent.terrainKey(helper.getBlockState(new BlockPos(x,0,z)))!=null)automatic++;
            helper.assertTrue(automatic>0,"Node did not spread through the real server block-entity ticker");
            helper.setBlock(nodePos,Blocks.AIR);
            BlockPos sample=new BlockPos(5,0,4);helper.setBlock(sample,Blocks.SANDSTONE);
            helper.runAfterDelay(80,()->{
                helper.assertTrue(helper.getBlockState(sample).is(Blocks.SANDSTONE),"Ordinary terrain spread without an active node");helper.succeed();
            });
        });
    }
    @GameTest(template="empty64",timeoutTicks=150,batch="ecology_hunt_split")
    public static void huntingSquadsSplitPreyAndKeepPermanentGuards(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,34,0,18);
        var core=fundedCore(helper,new BlockPos(3,1,9),0,1);
        var hunters=new java.util.ArrayList<dev.hui.enderinfection.entity.InfectedMob>();
        for(int i=0;i<4;i++)hunters.add(battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(6+i,1,5+i*2),core,"hunter"));
        var guard=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_COW,new BlockPos(4,1,11),core,"guard");
        for(int z:new int[]{3,9,15}){var prey=net.minecraft.world.entity.EntityType.COW.create(helper.getLevel());placeMob(helper,prey,new BlockPos(26,1,z));prey.setNoAi(true);prey.setInvulnerable(true);helper.getLevel().addFreshEntity(prey);}
        helper.runAfterDelay(100,()->{
            var loads=new java.util.HashMap<java.util.UUID,Integer>();for(var hunter:hunters)if(hunter.getTarget()!=null)loads.merge(hunter.getTarget().getUUID(),1,Integer::sum);
            helper.assertTrue(loads.size()>=2&&loads.values().stream().allMatch(n->n<=1),"Hunters piled onto one ordinary prey instead of reserving separate targets: "+loads);
            helper.assertTrue(guard.getTarget()==null&&guard.distanceToSqr(core)<144,"Permanent guard joined routine animal hunting");clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_learning")
    public static void realKillsTeachOnlyThePayingMotherAndLearningSurvivesReload(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,20,0,12);var level=helper.getLevel();
        var core=fundedCore(helper,new BlockPos(3,1,3),0,1);var foreign=fundedCore(helper,new BlockPos(18,1,9),0,1);
        var kind=dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG;var tactic=dev.hui.enderinfection.entity.PlayerTactics.Tactic.BALANCED;
        var unit=battleUnit(helper,kind,new BlockPos(8,1,4),core,"hunter");unit.setNoAi(true);dev.hui.enderinfection.entity.BroodLearning.stamp(unit,kind,tactic);
        for(int i=0;i<6;i++){var prey=net.minecraft.world.entity.EntityType.COW.create(level);placeMob(helper,prey,new BlockPos(10,1,4));prey.setHealth(1);prey.setNoAi(true);level.addFreshEntity(prey);prey.hurt(level.damageSources().mobAttack(unit),10);}
        int score=core.motherBrain().learning().score(tactic,kind,level.getGameTime());
        helper.assertTrue(score>=15&&foreign.motherBrain().learning().score(tactic,kind,level.getGameTime())==0,"Combat outcomes were not isolated to the original mother");
        int aggression=core.motherBrain().learning().aggression();
        helper.assertTrue(aggression>50&&foreign.motherBrain().learning().aggression()==50,"Winning fights did not raise brood aggression or leaked to another mother");
        unit.discard();var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);core.readAdditionalSaveData(saved);
        helper.assertTrue(core.motherBrain().learning().score(tactic,kind,level.getGameTime())==score,"Learned brood preferences were lost on reload");
        helper.assertTrue(core.motherBrain().learning().aggression()==aggression,"Learned aggression was lost on reload");
        int chosen=0;for(int i=0;i<24;i++)if(core.motherBrain().pickHost()==kind)chosen++;
        helper.assertTrue(chosen>=20,"Successful brood did not influence actual host selection: "+chosen);
        helper.assertTrue(core.motherBrain().learning().score(tactic,kind,level.getGameTime()+24000)==0,"Old combat preferences failed to decay");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=60,batch="ecology_policy")
    public static void colonyPolicyLearnsWhichInvestmentPaysAndSurvivesReload(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,16,0,12);
        var core=fundedCore(helper,new BlockPos(3,1,3),0,1);var policy=core.motherBrain().policy();
        helper.assertTrue(!policy.acting(),"Economy policy acted before it had any measurements");
        for(int window=0;window<40;window++)policy.observeWindow(policy.arm()==dev.hui.enderinfection.entity.ColonyPolicy.Action.EXPAND?20.0:-6.0,core.getRandom());
        helper.assertTrue(policy.acting()&&policy.expandValue()>policy.hatchValue(),"Expanding never outranked hatching after better windows: "+policy.expandValue()+" / "+policy.hatchValue());
        double hold=policy.expandValue(),hatch=policy.hatchValue();int windows=policy.windows();
        var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);core.readAdditionalSaveData(saved);
        var reloaded=core.motherBrain().policy();
        helper.assertTrue(reloaded.windows()==windows&&Math.abs(reloaded.expandValue()-hold)<1e-9&&Math.abs(reloaded.hatchValue()-hatch)<1e-9,"Learned economy policy was lost on reload");
        helper.assertTrue(!reloaded.skipHatching(core),"A reloaded policy skipped hatching without an owned node to spend the biomass");
        clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="ecology_depth")
    public static void nodeErodesDownwardInsteadOfOnlyTheSurfaceLayer(GameTestHelper helper){
        clearArenaMobs(helper);flatBattlefield(helper,0,12,0,8);var level=helper.getLevel();
        for(int x=2;x<=9;x++)for(int z=1;z<=7;z++){helper.setBlock(new BlockPos(x,2,z),Blocks.DIRT);helper.setBlock(new BlockPos(x,1,z),Blocks.DIRT);}
        var core=fundedCore(helper,new BlockPos(2,3,4),1000,2);
        BlockPos p=new BlockPos(4,3,4);helper.setBlock(p,InfectedContent.NEST.get());
        var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());
        var tag=node.saveWithFullMetadata();tag.putInt("Radius",3);node.load(tag);
        for(int i=0;i<240;i++)node.serverTick(level);
        boolean surface=false,deep=false;
        for(int x=2;x<=9;x++)for(int z=1;z<=7;z++){
            if(InfectedContent.terrainKey(helper.getBlockState(new BlockPos(x,2,z)))!=null)surface=true;
            if(InfectedContent.terrainKey(helper.getBlockState(new BlockPos(x,1,z)))!=null)deep=true;
        }
        helper.assertTrue(surface,"Node never eroded the surface layer");
        helper.assertTrue(deep&&node.deepenedBlocks()>0,"Node only eroded the top layer: deepened="+node.deepenedBlocks());
        int buried=0;for(int x=2;x<=9;x++)for(int z=1;z<=7;z++)if(InfectedContent.terrainKey(helper.getBlockState(new BlockPos(x,0,z)))!=null)buried++;
        helper.assertTrue(buried>0,"Buried ground under the surface was left vanilla: "+buried);
        helper.assertTrue(core.colonyBiomass()==1000-node.spentBiomass(),"Deep erosion did not debit the owning mother");
        clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_network_open")
    public static void stageThreeLiftsTheNodeCapWithoutFreeNodes(GameTestHelper helper){
        clearArenaMobs(helper);flatBattlefield(helper,0,40,0,20);var level=helper.getLevel();
        for(int x=0;x<=40;x++)for(int z=0;z<=20;z++)helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("crystal_crust").get());
        var core=fundedCore(helper,new BlockPos(5,1,10),1000,3);
        for(int[] site:new int[][]{{30,2},{34,2},{38,2},{30,8},{34,8},{38,8}}){
            BlockPos pos=new BlockPos(site[0],1,site[1]);helper.setBlock(pos,Blocks.AIR);helper.setBlock(pos,InfectedContent.NEST.get());
            var other=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(pos));other.setOwner(core.getUUID());core.observeExpansion(other);
        }
        helper.assertTrue(core.colonyNodeCount()==6,"Fixture did not establish the six node network: "+core.colonyNodeCount());
        BlockPos p=new BlockPos(5,1,10);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());
        var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());core.observeExpansion(node);
        var tag=node.saveWithFullMetadata();tag.putInt("Radius",10);tag.putInt("OutpostClock",600);tag.putInt("DeepClock",0);tag.putInt("MatureClock",0);tag.putInt("FloraClock",0);tag.putInt("SpawnClock",0);node.load(tag);
        node.serverTick(level);
        helper.assertTrue(core.colonyNodeCount()==8,"Stage three still capped the network at six nodes: "+core.colonyNodeCount());
        helper.assertTrue(core.colonyBiomass()==992,"The new nodes were not paid for: stock="+core.colonyBiomass()+", nodeSpent="+node.spentBiomass()+", changes="+node.purchasedChanges()+", deepened="+node.deepenedBlocks()+", born="+node.soilBorn()+", note="+node.spawnNote());
        clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=120,batch="ecology_soil_life")
    public static void matureSoilGrowsFloraAndBirthsUnits(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++)helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("living_tissue").get());
        var core=fundedCore(helper,new BlockPos(4,1,4),1000,3);core.setNoAi(false);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,InfectedContent.NEST.get());
        var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());
        var tag=node.saveWithFullMetadata();tag.putInt("Radius",4);tag.putInt("FloraClock",0);tag.putInt("SpawnClock",0);node.load(tag);
        for(int i=0;i<220;i++)node.serverTick(level);
        int plants=0;for(BlockPos q:BlockPos.betweenClosed(new BlockPos(1,1,1),new BlockPos(7,3,7)))if(helper.getBlockState(q).getBlock() instanceof dev.hui.enderinfection.block.InfectedPlantBlock)plants++;
        helper.assertTrue(plants>0,"Fully eroded ground never grew its own flora");
        helper.assertTrue(node.soilBorn()>0,"Fully eroded ground never birthed a unit: born="+node.soilBorn()+", note="+node.spawnNote()+", stock="+core.colonyBiomass()+", nodeRadius="+node.radius()+", changes="+node.purchasedChanges());
        helper.assertTrue(core.colonyBiomass()<1000,"Land born units were free: "+core.colonyBiomass());
        clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="ecology_mature")
    public static void erodedGroundMaturesInsteadOfStayingAtFirstStage(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++)helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("eroded_grass").get());
        var core=fundedCore(helper,new BlockPos(4,1,4),1000,3);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,InfectedContent.NEST.get());
        var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());
        var tag=node.saveWithFullMetadata();tag.putInt("Radius",3);tag.putInt("MatureClock",0);tag.putInt("SpreadClock",0);tag.putInt("SpawnClock",0);tag.putInt("FloraClock",0);tag.putInt("DeepClock",0);node.load(tag);
        for(int i=0;i<200;i++)node.serverTick(level);
        int mature=0;
        for(BlockPos q:BlockPos.betweenClosed(new BlockPos(1,0,1),new BlockPos(7,0,7))){
            String key=InfectedContent.terrainKey(helper.getBlockState(q));
            if(key!=null&&!key.startsWith("eroded_"))mature++;
        }
        helper.assertTrue(mature>0,"Eroded ground never matured past its first stage: "+mature);
        helper.assertTrue(core.colonyBiomass()<1000,"Maturing ground was free: "+core.colonyBiomass());
        clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_slope")
    public static void frontierErosionClimbsConnectedOneBlockSteps(GameTestHelper helper){
        clearArenaMobs(helper);flatBattlefield(helper,0,12,0,8);var core=fundedCore(helper,new BlockPos(2,1,6),1000,2);
        for(int x=6;x<=12;x++)for(int z=0;z<=8;z++)helper.setBlock(new BlockPos(x,1,z),Blocks.DIRT);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());
        var saved=node.saveWithFullMetadata();saved.putInt("Radius",5);node.load(saved);for(int i=0;i<180;i++)node.serverTick(helper.getLevel());
        helper.assertTrue(InfectedContent.terrainKey(helper.getBlockState(new BlockPos(6,1,4)))!=null,"Connected frontier stopped at a one-block step");
        helper.assertTrue(node.purchasedChanges()>0&&core.colonyBiomass()==1000-node.spentBiomass(),"Slope changes did not debit the owner correctly");
        var copy=new ExpansionNodeEntity(helper.absolutePos(p),InfectedContent.NEST.get().defaultBlockState());copy.load(node.saveWithFullMetadata());helper.assertTrue(copy.saveWithFullMetadata().getInt("FrontierCursor")==node.saveWithFullMetadata().getInt("FrontierCursor"),"Frontier scan restarted after reload");core.discard();helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_frontier_choice")
    public static void outpostSelectsFreshNutrientsOverExhaustedLand(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,34,0,24);var level=helper.getLevel();
        for(int x=0;x<=34;x++)for(int z=0;z<=24;z++)helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("mycelial_mat").get());
        var core=fundedCore(helper,new BlockPos(15,1,12),300,3);BlockPos p=new BlockPos(15,1,12);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)level.getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());core.observeExpansion(node);
        var soils=dev.hui.enderinfection.biomass.LandNutrients.get(level);
        for(int x=0;x<=15;x++)for(int z=0;z<=24;z++)for(int t=0;t<120;t++)soils.harvest(helper.absolutePos(new BlockPos(x,0,z)),1,level.getGameTime()+t*200L);
        double west=node.frontierScore(level,helper.absolutePos(new BlockPos(7,1,12)),core),east=node.frontierScore(level,helper.absolutePos(new BlockPos(23,1,12)),core);
        helper.assertTrue(east>west+10,"Fresh frontier was not valued above depleted land");
        var saved=node.saveWithFullMetadata();saved.putInt("Radius",10);saved.putInt("OutpostClock",600);node.load(saved);node.serverTick(level);
        var child=core.activeColonyNodes().stream().filter(n->n!=node).findFirst().orElse(null);
        helper.assertTrue(child!=null&&child.getBlockPos().getX()>helper.absolutePos(p).getX()&&core.colonyBiomass()==292,"Outpost failed to choose/pay for the fresh eastern frontier");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="ecology_boss_production")
    public static void bossBroodRequiresHighTierOwnFundsSpaceAndOwnedCap(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,32,0,24);var level=helper.getLevel();
        var young=fundedCore(helper,new BlockPos(3,1,4),1000,3);helper.assertTrue(!young.summonSiege(level),"Stage three spawned a boss before its unlock");young.discard();
        var core=fundedCore(helper,new BlockPos(12,1,12),500,4);helper.assertTrue(core.getMaxHealth()==900&&core.maxAutoNodes()==dev.hui.enderinfection.entity.InfectedMob.UNLIMITED_NODES,"Stage four did not get its own attributes/network capacity");
        helper.assertTrue(core.summonSiege(level)&&core.colonyBiomass()==340,"Boss production failed to debit 160 biomass");
        var beasts=level.getEntitiesOfClass(dev.hui.enderinfection.entity.InfectedMob.class,core.getBoundingBox().inflate(24),e->e.kind==dev.hui.enderinfection.entity.InfectedKind.SIEGE_BEAST);
        helper.assertTrue(beasts.size()==1&&beasts.get(0).getMaxHealth()==400&&beasts.get(0).getPersistentData().getUUID("ColonyMother").equals(core.getUUID()),"Boss entity stats/ownership incorrect");
        helper.assertTrue(!core.summonSiege(level)&&core.colonyBiomass()==340,"Owned boss cap allowed duplicate or charged a failed spawn");
        var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);saved.putInt("MotherStage",5);core.readAdditionalSaveData(saved);helper.assertTrue(core.motherStage()==5&&core.getMaxHealth()==1000&&core.maxAutoNodes()==dev.hui.enderinfection.entity.InfectedMob.UNLIMITED_NODES,"Stage five did not survive its NBT lifecycle");
        core.spendBiomass(core.colonyBiomass());helper.assertTrue(!core.summonSiege(level)&&core.colonyBiomass()==0,"Empty high-tier mother produced a free boss");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=170,batch="ecology_boss_wave")
    public static void bossCrystalWaveHitsDistantEnemyOnceAndSparesFaction(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,30,0,16);var level=helper.getLevel();
        var beast=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.SIEGE_BEAST,new BlockPos(3,1,8),null,"hunter");
        var ally=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_COW,new BlockPos(12,1,11),null,"guard");ally.setNoAi(true);
        var enemy=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);placeMob(helper,enemy,new BlockPos(17,1,8));enemy.setNoAi(true);enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);enemy.setHealth(100);level.addFreshEntity(enemy);beast.setTarget(enemy);
        helper.runAfterDelay(115,()->{helper.assertTrue(enemy.getHealth()>=87&&enemy.getHealth()<=89,"Crystal wave failed to reach its target or damaged it more than once: "+enemy.getHealth()+" beast="+beast.position()+" attacking="+beast.attacking()+" enemy="+enemy.position()+" onFire="+enemy.isOnFire()+" ally="+ally.getHealth());helper.assertTrue(ally.getHealth()==ally.getMaxHealth(),"Boss crystal wave hurt its own faction");clearWideBattlefield(helper);helper.succeed();});
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_high_evolution")
    public static void highMotherEvolutionRequiresRealTerritoryAndPaysBothUpgradeCosts(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,40,0,40);var level=helper.getLevel();var core=fundedCore(helper,new BlockPos(10,1,10),2000,3);
        helper.assertTrue(!core.expansionReady(),"High evolution was ready without any territory");
        for(int x=0;x<=40;x++)for(int z=0;z<=40;z++)helper.setBlock(new BlockPos(x,0,z),InfectedContent.TERRAIN.get("mycelial_mat").get());
        for(BlockPos pos:new BlockPos[]{new BlockPos(4,1,4),new BlockPos(16,1,4),new BlockPos(28,1,4),new BlockPos(4,1,22),new BlockPos(16,1,22),new BlockPos(28,1,22)})incomeNode(helper,pos,core,14);
        forceLandHarvest(helper,core);int before=core.colonyBiomass();
        helper.assertTrue(core.territoryColumns()>=480&&core.expansionReady(),"Loaded real territory did not unlock high evolution");
        for(int i=0;i<8;i++)core.motherBrain().tick(level);
        helper.assertTrue(core.motherStage()==4&&core.colonyBiomass()==before-500,"Stage four did not require/pay 500 biomass");
        var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);saved.putInt("EmergenceTicks",0);core.readAdditionalSaveData(saved);
        for(int i=0;i<8;i++)core.motherBrain().tick(level);
        helper.assertTrue(core.motherStage()==5&&core.getMaxHealth()==1000&&core.colonyBiomass()==before-1900,"Stage five did not require/pay its additional 1400 biomass");
        core.setNoAi(true);clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=370,batch="ecology_suppression")
    public static void craftedDustPausesOwnedNodeWithoutSpendingThenResumes(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();var core=fundedCore(helper,new BlockPos(7,1,7),200,2);
        BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,InfectedContent.NEST.get());var absolute=helper.absolutePos(p);var node=(ExpansionNodeEntity)level.getBlockEntity(absolute);node.setOwner(core.getUUID());
        var fake=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"SuppressionTest"));fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var stack=new net.minecraft.world.item.ItemStack(dev.hui.enderinfection.item.InfectionItems.DUST.get(),2);fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(absolute),net.minecraft.core.Direction.UP,absolute,false);
        dev.hui.enderinfection.item.InfectionItems.DUST.get().useOn(new net.minecraft.world.item.context.UseOnContext(fake,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
        helper.assertTrue(stack.getCount()==1&&node.inhibited(level),"Dust item did not consume/apply suppression");
        for(int i=0;i<100;i++)node.serverTick(level);helper.assertTrue(core.colonyBiomass()==200&&node.spentBiomass()==0,"Suppressed node still spent biomass");
        var copy=new ExpansionNodeEntity(absolute,InfectedContent.NEST.get().defaultBlockState());copy.load(node.saveWithFullMetadata());helper.assertTrue(copy.inhibited(level),"Node suppression was lost on reload");
        helper.runAfterDelay(330,()->{helper.assertTrue(!node.inhibited(level)&&node.purchasedChanges()>0,"Node did not resume paid spreading after suppression expired");fake.discard();core.discard();helper.succeed();});
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_loot")
    public static void bossLootSpawnsRealMaterialsAndRecipesAreLoaded(GameTestHelper helper){
        clearArenaMobs(helper);prepareBurstArena(helper);var level=helper.getLevel();
        var boss=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.SIEGE_BEAST,new BlockPos(4,1,4),null,"hunter");boss.setNoAi(true);boss.kill();
        helper.runAfterDelay(5,()->{
            var items=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,7,9))));
            helper.assertTrue(items.stream().anyMatch(e->e.getItem().is(dev.hui.enderinfection.item.InfectionItems.HEART.get()))&&items.stream().anyMatch(e->e.getItem().is(dev.hui.enderinfection.item.InfectionItems.SHARD.get())),"Boss death produced no actual heart/shard drops");
            helper.assertTrue(level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("ender_infection","resonance_blade")).isPresent()&&level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("ender_infection","suppressing_dust")).isPresent(),"New crafting recipes did not load");items.forEach(net.minecraft.world.entity.Entity::discard);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="ecology_remote_credit")
    public static void distantFrontierKillsNeverCreditAForeignMotherWhenHomeIsUnavailable(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,63,0,63);var level=helper.getLevel();
        var owner=fundedCore(helper,new BlockPos(2,1,2),100,5);var foreign=fundedCore(helper,new BlockPos(54,1,54),500,3);
        var hunter=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(60,1,59),owner,"hunter");hunter.setNoAi(true);
        var prey=net.minecraft.world.entity.EntityType.COW.create(level);placeMob(helper,prey,new BlockPos(60,1,60));prey.setNoAi(true);level.addFreshEntity(prey);prey.hurt(level.damageSources().mobAttack(hunter),100);
        helper.assertTrue(owner.colonyBiomass()==105&&foreign.colonyBiomass()==500,"A hunt beyond 64 blocks credited the closer foreign mother");
        owner.discard();int shared=dev.hui.enderinfection.biomass.BiomassData.get(level).amount();
        var second=net.minecraft.world.entity.EntityType.COW.create(level);placeMob(helper,second,new BlockPos(59,1,60));second.setNoAi(true);level.addFreshEntity(second);second.hurt(level.damageSources().mobAttack(hunter),100);
        helper.assertTrue(hunter.getPersistentData().getInt("PendingColonyBiomass")==5&&foreign.colonyBiomass()==500&&dev.hui.enderinfection.biomass.BiomassData.get(level).amount()==shared,"Unavailable owner caused the hunt proceeds to be transferred to another colony");
        var evolved=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.PIG).get().create(level);dev.hui.enderinfection.entity.FactionAI.inheritOrders(hunter,evolved);helper.assertTrue(evolved.getPersistentData().getInt("PendingColonyBiomass")==5,"Evolution dropped pending colony proceeds");
        clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="surface_seed")
    public static void motherSeedsAnOwnedSurfaceReplacementInsteadOfAnAirBlock(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,12,0,12);var level=helper.getLevel();
        var core=fundedCore(helper,new BlockPos(4,1,4),100,1);var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);saved.putBoolean("ExpansionNodeCreated",false);core.readAdditionalSaveData(saved);
        helper.runAfterDelay(45,()->{
            var nodes=core.activeColonyNodes();helper.assertTrue(nodes.size()==1,"Mother failed to seed exactly one node");var node=nodes.get(0);var p=node.getBlockPos();
            helper.assertTrue(p.getY()==helper.absolutePos(BlockPos.ZERO).getY()&&node.getBlockState().getValue(dev.hui.enderinfection.block.InfectedNestBlock.ROOTED)&&level.getBlockState(p.above()).isAir(),"Seed occupied air above the ground");
            helper.assertTrue(node.ownerId().equals(core.getUUID())&&node.saveWithFullMetadata().getString("SubstrateBlock").equals("minecraft:stone")&&core.colonyBiomass()<=92,"Seed failed to preserve owner/substrate or debit construction");
            clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="plant_mushrooms")
    public static void fungiAboveMyceliumErodeWithoutBlockingTheFrontier(GameTestHelper helper){
        clearWideBattlefield(helper);var level=helper.getLevel();preparePlantPatch(helper);var core=fundedCore(helper,new BlockPos(7,1,7),10,1);
        BlockPos soil=new BlockPos(5,0,4);helper.setBlock(soil,Blocks.MYCELIUM);helper.setBlock(soil.above(),Blocks.RED_MUSHROOM);
        var node=plantNode(helper,core);while(node.purchasedChanges()==0&&node.saveWithFullMetadata().getInt("ActiveTicks")<100)node.serverTick(level);
        var ground=helper.getBlockState(soil);var plant=helper.getBlockState(soil.above());
        helper.assertTrue(ground.is(InfectedContent.TERRAIN.get("eroded_grass").get())&&ground.getValue(dev.hui.enderinfection.block.ErodedGroundBlock.SUBSTRATE)==3,"Mushroom blocked mycelium erosion or lost its native substrate");
        helper.assertTrue(plant.is(InfectedContent.PLANTS.get("eroded_vegetation").get())&&plant.getValue(dev.hui.enderinfection.block.ErodedVegetationBlock.SOURCE)==17&&plant.getValue(dev.hui.enderinfection.block.ErodedVegetationBlock.EROSION)==0,"Red mushroom skipped its native-shaped initial erosion");
        helper.assertTrue(core.colonyBiomass()==8&&node.purchasedChanges()==2,"Soil and mushroom changes did not cost two biomass");
        helper.assertTrue(core.nodePurchasedChanges()==2&&core.nodeSpentBiomass()==2&&Math.abs(core.nodeEfficiency()-1.0)<1e-9,"Mother did not aggregate her node ledger: changes "+core.nodePurchasedChanges()+", spend "+core.nodeSpentBiomass()+", efficiency "+core.nodeEfficiency());
        core.receiveColonyBiomass(190);helper.setBlock(soil,InfectedContent.TERRAIN.get("mycelial_mat").get());for(int i=0;i<200;i++)node.serverTick(level);
        helper.assertTrue(helper.getBlockState(soil.above()).is(InfectedContent.PLANTS.get("infected_fungus").get()),"Eroded mushroom never matured into infected fungus");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="plant_crops")
    public static void cropAgeAndFarmlandHeightSurviveAtomicPaidErosion(GameTestHelper helper){
        clearWideBattlefield(helper);preparePlantPatch(helper);var level=helper.getLevel();var core=fundedCore(helper,new BlockPos(7,1,7),10,1);BlockPos soil=new BlockPos(5,0,4);
        var node=plantNode(helper,core);
        helper.setBlock(soil,Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,7));helper.setBlock(soil.above(),Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,5));
        helper.assertTrue(dev.hui.enderinfection.block.Vegetation.singleIndex(helper.getBlockState(soil.above()))==36,"Native age-five crop fixture was not present before any erosion: "+helper.getBlockState(soil.above()));
        for(int i=0;i<100&&node.purchasedChanges()==0;i++)node.serverTick(level);
        var ground=helper.getBlockState(soil);var crop=helper.getBlockState(soil.above());
        helper.assertTrue(dev.hui.enderinfection.block.ErodedGroundBlock.farmland(ground)&&ground.getValue(dev.hui.enderinfection.block.ErodedGroundBlock.SUBSTRATE)==6&&ground.getCollisionShape(level,helper.absolutePos(soil)).max(net.minecraft.core.Direction.Axis.Y)==.9375,"Moist farmland texture or physical height was lost");
        helper.assertTrue(crop.is(InfectedContent.PLANTS.get("eroded_vegetation").get())&&crop.getValue(dev.hui.enderinfection.block.ErodedVegetationBlock.SOURCE)==36&&crop.canSurvive(level,helper.absolutePos(soil.above())),"Wheat age five was dropped or reset while replacing farmland: "+crop+", surviving="+crop.canSurvive(level,helper.absolutePos(soil.above()))+", changes="+node.purchasedChanges());
        helper.assertTrue(core.colonyBiomass()==8&&node.purchasedChanges()==2,"Crop conversion was not billed atomically");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="plant_tall")
    public static void tallPlantsKeepBothHalvesUntilMatureAndNeverPartiallySpend(GameTestHelper helper){
        clearWideBattlefield(helper);preparePlantPatch(helper);var level=helper.getLevel();var core=fundedCore(helper,new BlockPos(7,1,7),10,1);BlockPos soil=new BlockPos(5,0,4);
        helper.setBlock(soil,Blocks.GRASS_BLOCK);helper.setBlock(soil.above(),Blocks.LARGE_FERN.defaultBlockState());helper.setBlock(soil.above(2),Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));var node=plantNode(helper,core);
        for(int i=0;i<100;i++)node.serverTick(level);
        helper.assertTrue(helper.getBlockState(soil).is(Blocks.GRASS_BLOCK)&&helper.getBlockState(soil.above()).is(Blocks.LARGE_FERN)&&core.colonyBiomass()==10&&node.purchasedChanges()==0,"Unaffordable soil/fern transaction partially changed blocks or charged biomass");
        core.receiveColonyBiomass(190);for(int i=0;i<100&&node.purchasedChanges()==0;i++)node.serverTick(level);
        var lower=helper.getBlockState(soil.above());var upper=helper.getBlockState(soil.above(2));
        helper.assertTrue(lower.is(InfectedContent.PLANTS.get("eroded_tall_vegetation").get())&&lower.getValue(dev.hui.enderinfection.block.ErodedTallVegetationBlock.SOURCE)==1&&lower.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER&&upper.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER,"Double fern lost species or one half");
        helper.assertTrue(core.colonyBiomass()==197&&node.purchasedChanges()==3,"Double plant transaction did not bill all three blocks");
        helper.setBlock(soil,InfectedContent.TERRAIN.get("mycelial_mat").get());for(int i=0;i<160;i++)node.serverTick(level);
        helper.assertTrue(helper.getBlockState(soil.above()).is(InfectedContent.PLANTS.get("infected_grass").get())&&helper.getBlockState(soil.above(2)).isAir(),"Mature plant left a floating upper half");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=120,batch="crystal_skeleton")
    public static void matureSkeletonActuallyCastsWarnedSpikesInsteadOfArrows(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,24,0,16);var level=helper.getLevel();
        for(int x=0;x<=24;x++)for(int z=0;z<=16;z++)helper.setBlock(new BlockPos(x,4,z),Blocks.STONE);
        var caster=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.SKELETON,new BlockPos(4,1,8),null,"hunter");var target=durableTarget(helper,new BlockPos(14,1,8));caster.setTarget(target);
        var ally=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_PIG,new BlockPos(14,1,10),null,"guard");ally.setNoAi(true);
        var observed=new boolean[1];
        for(int t=1;t<=84;t++)helper.runAfterDelay(t,()->{if(!spellEntities(helper).isEmpty())observed[0]=true;helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,caster.getBoundingBox().inflate(28)).isEmpty(),"Mature skeleton still fired arrows");});
        helper.runAfterDelay(85,()->{helper.assertTrue(observed[0]&&target.getHealth()<100&&target.getHealth()>89,"Actual skeleton AI failed to cast one damaging spell: hp="+target.getHealth());helper.assertTrue(ally.getHealth()==ally.getMaxHealth(),"Crystal spell damaged the infected faction");clearWideBattlefield(helper);spellEntities(helper).forEach(net.minecraft.world.entity.Entity::discard);helper.succeed();});
    }
    @GameTest(template="empty64",timeoutTicks=100,batch="crystal_dodge")
    public static void crystalWarningCanBeDodgedAndEachCastHitsAnEnemyOnlyOnce(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,28,0,20);var level=helper.getLevel();var caster=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.SKELETON,new BlockPos(4,1,8),null,"hunter");caster.setNoAi(true);
        var target=durableTarget(helper,new BlockPos(13,1,8));var dodge=durableTarget(helper,new BlockPos(13,1,8));
        int count=dev.hui.enderinfection.combat.CrystalAttacks.cast(caster,caster.kind,target,16);helper.assertTrue(count==7,"Skeleton did not create its center and hexagon: "+count);
        helper.runAfterDelay(10,()->{helper.assertTrue(target.getHealth()==100&&dodge.getHealth()==100,"Ground spikes damaged enemies during their warning");var p=helper.absolutePos(new BlockPos(23,1,15));dodge.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);});
        helper.runAfterDelay(42,()->{helper.assertTrue(target.getHealth()>91&&target.getHealth()<94&&dodge.getHealth()==100,"Spell repeated damage or followed an enemy after casting: "+target.getHealth()+" / "+dodge.getHealth());helper.assertTrue(spellEntities(helper).isEmpty(),"Expired spikes remained in the world");helper.assertTrue(helper.getBlockState(new BlockPos(13,0,8)).is(Blocks.STONE),"Temporary spell replaced terrain");clearWideBattlefield(helper);helper.succeed();});
    }
    @GameTest(template="empty64",timeoutTicks=110,batch="crystal_cancel")
    public static void recallCancelsPendingCrystalDamage(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,24,0,16);var level=helper.getLevel();for(int x=0;x<=24;x++)for(int z=0;z<=16;z++)helper.setBlock(new BlockPos(x,4,z),Blocks.STONE);
        var caster=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.SKELETON,new BlockPos(4,1,8),null,"hunter");var target=durableTarget(helper,new BlockPos(14,1,8));caster.setTarget(target);
        helper.runAfterDelay(40,()->{helper.assertTrue(caster.attacking()&&!spellEntities(helper).isEmpty(),"Cancellation test missed the warned cast");caster.interruptForOrders();caster.setTarget(null);caster.setNoAi(true);});
        helper.runAfterDelay(80,()->{helper.assertTrue(target.getHealth()==100&&spellEntities(helper).isEmpty(),"Interrupted cast still dealt damage or leaked spikes");clearWideBattlefield(helper);helper.succeed();});
    }
    @GameTest(template="empty64",timeoutTicks=80,batch="crystal_species")
    public static void matureSpeciesUseDistinctPatternsAndEarlyHostsCannotCast(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,32,0,24);var level=helper.getLevel();var target=durableTarget(helper,new BlockPos(17,1,12));
        var kinds=new dev.hui.enderinfection.entity.InfectedKind[]{dev.hui.enderinfection.entity.InfectedKind.COW,dev.hui.enderinfection.entity.InfectedKind.PIG,dev.hui.enderinfection.entity.InfectedKind.SHEEP,dev.hui.enderinfection.entity.InfectedKind.SPIDER,dev.hui.enderinfection.entity.InfectedKind.ENDERMAN};int[] expected={6,6,8,5,7};
        for(int i=0;i<kinds.length;i++){var caster=battleUnit(helper,kinds[i],new BlockPos(8,1,12),null,"hunter");caster.setNoAi(true);int count=dev.hui.enderinfection.combat.CrystalAttacks.cast(caster,kinds[i],target,18);helper.assertTrue(count==expected[i],"Missing species pattern for "+kinds[i]+": "+count);spellEntities(helper).forEach(net.minecraft.world.entity.Entity::discard);caster.discard();}
        var early=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_SKELETON,new BlockPos(8,1,12),null,"hunter");helper.assertTrue(dev.hui.enderinfection.combat.CrystalAttacks.cast(early,early.kind,target,16)==0,"Early host acquired mature crystal spell");clearWideBattlefield(helper);helper.succeed();
    }
    @GameTest(template="empty64",timeoutTicks=130,batch="siege_ravager_charge")
    public static void ravagerSiegeWarnsBeforeLungingAndHitsEachEnemyOnce(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,30,0,18);var level=helper.getLevel();
        var beast=readySiege(helper,new BlockPos(3,1,8));var target=durableTarget(helper,new BlockPos(17,1,8));beast.setTarget(target);
        var ally=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.EARLY_COW,new BlockPos(16,1,10),null,"guard");ally.setNoAi(true);var start=beast.position();
        helper.runAfterDelay(8,()->helper.assertTrue(beast.attacking()&&target.getHealth()==100&&beast.position().distanceToSqr(start)<.5,"Siege beast skipped its stationary warning"));
        helper.runAfterDelay(24,()->helper.assertTrue(beast.getX()-start.x>.8&&beast.getX()-start.x<4.5,"Committed siege lunge did not advance a bounded distance: "+beast.position()));
        helper.runAfterDelay(95,()->{
            helper.assertTrue(target.getHealth()>=87&&target.getHealth()<=89,"Siege wave missed or hit the distant enemy repeatedly: "+target.getHealth());
            helper.assertTrue(ally.getHealth()==ally.getMaxHealth(),"New siege effects/attack hurt an infected ally");
            helper.assertTrue(helper.getBlockState(new BlockPos(6,0,8)).is(Blocks.STONE),"Siege impact replaced its terrain");clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=125,batch="siege_ravager_wall")
    public static void ravagerSiegeLungeStopsAtWallsAndCannotDamageThroughThem(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,30,0,18);var level=helper.getLevel();
        var beast=readySiege(helper,new BlockPos(3,1,8));var target=durableTarget(helper,new BlockPos(17,1,8));beast.setTarget(target);
        helper.runAfterDelay(5,()->{
            helper.assertTrue(beast.attacking(),"Wall test missed the start of the visible windup");
            for(int z=0;z<=18;z++)for(int y=1;y<=6;y++)helper.setBlock(new BlockPos(8,y,z),Blocks.STONE);
        });
        helper.runAfterDelay(95,()->{
            helper.assertTrue(beast.getX()<helper.absolutePos(new BlockPos(8,1,8)).getX()&&target.getHealth()==100,"Siege lunge or shock wave crossed a solid wall");
            helper.assertTrue(helper.getBlockState(new BlockPos(8,2,8)).is(Blocks.STONE),"Cosmetic impact broke a wall");clearWideBattlefield(helper);helper.succeed();
        });
    }
    @GameTest(template="empty64",timeoutTicks=90,batch="siege_ravager_interrupt")
    public static void recalledRavagerSiegeCancelsItsLungeAndPendingWave(GameTestHelper helper){
        clearWideBattlefield(helper);flatBattlefield(helper,0,30,0,18);var level=helper.getLevel();
        var beast=readySiege(helper,new BlockPos(3,1,8));var target=durableTarget(helper,new BlockPos(17,1,8));beast.setTarget(target);var start=beast.position();
        helper.runAfterDelay(8,()->{helper.assertTrue(beast.attacking(),"Recall test missed the siege windup");beast.interruptForOrders();beast.setTarget(null);beast.setNoAi(true);});
        helper.runAfterDelay(65,()->{helper.assertTrue(!beast.attacking()&&target.getHealth()==100&&beast.position().distanceToSqr(start)<.5,"Recalled siege beast kept moving or resolved a canceled hit");clearWideBattlefield(helper);helper.succeed();});
    }
    private static dev.hui.enderinfection.entity.InfectedMob readySiege(GameTestHelper helper,BlockPos relative){
        var beast=battleUnit(helper,dev.hui.enderinfection.entity.InfectedKind.SIEGE_BEAST,relative,null,"hunter");var saved=new net.minecraft.nbt.CompoundTag();beast.addAdditionalSaveData(saved);saved.putInt("EmergenceTicks",0);beast.readAdditionalSaveData(saved);return beast;
    }
    private static void preparePlantPatch(GameTestHelper helper){for(int x=0;x<9;x++)for(int z=0;z<9;z++){helper.setBlock(new BlockPos(x,0,z),Blocks.BARRIER);for(int y=1;y<7;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);}}
    private static ExpansionNodeEntity plantNode(GameTestHelper helper,dev.hui.enderinfection.entity.InfectedMob core){BlockPos p=new BlockPos(4,1,4);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(p));node.setOwner(core.getUUID());return node;}
    private static net.minecraft.world.entity.monster.Zombie durableTarget(GameTestHelper helper,BlockPos pos){var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());placeMob(helper,target,pos);target.setNoAi(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,1000));helper.getLevel().addFreshEntity(target);return target;}
    private static java.util.List<dev.hui.enderinfection.entity.CrystalSpike> spellEntities(GameTestHelper helper){return helper.getLevel().getEntitiesOfClass(dev.hui.enderinfection.entity.CrystalSpike.class,new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO),helper.absolutePos(new BlockPos(64,12,64))));}
    private static dev.hui.enderinfection.entity.InfectedMob fundedCore(GameTestHelper helper,BlockPos relative,int stock,int stage){
        var core=InfectedContent.TYPES.get(dev.hui.enderinfection.entity.InfectedKind.CORE).get().create(helper.getLevel());core.setNoAi(true);core.setInvulnerable(true);
        var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);tag.putInt("MotherStage",stage);tag.putInt("ColonyBiomass",stock);tag.putInt("ExpansionAllowance",10000);tag.putInt("EmergenceTicks",0);tag.putBoolean("ExpansionNodeCreated",true);core.readAdditionalSaveData(tag);
        var pos=helper.absolutePos(relative);core.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);helper.getLevel().addFreshEntity(core);return core;
    }
    private static void clearWideBattlefield(GameTestHelper helper){helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,7,9))).inflate(64)).forEach(net.minecraft.world.entity.Entity::discard);}
    private static void flatBattlefield(GameTestHelper helper,int minX,int maxX,int minZ,int maxZ){for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);for(int y=1;y<=5;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);}}
    private static void placeMob(GameTestHelper helper,net.minecraft.world.entity.Mob mob,BlockPos pos){var point=helper.absolutePos(pos);mob.moveTo(point.getX()+.5,point.getY(),point.getZ()+.5,0,0);}
    private static void placeMobPlayer(GameTestHelper helper,net.minecraft.world.entity.player.Player player,BlockPos pos){var point=helper.absolutePos(pos);player.moveTo(point.getX()+.5,point.getY(),point.getZ()+.5,0,0);}
    private static dev.hui.enderinfection.entity.InfectedMob battleUnit(GameTestHelper helper,dev.hui.enderinfection.entity.InfectedKind kind,BlockPos pos,dev.hui.enderinfection.entity.InfectedMob owner,String duty){var unit=InfectedContent.TYPES.get(kind).get().create(helper.getLevel());placeMob(helper,unit,pos);if(owner!=null)unit.getPersistentData().putUUID("ColonyMother",owner.getUUID());unit.getPersistentData().putString("ColonyDuty",duty);helper.getLevel().addFreshEntity(unit);return unit;}
    private static void fundNode(GameTestHelper helper,ExpansionNodeEntity node){node.setOwner(fundedCore(helper,new BlockPos(7,1,7),10000,3).getUUID());}
    private static ExpansionNodeEntity matureExpansionNode(GameTestHelper helper,dev.hui.enderinfection.entity.InfectedMob core,int radius){
        BlockPos p=new BlockPos(4,20,4);helper.setBlock(p,Blocks.AIR);helper.setBlock(p,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(p));
        var data=node.saveWithFullMetadata();data.putInt("Radius",radius);node.load(data);node.setOwner(core.getUUID());node.serverTick(helper.getLevel());
        var saved=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(saved);saved.putBoolean("ExpansionNodeCreated",true);core.readAdditionalSaveData(saved);return node;
    }
    private static void clearArenaMobs(GameTestHelper helper){helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0,0,0)),helper.absolutePos(new BlockPos(9,7,9))).inflate(24)).forEach(net.minecraft.world.entity.Entity::discard);}
    private static void prepareBurstArena(GameTestHelper helper){for(int x=0;x<9;x++)for(int z=0;z<9;z++){helper.setBlock(new BlockPos(x,0,z),Blocks.STONE);for(int y=1;y<8;y++)helper.setBlock(new BlockPos(x,y,z),Blocks.AIR);}}
    private static void prepareOutpostArea(GameTestHelper helper,boolean infected){for(int x=-9;x<=17;x++)for(int z=-9;z<=17;z++)helper.setBlock(new BlockPos(x,0,z),infected?InfectedContent.TERRAIN.get("crystal_crust").get():Blocks.BARRIER);helper.setBlock(new BlockPos(4,0,4),Blocks.BARRIER);}
    private static ExpansionNodeEntity incomeNode(GameTestHelper helper,BlockPos pos,dev.hui.enderinfection.entity.InfectedMob core,int radius){helper.setBlock(pos,Blocks.AIR);helper.setBlock(pos,InfectedContent.NEST.get());var node=(ExpansionNodeEntity)helper.getLevel().getBlockEntity(helper.absolutePos(pos));node.setOwner(core.getUUID());var tag=node.saveWithFullMetadata();tag.putInt("Radius",radius);node.load(tag);core.observeExpansion(node);return node;}
    private static int forceLandHarvest(GameTestHelper helper,dev.hui.enderinfection.entity.InfectedMob core){var tag=new net.minecraft.nbt.CompoundTag();core.addAdditionalSaveData(tag);tag.putLong("NextTerritoryHarvest",helper.getLevel().getGameTime());core.readAdditionalSaveData(tag);core.setNoAi(false);return core.collectTerritoryBiomass(helper.getLevel());}
    private static java.util.List<dev.hui.enderinfection.entity.InfectedMob> burstGuards(GameTestHelper helper,dev.hui.enderinfection.entity.InfectedMob core){return helper.getLevel().getEntitiesOfClass(dev.hui.enderinfection.entity.InfectedMob.class,core.getBoundingBox().inflate(24),e->e.getPersistentData().hasUUID("DefendingMother")&&e.getPersistentData().getUUID("DefendingMother").equals(core.getUUID()));}

}
