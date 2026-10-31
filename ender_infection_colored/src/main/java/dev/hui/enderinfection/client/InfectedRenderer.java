package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class InfectedRenderer extends GeoEntityRenderer<InfectedMob> {
    public InfectedRenderer(EntityRendererProvider.Context context,InfectedKind kind){
        super(context,new Model(kind));this.shadowRadius=kind.width*.5F;
        if(kind==InfectedKind.SIEGE_BEAST)addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }
    private static final class Model extends GeoModel<InfectedMob>{
        private final String key;
        Model(InfectedKind kind){key="infected_"+kind.key;}
        private String appearance(InfectedMob entity){return entity.kind==InfectedKind.CORE&&entity.motherStage()!=2?"infected_core_stage"+entity.motherStage():key;}
        @Override public ResourceLocation getModelResource(InfectedMob entity){return new ResourceLocation(EnderInfection.MOD_ID,"geo/"+appearance(entity)+".geo.json");}
        @Override public ResourceLocation getTextureResource(InfectedMob entity){return new ResourceLocation(EnderInfection.MOD_ID,"textures/entity/"+appearance(entity)+".png");}
        @Override public ResourceLocation getAnimationResource(InfectedMob entity){return new ResourceLocation(EnderInfection.MOD_ID,"animations/"+key+".animation.json");}
    }
}
