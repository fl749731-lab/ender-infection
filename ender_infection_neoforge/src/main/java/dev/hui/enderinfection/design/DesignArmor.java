package dev.hui.enderinfection.design;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

public final class DesignArmor extends ArmorItem implements GeoItem {
    public final String set;private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public DesignArmor(String set,net.minecraft.core.Holder<ArmorMaterial> material,Type type,int durability){super(material,type,new Item.Properties().durability(durability).rarity(Rarity.RARE));this.set=set;}
    @Override public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,net.minecraft.world.entity.EquipmentSlot slot,ArmorMaterial.Layer layer,boolean innerModel){return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ender_infection","textures/armor/"+set+".png");}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){dev.hui.enderinfection.client.DesignArmorRenderer.extensions(this,consumer);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
