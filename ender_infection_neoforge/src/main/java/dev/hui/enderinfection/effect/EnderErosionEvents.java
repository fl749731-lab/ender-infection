package dev.hui.enderinfection.effect;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.FactionAI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/** Any outsider that walks into infected ground catches ender erosion; faction units are immune. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=EventBusSubscriber.Bus.GAME)
public final class EnderErosionEvents {
    private EnderErosionEvents(){}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity living))return;
        if(!(living.level() instanceof ServerLevel))return;
        if(living.tickCount%20!=0||!living.isAlive())return;
        if(FactionAI.infected(living))return;
        if(!inInfectedArea(living))return;
        living.addEffect(new MobEffectInstance(InfectedContent.ENDER_EROSION,200,0,false,true,true));
    }
    /** The block under the feet plus its four neighbours is enough for a terrain aura. */
    private static boolean inInfectedArea(LivingEntity living){
        BlockPos base=living.blockPosition();
        if(infected(living,base)||infected(living,base.below()))return true;
        BlockPos ground=base.below();
        for(net.minecraft.core.Direction direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
            if(infected(living,ground.relative(direction)))return true;
        }
        return false;
    }
    private static boolean infected(LivingEntity living,BlockPos pos){
        return InfectedContent.terrainKey(living.level().getBlockState(pos))!=null;
    }
}
