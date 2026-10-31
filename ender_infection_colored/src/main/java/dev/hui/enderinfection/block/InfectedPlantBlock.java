package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class InfectedPlantBlock extends BushBlock {
    public InfectedPlantBlock(Properties properties){super(properties);}
    @Override protected boolean mayPlaceOn(BlockState state,BlockGetter level,BlockPos pos){
        return super.mayPlaceOn(state,level,pos)||state.is(InfectedContent.NEST.get())||InfectedContent.terrainKey(state)!=null;
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(random.nextInt(12)==0)level.addParticle(FactionParticles.SPORE.get(),pos.getX()+.5,pos.getY()+.7,pos.getZ()+.5,0,.02,0);
    }
}
