package dev.hui.enderinfection.item;

import dev.hui.enderinfection.client.CrystalArmorModel;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

final class CrystalArmor extends ArmorItem {
    final CrystalMaterial crystal;
    CrystalArmor(CrystalMaterial crystal, Type slot, Properties properties) {
        super(crystal.armorHolder(), slot, properties.durability(crystal.getDurabilityForType(slot)));
        this.crystal=crystal;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(CrystalArmorModel.forMaterial(() -> crystal.id));
    }
}
