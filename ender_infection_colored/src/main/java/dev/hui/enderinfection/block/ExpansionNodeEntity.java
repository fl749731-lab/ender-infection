package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.biomass.ColonyEconomy;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The sole source of terrain spread. Ordinary infected terrain and trees have no spreading ticker. */
public final class ExpansionNodeEntity extends BlockEntity {
    public static final int MAX_RADIUS=16;
    public static final int OUTPOST_RADIUS=10;
    private static final TagKey<Block> GROUND=TagKey.create(Registries.BLOCK,new ResourceLocation("ender_infection","infectable_ground"));
    private int activeTicks,spreadClock,growthClock,purchasedChanges,spentBiomass,radius=2;
    private UUID owner;
    private int outpostClock=600;
    private boolean outpostCreated;
    private int frontierCursor;
    private int legacyCanopyCursor;
    private long inhibitedUntil;
    public void inhibit(ServerLevel level,int ticks){inhibitedUntil=Math.max(inhibitedUntil,level.getGameTime()+Math.max(0,Math.min(1200,ticks)));setChanged();}
    public boolean inhibited(ServerLevel level){return level.getGameTime()<inhibitedUntil;}
    private static final java.util.List<BlockPos> OFFSETS=createOffsets();
    private static java.util.List<BlockPos> createOffsets(){
        var result=new java.util.ArrayList<BlockPos>();
        for(int x=-MAX_RADIUS;x<=MAX_RADIUS;x++)for(int z=-MAX_RADIUS;z<=MAX_RADIUS;z++)if(x*x+z*z<=MAX_RADIUS*MAX_RADIUS)result.add(new BlockPos(x,0,z));
        result.sort(java.util.Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));return java.util.List.copyOf(result);
    }
    public UUID ownerId(){return owner;}
    public void setOwner(UUID id){if(owner==null||owner.equals(id)){owner=id;setChanged();}}
    public int purchasedChanges(){return purchasedChanges;}
    public int spentBiomass(){return spentBiomass;}
    /** Territory blocks produced per biomass this node has spent. Read by the mother's ledger. */
    public double territoryPerBiomass(){return spentBiomass<=0?0:(double)purchasedChanges/spentBiomass;}
    public int radius(){return radius;}
    public net.minecraft.world.phys.Vec3 effectPosition(){return net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(0,getBlockState().getValue(InfectedNestBlock.ROOTED)?.5:0,0);}
    public static boolean replaceSurface(ServerLevel level,BlockPos pos,InfectedMob core,boolean allowNatural){
        if(!level.hasChunkAt(pos)||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return false;
        BlockState original=level.getBlockState(pos),above=level.getBlockState(pos.above());String key=InfectedContent.terrainKey(original);
        if(original.hasBlockEntity()||!(isGround(key)||allowNatural&&original.is(GROUND))||!(original.isFaceSturdy(level,pos,net.minecraft.core.Direction.UP)||original.getBlock() instanceof net.minecraft.world.level.block.FarmBlock||ErodedGroundBlock.farmland(original))||!above.getFluidState().isEmpty()||!above.isAir()&&!above.canBeReplaced()&&!Vegetation.supported(above))return false;
        boolean tall=Vegetation.tallBottom(above);BlockState upper=level.getBlockState(pos.above(2));
        int allowance=Math.min(core.expansionAllowance(),ColonyEconomy.NODE);if(!core.spendForExpansion(ColonyEconomy.NODE))return false;
        if(!above.isAir())level.setBlock(pos.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        if(tall)level.setBlock(pos.above(2),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        if(level.setBlock(pos,InfectedContent.NEST.get().defaultBlockState().setValue(InfectedNestBlock.ROOTED,true),3)&&level.getBlockEntity(pos) instanceof ExpansionNodeEntity node){
            node.setOwner(core.getUUID());core.observeExpansion(node);node.substrate=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(original.getBlock()).toString();node.setChanged();
            dev.hui.enderinfection.particle.FactionEffects.rune(level,node.effectPosition(),1.5);dev.hui.enderinfection.particle.FactionEffects.shatter(level,node.effectPosition(),10);return true;
        }
        level.setBlock(pos,original,3);if(!above.isAir())level.setBlock(pos.above(),above,2);if(tall)level.setBlock(pos.above(2),upper,3);core.refundExpansion(ColonyEconomy.NODE,allowance);return false;
    }
    private String substrate="";
    private InfectedMob mother(ServerLevel level){
        if(owner!=null){var entity=level.getEntity(owner);return entity instanceof InfectedMob core&&core.kind==InfectedKind.CORE&&core.isAlive()?core:null;}
        var nearest=level.getEntitiesOfClass(InfectedMob.class,new net.minecraft.world.phys.AABB(worldPosition).inflate(32,12,32),core->core.kind==InfectedKind.CORE&&core.isAlive()).stream().min(java.util.Comparator.comparingDouble(core->core.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)))).orElse(null);
        if(nearest!=null)setOwner(nearest.getUUID());return nearest;
    }
    private boolean change(ServerLevel level,BlockPos pos,BlockState replacement){
        var changes=new java.util.LinkedHashMap<BlockPos,BlockState>();changes.put(pos,replacement);
        BlockState plant=level.getBlockState(pos.above());
        if(isGround(InfectedContent.terrainKey(replacement))&&!(plant.getBlock() instanceof ErodedVegetationBlock)&&!(plant.getBlock() instanceof ErodedTallVegetationBlock)){
            BlockState initial=Vegetation.initial(plant);
            if(initial!=null){changes.put(pos.above(),initial);if(Vegetation.tallBottom(plant))changes.put(pos.above(2),initial.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));}
        }
        return changeAll(level,changes);
    }
    private int groundChangeCost(ServerLevel level,BlockPos pos){BlockState plant=level.getBlockState(pos.above());return 1+(Vegetation.initial(plant)==null?0:Vegetation.tallBottom(plant)?2:1);}
    private int changeCount(ServerLevel level,BlockPos pos,BlockState replacement){int before=purchasedChanges;return change(level,pos,replacement)?purchasedChanges-before:0;}
    private boolean changeAll(ServerLevel level,java.util.LinkedHashMap<BlockPos,BlockState> changes){
        changes.entrySet().removeIf(e->level.getBlockState(e.getKey()).equals(e.getValue()));if(changes.isEmpty())return false;
        InfectedMob core=mother(level);if(core==null)return false;
        var originals=new java.util.LinkedHashMap<BlockPos,BlockState>();for(var entry:changes.entrySet()){BlockState state=level.getBlockState(entry.getKey());if(state.hasBlockEntity())return false;originals.put(entry.getKey(),state);}
        int price=changes.size()*ColonyEconomy.EXPANSION,allowance=Math.min(core.expansionAllowance(),price);if(!core.spendForExpansion(price))return false;
        // Suppress shape propagation until both soil and plant halves have been replaced.
        for(var entry:changes.entrySet())if(!level.setBlock(entry.getKey(),entry.getValue(),18)){
            originals.forEach((p,s)->level.setBlock(p,s,18));originals.forEach((p,s)->s.updateNeighbourShapes(level,p,3));core.refundExpansion(price,allowance);return false;
        }
        changes.forEach((p,s)->{s.updateNeighbourShapes(level,p,3);level.updateNeighborsAt(p,s.getBlock());});purchasedChanges=Math.min(1000000,purchasedChanges+changes.size());spentBiomass+=price;setChanged();return true;
    }
    private int erodeVegetation(ServerLevel level,BlockPos pos,int budget){
        if(budget<=0)return 0;BlockState state=level.getBlockState(pos),below=level.getBlockState(pos.below());
        boolean tall=state.getBlock() instanceof ErodedTallVegetationBlock;
        if(!(state.getBlock() instanceof ErodedVegetationBlock)&&!tall){
            BlockState initial=Vegetation.initial(state);if(initial==null)return 0;
            var first=new java.util.LinkedHashMap<BlockPos,BlockState>();first.put(pos,initial);if(Vegetation.tallBottom(state))first.put(pos.above(),initial.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            return first.size()<=budget&&changeAll(level,first)?first.size():0;
        }
        if(tall&&!Vegetation.tallBottom(state))return 0;
        int groundStage=below.hasProperty(ErodedGroundBlock.EROSION)?below.getValue(ErodedGroundBlock.EROSION):2;
        int stage=tall?state.getValue(ErodedTallVegetationBlock.EROSION):state.getValue(ErodedVegetationBlock.EROSION);
        if(groundStage<1||stage==1&&groundStage<2)return 0;
        BlockState next=stage==0?state.setValue(tall?ErodedTallVegetationBlock.EROSION:ErodedVegetationBlock.EROSION,1):InfectedContent.PLANTS.get(Vegetation.mature(state)).get().defaultBlockState();
        var changes=new java.util.LinkedHashMap<BlockPos,BlockState>();changes.put(pos,next);
        if(tall)changes.put(pos.above(),stage==0?next.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER):net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        return changes.size()<=budget&&changeAll(level,changes)?changes.size():0;
    }
    public ExpansionNodeEntity(BlockPos pos,BlockState state){super(InfectedContent.NODE_ENTITY.get(),pos,state);}
    public void serverTick(ServerLevel level){
        activeTicks++;InfectedMob core=mother(level);if(core==null)return;core.observeExpansion(this);
        if(inhibited(level)){if(activeTicks%40==0)dev.hui.enderinfection.particle.FactionEffects.suppress(level,effectPosition());return;}
        if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return;
        if(radius<MAX_RADIUS&&(growthClock=Math.min(growthClock+1,ColonyEconomy.RADIUS_BASE_TICKS*2))>=ColonyEconomy.interval(ColonyEconomy.RADIUS_BASE_TICKS,core.colonyBiomass())&&(getBlockState().getValue(InfectedNestBlock.ROOTED)||purchasedChanges>0||isGround(InfectedContent.terrainKey(level.getBlockState(worldPosition.below()))))&&core.spendForExpansion(ColonyEconomy.RADIUS)){
            radius++;spentBiomass+=ColonyEconomy.RADIUS;growthClock=0;setChanged();
        }
        if(radius>=OUTPOST_RADIUS&&!outpostCreated&&++outpostClock>=ColonyEconomy.interval(ColonyEconomy.OUTPOST_BASE_TICKS,core.colonyBiomass())){outpostClock=0;establishOutpost(level,core);setChanged();}
        if(++spreadClock<ColonyEconomy.interval(ColonyEconomy.SPREAD_BASE_TICKS,core.colonyBiomass()))return;spreadClock=0;
        setChanged();
        if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return;
        if(!core.canExpand())return;
        int changes=spreadFrontier(level,ColonyEconomy.SPREAD_PER_ROUND-1);
        for(int attempt=0;attempt<16&&changes<ColonyEconomy.SPREAD_PER_ROUND;attempt++){
            int dx=level.random.nextInt(radius*2+1)-radius,dz=level.random.nextInt(radius*2+1)-radius;
            if(dx*dx+dz*dz>radius*radius)continue;
            BlockPos column=worldPosition.offset(dx,0,dz);
            if(!level.hasChunkAt(column))continue;
            for(int dy=3;dy>=-4;dy--){
                BlockPos pos=column.offset(0,dy,0);BlockState state=level.getBlockState(pos);
                if(isGround(InfectedContent.terrainKey(state))&&(level.getBlockState(pos.above()).is(BlockTags.LOGS)||level.getBlockState(pos.above()).getBlock() instanceof InfectedTimberBlock)){
                    changes+=erodeTree(level,pos.above(),Math.min(1,ColonyEconomy.SPREAD_PER_ROUND-changes));break;
                }
                String previous=InfectedContent.terrainKey(state);
                if(!(state.is(GROUND)||isGround(previous))||state.hasBlockEntity())continue;
                BlockState above=level.getBlockState(pos.above());
                if(!above.getFluidState().isEmpty())continue;
                if(!above.isAir()&&!above.canBeReplaced()&&!above.is(BlockTags.LOGS)&&!Vegetation.supported(above))continue;
                String next=null;
                if(previous==null){
                    // Grow out from the node or an existing patch rather than scattering isolated stamps.
                    boolean connected=dx*dx+dz*dz<=2;
                    for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
                        BlockPos neighbor=pos.relative(direction);
                        if(level.hasChunkAt(neighbor)&&isGround(InfectedContent.terrainKey(level.getBlockState(neighbor))))connected=true;
                    }
                    if(!connected)continue;
                    if(groundChangeCost(level,pos)<=ColonyEconomy.SPREAD_PER_ROUND-changes)changes+=changeCount(level,pos,ErodedGroundBlock.fromNatural(state));break;
                }
                else if(previous.startsWith("eroded_")&&level.random.nextInt(3)==0){
                    if(state.hasProperty(ErodedGroundBlock.EROSION)&&state.getValue(ErodedGroundBlock.EROSION)<2){
                        if(groundChangeCost(level,pos)<=ColonyEconomy.SPREAD_PER_ROUND-changes)changes+=changeCount(level,pos,state.cycle(ErodedGroundBlock.EROSION));break;
                    }
                    double distance=Math.sqrt(dx*dx+dz*dz)/Math.max(1,radius);
                    next=previous.equals("eroded_stone")?"crystal_crust":distance<.4?"living_tissue":distance<.7?(level.random.nextBoolean()?"mycelial_mat":"necrotic_soil"):"tainted_grass";
                }
                if(next!=null&&groundChangeCost(level,pos)<=ColonyEconomy.SPREAD_PER_ROUND-changes)changes+=changeCount(level,pos,InfectedContent.TERRAIN.get(next).get().defaultBlockState());
                changes+=erodeVegetation(level,pos.above(),ColonyEconomy.SPREAD_PER_ROUND-changes);
                changes+=erodeTree(level,pos.above(),ColonyEconomy.SPREAD_PER_ROUND-changes);
                if(changes<ColonyEconomy.SPREAD_PER_ROUND&&previous!=null&&!previous.startsWith("eroded_")&&level.random.nextInt(48)==0&&level.getBlockState(pos.above()).isAir()&&hasPlantRoom(level,pos.above())){
                    String plantKey=level.random.nextBoolean()?"infected_grass":"infected_tendrils";
                    if(dx*dx+dz*dz<=16&&level.random.nextInt(8)==0)plantKey=level.random.nextBoolean()?"infected_fungus":"infected_flower";
                    BlockState plant=InfectedContent.PLANTS.get(plantKey).get().defaultBlockState();
                    if(plant.canSurvive(level,pos.above())&&change(level,pos.above(),plant))changes++;
                }
                break;
            }
        }
    }
    private int spreadFrontier(ServerLevel level,int budget){
        int extent=0;while(extent<OFFSETS.size()&&OFFSETS.get(extent).distSqr(BlockPos.ZERO)<=radius*radius)extent++;
        int changes=0;
        for(int scan=0;scan<Math.min(64,extent)&&changes<budget;scan++){
            BlockPos offset=OFFSETS.get(Math.floorMod(frontierCursor++,extent));
            BlockPos column=worldPosition.offset(offset);if(!level.hasChunkAt(column))continue;
            for(int dy=3;dy>=-4;dy--){
                BlockPos pos=column.offset(0,dy,0);BlockState state=level.getBlockState(pos);
                if(isGround(InfectedContent.terrainKey(state))&&Vegetation.supported(level.getBlockState(pos.above()))){changes+=erodeVegetation(level,pos.above(),budget-changes);break;}
                if(isGround(InfectedContent.terrainKey(state))&&(level.getBlockState(pos.above()).is(BlockTags.LOGS)||level.getBlockState(pos.above()).getBlock() instanceof InfectedTimberBlock)){
                    changes+=erodeTree(level,pos.above(),Math.min(1,budget-changes));break;
                }
                if(!state.is(GROUND)||state.hasBlockEntity()||InfectedContent.terrainKey(state)!=null)continue;
                BlockState above=level.getBlockState(pos.above());
                if(!above.getFluidState().isEmpty()||!above.isAir()&&!above.canBeReplaced()&&!above.is(BlockTags.LOGS)&&!Vegetation.supported(above))continue;
                boolean connected=offset.distSqr(BlockPos.ZERO)<=2;
                for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int step=-1;step<=1;step++){
                    BlockPos adjacent=pos.relative(direction).offset(0,step,0);
                    if(level.hasChunkAt(adjacent)&&isGround(InfectedContent.terrainKey(level.getBlockState(adjacent))))connected=true;
                }
                // A two-block plant may use the whole tick atomically; otherwise reserve one slot for canopy upkeep.
                if(connected&&groundChangeCost(level,pos)<=(changes==0?ColonyEconomy.SPREAD_PER_ROUND:budget-changes))changes+=changeCount(level,pos,ErodedGroundBlock.fromNatural(state));
                break;
            }
        }
        return changes;
    }
    private boolean hasPlantRoom(ServerLevel level,BlockPos center){
        int count=0;
        for(BlockPos p:BlockPos.betweenClosed(center.offset(-3,-1,-3),center.offset(3,1,3))){
            if(!level.hasChunkAt(p))continue;
            BlockState s=level.getBlockState(p);
            for(var plant:InfectedContent.PLANTS.values())if(s.is(plant.get())&&++count>=2)return false;
        }
        return true;
    }
    private static boolean isGround(String key){return key!=null&&!key.contains("log")&&!key.contains("leaves");}
    private void establishOutpost(ServerLevel level,InfectedMob core){
        if(!core.canExpand(ColonyEconomy.NODE)||core.colonyNodeCount()>=core.maxAutoNodes())return;
        double start=level.random.nextDouble()*Math.PI*2;
        BlockPos best=null;double bestScore=-Double.MAX_VALUE;
        for(int attempt=0;attempt<48;attempt++){
            double angle=start+(attempt%16)*Math.PI/8;double distance=radius-1-(attempt/16);
            BlockPos column=worldPosition.offset((int)Math.round(Math.cos(angle)*distance),0,(int)Math.round(Math.sin(angle)*distance));
            if(!level.hasChunkAt(column)||core.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(column))>core.expansionRange()*core.expansionRange())continue;
            for(int dy=3;dy>=-4;dy--){
                BlockPos pos=column.offset(0,dy,0);BlockState ground=level.getBlockState(pos.below());
                if((!level.getBlockState(pos).isAir()&&!Vegetation.supported(level.getBlockState(pos)))||!isGround(InfectedContent.terrainKey(ground))||!(ground.isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)||ErodedGroundBlock.farmland(ground))||!core.nodeSiteAvailable(pos))continue;
                BlockPos surface=pos.below();if(!core.nodeSiteAvailable(surface))continue;
                double score=frontierScore(level,pos,core);if(score>bestScore){bestScore=score;best=surface.immutable();}break;
            }
        }
        if(best==null)return;
        if(replaceSurface(level,best,core,false)){outpostCreated=true;spentBiomass+=ColonyEconomy.NODE;}
    }
    public double frontierScore(ServerLevel level,BlockPos pos,InfectedMob core){
        double score=Math.sqrt(pos.distSqr(core.blockPosition()))*.2;
        var nodes=core.activeColonyNodes();var soil=dev.hui.enderinfection.biomass.LandNutrients.get(level);
        for(int x=-6;x<=6;x+=3)for(int z=-6;z<=6;z+=3){
            BlockPos sample=pos.offset(x,0,z);if(!level.hasChunkAt(sample))continue;
            for(int y=2;y>=-3;y--){BlockPos ground=sample.offset(0,y-1,0);BlockState state=level.getBlockState(ground);String key=InfectedContent.terrainKey(state);
                if(!state.is(GROUND)&&!isGround(key))continue;
                if(!level.getBlockState(ground.above()).isAir()&&!level.getBlockState(ground.above()).canBeReplaced()&&!Vegetation.supported(level.getBlockState(ground.above())))continue;
                boolean covered=false;for(var node:nodes)if(node!=this&&node.getBlockPos().distSqr(ground)<node.radius()*node.radius()){covered=true;break;}
                if(!covered)score+=(key==null?4:1)*soil.remaining(ground)/(double)dev.hui.enderinfection.biomass.LandNutrients.CYCLES;
                break;
            }
        }
        return score;
    }
    private int erodeTree(ServerLevel level,BlockPos base,int budget){
        int changed=0;if(budget<=0)return 0;
        // Old unknown leaf variants are repaired in bounded batches before further trunk maturation.
        for(int scan=0;scan<36&&changed<budget;scan++){
            int index=Math.floorMod(legacyCanopyCursor++,500);
            BlockPos leaf=base.offset(index%5-2,index/25,(index/5)%5-2);
            int dx=leaf.getX()-worldPosition.getX(),dz=leaf.getZ()-worldPosition.getZ();
            if(dx*dx+dz*dz>MAX_RADIUS*MAX_RADIUS||!level.hasChunkAt(leaf))continue;
            BlockState old=level.getBlockState(leaf);
            if(old.getBlock() instanceof InfectedCanopyBlock&&!InfectedCanopyBlock.known(old)){
                BlockState repaired=InfectedCanopyBlock.recoverLegacy(level,leaf);
                if(repaired!=null&&change(level,leaf,repaired))changed++;
            }
        }
        if(changed>=budget)return changed;
        // Tree conversion follows trunks upward. Hand-placed persistent leaves are excluded.
        boolean foundTree=false;BlockState nativeTrunk=null;
        for(int y=0;y<20;y++){
            BlockPos p=base.above(y);BlockState s=level.getBlockState(p);
            if(nativeTrunk==null&&s.is(BlockTags.LOGS)&&!(s.getBlock() instanceof InfectedTimberBlock))nativeTrunk=s;
            if(s.is(BlockTags.LEAVES)&&(!s.hasProperty(LeavesBlock.PERSISTENT)||!s.getValue(LeavesBlock.PERSISTENT))){foundTree=true;break;}
            for(int side=-1;side<=1;side+=2){
                BlockPos sidePos=p.offset(side,0,0);if(!level.hasChunkAt(sidePos))continue;
                BlockState leaf=level.getBlockState(sidePos);
                if(leaf.is(BlockTags.LEAVES)&&(!leaf.hasProperty(LeavesBlock.PERSISTENT)||!leaf.getValue(LeavesBlock.PERSISTENT)))foundTree=true;
            }
        }
        if(!foundTree&&!isInfectedLog(level.getBlockState(base)))return 0;
        for(int y=0;y<20&&changed<budget;y++){
            BlockPos p=base.above(y);if(!level.hasChunkAt(p))continue;
            BlockState s=level.getBlockState(p);String key=InfectedContent.terrainKey(s);
            if(nativeTrunk!=null&&s.getBlock() instanceof InfectedTimberBlock&&!s.getValue(InfectedTimberBlock.KNOWN)){
                BlockState repaired=InfectedTimberBlock.from(nativeTrunk,"infected_log".equals(key)).setValue(RotatedPillarBlock.AXIS,s.getValue(RotatedPillarBlock.AXIS));
                if(change(level,p,repaired)){changed++;s=repaired;}
                if(changed>=budget)break;
            }
            String next=s.is(BlockTags.LOGS)&&key==null?"eroded_log":"eroded_log".equals(key)&&level.random.nextInt(3)==0?"infected_log":null;
            if(next!=null){BlockState replacement=InfectedTimberBlock.from(s,"infected_log".equals(next));if(change(level,p,replacement))changed++;}
            if(changed>=budget)break;
            BlockPos leaf=p.offset(level.random.nextInt(5)-2,level.random.nextInt(3)-1,level.random.nextInt(5)-2);
            int lx=leaf.getX()-worldPosition.getX(),lz=leaf.getZ()-worldPosition.getZ();
            if(lx*lx+lz*lz>MAX_RADIUS*MAX_RADIUS||!level.hasChunkAt(leaf))continue;
            BlockState ls=level.getBlockState(leaf);String lk=InfectedContent.terrainKey(ls);
            if(ls.getBlock() instanceof InfectedCanopyBlock&&!InfectedCanopyBlock.known(ls)){
                BlockState recovered=InfectedCanopyBlock.recoverLegacy(level,leaf);
                if(recovered!=null&&change(level,leaf,recovered)){changed++;ls=recovered;if(changed>=budget)break;}
            }
            if(ls.is(BlockTags.LEAVES)&&lk==null&&(!ls.hasProperty(LeavesBlock.PERSISTENT)||!ls.getValue(LeavesBlock.PERSISTENT))){if(change(level,leaf,InfectedCanopyBlock.from(ls,false)))changed++;}
            else if("eroded_leaves".equals(lk)&&level.random.nextInt(3)==0){if(change(level,leaf,InfectedCanopyBlock.from(ls,true)))changed++;}
        }
        return changed;
    }
    private boolean isInfectedLog(BlockState state){String k=InfectedContent.terrainKey(state);return "eroded_log".equals(k)||"infected_log".equals(k);}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putString("SubstrateBlock",substrate);tag.putInt("LegacyCanopyCursor",legacyCanopyCursor);tag.putLong("InhibitedUntil",inhibitedUntil);tag.putInt("FrontierCursor",frontierCursor);tag.putInt("ActiveTicks",activeTicks);tag.putInt("SpreadClock",spreadClock);tag.putInt("PurchasedChanges",purchasedChanges);tag.putInt("SpentBiomass",spentBiomass);tag.putInt("Radius",radius);tag.putInt("GrowthClock",growthClock);tag.putInt("OutpostClock",outpostClock);tag.putBoolean("OutpostCreated",outpostCreated);if(owner!=null)tag.putUUID("Owner",owner);}
    @Override public void load(CompoundTag tag){super.load(tag);substrate=tag.getString("SubstrateBlock");legacyCanopyCursor=Math.max(0,tag.getInt("LegacyCanopyCursor"));inhibitedUntil=Math.max(0,tag.getLong("InhibitedUntil"));frontierCursor=Math.max(0,tag.getInt("FrontierCursor"));activeTicks=Math.max(0,tag.getInt("ActiveTicks"));spreadClock=Math.max(0,Math.min(40,tag.getInt("SpreadClock")));purchasedChanges=tag.contains("PurchasedChanges")?Math.max(0,Math.min(1000000,tag.getInt("PurchasedChanges"))):Math.min(224,activeTicks/300*16);radius=tag.contains("Radius")?Math.max(2,Math.min(MAX_RADIUS,tag.getInt("Radius"))):Math.min(MAX_RADIUS,2+activeTicks/300);spentBiomass=Math.max(0,tag.getInt("SpentBiomass"));growthClock=Math.max(0,Math.min(300,tag.getInt("GrowthClock")));outpostClock=tag.contains("OutpostClock")?Math.max(0,Math.min(600,tag.getInt("OutpostClock"))):600;outpostCreated=tag.getBoolean("OutpostCreated");owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;}
}
