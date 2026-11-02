package dev.hui.enderinfection.design;

import dev.hui.enderinfection.*;
import dev.hui.enderinfection.item.InfectionItems;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import java.util.*;

public final class DesignContent {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,EnderInfection.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,EnderInfection.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,EnderInfection.MOD_ID);
    private static final DeferredRegister<EntityType<?>> MOBS=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,EnderInfection.MOD_ID);
    public static final Map<String,RegistryObject<Item>> ITEMS_BY_KEY=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Block>> BLOCKS_BY_KEY=new LinkedHashMap<>();
    public static final RegistryObject<EntityType<DesignProjectile>> PROJECTILE=MOBS.register("design_crystal",()->EntityType.Builder.<DesignProjectile>of(DesignProjectile::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(8).updateInterval(1).build("ender_infection:design_crystal"));
    public static final RegistryObject<Block> PART=BLOCKS.register("lab_fixture_part",()->new LabPartBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion()));
    static {
        for(String key:new String[]{"mycelium_bundle","spore_pod","chitin_plate","condensed_biomass","resonance_organ"})ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new Item(new Item.Properties())));
        for(String key:new String[]{"prism_spear","earthsplitter_hammer","crystal_spike_crossbow","resonance_staff"})ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new DesignWeapon(key)));
        for(String set:new String[]{"crystal_carapace","brood_heavy"})for(ArmorItem.Type slot:ArmorItem.Type.values()){
            String key=set+"_"+slot.getName();ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new DesignArmor(set,new Material(set),slot)));
        }
        for(String base:new String[]{"lab_tile_light","lab_tile_dark","lab_tile","lab_checker","lab_slab","lab_stair","lab_vent","lab_hatch","lab_door","lab_cabinet","incubator","surgery_table","lab_workbench","lab_window"})for(boolean eroded:new boolean[]{false,true}){
            String key=base+(eroded?"_eroded":"");
            if(!eroded&&Set.of("lab_tile_light","lab_tile_dark","lab_tile","lab_checker","lab_cabinet","incubator","surgery_table").contains(base))continue;
            var block=BLOCKS.register(key,()->{
                var p=BlockBehaviour.Properties.of().strength(3).sound(SoundType.METAL).noOcclusion();
                if(base.equals("lab_slab"))return new SlabBlock(p);
                if(base.equals("lab_stair"))return new StairBlock(()->InfectedContent.LAB_TILE_LIGHT.get().defaultBlockState(),p);
                if(isFixture(key))return new LabFixtureBlock(key,p.lightLevel(s->base.equals("incubator")?7:eroded?2:0));
                return new Block(p);
            });
            BLOCKS_BY_KEY.put(key,block);ITEMS_BY_KEY.put(key,ITEMS.register(key,()->new BlockItem(block.get(),new Item.Properties())));
        }
    }
    public static final RegistryObject<BlockEntityType<LabFixtureEntity>> FIXTURE_ENTITY=ENTITIES.register("lab_fixture",()->{
        var blocks=new ArrayList<Block>();BLOCKS_BY_KEY.forEach((k,v)->{if(isFixture(k))blocks.add(v.get());});
        blocks.add(InfectedContent.LAB_CABINET.get());blocks.add(InfectedContent.INCUBATOR.get());blocks.add(InfectedContent.SURGERY_TABLE.get());
        return BlockEntityType.Builder.of(LabFixtureEntity::new,blocks.toArray(Block[]::new)).build(null);
    });
    public static boolean isFixture(String key){return switch(key.replace("_eroded","")){case "lab_vent","lab_hatch","lab_door","lab_cabinet","incubator","surgery_table","lab_workbench","lab_window"->true;default->false;};}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MOBS.register(bus);}
    public record Material(String set) implements ArmorMaterial {
        public int getDurabilityForType(ArmorItem.Type t){return switch(t){case HELMET->13;case CHESTPLATE->16;case LEGGINGS->15;case BOOTS->11;}*(set.equals("brood_heavy")?35:22);}
        public int getDefenseForType(ArmorItem.Type t){return set.equals("brood_heavy")?switch(t){case HELMET->3;case CHESTPLATE->8;case LEGGINGS->6;case BOOTS->3;}:switch(t){case HELMET->2;case CHESTPLATE->6;case LEGGINGS->5;case BOOTS->2;};}
        public int getEnchantmentValue(){return 14;}
        public SoundEvent getEquipSound(){return SoundEvents.ARMOR_EQUIP_NETHERITE;}
        public Ingredient getRepairIngredient(){return Ingredient.of(ITEMS_BY_KEY.get("chitin_plate").get());}
        public String getName(){return "ender_infection:"+set;}
        public float getToughness(){return set.equals("brood_heavy")?3:1;}
        public float getKnockbackResistance(){return set.equals("brood_heavy")?.1F:0;}
    }
}
