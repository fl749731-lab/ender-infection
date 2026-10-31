package dev.hui.enderinfection.client;

import com.mojang.blaze3d.vertex.*;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.CrystalSpike;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class CrystalSpikeRenderer extends EntityRenderer<CrystalSpike> {
    private static final ResourceLocation TEXTURE=new ResourceLocation(EnderInfection.MOD_ID,"textures/entity/crystal_spike.png");
    public CrystalSpikeRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(CrystalSpike entity){return TEXTURE;}
    @Override public void render(CrystalSpike entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=entity.tickCount+partial-entity.warmup();float scale=Math.min(1,Math.max(0,age/4))*Math.min(1,Math.max(0,(20-age)/6));if(scale<=0)return;
        pose.pushPose();pose.scale(1,scale,1);var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        prism(pose,consumer,0,0,0,.28F,1.8F);prism(pose,consumer,.32F,0,.12F,.16F,1.05F);prism(pose,consumer,-.25F,0,-.2F,.14F,.8F);
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
    private static void prism(PoseStack pose,VertexConsumer out,float x,float y,float z,float width,float height){
        for(int side=0;side<4;side++){
            double a=side*Math.PI/2,b=(side+1)*Math.PI/2;float ax=x+(float)Math.cos(a)*width,az=z+(float)Math.sin(a)*width,bx=x+(float)Math.cos(b)*width,bz=z+(float)Math.sin(b)*width;
            vertex(pose,out,ax,y,az,0,1,side);vertex(pose,out,bx,y,bz,1,1,side);vertex(pose,out,x,y+height,z,.5F,0,side);vertex(pose,out,x,y+height,z,.5F,0,side);
        }
    }
    private static void vertex(PoseStack pose,VertexConsumer out,float x,float y,float z,float u,float v,int side){float tint=.65F+side*.1F;out.vertex(pose.last().pose(),x,y,z).color(tint*.8F,tint*.45F,tint,1).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,1,0).endVertex();}
}
