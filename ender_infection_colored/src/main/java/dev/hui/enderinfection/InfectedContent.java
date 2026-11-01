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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
    public static final RegistryObject<Block> NEST=BLOCKS.register("infected_nest",()->new InfectedNestBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.AMETHYST).lightLevel(s->6).noOcclusion()));
    public static final RegistryObject<Item> NEST_ITEM=ITEMS.register("infected_nest",()->new BlockItem(NEST.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ExpansionNodeEntity>> NODE_ENTITY=BLOCK_ENTITIES.register("expansion_node",()->BlockEntityType.Builder.of(ExpansionNodeEntity::new,NEST.get()).build(null));
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
    private InfectedContent(){}
    public static void register(IEventBus bus){
        BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);ENTITIES.register(bus);ITEMS.register(bus);MOB_EFFECTS.register(bus);
        bus.addListener(InfectedContent::attributes);bus.addListener(InfectedContent::creative);
    }
    private static void attributes(EntityAttributeCreationEvent event){TYPES.forEach((kind,type)->event.put(type.get(),InfectedMob.attributes(kind).build()));}
    private static void creative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey()==CreativeModeTabs.SPAWN_EGGS)EGGS.values().forEach(egg->event.accept(egg));
        if(event.getTabKey()==CreativeModeTabs.NATURAL_BLOCKS){event.accept(NEST_ITEM);PLANT_ITEMS.values().forEach(item->event.accept(item));TERRAIN_ITEMS.values().forEach(item->event.accept(item));}
    }
    public static String terrainKey(BlockState state){for(var entry:TERRAIN.entrySet())if(state.is(entry.getValue().get()))return entry.getKey();return null;}
}
