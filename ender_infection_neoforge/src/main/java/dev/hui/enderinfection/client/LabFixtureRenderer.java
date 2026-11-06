package dev.hui.enderinfection.client;
import dev.hui.enderinfection.design.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.animation.AnimationState;

public final class LabFixtureRenderer extends GeoBlockRenderer<LabFixtureEntity> {
    public LabFixtureRenderer(){super(new Model());addRenderLayer(new GuardedGlowLayer<>(this));}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(LabFixtureEntity entity){return entity.getRenderBoundingBox();}
    private static final class Model extends GeoModel<LabFixtureEntity>{
        private String modelKey(LabFixtureEntity e){
            var side=e.getLevel()==null?LabFixtureBlock.DoorPairSide.NONE:LabFixtureBlock.doorPairSide(e.getLevel(),e.getBlockPos(),e.getBlockState());
            return e.key()+(side==LabFixtureBlock.DoorPairSide.NONE?"":side==LabFixtureBlock.DoorPairSide.LEFT?"_double_left":"_double_right");
        }
        public ResourceLocation getModelResource(LabFixtureEntity e){return ResourceLocation.fromNamespaceAndPath("ender_infection","geo/"+modelKey(e)+".geo.json");}
        public ResourceLocation getTextureResource(LabFixtureEntity e){return ResourceLocation.fromNamespaceAndPath("ender_infection","textures/block/design/"+e.key()+".png");}
        public ResourceLocation getAnimationResource(LabFixtureEntity e){return ResourceLocation.fromNamespaceAndPath("ender_infection","animations/"+e.key()+".animation.json");}
        @Override public RenderType getRenderType(LabFixtureEntity e,ResourceLocation texture){return RenderType.entityTranslucent(texture);}
        @Override public void setCustomAnimations(LabFixtureEntity e,long id,AnimationState<LabFixtureEntity> state){
            super.setCustomAnimations(e,id,state);float p=e.previousOpen+(e.openProgress-e.previousOpen)*state.getPartialTick();float angle=p*(float)Math.toRadians(90);
            for(String name:new String[]{"hatch_hinge","door_hinge","door_-1","door_1"})getBone(name).ifPresent(b->b.setRotY(b.getPivotX()<0?angle:-angle));
            for(int i=0;i<8;i++)getBone("drawer_"+i).ifPresent(b->b.setPosZ(-4*p));
            getBone("curtain_left").ifPresent(b->{b.setPosX(6*p);b.setScaleX(1-.86F*p);});
            getBone("curtain_right").ifPresent(b->{b.setPosX(-6*p);b.setScaleX(1-.86F*p);});
            float time=(e.getLevel()==null?0:e.getLevel().getGameTime())+state.getPartialTick();
            getBone("specimen").ifPresent(b->{b.setRotY(time*.012F);b.setPosY((float)Math.sin(time*.05)*.35F);});
            for(int i=0;i<3;i++){final int n=i;getBone("bubble_"+i).ifPresent(b->b.setPosY((time*.07F+n*4)%12-6));}
        }
    }
}
