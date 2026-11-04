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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class LabFixtureEntity extends LabStorageInventory implements GeoBlockEntity {
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public boolean removing;public float openProgress,previousOpen;private int age;
    public LabFixtureEntity(BlockPos pos,BlockState state){super(DesignContent.FIXTURE_ENTITY.get(),pos,state);}
    @Override public boolean isStorage(){return ((LabFixtureBlock)getBlockState().getBlock()).isStorage();}
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
    @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(2,3,2);}
    @Override public CompoundTag getUpdateTag(){return new CompoundTag();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
