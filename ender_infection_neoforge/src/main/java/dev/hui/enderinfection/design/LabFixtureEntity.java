package dev.hui.enderinfection.design;
import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class LabFixtureEntity extends LabStorageInventory implements GeoBlockEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public boolean removing;public float openProgress,previousOpen;private int age;
    public LabFixtureEntity(BlockPos pos,BlockState state){super(DesignContent.FIXTURE_ENTITY.get(),pos,state);}
    @Override public boolean isStorage(){return ((LabFixtureBlock)getBlockState().getBlock()).isStorage();}
    private boolean instituteBound;private BlockPos instituteOffset=BlockPos.ZERO;
    public java.util.Optional<BlockPos> boundOrigin(){return instituteBound&&dev.hui.enderinfection.institute.InstituteOperations.validOffset(instituteOffset)?java.util.Optional.of(worldPosition.subtract(instituteOffset)):java.util.Optional.empty();}
    @Override public void onLoad(){super.onLoad();if(getLevel() instanceof ServerLevel server&&boundOrigin().isPresent())server.getServer().execute(()->{if(!isRemoved()&&server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4)!=null&&server.getBlockEntity(worldPosition)==this)dev.hui.enderinfection.institute.InstituteOperations.registerBound(server,worldPosition,instituteOffset);});}
    @Override protected void loadAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){super.loadAdditional(tag,registries);instituteBound=tag.getBoolean("InstituteBound");instituteOffset=new BlockPos(tag.getInt("InstituteOffsetX"),tag.getInt("InstituteOffsetY"),tag.getInt("InstituteOffsetZ"));if(!dev.hui.enderinfection.institute.InstituteOperations.validOffset(instituteOffset))instituteBound=false;}
    @Override protected void saveAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){super.saveAdditional(tag,registries);if(instituteBound){tag.putBoolean("InstituteBound",true);tag.putInt("InstituteOffsetX",instituteOffset.getX());tag.putInt("InstituteOffsetY",instituteOffset.getY());tag.putInt("InstituteOffsetZ",instituteOffset.getZ());}}
    public String key(){return ((LabFixtureBlock)getBlockState().getBlock()).key;}
    public static void tick(Level level,BlockPos pos,BlockState state,LabFixtureEntity be){
        be.age++;be.previousOpen=be.openProgress;be.openProgress=Math.max(0,Math.min(1,be.openProgress+(state.getValue(LabFixtureBlock.OPEN)?.125F:-.125F)));
        if(!(level instanceof ServerLevel server))return;
        if(be.age%200==0&&be.key().startsWith("incubator")){
            var cores=server.getEntitiesOfClass(InfectedMob.class,new AABB(pos).inflate(24),e->e.isAlive()&&e.kind==InfectedKind.CORE&&!e.isNoAi());
            cores.stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(pos.getX(),pos.getY(),pos.getZ()))).ifPresent(core->DesignBehavior.spawn(core,core,InfectedKind.FORAGER,server));
        }
        if(be.age%80==0&&be.key().startsWith("surgery_table"))for(InfectedMob mob:server.getEntitiesOfClass(InfectedMob.class,new AABB(pos).inflate(4,2,4),e->e.isAlive()&&e.getHealth()<e.getMaxHealth())){mob.heal(2);FactionEffects.gather(server,mob.position().add(0,1,0),2);}
    }
    public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2,3,2);}
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries){return new CompoundTag();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
