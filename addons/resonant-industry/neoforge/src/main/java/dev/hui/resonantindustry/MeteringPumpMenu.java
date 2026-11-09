package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.FluidAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class MeteringPumpMenu extends AbstractContainerMenu {
    public static final int SET_DOSE_MODE = 0, SET_AUTO_MODE = 1, CYCLE_DOSE = 2, OPEN_BENCH = 3,
            INSTALL_SEAL = 4, REPLACE_HEAD = 5;
    public static final int STOCK = 0, REAGENT = 1, OUTPUT = 2;
    private static final int FLAGS = 0, STATUS = 1, ENERGY = 2, CAPACITY = 3, DOSE = 4,
            OXIDATION = 5, WEAR = 6, SECONDS = 7, RATE = 8, PROGRESS = 9, DURATION = 10, GAUGES = 11;
    private static final int AUTOMATIC = 1, SIGNAL = 2, SEALED = 4, BENCH = 8;
    private static final int GAUGE_FIELDS = 4;

    public record Gauge(Fluid fluid, int amount, int capacity) {
        public boolean connected() { return capacity > 0; }
    }

    private final MeteringPumpEntity pump;
    private final SimpleContainerData values = new SimpleContainerData(GAUGES + 3 * GAUGE_FIELDS);
    private final SimpleContainer vessels = new SimpleContainer(2);
    private boolean showVessels = true;

    public MeteringPumpMenu(int id, Inventory inventory) { this(id, inventory, null); }

    MeteringPumpMenu(int id, Inventory inventory, MeteringPumpEntity pump) {
        super(WorkshopContent.PUMP_MENU.get(), id);
        this.pump = pump;
        addReadOnlyVessel(0, 102);
        addReadOnlyVessel(1, 127);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 42 + col * 18, 146 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 42 + col * 18, 204));
        addDataSlots(values);
        refresh();
    }

    private void addReadOnlyVessel(int index, int x) {
        addSlot(new Slot(vessels, index, x, 61) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return showVessels && (index == 0 || automatic()); }
        });
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != vessels && super.canTakeItemForPickAll(stack, slot);
    }

    @Override public boolean stillValid(Player player) { return pump == null || pump.stillValid(player); }

    @Override public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    private void refresh() {
        if (pump != null && !pump.isRemoved()) pump.syncPanel(this);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (pump == null || !stillValid(player)) return false;
        return switch (id) {
            case SET_DOSE_MODE -> pump.setProgram(player, false);
            case SET_AUTO_MODE -> pump.setProgram(player, true);
            case CYCLE_DOSE -> pump.cycleDose(player);
            case OPEN_BENCH -> pump.openBench(player);
            case INSTALL_SEAL -> applyPart(player, "vulcanized_seal");
            case REPLACE_HEAD -> applyPart(player, "precision_pump_head");
            default -> false;
        };
    }

    private boolean applyPart(Player player, String id) {
        var part = IndustryContent.item(id);
        for (var stack : player.getInventory().items) {
            if (stack.is(part)) return pump.maintain(player, stack);
        }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 2 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = getSlot(index);
        var stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var original = stack.copy();
        boolean moved = index < 29 ? moveItemStackTo(stack, 29, 38, false) : moveItemStackTo(stack, 2, 29, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    void syncPump(boolean automatic, boolean signal, boolean sealed, boolean bench, int status,
                  int energy, int capacity, int dose, int oxidation, int wear, int seconds, int rate) {
        int flags = (automatic ? AUTOMATIC : 0) | (signal ? SIGNAL : 0) | (sealed ? SEALED : 0) | (bench ? BENCH : 0);
        values.set(FLAGS, flags);
        values.set(STATUS, status);
        values.set(ENERGY, energy);
        values.set(CAPACITY, capacity);
        values.set(DOSE, dose);
        values.set(OXIDATION, oxidation);
        values.set(WEAR, wear);
        values.set(SECONDS, seconds);
        values.set(RATE, rate);
    }

    void syncReaction(int progress, int duration, ItemStack sample, ItemStack reagent) {
        values.set(PROGRESS, progress);
        values.set(DURATION, duration);
        syncVessel(0, sample);
        syncVessel(1, reagent);
    }

    private void syncVessel(int index, ItemStack stack) {
        if (!ItemStack.matches(vessels.getItem(index), stack)) vessels.setItem(index, stack.copy());
    }

    void syncTank(int slot, FluidAccess tank) {
        Fluid fluid = tank == null || tank.amount() == 0 ? Fluids.EMPTY
                : BuiltInRegistries.FLUID.get(IndustryContent.id(tank.fluid()));
        syncGauge(slot, fluid, tank == null ? 0 : tank.amount(), tank == null ? 0 : tank.capacity());
    }

    void syncGauge(int slot, Fluid fluid, int amount, int capacity) {
        int base = GAUGES + slot * GAUGE_FIELDS;
        int id = BuiltInRegistries.FLUID.getId(fluid);
        // Vanilla menu data carries signed shorts, including in large modpacks.
        values.set(base, id & 0xffff);
        values.set(base + 1, id >>> 16);
        values.set(base + 2, amount);
        values.set(base + 3, capacity);
    }

    public Gauge gauge(int slot) {
        int base = GAUGES + slot * GAUGE_FIELDS;
        int id = (values.get(base) & 0xffff) | (values.get(base + 1) << 16);
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        return new Gauge(fluid == null ? Fluids.EMPTY : fluid, values.get(base + 2), values.get(base + 3));
    }

    public void showVessels(boolean show) { showVessels = show; }
    public boolean automatic() { return (values.get(FLAGS) & AUTOMATIC) != 0; }
    public boolean signaled() { return (values.get(FLAGS) & SIGNAL) != 0; }
    public boolean sealed() { return (values.get(FLAGS) & SEALED) != 0; }
    public boolean benchConnected() { return (values.get(FLAGS) & BENCH) != 0; }
    public int energy() { return values.get(ENERGY); }
    public int capacity() { return values.get(CAPACITY); }
    public int dose() { return values.get(DOSE); }
    public int oxidation() { return values.get(OXIDATION); }
    public int wear() { return values.get(WEAR); }
    public int seconds() { return values.get(SECONDS); }
    public int rate() { return values.get(RATE); }
    public int progress() { return values.get(PROGRESS); }
    public int duration() { return values.get(DURATION); }
    public Component statusText(boolean compact) { return MeteringPumpEntity.statusText(values.get(STATUS), compact); }

    public boolean hasPart(String id) {
        var part = IndustryContent.item(id);
        return slots.subList(2, slots.size()).stream().anyMatch(slot -> slot.getItem().is(part));
    }
}
