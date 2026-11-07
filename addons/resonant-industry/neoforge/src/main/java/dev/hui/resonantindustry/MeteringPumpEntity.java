package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.FluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

final class MeteringPumpEntity extends BlockEntity implements MenuProvider {
    private static final int CAPACITY = 256, FEED_COST = 16, STIR_COST = 4, COLLECT_COST = 8;
    private static final int AGE_PER_STAGE = 600, MAX_AGE = 3 * AGE_PER_STAGE;
    private static final String WEAR_COMPONENT = "WorkshopWear";
    private static final int[] DOSES = {5, 10, 25, 50, 100, 250};
    private static final int[] PRECISION_DOSES = {1, 2, 5, 10, 25, 50, 100, 250};
    private static final Chemistry.Experiment MIX = Chemistry.Experiment.NEUTRALIZATION;

    private enum Status {
        SIGNAL, POWER, LAYOUT, INPUT, VESSEL, OUTPUT, DOSED, MIXING, BUSY, OXIDIZED, READY
    }

    private final Buffer buffer = new Buffer();
    private boolean automatic, dosed, sealed;
    private int doseIndex, oxidationAge;

    MeteringPumpEntity(BlockPos pos, BlockState state) {
        super(WorkshopContent.PUMP_ENTITY.get(), pos, state);
        doseIndex = precision() ? 4 : 2;
    }

    EnergyStorage energy() { return buffer; }
    private boolean precision() { return ((MeteringPumpBlock) getBlockState().getBlock()).precision(); }
    private int[] doses() { return precision() ? PRECISION_DOSES : DOSES; }
    private Direction facing() { return getBlockState().getValue(MeteringPumpBlock.FACING); }
    private boolean signaled() { return level.hasNeighborSignal(worldPosition); }
    private int oxidation() { return oxidationAge / AGE_PER_STAGE; }
    private boolean jammed() { return oxidationAge == MAX_AGE; }
    private int feedCost() { return FEED_COST + 4 * oxidation(); }
    private int stirCost() { return STIR_COST + oxidation(); }
    private int collectCost() { return COLLECT_COST + 2 * oxidation(); }

    boolean maintain(Player player, ItemStack part) {
        if (part.is(IndustryContent.item("vulcanized_seal")) && !sealed) {
            sealed = true;
        } else if (part.is(IndustryContent.item("precision_pump_head")) && oxidationAge > 0) {
            oxidationAge = 0;
            syncWear();
        } else {
            inspect(player);
            return false;
        }
        part.consume(1, player);
        setChanged();
        inspect(player);
        return true;
    }

    void toggleProgram(Player player) {
        setProgram(player, !automatic);
    }

    boolean setProgram(Player player, boolean enabled) {
        if (automatic == enabled) return false;
        var bench = bench();
        if (automatic && bench != null && bench.chemistry() != null && bench.chemistry().metered()) {
            display(player, Status.BUSY);
            return false;
        }
        automatic = enabled;
        configurationChanged(player);
        return true;
    }

    boolean cycleDose(Player player) {
        if (!automatic) {
            doseIndex = (doseIndex + 1) % doses().length;
            configurationChanged(player);
            return true;
        } else {
            inspect(player);
            return false;
        }
    }

    private void configurationChanged(Player player) {
        // Reconfiguration while a pulse is high leaves that pulse consumed.
        if (signaled()) dosed = true;
        setChanged();
        inspect(player);
    }

    void inspect(Player player) { display(player, operate(false)); }

    static Component statusText(int id, boolean compact) {
        var status = Status.values()[Math.clamp(id, 0, Status.values().length - 1)];
        String key = status.name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable((compact ? "workshop.resonant_industry.gui.reason." : "workshop.resonant_industry.status.") + key);
    }

    private void display(Player player, Status status) {
        var mode = automatic ? Component.translatable("workshop.resonant_industry.mix")
                : Component.translatable("workshop.resonant_industry.dose", doses()[doseIndex]);
        var reason = statusText(status.ordinal(), false);
        var wear = Component.translatable("workshop.resonant_industry.wear." + oxidation());
        var seal = Component.translatable("workshop.resonant_industry.sealed." + sealed);
        player.displayClientMessage(Component.translatable("workshop.resonant_industry.info",
                mode, buffer.getEnergyStored(), CAPACITY, wear, seal, reason), true);
    }

    void tick() {
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), 20) == 0) tickWear();
        if (!signaled() && dosed) {
            dosed = false;
            setChanged();
        }
        operate(true);
    }

    private void tickWear() {
        if (!sealed && !jammed()) {
            oxidationAge = Math.min(MAX_AGE, oxidationAge + wearRate(tank(facing().getOpposite())));
            setChanged();
        }
        syncWear();
    }

    private static int wearRate(FluidAccess stock) {
        return stock != null && stock.amount() > 0 && stock.fluid().equals(fluidId(MIX.sample)) ? 4 : 1;
    }

    private void syncWear() {
        var state = getBlockState();
        if (level != null && !level.isClientSide && state.getValue(MeteringPumpBlock.OXIDATION) != oxidation()) {
            level.setBlock(worldPosition, state.setValue(MeteringPumpBlock.OXIDATION, oxidation()), Block.UPDATE_CLIENTS);
        }
    }

    private Status operate(boolean execute) {
        if (jammed()) return Status.OXIDIZED;
        if (!signaled()) return Status.SIGNAL;
        var bench = bench();
        var input = tank(facing().getOpposite());
        if (bench == null || input == null) return Status.LAYOUT;
        if (!automatic) return dose(bench, input, execute);

        var reaction = bench.chemistry();
        var reagent = tank(facing().getCounterClockWise());
        var output = tank(facing().getClockWise());
        if (reaction == null || !bench.getBlockState().is(IndustryContent.block("magnetic_stirrer"))
                || reagent == null || output == null) return Status.LAYOUT;
        if (!outputReady(output)) return Status.OUTPUT;
        if (reaction.meteredComplete()) return collect(bench, reaction, output, execute);
        if (reaction.running()) {
            if (!reaction.metered()) return Status.BUSY;
            return buffer.getEnergyStored() >= stirCost() ? Status.MIXING : Status.POWER;
        }
        if (buffer.getEnergyStored() < feedCost()) return Status.POWER;
        if (!available(input, MIX.sample) || !available(reagent, MIX.reagent)) return Status.INPUT;
        var sample = chargedVessel(bench.getItem(ChemistryStation.SAMPLE), MIX.sample, true);
        var additive = chargedVessel(bench.getItem(ChemistryStation.LIQUID_REAGENT), MIX.reagent, false);
        if (sample.isEmpty() || additive.isEmpty() || !reaction.getItem(ChemistryStation.INGREDIENT).isEmpty()) {
            return Status.VESSEL;
        }
        if (execute && reaction.beginMeteredMix(sample, additive)) {
            input.drain(MIX.sample.amount(), false);
            reagent.drain(MIX.reagent.amount(), false);
            buffer.spend(feedCost());
            bench.setChanged();
        }
        return Status.MIXING;
    }

    private Status dose(LabBenchEntity bench, FluidAccess input, boolean execute) {
        if (dosed) return Status.DOSED;
        if (bench.chemistry() != null && (bench.chemistry().running() || bench.chemistry().metered())) return Status.BUSY;
        if (buffer.getEnergyStored() < feedCost()) return Status.POWER;
        int amount = doses()[doseIndex];
        if (input.amount() < amount) return Status.INPUT;
        var tank = IndustryNode.at(level, worldPosition.relative(facing().getOpposite()));
        var source = Platform.fluidView(tank, null);
        var target = bench.fluidView();
        var sample = source.drain(amount, SIMULATE);
        if (target.fill(sample, SIMULATE) != amount) {
            return Status.VESSEL;
        }
        if (!execute) return Status.READY;
        if (LabFluids.transfer(source, target, amount) == amount) {
            dosed = true;
            buffer.spend(feedCost());
            return Status.DOSED;
        }
        return Status.VESSEL;
    }

    private Status collect(LabBenchEntity bench, ChemistryStation reaction, FluidAccess output, boolean execute) {
        var vessel = bench.getItem(ChemistryStation.SAMPLE);
        var product = LabFluids.read(vessel, level);
        if (!MIX.product.matches(product, true)) return Status.VESSEL;
        if (buffer.getEnergyStored() < collectCost()) return Status.POWER;
        if (execute) {
            output.fill(fluidId(MIX.product), product.getAmount(), false);
            LabFluids.write(vessel, FluidStack.EMPTY, level);
            buffer.spend(collectCost());
            reaction.meteredCollected();
            bench.setChanged();
        }
        return Status.MIXING;
    }

    static ChemistryStation.Status mixingHold(LabBenchEntity bench, boolean consume) {
        var level = bench.getLevel();
        if (level == null) return ChemistryStation.Status.SIGNAL;
        var facing = bench.getBlockState().getValue(LabBenchBlock.FACING);
        var pos = bench.getBlockPos().relative(facing.getOpposite());
        if (!IndustryNode.loaded(level, pos) || !(level.getBlockEntity(pos) instanceof MeteringPumpEntity pump)
                || !pump.automatic || pump.bench() != bench) {
            return ChemistryStation.Status.SIGNAL;
        }
        if (pump.jammed()) return ChemistryStation.Status.OXIDIZED;
        if (!pump.signaled() || pump.buffer.getEnergyStored() < pump.stirCost()) return ChemistryStation.Status.SIGNAL;
        if (pump.tank(facing.getOpposite()) == null || pump.tank(facing.getCounterClockWise()) == null
                || !outputReady(pump.tank(facing.getClockWise()))) return ChemistryStation.Status.RECEIVER;
        if (consume) pump.buffer.spend(pump.stirCost());
        return null;
    }

    private boolean sameChunk(BlockPos pos) {
        return ChunkPos.asLong(pos) == ChunkPos.asLong(worldPosition);
    }

    private LabBenchEntity bench() {
        var pos = worldPosition.relative(facing());
        return sameChunk(pos) && level.getBlockEntity(pos) instanceof LabBenchEntity bench
                && !bench.isRemoved() && bench.getBlockState().getValue(LabBenchBlock.FACING) == facing() ? bench : null;
    }

    private FluidAccess tank(Direction side) {
        var pos = worldPosition.relative(side);
        if (!sameChunk(pos)) return null;
        var node = IndustryNode.at(level, pos);
        return node != null && node.role().equals("tank") ? node.fluidAccess(null) : null;
    }

    private static String fluidId(Chemistry.Portion portion) { return IndustryContent.id(portion.fluid()).toString(); }
    private static boolean available(FluidAccess stock, Chemistry.Portion portion) {
        return stock.fluid().equals(fluidId(portion)) && stock.amount() >= portion.amount();
    }

    private static boolean outputReady(FluidAccess output) {
        return output != null && output.fill(fluidId(MIX.product), MIX.product.amount(), true) == MIX.product.amount();
    }

    private ItemStack chargedVessel(ItemStack vessel, Chemistry.Portion portion, boolean sample) {
        if (!LabVesselItem.vessel(vessel) || !LabFluids.read(vessel, level).isEmpty()
                || LabVesselItem.capacity(vessel) < portion.amount()) return ItemStack.EMPTY;
        if (sample && !vessel.is(IndustryContent.item("test_tube")) && !vessel.is(IndustryContent.item("beaker"))) {
            return ItemStack.EMPTY;
        }
        var filled = vessel.copy();
        LabFluids.write(filled, portion.stack(), level);
        return filled;
    }

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }

    @Override public MeteringPumpMenu createMenu(int id, Inventory inventory, Player player) {
        return new MeteringPumpMenu(id, inventory, this);
    }

    boolean stillValid(Player player) {
        return level != null && player.level() == level && !isRemoved() && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    boolean openBench(Player player) {
        var bench = bench();
        if (bench == null || !bench.stillValid(player)) return false;
        player.openMenu(bench);
        return true;
    }

    void syncPanel(MeteringPumpMenu menu) {
        var stock = tank(facing().getOpposite());
        var bench = bench();
        int rate = wearRate(stock);
        int remaining = sealed || jammed() ? 0 : Math.ceilDiv((oxidation() + 1) * AGE_PER_STAGE - oxidationAge, rate);
        menu.syncPump(automatic, signaled(), sealed, bench != null, operate(false).ordinal(), buffer.getEnergyStored(),
                CAPACITY, doses()[doseIndex], oxidation(), 100 * oxidationAge / MAX_AGE, remaining, rate);
        menu.syncTank(MeteringPumpMenu.STOCK, stock);
        menu.syncTank(MeteringPumpMenu.REAGENT, automatic ? tank(facing().getCounterClockWise()) : null);
        if (automatic) {
            menu.syncTank(MeteringPumpMenu.OUTPUT, tank(facing().getClockWise()));
            var chemistry = bench == null ? null : bench.chemistry();
            int progress = chemistry != null && chemistry.metered() ? chemistry.data().get(3) : 0;
            menu.syncReaction(progress, MIX.duration, bench == null ? ItemStack.EMPTY : bench.getItem(ChemistryStation.SAMPLE),
                    bench == null ? ItemStack.EMPTY : bench.getItem(ChemistryStation.LIQUID_REAGENT));
        } else {
            var sample = bench == null ? ItemStack.EMPTY : bench.getItem(bench.data.get(0));
            var liquid = LabFluids.read(sample, level);
            menu.syncGauge(MeteringPumpMenu.OUTPUT, liquid.getFluid(), liquid.getAmount(), LabVesselItem.capacity(sample));
            menu.syncReaction(0, 0, sample, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", buffer.getEnergyStored());
        tag.putBoolean("Automatic", automatic);
        tag.putBoolean("Dosed", dosed);
        tag.putInt("Dose", doses()[doseIndex]);
        saveWear(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.restore(tag.getInt("Energy"));
        automatic = tag.getBoolean("Automatic");
        dosed = tag.getBoolean("Dosed");
        readWear(tag);
        int amount = tag.getInt("Dose");
        for (int i = 0; i < doses().length; i++) if (doses()[i] == amount) doseIndex = i;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!sealed && oxidationAge == 0) return;
        var wear = new CompoundTag();
        saveWear(wear);
        var tag = new CompoundTag();
        tag.put(WEAR_COMPONENT, wear);
        components.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        var data = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains(WEAR_COMPONENT)) readWear(data.getCompound(WEAR_COMPONENT));
    }

    private void saveWear(CompoundTag tag) {
        tag.putInt("OxidationAge", oxidationAge);
        tag.putBoolean("Sealed", sealed);
    }

    private void readWear(CompoundTag tag) {
        oxidationAge = Math.clamp(tag.getInt("OxidationAge"), 0, MAX_AGE);
        sealed = tag.getBoolean("Sealed");
    }

    private final class Buffer extends EnergyStorage {
        private Buffer() { super(CAPACITY, 128, 0); }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (isRemoved() || amount <= 0) return 0;
            int accepted = super.receiveEnergy(amount, simulate);
            if (accepted > 0 && !simulate) setChanged();
            return accepted;
        }

        private void spend(int amount) {
            energy -= amount;
            setChanged();
        }

        private void restore(int amount) { energy = Math.max(0, Math.min(CAPACITY, amount)); }
    }
}
