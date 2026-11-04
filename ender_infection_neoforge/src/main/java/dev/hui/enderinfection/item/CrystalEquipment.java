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
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@EventBusSubscriber(modid = EnderInfection.MOD_ID)
public final class CrystalEquipment {
    public static final net.neoforged.neoforge.registries.DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.ARMOR_MATERIAL,EnderInfection.MOD_ID);
    static final java.util.Map<String,net.neoforged.neoforge.registries.DeferredHolder<ArmorMaterial,ArmorMaterial>> ARMOR_BY_KEY=new java.util.HashMap<>();
    static {for(var material:CrystalMaterial.values())ARMOR_BY_KEY.put(material.id,ARMOR_MATERIALS.register(material.id,material::armorMaterial));}

    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, EnderInfection.MOD_ID);
    private static final List<DeferredHolder<Item,Item>> COMBAT = new ArrayList<>();
    private static final List<DeferredHolder<Item,Item>> TOOLS = new ArrayList<>();
    private static final TagKey<Block> CRYSTAL_ORES =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID, "crystal_ores"));

    static {
        for (CrystalMaterial material : CrystalMaterial.values()) {
            for (ArmorItem.Type slot : java.util.Arrays.stream(ArmorItem.Type.values()).filter(t -> t != ArmorItem.Type.BODY).toArray(ArmorItem.Type[]::new)) {
                add(COMBAT, material, slot.getName(), () -> new CrystalArmor(material, slot, new Item.Properties()));
            }
            add(COMBAT, material, "sword", () -> new CrystalSword(material));
            add(TOOLS, material, "pickaxe", () -> new PickaxeItem(material, new Item.Properties().attributes(DiggerItem.createAttributes(material, 1, -2.8F))));
            add(TOOLS, material, "axe", () -> new AxeItem(material, new Item.Properties().attributes(AxeItem.createAttributes(material, 5F, -3.1F))));
            add(TOOLS, material, "shovel", () -> new ShovelItem(material, new Item.Properties().attributes(ShovelItem.createAttributes(material, 1.5F, -3F))));
            add(TOOLS, material, "hoe", () -> new HoeItem(material, new Item.Properties().attributes(HoeItem.createAttributes(material, -material.getLevel(), -1F))));
        }
    }

    private CrystalEquipment() {}

    private static void add(List<DeferredHolder<Item,Item>> group, CrystalMaterial material, String slot,
                            Supplier<Item> item) {
        group.add(ITEMS.register(material.id + "_" + slot, item));
    }

    public static void register(IEventBus bus) {
        ARMOR_MATERIALS.register(bus);ITEMS.register(bus);
        bus.addListener(CrystalEquipment::creative);
    }

    private static void creative(BuildCreativeModeTabContentsEvent event) {
        var tab = event.getTabKey();
        if (tab.equals(CreativeModeTabs.COMBAT) || tab.equals(InfectedContent.TAB.getKey())) {
            COMBAT.forEach(item -> event.accept(item.get()));
        }
        if (tab.equals(CreativeModeTabs.TOOLS_AND_UTILITIES) || tab.equals(InfectedContent.TAB.getKey())) {
            TOOLS.forEach(item -> event.accept(item.get()));
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
    public static void damage(LivingIncomingDamageEvent event) {
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
        if (item instanceof CrystalArmor armor && armor.crystal != null) {
            key = armor.crystal.id + ".armor";
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
        for (ArmorItem.Type slot : java.util.Arrays.stream(ArmorItem.Type.values()).filter(t -> t != ArmorItem.Type.BODY).toArray(ArmorItem.Type[]::new)) {
            var stack = player.getItemBySlot(slot.getSlot());
            if (!(stack.getItem() instanceof CrystalArmor armor) || armor.getType() != slot
                || (set != null && set != armor.crystal)) {
                return null;
            }
            set = armor.crystal;
        }
        return set;
    }
}
