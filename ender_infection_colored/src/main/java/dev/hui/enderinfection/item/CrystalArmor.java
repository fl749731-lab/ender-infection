package dev.hui.enderinfection.item;

import dev.hui.enderinfection.client.CrystalArmorModel;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

final class CrystalArmor extends ArmorItem {
    CrystalArmor(CrystalMaterial crystal, Type slot, Properties properties) {
        super(crystal, slot, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(CrystalArmorModel.forMaterial(() -> ((CrystalMaterial) getMaterial()).id));
    }
}
