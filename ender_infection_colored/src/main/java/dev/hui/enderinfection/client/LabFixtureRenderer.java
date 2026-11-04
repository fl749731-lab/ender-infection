package dev.hui.enderinfection.client;
import dev.hui.enderinfection.design.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.core.animation.AnimationState;

public final class LabFixtureRenderer extends GeoBlockRenderer<LabFixtureEntity> {
    public LabFixtureRenderer(){super(new Model());addRenderLayer(new GuardedGlowLayer<>(this));}
    private static final class Model extends GeoModel<LabFixtureEntity>{
        public ResourceLocation getModelResource(LabFixtureEntity e){return new ResourceLocation("ender_infection","geo/"+e.key()+".geo.json");}
        public ResourceLocation getTextureResource(LabFixtureEntity e){return new ResourceLocation("ender_infection","textures/block/design/"+e.key()+".png");}
        public ResourceLocation getAnimationResource(LabFixtureEntity e){return new ResourceLocation("ender_infection","animations/"+e.key()+".animation.json");}
        @Override public RenderType getRenderType(LabFixtureEntity e,ResourceLocation texture){return RenderType.entityTranslucent(texture);}
        @Override public void setCustomAnimations(LabFixtureEntity e,long id,AnimationState<LabFixtureEntity> state){
            super.setCustomAnimations(e,id,state);float p=e.previousOpen+(e.openProgress-e.previousOpen)*state.getPartialTick();float angle=p*(float)Math.toRadians(90);
            for(String name:new String[]{"hatch_hinge","door_hinge","door_-1","door_1"})getBone(name).ifPresent(b->b.setRotY(name.equals("door_-1")?-angle:angle));
            for(int i=0;i<8;i++)getBone("drawer_"+i).ifPresent(b->b.setPosZ(-4*p));
            getBone("curtain_left").ifPresent(b->{b.setPosX(6*p);b.setScaleX(1-.86F*p);});
            getBone("curtain_right").ifPresent(b->{b.setPosX(-6*p);b.setScaleX(1-.86F*p);});
            float time=(e.getLevel()==null?0:e.getLevel().getGameTime())+state.getPartialTick();
            getBone("specimen").ifPresent(b->{b.setRotY(time*.012F);b.setPosY((float)Math.sin(time*.05)*.35F);});
            for(int i=0;i<3;i++){final int n=i;getBone("bubble_"+i).ifPresent(b->b.setPosY((time*.07F+n*4)%12-6));}
        }
    }
}
