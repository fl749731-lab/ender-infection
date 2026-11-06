package dev.hui.enderinfection.client;
import dev.hui.enderinfection.design.DesignArmor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.model.HumanoidModel;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import java.util.function.Consumer;

public final class DesignArmorRenderer extends GeoArmorRenderer<DesignArmor> {
    public DesignArmorRenderer(){super(new GeoModel<>(){
        public ResourceLocation getModelResource(DesignArmor a){return ResourceLocation.fromNamespaceAndPath("ender_infection","geo/"+a.set+".geo.json");}
        public ResourceLocation getTextureResource(DesignArmor a){return ResourceLocation.fromNamespaceAndPath("ender_infection","textures/armor/"+a.set+".png");}
        public ResourceLocation getAnimationResource(DesignArmor a){return ResourceLocation.fromNamespaceAndPath("ender_infection","animations/"+a.set+".animation.json");}
    });addRenderLayer(new GuardedGlowLayer<>(this));}
    private software.bernie.geckolib.cache.object.GeoBone[] armorBones(){return new software.bernie.geckolib.cache.object.GeoBone[]{head,body,rightArm,leftArm,rightLeg,leftLeg,rightBoot,leftBoot};}
    @Override protected void applyBaseTransformations(HumanoidModel<?> base){
        super.applyBaseTransformations(base);
        if(ArmorVisualCheck.enabled())ArmorVisualCheck.observePose(currentEntity,base,armorBones());
    }
    @Override protected void applyBoneVisibilityBySlot(EquipmentSlot slot){
        super.applyBoneVisibilityBySlot(slot);
        if(ArmorVisualCheck.enabled())ArmorVisualCheck.observeSlot(currentEntity,slot,animatable,armorBones());
    }
    public static void extensions(DesignArmor item,Consumer<IClientItemExtensions> consumer){consumer.accept(new IClientItemExtensions(){
        private DesignArmorRenderer renderer;
        @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity living,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
            if(renderer==null)renderer=new DesignArmorRenderer();renderer.prepForRender(living,stack,slot,original);return renderer;
        }
    });}
}
