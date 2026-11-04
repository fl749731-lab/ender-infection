package dev.hui.enderinfection.block;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** A lab incubator: while it stands on infected ground it grows fresh brood for the nearest mother. */
public class IncubatorBlock extends Block {
    public IncubatorBlock(Properties properties){super(properties);}
    @Override public boolean isRandomlyTicking(BlockState state){return true;}
    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(random.nextInt(20)!=0)return;
        if(InfectedContent.terrainKey(level.getBlockState(pos.below()))==null)return;
        var cores=level.getEntitiesOfClass(InfectedMob.class,new AABB(pos).inflate(24),e->e.kind==InfectedKind.CORE&&e.isAlive());
        if(cores.isEmpty())return;
        InfectedMob core=cores.get(0);
        if(!core.canExpand(ColonyEconomyCost()))return;
        InfectedKind kind=core.motherStage()>=2&&random.nextBoolean()?InfectedKind.EARLY_PIG:InfectedKind.EARLY_ZOMBIE;
        InfectedMob brood=InfectedContent.TYPES.get(kind).get().create(level);
        if(brood==null)return;
        brood.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,random.nextFloat()*360,0);
        if(!level.noCollision(brood,brood.getBoundingBox()))return;
        brood.getPersistentData().putUUID("ColonyMother",core.getUUID());
        if(level.addFreshEntity(brood)){
            FactionEffects.shatter(level,brood.position(),10);
            level.playSound(null,pos,SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.BLOCKS,.8F,1.2F);
        }
    }
    private static int ColonyEconomyCost(){return dev.hui.enderinfection.biomass.ColonyEconomy.EARLY_UNIT;}
}
