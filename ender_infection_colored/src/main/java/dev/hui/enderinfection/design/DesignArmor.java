package dev.hui.enderinfection.design;
import net.minecraft.world.item.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

public final class DesignArmor extends ArmorItem implements GeoItem {
    public final String set;private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public DesignArmor(String set,ArmorMaterial material,Type type){super(material,type,new Item.Properties().rarity(Rarity.RARE));this.set=set;}
    @Override public String getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,net.minecraft.world.entity.EquipmentSlot slot,String type){return "ender_infection:textures/armor/"+set+".png";}
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){dev.hui.enderinfection.client.DesignArmorRenderer.extensions(this,consumer);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
