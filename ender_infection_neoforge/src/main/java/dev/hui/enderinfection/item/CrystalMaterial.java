package dev.hui.enderinfection.item;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

enum CrystalMaterial implements Tier {
    RESONANT("resonant_crystal", InfectionItems.SHARD, 2, 360, 6.5F, 2F, 18, 0F, 2, 5, 6, 2),
    EMBER("ember_crystal", InfectedContent.EMBER_CRYSTAL, 2, 640, 7F, 2.5F, 25, 1F, 2, 5, 7, 3),
    VOID("void_crystal", InfectedContent.VOID_CRYSTAL, 3, 1700, 8F, 3F, 35, 2F, 3, 6, 8, 3);

    final String id;
    private final Supplier<Item> crystal;
    private final int miningLevel;
    private final int toolDurability;
    private final float miningSpeed;
    private final float attackBonus;
    private final int armorDurability;
    private final float toughness;
    private final int boots;
    private final int leggings;
    private final int chestplate;
    private final int helmet;

    CrystalMaterial(String id, Supplier<Item> crystal, int miningLevel, int toolDurability,
                    float miningSpeed, float attackBonus, int armorDurability, float toughness,
                    int boots, int leggings, int chestplate, int helmet) {
        this.id = id;
        this.crystal = crystal;
        this.miningLevel = miningLevel;
        this.toolDurability = toolDurability;
        this.miningSpeed = miningSpeed;
        this.attackBonus = attackBonus;
        this.armorDurability = armorDurability;
        this.toughness = toughness;
        this.boots = boots;
        this.leggings = leggings;
        this.chestplate = chestplate;
        this.helmet = helmet;
    }

    @Override public int getUses() { return toolDurability; }
    @Override public float getSpeed() { return miningSpeed; }
    @Override public float getAttackDamageBonus() { return attackBonus; }
    public int getLevel() { return miningLevel; }
    @Override public int getEnchantmentValue() { return 14; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(crystal.get()); }
    public String getName() { return EnderInfection.MOD_ID + ":" + id; }
    public net.minecraft.core.Holder<SoundEvent> getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
    public float getToughness() { return toughness; }
    public float getKnockbackResistance() { return 0; }

    public int getDurabilityForType(ArmorItem.Type slot) {
        return armorDurability * switch (slot) {
            case HELMET -> 13;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 11;
            case BODY -> 16;
        };
    }

    public int getDefenseForType(ArmorItem.Type slot) {
        return switch (slot) {
            case HELMET -> helmet;
            case CHESTPLATE -> chestplate;
            case LEGGINGS -> leggings;
            case BOOTS -> boots;
            case BODY -> 0;
        };
    }
    @Override public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
        return miningLevel >= 3 ? net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL : net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL;
    }
    public net.minecraft.core.Holder<ArmorMaterial> armorHolder(){return CrystalEquipment.ARMOR_BY_KEY.get(id);}
    public ArmorMaterial armorMaterial(){
        var defense = new java.util.EnumMap<ArmorItem.Type,Integer>(ArmorItem.Type.class);
        for(var type:ArmorItem.Type.values())defense.put(type,getDefenseForType(type));
        return new ArmorMaterial(defense,14,SoundEvents.ARMOR_EQUIP_IRON,this::getRepairIngredient,
            java.util.List.of(new ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID,id))),toughness,0);
    }
}
