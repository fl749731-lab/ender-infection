package dev.hui.resonantindustry;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import dev.hui.resonantindustry.api.IndustryApi;

/** Values are populated by the loader's deferred registries before any world loads. */
public final class IndustryContent {
    public static final List<String> ROLES=List.of("controller","casing","window","rotor","energy_port","item_input","item_output","fluid_input","fluid_output","cable","fluid_pipe","battery","tank","pump","rotor_blades",
        "generator_visual","processor_lower_visual","processor_upper_visual","centrifuge_front_visual","centrifuge_back_visual","generator_rotor_visual","processor_rotor_visual","centrifuge_rotor_visual","processor_water_visual","processor_slurry_visual","energy_glow_visual","wire_core_visual","wire_arm_visual","wire_plug_visual","pipe_core_visual","pipe_arm_visual","pipe_plug_visual","hatch_energy_visual","hatch_item_input_visual","hatch_item_output_visual","hatch_fluid_input_visual","hatch_fluid_output_visual","hatch_controller_visual");
    public static BlockEntityType<IndustryNode> NODE;
    public static MenuType<IndustryMenu> MENU;
    public static Fluid SLURRY;
    public static Runnable OPEN_MANUAL=()->{};
    public static ResourceLocation id(String id){return new ResourceLocation(id.contains(":")?id:IndustryApi.MOD_ID+":"+id);}
    public static Block block(String key){return BuiltInRegistries.BLOCK.get(id(key));}
    public static Item item(String key){return BuiltInRegistries.ITEM.get(id(key));}
    public static String itemId(ItemStack stack){return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    private IndustryContent(){}
}
