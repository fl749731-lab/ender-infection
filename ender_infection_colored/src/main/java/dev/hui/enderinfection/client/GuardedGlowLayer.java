package dev.hui.enderinfection.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** GeckoLib 4 rejects transparent glow masks. Skip optional masks before texture registration. */
public final class GuardedGlowLayer<T extends GeoAnimatable> extends AutoGlowingGeoLayer<T> {
    private static final Map<ResourceLocation,Boolean> MASKS=new ConcurrentHashMap<>();
    public GuardedGlowLayer(GeoRenderer<T> renderer){super(renderer);}
    public static void clearCache(){MASKS.clear();}
    public static boolean hasGlow(ResourceLocation base){return MASKS.computeIfAbsent(base,GuardedGlowLayer::readMask);}
    public static boolean hasVisiblePixels(NativeImage image){
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getPixelRGBA(x,y)>>>24)!=0)return true;
        return false;
    }
    private static boolean readMask(ResourceLocation base){
        String path=base.getPath();if(!path.endsWith(".png"))return false;
        var mask=new ResourceLocation(base.getNamespace(),path.substring(0,path.length()-4)+"_glowmask.png");
        var resource=Minecraft.getInstance().getResourceManager().getResource(mask);
        if(resource.isEmpty())return false;
        try(var stream=resource.get().open();var image=NativeImage.read(stream)){return hasVisiblePixels(image);}
        catch(IOException|RuntimeException ex){LogUtils.getLogger().warn("Skipping unreadable optional glow mask {}: {}",mask,ex.toString());return false;}
    }
    @Override public void render(PoseStack pose,T animatable,BakedGeoModel model,RenderType renderType,MultiBufferSource buffers,VertexConsumer buffer,float partialTick,int light,int overlay){
        var texture=getTextureResource(animatable);
        if(!hasGlow(texture))return;
        super.render(pose,animatable,model,renderType,buffers,buffer,partialTick,light,overlay);
    }
}
