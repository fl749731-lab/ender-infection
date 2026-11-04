package dev.hui.enderinfection;

import dev.hui.enderinfection.block.InfectedNestBlock;
import dev.hui.enderinfection.block.InfectedPlantBlock;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import dev.hui.enderinfection.block.ErodedGroundBlock;
import dev.hui.enderinfection.block.InfectedTimberBlock;
import dev.hui.enderinfection.block.InfectedCanopyBlock;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.effect.MobEffect;
import dev.hui.enderinfection.effect.EnderErosionEffect;
import dev.hui.enderinfection.effect.MyceliumInfectionEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import dev.hui.enderinfection.item.StageEggItem;
import dev.hui.enderinfection.worldgen.InfectedRuinFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.EnumMap;
import java.util.Map;
import java.util.LinkedHashMap;

public final class InfectedContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,EnderInfection.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,EnderInfection.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,EnderInfection.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,EnderInfection.MOD_ID);
    private static final DeferredRegister<MobEffect> MOB_EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,EnderInfection.MOD_ID);
    /** Standing inside infected ground: any outsider catches this. */
    public static final RegistryObject<MobEffect> ENDER_EROSION=MOB_EFFECTS.register("ender_erosion",EnderErosionEffect::new);
    public static final RegistryObject<MobEffect> MYCELIUM_INFECTION=MOB_EFFECTS.register("mycelium_infection",MyceliumInfectionEffect::new);
    public static final Map<InfectedKind,RegistryObject<EntityType<InfectedMob>>> TYPES=new EnumMap<>(InfectedKind.class);
    public static final Map<InfectedKind,RegistryObject<Item>> EGGS=new EnumMap<>(InfectedKind.class);
    /** One egg per mother stage so every tier can be spawned directly. */
    public static final Map<Integer,RegistryObject<Item>> STAGE_EGGS=new LinkedHashMap<>();
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(ForgeRegistries.FEATURES,EnderInfection.MOD_ID);
    public static final RegistryObject<Feature<?>> INFECTED_RUIN=FEATURES.register("infected_ruin",()->new InfectedRuinFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<?>> LAB_RESEARCH=FEATURES.register("lab_research",()->new InfectedRuinFeature(NoneFeatureConfiguration.CODEC,InfectedRuinFeature.Style.RESEARCH));
    public static final RegistryObject<Feature<?>> LAB_HOSPITAL=FEATURES.register("lab_hospital",()->new InfectedRuinFeature(NoneFeatureConfiguration.CODEC,InfectedRuinFeature.Style.HOSPITAL));
    public static final RegistryObject<Feature<?>> LAB_PRISON=FEATURES.register("lab_prison",()->new InfectedRuinFeature(NoneFeatureConfiguration.CODEC,InfectedRuinFeature.Style.PRISON));
    private static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENTS=DeferredRegister.create(net.minecraft.core.registries.Registries.PLACEMENT_MODIFIER_TYPE,EnderInfection.MOD_ID);
    public static final RegistryObject<net.minecraft.world.level.levelgen.placement.PlacementModifierType<dev.hui.enderinfection.worldgen.GridPlacement>> GRID_PLACEMENT=PLACEMENTS.register("grid",()->()->dev.hui.enderinfection.worldgen.GridPlacement.CODEC);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,EnderInfection.MOD_ID);
    public static final RegistryObject<Block> NEST=BLOCKS.register("infected_nest",()->new InfectedNestBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.AMETHYST).lightLevel(s->6).noOcclusion()));
    public static final RegistryObject<Item> NEST_ITEM=ITEMS.register("infected_nest",()->new BlockItem(NEST.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ExpansionNodeEntity>> NODE_ENTITY=BLOCK_ENTITIES.register("expansion_node",()->BlockEntityType.Builder.of(ExpansionNodeEntity::new,NEST.get()).build(null));
    /** Laboratory set: tiles, cabinet, incubator, surgery table and the two syringes. */
    public static final RegistryObject<Block> LAB_TILE=BLOCKS.register("lab_tile",()->new Block(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> LAB_TILE_DARK=BLOCKS.register("lab_tile_dark",()->new Block(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> LAB_TILE_LIGHT=BLOCKS.register("lab_tile_light",()->new Block(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> LAB_CHECKER=BLOCKS.register("lab_checker",()->new Block(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> LAB_CHECKER_ITEM=ITEMS.register("lab_checker",()->new BlockItem(LAB_CHECKER.get(),new Item.Properties()));
    public static final RegistryObject<Block> LAB_CABINET=BLOCKS.register("lab_cabinet",()->new dev.hui.enderinfection.design.LabFixtureBlock("lab_cabinet",BlockBehaviour.Properties.of().strength(3F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> INCUBATOR=BLOCKS.register("incubator",()->new dev.hui.enderinfection.design.LabFixtureBlock("incubator",BlockBehaviour.Properties.of().strength(3F).sound(SoundType.METAL).lightLevel(s->7).requiresCorrectToolForDrops().randomTicks()));
    public static final RegistryObject<Block> SURGERY_TABLE=BLOCKS.register("surgery_table",()->new dev.hui.enderinfection.design.LabFixtureBlock("surgery_table",BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).randomTicks()));
    public static final RegistryObject<Item> LAB_TILE_ITEM=ITEMS.register("lab_tile",()->new BlockItem(LAB_TILE.get(),new Item.Properties()));
    public static final RegistryObject<Item> LAB_TILE_DARK_ITEM=ITEMS.register("lab_tile_dark",()->new BlockItem(LAB_TILE_DARK.get(),new Item.Properties()));
    public static final RegistryObject<Item> LAB_TILE_LIGHT_ITEM=ITEMS.register("lab_tile_light",()->new BlockItem(LAB_TILE_LIGHT.get(),new Item.Properties()));
    public static final RegistryObject<Item> LAB_CABINET_ITEM=ITEMS.register("lab_cabinet",()->new BlockItem(LAB_CABINET.get(),new Item.Properties()));
    public static final RegistryObject<Item> INCUBATOR_ITEM=ITEMS.register("incubator",()->new BlockItem(INCUBATOR.get(),new Item.Properties()));
    public static final RegistryObject<Item> SURGERY_TABLE_ITEM=ITEMS.register("surgery_table",()->new BlockItem(SURGERY_TABLE.get(),new Item.Properties()));
    public static final RegistryObject<Item> SYRINGE=ITEMS.register("syringe",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> MUTATION_SYRINGE=ITEMS.register("mutation_syringe",()->new dev.hui.enderinfection.item.MutationSyringeItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RESONANT_ORE = registerOre("resonant_ore", Blocks.STONE, 1, 3);
    public static final RegistryObject<Item> DEEPSLATE_RESONANT_ORE = registerOre("deepslate_resonant_ore", Blocks.DEEPSLATE, 1, 3);
    public static final RegistryObject<Item> EMBER_CRYSTAL_ORE = registerOre("ember_crystal_ore", Blocks.NETHERRACK, 1, 3);
    public static final RegistryObject<Item> VOID_CRYSTAL_ORE = registerOre("void_crystal_ore", Blocks.END_STONE, 2, 5);
    public static final RegistryObject<Item> EMBER_CRYSTAL = ITEMS.register("ember_crystal", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VOID_CRYSTAL = ITEMS.register("void_crystal", () -> new Item(new Item.Properties()));

    private static RegistryObject<Item> registerOre(String name, Block substrate, int minXp, int maxXp) {
        var block = BLOCKS.register(name, () -> new DropExperienceBlock(
            BlockBehaviour.Properties.copy(substrate).strength(substrate == Blocks.DEEPSLATE ? 4.5F : 3F, 3F)
                .requiresCorrectToolForDrops(),
            UniformInt.of(minXp, maxXp)));
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static final Map<String,RegistryObject<Block>> TERRAIN=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Item>> TERRAIN_ITEMS=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Block>> PLANTS=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Item>> PLANT_ITEMS=new LinkedHashMap<>();
    static {
        for(String name:new String[]{"eroded_grass","eroded_soil","eroded_stone","eroded_sand","tainted_grass","mycelial_mat","necrotic_soil","crystal_crust","living_tissue","eroded_log","infected_log","eroded_leaves","infected_leaves"}){
            RegistryObject<Block> block=BLOCKS.register(name,()->name.endsWith("log")?new InfectedTimberBlock(BlockBehaviour.Properties.of().strength(2).sound(SoundType.WOOD)):
                name.endsWith("leaves")?new InfectedCanopyBlock(BlockBehaviour.Properties.of().strength(.2F).sound(SoundType.GRASS).noOcclusion()):
                name.startsWith("eroded_")&&!name.endsWith("leaves")?new ErodedGroundBlock(BlockBehaviour.Properties.of().strength(1.8F).sound(SoundType.GRAVEL)):
                new Block(BlockBehaviour.Properties.of().strength(name.endsWith("leaves")?.2F:1.8F).sound(name.endsWith("leaves")?SoundType.GRASS:name.contains("crust")?SoundType.AMETHYST:SoundType.GRAVEL).noOcclusion()));
            TERRAIN.put(name,block);TERRAIN_ITEMS.put(name,ITEMS.register(name,()->new BlockItem(block.get(),new Item.Properties())));
        }
        for(String plant:new String[]{"infected_grass","infected_flower","infected_fungus","infected_tendrils","eroded_vegetation","eroded_tall_vegetation"}){
            RegistryObject<Block> block=BLOCKS.register(plant,()->plant.equals("eroded_vegetation")?new dev.hui.enderinfection.block.ErodedVegetationBlock(BlockBehaviour.Properties.of().noCollission().instabreak().sound(SoundType.GRASS)):
                plant.equals("eroded_tall_vegetation")?new dev.hui.enderinfection.block.ErodedTallVegetationBlock(BlockBehaviour.Properties.of().noCollission().instabreak().sound(SoundType.GRASS)):
                new InfectedPlantBlock(BlockBehaviour.Properties.of().noCollission().instabreak().sound(SoundType.GRASS).lightLevel(state->2)));
            PLANTS.put(plant,block);PLANT_ITEMS.put(plant,ITEMS.register(plant,()->new BlockItem(block.get(),new Item.Properties())));
        }
        for(InfectedKind kind:InfectedKind.values()){
            RegistryObject<EntityType<InfectedMob>> type=ENTITIES.register("infected_"+kind.key,()->EntityType.Builder.<InfectedMob>of((t,level)->new InfectedMob(t,level,kind),MobCategory.MONSTER)
                .sized(kind.width,kind.height).clientTrackingRange(10).updateInterval(1).build(EnderInfection.MOD_ID+":infected_"+kind.key));
            TYPES.put(kind,type);
            EGGS.put(kind,ITEMS.register("infected_"+kind.key+"_spawn_egg",()->new ForgeSpawnEggItem(type,0x15121D,0xBB6CEF,new Item.Properties())));
        }
    }
    static {
        for(int stage=1;stage<=5;stage++){final int s=stage;STAGE_EGGS.put(stage,ITEMS.register("mother_stage"+stage,()->new StageEggItem(s)));}
    }
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("ender_infection",()->CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.ender_infection"))
        .icon(()->new ItemStack(NEST_ITEM.get()))
        .displayItems((params,output)->{
            dev.hui.enderinfection.design.DesignContent.ITEMS_BY_KEY.values().forEach(i->output.accept(i.get()));
            output.accept(dev.hui.enderinfection.item.InfectionItems.TISSUE.get());output.accept(dev.hui.enderinfection.item.InfectionItems.SHARD.get());output.accept(dev.hui.enderinfection.item.InfectionItems.HEART.get());
            STAGE_EGGS.values().forEach(egg->output.accept(egg.get()));
            EGGS.values().forEach(egg->output.accept(egg.get()));
            output.accept(NEST_ITEM.get());
            output.accept(dev.hui.enderinfection.rift.RiftContent.ANCHOR_ITEM.get());
            output.accept(dev.hui.enderinfection.rift.RiftContent.SUPPRESSOR.get());
            output.accept(RESONANT_ORE.get());
            output.accept(DEEPSLATE_RESONANT_ORE.get());
            output.accept(EMBER_CRYSTAL_ORE.get());
            output.accept(VOID_CRYSTAL_ORE.get());
            output.accept(EMBER_CRYSTAL.get());
            output.accept(VOID_CRYSTAL.get());
            output.accept(LAB_TILE_ITEM.get());output.accept(LAB_CHECKER_ITEM.get());output.accept(LAB_TILE_DARK_ITEM.get());output.accept(LAB_TILE_LIGHT_ITEM.get());
            output.accept(LAB_CABINET_ITEM.get());output.accept(INCUBATOR_ITEM.get());output.accept(SURGERY_TABLE_ITEM.get());
            output.accept(SYRINGE.get());output.accept(MUTATION_SYRINGE.get());
            PLANT_ITEMS.values().forEach(item->output.accept(item.get()));
            TERRAIN_ITEMS.values().forEach(item->output.accept(item.get()));
        }).build());
    private InfectedContent(){}
    public static void register(IEventBus bus){
        BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);ENTITIES.register(bus);ITEMS.register(bus);MOB_EFFECTS.register(bus);
        bus.addListener(InfectedContent::attributes);bus.addListener(InfectedContent::creative);TABS.register(bus);FEATURES.register(bus);PLACEMENTS.register(bus);
    }
    private static void attributes(EntityAttributeCreationEvent event){TYPES.forEach((kind,type)->event.put(type.get(),InfectedMob.attributes(kind).build()));}
    private static void creative(BuildCreativeModeTabContentsEvent event){
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(RESONANT_ORE);
            event.accept(DEEPSLATE_RESONANT_ORE);
            event.accept(EMBER_CRYSTAL_ORE);
            event.accept(VOID_CRYSTAL_ORE);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(EMBER_CRYSTAL);
            event.accept(VOID_CRYSTAL);
        }
        if(event.getTabKey()==CreativeModeTabs.SPAWN_EGGS)EGGS.values().forEach(egg->event.accept(egg));
        if(event.getTabKey()==CreativeModeTabs.NATURAL_BLOCKS){event.accept(NEST_ITEM);PLANT_ITEMS.values().forEach(item->event.accept(item));TERRAIN_ITEMS.values().forEach(item->event.accept(item));}
    }
    public static String terrainKey(BlockState state){for(var entry:TERRAIN.entrySet())if(state.is(entry.getValue().get()))return entry.getKey();return null;}
}
