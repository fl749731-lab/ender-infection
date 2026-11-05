package dev.hui.enderinfection.processing;

import dev.hui.enderinfection.api.institute.InstituteApi;
import dev.hui.enderinfection.api.institute.ProcessingRecipe;
import dev.hui.enderinfection.design.DesignContent;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Inputs and progress live on the server; a menu synchronizes only its own slots/data. */
public final class LabProcessingEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int progress, duration, status;
    private boolean powered, instituteBound;
    private int offsetX, offsetY, offsetZ;
    private String activeRecipe = "";
    private static final int[] INPUTS = {0, 1}, OUTPUT = {2};
    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch(index) { case 0 -> progress & 65535; case 1 -> duration & 65535; case 2 -> powered ? 1 : 0; case 3 -> deviceKey().equals("lab_centrifuge") ? 1 : 0; case 4 -> status; case 5 -> progress >>> 16; case 6 -> duration >>> 16; default -> 0; }; }
        @Override public void set(int index, int value) { /* Server-owned; clients use SimpleContainerData. */ }
        @Override public int getCount() { return 7; }
    };
    public LabProcessingEntity(BlockPos pos, BlockState state) { super(DesignContent.PROCESSING_ENTITY.get(), pos, state); }
    public String deviceKey() { return getBlockState().getBlock() instanceof LabProcessingBlock block ? block.key : ""; }
    public String deviceId() { return "ender_infection:" + deviceKey(); }
    public int progress() { return progress; }
    public boolean powered() { return powered; }
    public String activeRecipe() { return activeRecipe; }
    public boolean instituteBound() { return instituteBound; }
    public BlockPos instituteOffset() { return new BlockPos(offsetX, offsetY, offsetZ); }
    public java.util.Optional<BlockPos> boundOrigin() {
        return instituteBound && dev.hui.enderinfection.institute.InstituteOperations.validOffset(instituteOffset())
                ? java.util.Optional.of(worldPosition.subtract(instituteOffset())) : java.util.Optional.empty();
    }
    private static boolean itemIs(ItemStack stack, String id) { return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id); }
    private boolean supports(ProcessingRecipe recipe) { return recipe.devices().contains(deviceId()); }
    public boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot > 1) return false;
        return InstituteApi.processingRecipes().values().stream().filter(this::supports).anyMatch(recipe -> slot == 0
                ? itemIs(stack, recipe.inputId()) : recipe.reagentCount() > 0 && itemIs(stack, recipe.reagentId()));
    }
    private ProcessingRecipe matchingRecipe() {
        return InstituteApi.processingRecipes().values().stream().filter(this::supports)
                .filter(recipe -> itemIs(items.get(0), recipe.inputId()) && items.get(0).getCount() >= recipe.inputCount())
                .filter(recipe -> recipe.reagentCount() == 0 || itemIs(items.get(1), recipe.reagentId()) && items.get(1).getCount() >= recipe.reagentCount())
                .findFirst().orElse(null);
    }
    private ItemStack outputFor(ProcessingRecipe recipe) {
        return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(recipe.outputId()))
                .map(item -> new ItemStack(item, recipe.outputCount())).orElse(ItemStack.EMPTY);
    }
    private boolean fits(ItemStack output) {
        if (output.isEmpty()) return false;
        var existing = items.get(2);
        return existing.isEmpty() ? output.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize())
                : ItemStack.isSameItemSameTags(existing, output) && existing.getCount() + output.getCount() <= Math.min(getMaxStackSize(), existing.getMaxStackSize());
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, LabProcessingEntity processor) {
        if (!(level instanceof ServerLevel server) || processor.isRemoved()) return;
        processor.process(server);
    }
    private void process(ServerLevel server) {
        boolean oldPower = powered; int oldProgress = progress, oldStatus = status, oldDuration = duration; String oldRecipe = activeRecipe;
        powered = InstituteApi.isPowered(server, worldPosition);
        var recipe = matchingRecipe();
        if (recipe == null) { activeRecipe = ""; progress = 0; duration = 0; status = powered ? 1 : 0; }
        else {
            if (!activeRecipe.equals(recipe.id())) { activeRecipe = recipe.id(); progress = 0; }
            duration = recipe.ticks(); var output = outputFor(recipe);
            status = !powered ? 0 : !fits(output) ? 2 : 3;
            if (status == 3 && ++progress >= duration) {
                // No resources are consumed while power is absent or output is blocked.
                items.get(0).shrink(recipe.inputCount());
                if (recipe.reagentCount() > 0) items.get(1).shrink(recipe.reagentCount());
                if (items.get(2).isEmpty()) items.set(2, output); else items.get(2).grow(output.getCount());
                progress = 0;
                InstituteApi.recordProcess(server, worldPosition, recipe.id());
                setChanged();
            }
        }
        if (oldPower != powered || oldProgress != progress || oldStatus != status || oldDuration != duration || !oldRecipe.equals(activeRecipe)) setChanged();
    }
    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) { var result = ContainerHelper.removeItem(items, slot, count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && !isRemoved() && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5) <= 64; }
    @Override public void clearContent() { items.clear(); progress = duration = 0; activeRecipe = ""; setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return accepts(slot, stack); }
    @Override public int[] getSlotsForFace(Direction side) { return side == Direction.DOWN ? OUTPUT : INPUTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return side != Direction.DOWN && accepts(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == 2 && side == Direction.DOWN; }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new LabProcessingMenu(id, inventory, this); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); items = NonNullList.withSize(3, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag, items);
        progress = Math.max(0, tag.getInt("Progress")); duration = Math.max(0, tag.getInt("Duration")); activeRecipe = tag.getString("ActiveRecipe");
        instituteBound = tag.getBoolean("InstituteBound"); offsetX = tag.getInt("InstituteOffsetX"); offsetY = tag.getInt("InstituteOffsetY"); offsetZ = tag.getInt("InstituteOffsetZ");
        if (!dev.hui.enderinfection.institute.InstituteOperations.validOffset(instituteOffset())) instituteBound = false;
        powered = false; status = 0;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); ContainerHelper.saveAllItems(tag, items);
        tag.putInt("Progress",progress); tag.putInt("Duration",duration); tag.putString("ActiveRecipe",activeRecipe);
        tag.putBoolean("InstituteBound",instituteBound); tag.putInt("InstituteOffsetX",offsetX); tag.putInt("InstituteOffsetY",offsetY); tag.putInt("InstituteOffsetZ",offsetZ);
    }
    public CompoundTag saveInventoryTag() { return saveWithoutMetadata(); }
    @Override public CompoundTag getUpdateTag() { return new CompoundTag(); }
    @Override public void onLoad() {
        super.onLoad();
        if (instituteBound && level instanceof ServerLevel server) server.getServer().execute(() -> {
            if (!isRemoved() && server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) != null && server.getBlockEntity(worldPosition) == this)
                dev.hui.enderinfection.institute.InstituteOperations.registerBound(server, worldPosition, instituteOffset());
        });
    }
}
