package dev.hui.enderinfection.design;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.hui.enderinfection.*;
import dev.hui.enderinfection.item.InfectionItems;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
import java.util.*;

public final class DesignContent {
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS=DeferredRegister.create(net.minecraft.core.registries.Registries.ARMOR_MATERIAL,EnderInfection.MOD_ID);
    private static final Map<String,DeferredHolder<ArmorMaterial,ArmorMaterial>> MATERIAL_BY_KEY=new HashMap<>();
    static {for(String set:new String[]{"crystal_carapace","brood_heavy"})MATERIAL_BY_KEY.put(set,ARMOR_MATERIALS.register(set,()->new Material(set).value()));}

    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(BuiltInRegistries.ITEM,EnderInfection.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,EnderInfection.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,EnderInfection.MOD_ID);
    private static final DeferredRegister<EntityType<?>> MOBS=DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE,EnderInfection.MOD_ID);
    public static final Map<String,DeferredHolder<Item,Item>> ITEMS_BY_KEY=new LinkedHashMap<>();
    public static final Map<String,DeferredHolder<Block,Block>> BLOCKS_BY_KEY=new LinkedHashMap<>();
    public static final DeferredHolder<EntityType<?>,EntityType<DesignProjectile>> PROJECTILE=MOBS.register("design_crystal",()->EntityType.Builder.<DesignProjectile>of(DesignProjectile::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(8).updateInterval(1).build("ender_infection:design_crystal"));
    public static final DeferredHolder<Block,Block> PART=BLOCKS.register("lab_fixture_part",()->new LabPartBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion().dynamicShape()));
    static {
        for(String key:new String[]{"mycelium_bundle","spore_pod","chitin_plate","condensed_biomass","resonance_organ"})ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new Item(new Item.Properties())));
        for(String key:new String[]{"prism_spear","earthsplitter_hammer","crystal_spike_crossbow","resonance_staff"})ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new DesignWeapon(key)));
        for(String set:new String[]{"crystal_carapace","brood_heavy"})for(ArmorItem.Type slot:java.util.Arrays.stream(ArmorItem.Type.values()).filter(t -> t != ArmorItem.Type.BODY).toArray(ArmorItem.Type[]::new)){
            String key=set+"_"+slot.getName();ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new DesignArmor(set,MATERIAL_BY_KEY.get(set),slot,new Material(set).getDurabilityForType(slot))));
        }
        for(String base:new String[]{"lab_tile_light","lab_tile_dark","lab_tile","lab_checker","lab_slab","lab_stair","lab_vent","lab_hatch","lab_door","lab_cabinet","incubator","surgery_table","lab_workbench","lab_window"})for(boolean eroded:new boolean[]{false,true}){
            String key=base+(eroded?"_eroded":"");
            if(!eroded&&Set.of("lab_tile_light","lab_tile_dark","lab_tile","lab_checker","lab_cabinet","incubator","surgery_table").contains(base))continue;
            var block=BLOCKS.register(key,()->{
                var p=BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion();
                if(base.equals("lab_slab"))return new SlabBlock(p);
                if(base.equals("lab_stair"))return new StairBlock(InfectedContent.LAB_TILE_LIGHT.get().defaultBlockState(),p);
                if(isFixture(key))return new LabFixtureBlock(key,p.lightLevel(s->base.equals("incubator")?7:eroded?2:0));
                return new Block(p);
            });
            BLOCKS_BY_KEY.put(key,block);ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new BlockItem(block.get(),new Item.Properties())));
        }
    }
    static {
        for(String key:new String[]{"lab_monitor","lab_sample_rack","lab_shelf","lab_pipe","lab_light_panel","lab_chair","lab_sink","lab_storage_crate","lab_freezer","lab_floor_grate"}){
            DeferredHolder<Block,Block> block=BLOCKS.register(key,()->new LabDetailBlock(key,BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.METAL).noOcclusion().lightLevel(s->key.equals("lab_light_panel")?14:0)));
            BLOCKS_BY_KEY.put(key,block);ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new BlockItem(block.get(),new Item.Properties())));
        }
    }
    static {
        for(String key:new String[]{"lab_access_ladder","lab_lift"}){
            DeferredHolder<Block,Block> block=BLOCKS.register(key,()->key.equals("lab_lift")?new LabLiftBlock(BlockBehaviour.Properties.of().strength(4).sound(SoundType.METAL).lightLevel(s->5)):new LabAccessLadderBlock(BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion()));
            BLOCKS_BY_KEY.put(key,block);ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new BlockItem(block.get(),new Item.Properties())));
        }
    }
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<LabFixtureEntity>> FIXTURE_ENTITY=ENTITIES.register("lab_fixture",()->{
        var blocks=new ArrayList<Block>();BLOCKS_BY_KEY.forEach((k,v)->{if(isFixture(k))blocks.add(v.get());});
        blocks.add(InfectedContent.LAB_CABINET.get());blocks.add(InfectedContent.INCUBATOR.get());blocks.add(InfectedContent.SURGERY_TABLE.get());
        return BlockEntityType.Builder.of(LabFixtureEntity::new,blocks.toArray(Block[]::new)).build(null);
    });
    public static boolean isFixture(String key){return switch(key.replace("_eroded","")){case "lab_vent","lab_hatch","lab_door","lab_cabinet","incubator","surgery_table","lab_workbench","lab_window"->true;default->false;};}
    public static void register(IEventBus bus){ARMOR_MATERIALS.register(bus);BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MOBS.register(bus);}
    public record Material(String set) {
        public int getDurabilityForType(ArmorItem.Type t){return switch(t){case HELMET->13;case CHESTPLATE->16;case LEGGINGS->15;case BOOTS->11;case BODY->16;}*(set.equals("brood_heavy")?35:22);}
        public int getDefenseForType(ArmorItem.Type t){return set.equals("brood_heavy")?switch(t){case HELMET->3;case CHESTPLATE->8;case LEGGINGS->6;case BOOTS->3;case BODY->0;}:switch(t){case HELMET->2;case CHESTPLATE->6;case LEGGINGS->5;case BOOTS->2;case BODY->0;};}
        public int getEnchantmentValue(){return 14;}
        public net.minecraft.core.Holder<SoundEvent> getEquipSound(){return SoundEvents.ARMOR_EQUIP_NETHERITE;}
        public Ingredient getRepairIngredient(){return Ingredient.of(ITEMS_BY_KEY.get("chitin_plate").get());}
        public String getName(){return "ender_infection:"+set;}
        public float getToughness(){return set.equals("brood_heavy")?3:1;}
        public float getKnockbackResistance(){return set.equals("brood_heavy")?.1F:0;}
        ArmorMaterial value(){
            var defense=new EnumMap<ArmorItem.Type,Integer>(ArmorItem.Type.class);
            for(var type:ArmorItem.Type.values())defense.put(type,getDefenseForType(type));
            return new ArmorMaterial(defense,14,getEquipSound(),this::getRepairIngredient,List.of(new ArmorMaterial.Layer(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(EnderInfection.MOD_ID,set))),getToughness(),getKnockbackResistance());
        }
    }
}
