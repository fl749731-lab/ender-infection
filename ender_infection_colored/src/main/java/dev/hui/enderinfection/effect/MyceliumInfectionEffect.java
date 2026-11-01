package dev.hui.enderinfection.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The infection taking root inside a host: steady damage, slowed limbs and a nagging hunger. */
public final class MyceliumInfectionEffect extends MobEffect {
    public MyceliumInfectionEffect(){super(MobEffectCategory.HARMFUL,0x5A2D82);}
    @Override public boolean isDurationEffectTick(int duration,int amplifier){return duration%40==0;}
    @Override public void applyEffectTick(LivingEntity living,int amplifier){
        if(living.level().isClientSide)return;
        living.hurt(living.damageSources().magic(),1.0F+amplifier);
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,Math.min(1,amplifier),false,false,true));
        living.addEffect(new MobEffectInstance(MobEffects.HUNGER,120,amplifier,false,false,true));
    }
}
