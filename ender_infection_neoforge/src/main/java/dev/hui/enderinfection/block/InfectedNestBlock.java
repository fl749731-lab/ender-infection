package dev.hui.enderinfection.block;

import net.minecraft.core.BlockPos;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.MutantZombie;

public final class InfectedNestBlock extends Block implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ROOTED=net.minecraft.world.level.block.state.properties.BooleanProperty.create("rooted");
    private static final net.minecraft.world.phys.shapes.VoxelShape ROOTED_SHAPE=net.minecraft.world.phys.shapes.Shapes.or(net.minecraft.world.phys.shapes.Shapes.block(),Block.box(0,16,0,5,21,5),Block.box(11,16,0,16,22,5),Block.box(0,16,11,5,20,16),Block.box(11,16,11,16,23,16));
    public InfectedNestBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(ROOTED,false));}
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState> builder){builder.add(ROOTED);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return state.getValue(ROOTED)?ROOTED_SHAPE:super.getShape(state,world,pos,context);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ExpansionNodeEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide?null:(world,pos,blockState,entity)->{if(world instanceof ServerLevel server&&entity instanceof ExpansionNodeEntity node)node.serverTick(server);};
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(random.nextInt(3)==0)level.addParticle(FactionParticles.SPORE.get(),pos.getX()+random.nextDouble(),pos.getY()+1.15,pos.getZ()+random.nextDouble(),0,.03,0);
    }
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){
        super.stepOn(level,pos,state,entity);
        if(!level.isClientSide&&entity instanceof LivingEntity living && !(living instanceof InfectedMob)&&!(living instanceof MutantZombie)){
            if(living.tickCount%20==0)living.hurt(level.damageSources().magic(),2);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,0));
        }
    }
}
