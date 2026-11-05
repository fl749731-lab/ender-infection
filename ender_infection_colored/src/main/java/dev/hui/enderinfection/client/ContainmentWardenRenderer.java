package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.ContainmentWarden;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ContainmentWardenRenderer extends GeoEntityRenderer<ContainmentWarden> {
    public ContainmentWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new Model()); shadowRadius = .95F;
        addRenderLayer(new GuardedGlowLayer<>(this));
    }
    @Override protected float getDeathMaxRotation(ContainmentWarden mob) { return 0; }
    private static final class Model extends GeoModel<ContainmentWarden> {
        private ResourceLocation resource(String path) { return new ResourceLocation(EnderInfection.MOD_ID, path); }
        @Override public ResourceLocation getModelResource(ContainmentWarden mob) { return resource("geo/containment_warden.geo.json"); }
        @Override public ResourceLocation getTextureResource(ContainmentWarden mob) { return resource("textures/entity/containment_warden.png"); }
        @Override public ResourceLocation getAnimationResource(ContainmentWarden mob) { return resource("animations/containment_warden.animation.json"); }
    }
}
