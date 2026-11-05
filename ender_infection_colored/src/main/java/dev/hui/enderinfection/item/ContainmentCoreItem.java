package dev.hui.enderinfection.item;

import dev.hui.enderinfection.entity.ContainmentWarden;
import dev.hui.enderinfection.entity.FactionAI;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Reusable encounter reward, with a server-authoritative cooldown and line-of-sight check. */
public final class ContainmentCoreItem extends Item {
    public ContainmentCoreItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var held = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(held);
        if (level instanceof ServerLevel server) {
            for (var mob : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8),
                    e -> e.isAlive() && e.distanceToSqr(player) <= 64 && !(e instanceof ContainmentWarden) && FactionAI.infected(e))) {
                if (!player.hasLineOfSight(mob)) continue;
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY()+1, mob.getZ(), 12, .4, .7, .4, .05);
            }
            player.getCooldowns().addCooldown(this, 1200);
            server.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .8F, .7F);
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ender_infection.containment_core.desc"));
    }
}
