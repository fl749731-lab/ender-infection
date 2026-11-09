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
    private static final java.util.function.Supplier<Block> BENCH=BLOCKS.register("lab_bench", () -> new LabBenchBlock());
    private static final java.util.function.Supplier<Block> HEATER = BLOCKS.register("hot_plate", () -> new LabBenchBlock(Chemistry.Station.HEATER));
    private static final java.util.function.Supplier<Block> STIRRER = BLOCKS.register("magnetic_stirrer", () -> new LabBenchBlock(Chemistry.Station.STIRRER));
    private static final java.util.function.Supplier<Block> FILTER = BLOCKS.register("filter_funnel", () -> new LabBenchBlock(Chemistry.Station.FILTER));
    private static final java.util.function.Supplier<Block> CONDENSER = BLOCKS.register("condenser", () -> new LabBenchBlock(Chemistry.Station.CONDENSER));
    static {
        GOODS.put("lab_bench",ITEMS.register("lab_bench",()->new BlockItem(BENCH.get(),new Item.Properties())));
        GOODS.put("hot_plate", ITEMS.register("hot_plate", () -> new BlockItem(HEATER.get(), new Item.Properties())));
        GOODS.put("magnetic_stirrer", ITEMS.register("magnetic_stirrer", () -> new BlockItem(STIRRER.get(), new Item.Properties())));
        GOODS.put("filter_funnel", ITEMS.register("filter_funnel", () -> new BlockItem(FILTER.get(), new Item.Properties())));
        GOODS.put("condenser", ITEMS.register("condenser", () -> new BlockItem(CONDENSER.get(), new Item.Properties())));
        for (String id : List.of("alkali_ash", "neutral_salt", "filter_paper")) {
            GOODS.put(id, ITEMS.register(id, () -> new Item(new Item.Properties())));
        }
        GOODS.put("reagent_bottle",ITEMS.register("reagent_bottle",()->new LabVesselItem(250,250,25,50,100,250)));
        GOODS.put("dropper_bottle",ITEMS.register("dropper_bottle",()->new LabVesselItem(100,1,1,5,10,25)));
        GOODS.put("pipette",ITEMS.register("pipette",()->new LabVesselItem(25,5,1,5,10,25)));
        GOODS.put("test_tube",ITEMS.register("test_tube",()->new LabVesselItem(50,10,1,5,10,25,50)));
        GOODS.put("beaker",ITEMS.register("beaker",()->new LabVesselItem(1000,250,25,100,250,1000)));
    }
    private static final java.util.function.Supplier<BlockEntityType<LabBenchEntity>> BENCH_ENTITY=ENTITIES.register("lab_bench",()->BlockEntityType.Builder.of(LabBenchEntity::new,BENCH.get(),HEATER.get(),STIRRER.get(),FILTER.get(),CONDENSER.get()).build(null));
    private static final java.util.function.Supplier<MenuType<LabBenchMenu>> BENCH_MENU=MENUS.register("lab_bench",()->new MenuType<>(LabBenchMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final java.util.function.Supplier<MenuType<LabBenchMenu>> STATION_MENU = MENUS.register("lab_station",
            () -> new MenuType<>(LabBenchMenu::stationClient, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static BlockEntityType<LabBenchEntity> benchType(){return BENCH_ENTITY.get();}
    public static MenuType<LabBenchMenu> benchMenuType(){return BENCH_MENU.get();}
    public static MenuType<LabBenchMenu> stationMenuType() { return STATION_MENU.get(); }
    private static final java.util.function.Supplier<FluidType> SLURRY_TYPE=FLUID_TYPES.register("nutrient_slurry",()->new LabFluidType("nutrient_slurry", 0xffa96acf, 1300, 1800));
    private static final Map<Chemistry.Liquid, java.util.function.Supplier<FluidType>> CHEMICAL_TYPES = new EnumMap<>(Chemistry.Liquid.class);
    static {
        for (var liquid : Chemistry.Liquid.values()) {
            java.util.function.Supplier<FluidType> type = FLUID_TYPES.register(liquid.id(),
                    () -> new LabFluidType(liquid.id(), liquid.tint, 1050, 1100));
            CHEMICAL_TYPES.put(liquid, type);
            registerSolution(liquid.id(), type);
        }
    }

    private static void registerSolution(String id, java.util.function.Supplier<FluidType> type) {
        // Deferred registration resolves the source/flowing pair after both names are registered.
        java.util.function.Supplier<ForgeFlowingFluid.Properties> properties = () -> new ForgeFlowingFluid.Properties(type,
                () -> net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(id)),
                () -> net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id("flowing_" + id)));
        FLUIDS.register(id, () -> new ForgeFlowingFluid.Source(properties.get()));
        FLUIDS.register("flowing_" + id, () -> new ForgeFlowingFluid.Flowing(properties.get()));
    }

    public static FluidType chemicalType(Chemistry.Liquid liquid) { return CHEMICAL_TYPES.get(liquid).get(); }
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
    private static final class LabFluidType extends FluidType {
        private final int tint;

        LabFluidType(String id, int tint, int density, int viscosity) {
            super(FluidType.Properties.create().descriptionId("fluid.resonant_industry." + id).density(density).viscosity(viscosity));
            this.tint = tint;
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
                public net.minecraft.resources.ResourceLocation getStillTexture(){return IndustryContent.id("minecraft:block/water_still");}
                public net.minecraft.resources.ResourceLocation getFlowingTexture(){return IndustryContent.id("minecraft:block/water_flow");}
                public int getTintColor(){return tint;}
            });
        }
    }
}
