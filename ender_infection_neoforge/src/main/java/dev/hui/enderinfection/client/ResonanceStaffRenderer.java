package dev.hui.enderinfection.client;

import java.util.function.Consumer;
import dev.hui.enderinfection.design.ResonanceStaff;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class ResonanceStaffRenderer extends GeoItemRenderer<ResonanceStaff> {
    public ResonanceStaffRenderer(){
        super(new GeoModel<>(){
            public ResourceLocation getModelResource(ResonanceStaff item){return ResourceLocation.fromNamespaceAndPath("ender_infection","geo/resonance_staff.geo.json");}
            public ResourceLocation getTextureResource(ResonanceStaff item){return ResourceLocation.fromNamespaceAndPath("ender_infection","textures/item/design/resonance_staff.png");}
            public ResourceLocation getAnimationResource(ResonanceStaff item){return ResourceLocation.fromNamespaceAndPath("ender_infection","animations/resonance_staff.animation.json");}
        });
        addRenderLayer(new GuardedGlowLayer<>(this));
    }
    public static void extensions(Consumer<GeoRenderProvider> consumer){
        consumer.accept(new GeoRenderProvider(){
            private ResonanceStaffRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getGeoItemRenderer(){
                if(renderer==null)renderer=new ResonanceStaffRenderer();return renderer;
            }
        });
    }
}
