package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.IndustryApi;
import dev.hui.resonantindustry.api.IndustryRecipe;
import dev.hui.resonantindustry.api.MachineDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class WorkshopContent {
    private static final String MOD = IndustryApi.MOD_ID;
    static final String PRESS = MOD + ":plate_press";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD);
    static final java.util.function.Supplier<MenuType<MeteringPumpMenu>> PUMP_MENU =
            MENUS.register("metering_pump", () -> new MenuType<>(MeteringPumpMenu::new, FeatureFlags.DEFAULT_FLAGS));
    static final java.util.function.Supplier<Block> PUMP =
            BLOCKS.register("metering_pump", () -> new MeteringPumpBlock(false));
    static final java.util.function.Supplier<Block> PRECISION_PUMP =
            BLOCKS.register("precision_metering_pump", () -> new MeteringPumpBlock(true));
    static final java.util.function.Supplier<BlockEntityType<MeteringPumpEntity>> PUMP_ENTITY =
            ENTITIES.register("metering_pump", () -> BlockEntityType.Builder.of(
                    MeteringPumpEntity::new, PUMP.get(), PRECISION_PUMP.get()).build(null));

    static void register(IEventBus bus) {
        registerPress();
        MineralContent.register(bus);
        ITEMS.register("plate_press_blueprint", () -> new BlueprintItem(PRESS));
        ITEMS.register("metering_pump", () -> new BlockItem(PUMP.get(), new Item.Properties()));
        ITEMS.register("precision_metering_pump", () -> new BlockItem(PRECISION_PUMP.get(), new Item.Properties()));
        for (String name : List.of("iron_plate", "copper_plate", "copper_wire", "basic_motor", "precision_pump_head")) {
            ITEMS.register(name, () -> new Item(new Item.Properties()));
        }
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PUMP_ENTITY.get(),
                        (pump, side) -> pump.energy()));
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                ITEMS.getEntries().forEach(item -> event.accept(item.get()));
            }
        });
    }

    private static void registerPress() {
        var cells = new LinkedHashMap<MachineDefinition.Cell, String>();
        for (int y = -1; y <= 1; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = -1; x <= 1; x++) cells.put(new MachineDefinition.Cell(x, y, z), "casing");
            }
        }
        cells.put(new MachineDefinition.Cell(0, 0, 0), "controller");
        cells.put(new MachineDefinition.Cell(-1, 0, 0), "energy_port");
        cells.put(new MachineDefinition.Cell(1, 0, 0), "item_input");
        cells.put(new MachineDefinition.Cell(0, 0, 1), "rotor");
        cells.put(new MachineDefinition.Cell(0, 1, 0), "window");
        cells.put(new MachineDefinition.Cell(0, -1, 1), "item_output");
        IndustryApi.registerMachine(new MachineDefinition(PRESS, "machine." + MOD + ".plate_press", 3, 3, 2, false, cells));
        for (String metal : List.of("iron", "copper")) {
            IndustryApi.registerRecipe(new IndustryRecipe(MOD + ":" + metal + "_plate", PRESS,
                    "minecraft:" + metal + "_ingot", 1, "", 0, MOD + ":" + metal + "_plate", 1,
                    "", 0, "", 0, 80, 12));
        }
    }

    private WorkshopContent() {}
}
