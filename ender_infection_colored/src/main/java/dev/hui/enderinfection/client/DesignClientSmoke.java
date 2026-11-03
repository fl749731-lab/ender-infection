package dev.hui.enderinfection.client;
import dev.hui.enderinfection.*;
import dev.hui.enderinfection.design.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.GeckoLibCache;

/** Explicit -PdesignSmoke only: validate baked assets in an isolated client directory. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class DesignClientSmoke {
    private static boolean done;private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("ender_infection.designSmoke")||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(!(mc.screen instanceof TitleScreen)&&mc.level==null||mc.getOverlay()!=null||++ticks<40)return;done=true;
        try{
            int geo=0,animations=0,items=0,glows=0,nonGlowingFixtures=0;
            for(var e:mc.getResourceManager().listResources("geo",r->r.getNamespace().equals("ender_infection")&&r.getPath().endsWith(".geo.json")).keySet()){
                if(!GeckoLibCache.getBakedModels().containsKey(e))throw new IllegalStateException("Unbaked geometry "+e);geo++;
            }
            for(var e:mc.getResourceManager().listResources("animations",r->r.getNamespace().equals("ender_infection")&&r.getPath().endsWith(".animation.json")).keySet()){
                if(!GeckoLibCache.getBakedAnimations().containsKey(e))throw new IllegalStateException("Unbaked animation "+e);animations++;
            }
            for(var e:net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries()){
                if(!e.getKey().location().getNamespace().equals("ender_infection"))continue;
                var model=mc.getItemRenderer().getItemModelShaper().getItemModel(e.getValue());
                if(model==mc.getModelManager().getMissingModel()||model.getParticleIcon().contents().name().toString().equals("minecraft:missingno"))throw new IllegalStateException("Missing item model/sprite "+e.getKey());items++;
            }
            // Exercise GeckoLib's real texture registration, which model baking does not invoke.
            try(var probe=new com.mojang.blaze3d.platform.NativeImage(1,1,true)){
                probe.setPixelRGBA(0,0,0x00ffffff);
                if(GuardedGlowLayer.hasVisiblePixels(probe))throw new IllegalStateException("Transparent mask accepted");
                probe.setPixelRGBA(0,0,0xffffffff);
                if(!GuardedGlowLayer.hasVisiblePixels(probe))throw new IllegalStateException("Visible mask rejected");
            }
            for(var mask:mc.getResourceManager().listResources("textures",r->r.getNamespace().equals("ender_infection")&&r.getPath().endsWith("_glowmask.png")).keySet()){
                var base=new ResourceLocation(mask.getNamespace(),mask.getPath().replace("_glowmask.png",".png"));
                if(!GuardedGlowLayer.hasGlow(base))throw new IllegalStateException("Invalid emitted glow mask "+mask);
                software.bernie.geckolib.cache.texture.AutoGlowingTexture.getRenderType(base);glows++;
            }
            for(String key:new String[]{"lab_vent","lab_hatch","lab_door","lab_cabinet","lab_window","lab_workbench","surgery_table"}){
                var base=new ResourceLocation("ender_infection","textures/block/design/"+key+".png");
                if(GuardedGlowLayer.hasGlow(base))throw new IllegalStateException("Plain fixture should not glow "+key);
                nonGlowingFixtures++;
            }
            String result="CLIENT_ASSET_SMOKE_PASS geo="+geo+" animations="+animations+" design_items="+items+" registered_glows="+glows+" non_glowing_fixtures="+nonGlowingFixtures;
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("design-smoke-result.txt"),result);System.out.println(result);
        }catch(Exception e){try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("design-smoke-result.txt"),"FAIL "+e);}catch(Exception ignored){}throw new RuntimeException(e);}
        if(mc.level==null&&!Boolean.getBoolean("ender_infection.keepSmokeClient"))mc.stop();
    }
}
