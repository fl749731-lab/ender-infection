package dev.hui.enderinfection.effect;

import dev.hui.enderinfection.InfectedContent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** The debuff of standing inside infected ground: wither, nausea and our own mycelium infection. */
public final class EnderErosionEffect extends MobEffect {
    public EnderErosionEffect(){super(MobEffectCategory.HARMFUL,0x7A2E8E);}
    @Override public boolean isDurationEffectTick(int duration,int amplifier){return duration%40==0;}
    @Override public void applyEffectTick(LivingEntity living,int amplifier){
        if(living.level().isClientSide)return;
        living.addEffect(new MobEffectInstance(MobEffects.WITHER,100,amplifier,false,true,true));
        living.addEffect(new MobEffectInstance(MobEffects.CONFUSION,160,0,false,true,true));
        living.addEffect(new MobEffectInstance(InfectedContent.MYCELIUM_INFECTION.get(),200,amplifier,false,true,true));
    }
}
