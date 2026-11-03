package dev.hui.enderinfection.item;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.FactionAI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = EnderInfection.MOD_ID)
public final class CrystalEquipment {
    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, EnderInfection.MOD_ID);
    private static final List<RegistryObject<Item>> COMBAT = new ArrayList<>();
    private static final List<RegistryObject<Item>> TOOLS = new ArrayList<>();
    private static final TagKey<Block> CRYSTAL_ORES =
        TagKey.create(Registries.BLOCK, new ResourceLocation(EnderInfection.MOD_ID, "crystal_ores"));

    static {
        for (CrystalMaterial material : CrystalMaterial.values()) {
            for (ArmorItem.Type slot : ArmorItem.Type.values()) {
                add(COMBAT, material, slot.getName(), () -> new ArmorItem(material, slot, new Item.Properties()));
            }
            add(COMBAT, material, "sword", () -> new CrystalSword(material));
            add(TOOLS, material, "pickaxe", () -> new PickaxeItem(material, 1, -2.8F, new Item.Properties()));
            add(TOOLS, material, "axe", () -> new AxeItem(material, 5F, -3.1F, new Item.Properties()));
            add(TOOLS, material, "shovel", () -> new ShovelItem(material, 1.5F, -3F, new Item.Properties()));
            add(TOOLS, material, "hoe", () -> new HoeItem(material, -material.getLevel(), -1F, new Item.Properties()));
        }
    }

    private CrystalEquipment() {}

    private static void add(List<RegistryObject<Item>> group, CrystalMaterial material, String slot,
                            Supplier<Item> item) {
        group.add(ITEMS.register(material.id + "_" + slot, item));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(CrystalEquipment::creative);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        var tab = event.getTabKey();
        if (tab.equals(CreativeModeTabs.COMBAT) || tab.equals(InfectedContent.TAB.getKey())) {
            COMBAT.forEach(event::accept);
        }
        if (tab.equals(CreativeModeTabs.TOOLS_AND_UTILITIES) || tab.equals(InfectedContent.TAB.getKey())) {
            TOOLS.forEach(event::accept);
        }
    }

    @SubscribeEvent
    public static void miningSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        var stack = player.getMainHandItem();
        var state = event.getState();
        if (!(stack.getItem() instanceof DiggerItem tool) || !(tool.getTier() instanceof CrystalMaterial material)
            || stack.getDestroySpeed(state) <= 1
            || (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state))) {
            return;
        }

        float multiplier = switch (material) {
            case RESONANT -> state.is(Tags.Blocks.ORES) || state.is(CRYSTAL_ORES)
                || InfectedContent.terrainKey(player.level().getBlockState(player.getOnPos())) != null ? 1.35F : 1F;
            case EMBER -> player.level().dimension().equals(Level.NETHER) ? 1.25F : 1F;
            case VOID -> !player.onGround() && !player.isPassenger() && !player.isEyeInFluid(FluidTags.WATER) ? 3F : 1F;
        };
        event.setNewSpeed(event.getNewSpeed() * multiplier);
    }

    @SubscribeEvent
    public static void damage(LivingHurtEvent event) {
        var source = event.getSource();
        float amount = event.getAmount();
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player attacker
            && attacker.getMainHandItem().getItem() instanceof CrystalSword sword
            && sword.getTier() == CrystalMaterial.RESONANT && FactionAI.infected(event.getEntity())) {
            amount *= 1.25F;
        }

        if (event.getEntity() instanceof Player player) {
            CrystalMaterial fullSet = fullSet(player);
            if (fullSet != null) {
                amount *= switch (fullSet) {
                    case RESONANT -> FactionAI.infected(source.getEntity()) ? 0.8F : 1F;
                    case EMBER -> source.is(DamageTypeTags.IS_FIRE) ? 0.6F : 1F;
                    case VOID -> source.is(DamageTypeTags.IS_FALL) ? 0.5F
                        : source.is(DamageTypeTags.IS_PROJECTILE) ? 0.8F : 1F;
                };
            }
        }
        event.setAmount(amount);
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        String key;
        if (item instanceof ArmorItem armor && armor.getMaterial() instanceof CrystalMaterial material) {
            key = material.id + ".armor";
        } else if (item instanceof CrystalSword sword && sword.getTier() instanceof CrystalMaterial material) {
            key = material.id + ".sword";
        } else if (item instanceof DiggerItem tool && tool.getTier() instanceof CrystalMaterial material) {
            key = material.id + ".tool";
        } else {
            return;
        }
        event.getToolTip().add(Component.translatable("tooltip.ender_infection." + key).withStyle(ChatFormatting.GRAY));
    }

    private static CrystalMaterial fullSet(Player player) {
        CrystalMaterial set = null;
        for (ArmorItem.Type slot : ArmorItem.Type.values()) {
            var stack = player.getItemBySlot(slot.getSlot());
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getType() != slot
                || !(armor.getMaterial() instanceof CrystalMaterial material) || (set != null && set != material)) {
                return null;
            }
            set = material;
        }
        return set;
    }
}
