package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.particle.FactionParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.FoliageColor;
import dev.hui.enderinfection.block.InfectedCanopyBlock;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnderInfection.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        InfectedContent.TERRAIN.forEach((name,block)->{if(name.endsWith("leaves"))ItemBlockRenderTypes.setRenderLayer(block.get(),RenderType.cutout());});
        ItemBlockRenderTypes.setRenderLayer(InfectedContent.TERRAIN.get("eroded_grass").get(),RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(InfectedContent.TERRAIN.get("eroded_soil").get(),RenderType.cutoutMipped());
        InfectedContent.PLANTS.values().forEach(block->ItemBlockRenderTypes.setRenderLayer(block.get(),RenderType.cutout()));
    });}
    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event){
        event.register((state,world,pos,index)->index==0&&dev.hui.enderinfection.block.Vegetation.grassTint(state)?world!=null&&pos!=null?BiomeColors.getAverageGrassColor(world,pos):GrassColor.get(.5,1):-1,InfectedContent.PLANTS.get("eroded_vegetation").get(),InfectedContent.PLANTS.get("eroded_tall_vegetation").get());
        event.register((state,world,pos,index)->world!=null&&pos!=null?BiomeColors.getAverageGrassColor(world,pos):GrassColor.get(0.5,1.0),InfectedContent.TERRAIN.get("eroded_grass").get());
        event.register((state,world,pos,index)->switch(state.getValue(InfectedCanopyBlock.WOOD)){
            case 1->FoliageColor.getEvergreenColor();case 2->FoliageColor.getBirchColor();case 7,8,9->-1;
            default->world!=null&&pos!=null?BiomeColors.getAverageFoliageColor(world,pos):FoliageColor.getDefaultColor();
        },InfectedContent.TERRAIN.get("eroded_leaves").get());
    }
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event){
        event.register((stack,index)->index==0?GrassColor.get(.5,1):-1,InfectedContent.PLANT_ITEMS.get("eroded_vegetation").get(),InfectedContent.PLANT_ITEMS.get("eroded_tall_vegetation").get());
        event.register((stack,index)->index==0?GrassColor.get(0.5,1.0):-1,InfectedContent.TERRAIN_ITEMS.get("eroded_grass").get());
        event.register((stack,index)->index==0?FoliageColor.getDefaultColor():-1,InfectedContent.TERRAIN_ITEMS.get("eroded_leaves").get());
    }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event){
        event.registerSpriteSet(FactionParticles.SHARD.get(),sprites->new FactionParticle.Provider(sprites,FactionParticle.Kind.SHARD));
        event.registerSpriteSet(FactionParticles.SPORE.get(),sprites->new FactionParticle.Provider(sprites,FactionParticle.Kind.SPORE));
        event.registerSpriteSet(FactionParticles.MOTE.get(),sprites->new FactionParticle.Provider(sprites,FactionParticle.Kind.MOTE));
        event.registerSpriteSet(FactionParticles.RUNE.get(),sprites->new FactionParticle.Provider(sprites,FactionParticle.Kind.RUNE));
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EnderInfection.MUTANT_ZOMBIE.get(), MutantZombieRenderer::new);
        event.registerEntityRenderer(EnderInfection.CRYSTAL_SPIKE.get(),CrystalSpikeRenderer::new);
        InfectedContent.TYPES.forEach((kind,type)->event.registerEntityRenderer(type.get(),context->new InfectedRenderer(context,kind)));
    }
}
