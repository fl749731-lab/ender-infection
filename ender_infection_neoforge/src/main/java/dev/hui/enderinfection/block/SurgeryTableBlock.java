package dev.hui.enderinfection.block;

import dev.hui.enderinfection.entity.FactionAI;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** A surgery table: wounded brood standing near it slowly knits back together. */
public class SurgeryTableBlock extends Block {
    public SurgeryTableBlock(Properties properties){super(properties);}
    @Override public boolean isRandomlyTicking(BlockState state){return true;}
    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        var patients=level.getEntitiesOfClass(InfectedMob.class,new AABB(pos).inflate(4,2,4),InfectedMob::isAlive);
        for(InfectedMob patient:patients){
            if(patient.getHealth()<patient.getMaxHealth()){
                patient.heal(2.0F);
                FactionEffects.gather(level,patient.position().add(0,patient.getBbHeight()*.5,0),2);
            }
        }
    }
}
