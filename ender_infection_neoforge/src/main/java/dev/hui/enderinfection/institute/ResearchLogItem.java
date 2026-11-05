package dev.hui.enderinfection.institute;

import dev.hui.enderinfection.api.institute.InstituteApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Portable objective/status reader; never creates or resets facility state. */
public final class ResearchLogItem extends Item {
    public ResearchLogItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer user) {
            var found = InstituteApi.facility(server, user.blockPosition());
            if (found.isEmpty()) user.displayClientMessage(Component.translatable("message.ender_infection.operations.no_facility"), false);
            else {
                var state = found.get();
                ContainmentIncident.status(server, user.blockPosition()).ifPresent(incident -> user.displayClientMessage(
                        Component.translatable("message.ender_infection.incident.status",
                                Component.translatable("message.ender_infection.incident.phase." + incident.phase().name().toLowerCase(java.util.Locale.ROOT)),
                                incident.resolved(), incident.spawned(), Integer.bitCount(incident.faults())), false));
                String task = !state.repaired() ? "repair" : !state.servicePowered() ? "service"
                        : !state.containmentPowered() ? "containment" : !state.analyzed() ? "analyze"
                        : !state.separated() ? "separate" : !state.archiveClaimed() ? "archive" : "complete";
                user.displayClientMessage(Component.translatable("message.ender_infection.operations.journal",
                        state.origin().getX(), state.origin().getY(), state.origin().getZ()), false);
                user.displayClientMessage(Component.translatable("message.ender_infection.operations.objective." + task), false);
                user.displayClientMessage(Component.translatable("message.ender_infection.operations.power_status",
                        Component.translatable("message.ender_infection.operations." + (state.servicePowered() ? "on" : "off")),
                        Component.translatable("message.ender_infection.operations." + (state.containmentPowered() ? "on" : "off"))), false);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
