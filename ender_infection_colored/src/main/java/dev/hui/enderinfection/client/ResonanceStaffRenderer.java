package dev.hui.enderinfection.client;

import java.util.function.Consumer;
import dev.hui.enderinfection.design.ResonanceStaff;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class ResonanceStaffRenderer extends GeoItemRenderer<ResonanceStaff> {
    public ResonanceStaffRenderer(){
        super(new GeoModel<>(){
            public ResourceLocation getModelResource(ResonanceStaff item){return new ResourceLocation("ender_infection","geo/resonance_staff.geo.json");}
            public ResourceLocation getTextureResource(ResonanceStaff item){return new ResourceLocation("ender_infection","textures/item/design/resonance_staff.png");}
            public ResourceLocation getAnimationResource(ResonanceStaff item){return new ResourceLocation("ender_infection","animations/resonance_staff.animation.json");}
        });
        addRenderLayer(new GuardedGlowLayer<>(this));
    }
    public static void extensions(Consumer<IClientItemExtensions> consumer){
        consumer.accept(new IClientItemExtensions(){
            private ResonanceStaffRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer==null)renderer=new ResonanceStaffRenderer();return renderer;
            }
        });
    }
}
