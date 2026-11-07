package dev.hui.resonantindustry;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Server-owned state for a heater or stirrer, with one commit per finished batch. */
public final class ChemistryStation implements Container {
    public static final int SAMPLE = 0, LIQUID_REAGENT = 1;
    public static final int INGREDIENT = 0, FUEL = 1, PRODUCT = 2;
    public static final int DATA_COUNT = 11;
    private static final int AMBIENT = 20, MAX_HEAT = 450, FUEL_DURATION = 240;

    public enum Status { IDLE, INPUTS, FUEL, HEATING, SIGNAL, RUNNING, OUTPUT, COMPLETE, STOPPED, CONDENSER, RECEIVER, OXIDIZED }

    private final LabBenchEntity bench;
    private final Chemistry.Station kind;
    final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private Chemistry.Experiment experiment;
    private Status status = Status.IDLE;
    private int progress, temperature = AMBIENT, fuelTicks;
    private boolean running, metered;

    ChemistryStation(LabBenchEntity bench, Chemistry.Station kind) {
        this.bench = bench;
        this.kind = kind;
    }

    public static boolean accepts(int slot, ItemStack stack, Chemistry.Station kind) {
        if (slot == INGREDIENT && kind == Chemistry.Station.FILTER) return stack.is(IndustryContent.item("filter_paper"));
        if (slot == INGREDIENT) return !stack.isEmpty() && !LabVesselItem.vessel(stack);
        return slot == FUEL && kind == Chemistry.Station.HEATER && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL));
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return bench.stillValid(player); }
    @Override public void setChanged() { bench.setChanged(); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack, kind);
    }

    @Override public ItemStack removeItem(int slot, int count) {
        var removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }

    @Override public void setItem(int slot, ItemStack stack) {
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        if (stack.getCount() > limit) stack.setCount(limit);
        items.set(slot, stack);
        setChanged();
    }

    @Override public void clearContent() {
        items.clear();
        setChanged();
    }

    boolean start(boolean distillation) {
        if (running || distillation && kind != Chemistry.Station.HEATER) return false;
        metered = false;
        experiment = null;
        progress = 0;
        for (var candidate : Chemistry.Experiment.values()) {
            if (candidate.station != kind || candidate.distillation() != distillation || !matches(candidate)) continue;
            experiment = candidate;
            Status blocked = blockedOutput(candidate);
            running = blocked == null || candidate.receiverProduct.amount() > 0;
            status = blocked == null ? Status.RUNNING : blocked;
            return true;
        }
        status = Status.INPUTS;
        return true;
    }

    void stop() {
        running = false;
        metered = false;
        progress = 0;
        status = Status.STOPPED;
    }

    boolean metered() { return metered; }
    boolean meteredComplete() { return metered && status == Status.COMPLETE; }
    boolean running() { return running; }

    boolean beginMeteredMix(ItemStack sample, ItemStack reagent) {
        var recipe = Chemistry.Experiment.NEUTRALIZATION;
        if (kind != recipe.station || running || metered || !matches(recipe, sample, reagent)) return false;
        if (!LabFluids.read(bench.getItem(SAMPLE), bench.getLevel()).isEmpty()
                || !LabFluids.read(bench.getItem(LIQUID_REAGENT), bench.getLevel()).isEmpty()) return false;
        experiment = recipe;
        progress = 0;
        running = metered = true;
        status = Status.RUNNING;
        bench.replaceReactionVessel(SAMPLE, sample);
        bench.replaceReactionVessel(LIQUID_REAGENT, reagent);
        return true;
    }

    void meteredCollected() {
        metered = false;
        bench.markReactionDirty();
    }

    void tick() {
        boolean heater = kind == Chemistry.Station.HEATER;
        if (running && !matches(experiment)) {
            stop();
            status = Status.INPUTS;
            bench.markReactionDirty();
        }
        Status blocked = running ? blockedOutput(experiment) : null;
        boolean room = running && blocked == null;
        if (heater && (fuelTicks > 0 || temperature > AMBIENT || room)) {
            if (room && fuelTicks == 0 && temperature < experiment.temperature && canPlaceItem(FUEL, getItem(FUEL))) {
                removeItem(FUEL, 1);
                fuelTicks = FUEL_DURATION;
            }
            if (fuelTicks > 0) {
                fuelTicks--;
                temperature = Math.min(MAX_HEAT, temperature + 4);
            } else {
                temperature = Math.max(AMBIENT, temperature - 2);
            }
            bench.markReactionDirty();
        }
        if (running) {
            if (blocked != null) status = blocked;
            else if (kind == Chemistry.Station.STIRRER && (blocked = stirringHold(true)) != null) status = blocked;
            else if (heater && temperature < experiment.temperature) status = fuelTicks > 0 ? Status.HEATING : Status.FUEL;
            else {
                status = Status.RUNNING;
                if (progress + 1 >= experiment.duration) complete(experiment);
                else progress++;
            }
            bench.markReactionDirty();
        }
        boolean active = heater ? fuelTicks > 0 : running && status == Status.RUNNING;
        var state = bench.getBlockState();
        if (state.getValue(LabBenchBlock.ACTIVE) != active) {
            bench.getLevel().setBlock(bench.getBlockPos(), state.setValue(LabBenchBlock.ACTIVE, active), 3);
        }
    }

    private Status stirringHold(boolean consume) {
        if (metered) return LabPlatform.meteredHold(bench, consume);
        return bench.getLevel().hasNeighborSignal(bench.getBlockPos()) ? null : Status.SIGNAL;
    }

    private boolean matches(Chemistry.Experiment recipe) {
        return matches(recipe, bench.getItem(SAMPLE), bench.getItem(LIQUID_REAGENT));
    }

    private boolean matches(Chemistry.Experiment recipe, ItemStack sample, ItemStack reagent) {
        if (!LabVesselItem.vessel(sample)) return false;
        if (kind != Chemistry.Station.FILTER && !sample.is(IndustryContent.item("beaker"))
                && !sample.is(IndustryContent.item("test_tube"))) return false;
        if (recipe.product.amount() > LabVesselItem.capacity(sample)) return false;
        if (!recipe.sample.matches(LabFluids.read(sample, bench.getLevel()), true)) return false;
        if (recipe.reagent.amount() > 0 && !recipe.reagent.matches(LabFluids.read(reagent, bench.getLevel()), false)) return false;
        var solid = getItem(INGREDIENT);
        return recipe.ingredient.isEmpty() ? solid.isEmpty()
                : !solid.isEmpty() && solid.is(IndustryContent.item(recipe.ingredient));
    }

    private LabBenchEntity receiver(Chemistry.Experiment recipe) {
        return recipe.distillation() ? bench.connectedCondenser() : bench;
    }

    private static int receiverSlot(Chemistry.Experiment recipe) {
        return recipe.distillation() ? SAMPLE : LIQUID_REAGENT;
    }

    private Status blockedOutput(Chemistry.Experiment recipe) {
        if (!recipe.solidProduct.isEmpty()) {
            var output = getItem(PRODUCT);
            if (!output.isEmpty() && (!output.is(IndustryContent.item(recipe.solidProduct))
                    || output.getCount() >= output.getMaxStackSize())) return Status.OUTPUT;
        }
        if (recipe.receiverProduct.amount() == 0) return null;
        var destination = receiver(recipe);
        if (destination == null) return Status.CONDENSER;
        var port = LabFluids.view(destination.getItem(receiverSlot(recipe)), bench.getLevel(), () -> {});
        return port.fill(recipe.receiverProduct.stack(), SIMULATE) == recipe.receiverProduct.amount() ? null : Status.RECEIVER;
    }

    private void complete(Chemistry.Experiment recipe) {
        // Stage the receiving vessel before consuming the charge or the filter paper.
        LabBenchEntity destination = null;
        ItemStack recovered = ItemStack.EMPTY;
        if (recipe.receiverProduct.amount() > 0) {
            destination = receiver(recipe);
            if (destination == null) {
                progress = recipe.duration - 1;
                status = Status.CONDENSER;
                return;
            }
            recovered = destination.getItem(receiverSlot(recipe)).copy();
            var port = LabFluids.view(recovered, bench.getLevel(), () -> {});
            if (port.fill(recipe.receiverProduct.stack(), EXECUTE) != recipe.receiverProduct.amount()) {
                progress = recipe.duration - 1;
                status = Status.RECEIVER;
                return;
            }
        }
        var sample = bench.getItem(SAMPLE).copy();
        LabFluids.write(sample, recipe.product.stack(), bench.getLevel());
        var reagent = bench.getItem(LIQUID_REAGENT).copy();
        if (recipe.reagent.amount() > 0) {
            var remainder = LabFluids.read(reagent, bench.getLevel());
            remainder.shrink(recipe.reagent.amount());
            LabFluids.write(reagent, remainder, bench.getLevel());
        }
        var ingredient = getItem(INGREDIENT).copy();
        if (!recipe.ingredient.isEmpty()) ingredient.shrink(1);
        var output = getItem(PRODUCT).copy();
        if (!recipe.solidProduct.isEmpty()) {
            if (output.isEmpty()) output = new ItemStack(IndustryContent.item(recipe.solidProduct));
            else output.grow(1);
        }

        var product = output;
        var receivingBench = destination;
        var receivingVessel = recovered;
        Runnable settle = () -> {
            items.set(INGREDIENT, ingredient);
            items.set(PRODUCT, product);
            running = false;
            progress = recipe.duration;
            status = Status.COMPLETE;
            bench.replaceReactionVessel(SAMPLE, sample);
            bench.replaceReactionVessel(LIQUID_REAGENT, reagent);
            if (receivingBench != null) receivingBench.replaceReactionVessel(receiverSlot(recipe), receivingVessel);
        };
        if (recipe.distillation()) LabPlatform.settleDistillation(bench, receivingBench, settle);
        else settle.run();
        bench.setChanged();
        if (destination != null && destination != bench) destination.setChanged();
    }

    void save(CompoundTag tag) {
        if (experiment != null) tag.putString("Experiment", experiment.id());
        tag.putBoolean("Running", running);
        if (metered) tag.putBoolean("Metered", true);
        tag.putInt("Progress", progress);
        tag.putInt("Temperature", temperature);
        tag.putInt("FuelTicks", fuelTicks);
        tag.putString("Status", status.name());
    }

    void load(CompoundTag tag) {
        experiment = Chemistry.Experiment.read(tag.getString("Experiment"));
        if (experiment != null && experiment.station != kind) experiment = null;
        progress = experiment == null ? 0 : Math.max(0, Math.min(tag.getInt("Progress"), experiment.duration));
        running = experiment != null && tag.getBoolean("Running") && progress < experiment.duration;
        temperature = Math.max(AMBIENT, Math.min(MAX_HEAT, tag.getInt("Temperature")));
        fuelTicks = Math.max(0, Math.min(FUEL_DURATION, tag.getInt("FuelTicks")));
        try { status = Status.valueOf(tag.getString("Status")); }
        catch (IllegalArgumentException ignored) { status = Status.IDLE; }
        metered = experiment == Chemistry.Experiment.NEUTRALIZATION && tag.getBoolean("Metered")
                && (running || status == Status.COMPLETE);
    }

    ContainerData data() {
        return new ContainerData() {
            @Override public int getCount() { return DATA_COUNT; }
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> kind.ordinal();
                    case 1 -> status.ordinal();
                    case 2 -> temperature;
                    case 3 -> progress;
                    case 4 -> experiment == null ? 0 : experiment.duration;
                    case 5 -> experiment == null ? 0 : experiment.ordinal() + 1;
                    case 6 -> fuelTicks;
                    case 7 -> stirringHold(false) == null ? 1 : 0;
                    case 8 -> experiment == null || status != Status.COMPLETE ? 0 : experiment.evaporated();
                    case 9 -> experiment == null || status != Status.COMPLETE ? 0 : experiment.receiverProduct.amount();
                    case 10 -> kind == Chemistry.Station.HEATER && bench.connectedCondenser() != null ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
        };
    }
}
