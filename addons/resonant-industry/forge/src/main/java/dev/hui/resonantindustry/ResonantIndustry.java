package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fluids.*;

@Mod(IndustryApi.MOD_ID)
public final class ResonantIndustry {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,IndustryApi.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,IndustryApi.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,IndustryApi.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,IndustryApi.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(ForgeRegistries.FLUIDS,IndustryApi.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,IndustryApi.MOD_ID);
    private static final Map<String,java.util.function.Supplier<? extends Block>> PARTS=new LinkedHashMap<>();
    private static final Map<String,java.util.function.Supplier<? extends Item>> GOODS=new LinkedHashMap<>();
    static {
        for(String role:IndustryContent.ROLES){var b=BLOCKS.register(role,()->new IndustryBlock(role));PARTS.put(role,b);if(!role.equals("rotor_blades")&&!role.endsWith("_visual"))GOODS.put(role,ITEMS.register(role,()->new BlockItem(b.get(),new Item.Properties())));}
        for(String name:List.of("wrench","manual","slurry_canister","fuel_residue","purified_tissue"))GOODS.put(name,ITEMS.register(name,()->name.equals("manual")?new ManualItem():new Item(new Item.Properties().stacksTo(Set.of("wrench","slurry_canister").contains(name)?1:64))));
        for(String kind:List.of("generator","processor","centrifuge"))GOODS.put(kind+"_blueprint",ITEMS.register(kind+"_blueprint",()->new BlueprintItem(IndustryApi.MOD_ID+":"+kind)));
    }
    private static final java.util.function.Supplier<FluidType> SLURRY_TYPE=FLUID_TYPES.register("nutrient_slurry",()->new SlurryType());
    private static ForgeFlowingFluid.Properties slurryProperties(){return new ForgeFlowingFluid.Properties(SLURRY_TYPE,SLURRY,FLOWING_SLURRY).bucket(GOODS.get("slurry_canister"));}
    private static final java.util.function.Supplier<Fluid> SLURRY=FLUIDS.register("nutrient_slurry",()->new ForgeFlowingFluid.Source(slurryProperties()));
    private static final java.util.function.Supplier<Fluid> FLOWING_SLURRY=FLUIDS.register("flowing_nutrient_slurry",()->new ForgeFlowingFluid.Flowing(slurryProperties()));
    private static final java.util.function.Supplier<BlockEntityType<IndustryNode>> NODE=ENTITIES.register("industry_node",()->BlockEntityType.Builder.of(IndustryNode::new,PARTS.values().stream().map(java.util.function.Supplier::get).toArray(Block[]::new)).build(null));
    private static final java.util.function.Supplier<MenuType<IndustryMenu>> MENU=MENUS.register("machine",()->new MenuType<>(IndustryMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static BlockEntityType<IndustryNode> nodeType(){return NODE.get();}
    public static MenuType<IndustryMenu> menuType(){return MENU.get();}
    public static FluidType slurryType(){return SLURRY_TYPE.get();}
    public ResonantIndustry(){
        IndustryApi.bootstrap();var bus=FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);FLUID_TYPES.register(bus);FLUIDS.register(bus);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(()->{IndustryContent.NODE=NODE.get();IndustryContent.MENU=MENU.get();IndustryContent.SLURRY=SLURRY.get();IndustryApi.freeze();}));
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)GOODS.values().forEach(v->e.accept(v.get()));});
    }
    private static final class SlurryType extends FluidType {
        SlurryType(){super(FluidType.Properties.create().descriptionId("fluid.resonant_industry.nutrient_slurry").density(1300).viscosity(1800));}
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
                public net.minecraft.resources.ResourceLocation getStillTexture(){return IndustryContent.id("minecraft:block/water_still");}
                public net.minecraft.resources.ResourceLocation getFlowingTexture(){return IndustryContent.id("minecraft:block/water_flow");}
                public int getTintColor(){return 0xffa96acf;}
            });
        }
    }
}
