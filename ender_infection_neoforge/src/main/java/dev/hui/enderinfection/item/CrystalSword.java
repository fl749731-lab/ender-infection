package dev.hui.enderinfection.item;

import dev.hui.enderinfection.entity.FactionAI;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;

final class CrystalSword extends SwordItem {
    private final CrystalMaterial material;

    CrystalSword(CrystalMaterial material) {
        super(material, new Properties().attributes(SwordItem.createAttributes(material,3,-2.4F)));
        this.material = material;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.level().isClientSide) {
            if (material == CrystalMaterial.RESONANT && FactionAI.infected(target)) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60));
            } else if (material == CrystalMaterial.EMBER) {
                target.igniteForSeconds(3);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (material != CrystalMaterial.VOID) {
            return super.use(level, player, hand);
        }
        var sword = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(sword);
        }
        if (!level.isClientSide) {
            var pearl = new ThrownEnderpearl(level, player);
            pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.5F, 1F);
            if (!level.addFreshEntity(pearl)) {
                return InteractionResultHolder.fail(sword);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.5F, 1F);
            sword.hurtAndBreak(2, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
            player.getCooldowns().addCooldown(this, 240);
        }
        return InteractionResultHolder.sidedSuccess(sword, level.isClientSide);
    }
}
