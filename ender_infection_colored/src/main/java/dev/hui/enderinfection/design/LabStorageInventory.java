package dev.hui.enderinfection.design;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The server owns all contents. Only generated structure NBT supplies a loot table. */
public abstract class LabStorageInventory extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override protected void onOpen(Level level, BlockPos pos, BlockState state) {
            setOpen(level, pos, true);
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, .5F, .95F);
        }
        @Override protected void onClose(Level level, BlockPos pos, BlockState state) {
            setOpen(level, pos, false);
            level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, .5F, .95F);
        }
        @Override protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
            // Also repairs a persisted OPEN=true after a server restart (0 -> 0).
            setOpen(level, pos, newCount > 0);
        }
        @Override protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == LabStorageInventory.this;
        }
    };
    protected LabStorageInventory(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public abstract boolean isStorage();
    @Override public int getContainerSize() { return isStorage() ? 27 : 0; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> value) { items = value; }
    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return ChestMenu.threeRows(id, inventory, this); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        if (!isStorage()) return null;
        var menu = super.createMenu(id, inventory, player);
        if (menu != null) setChanged();
        return menu;
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(27, ItemStack.EMPTY);
        lootTable = null; lootTableSeed = 0;
        if (isStorage() && !tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (isStorage() && !trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }
    public CompoundTag saveInventoryTag() { return saveWithoutMetadata(); }
    // Menus synchronize their own slots. Chunk packets must never disclose inventory or unopened loot.
    @Override public CompoundTag getUpdateTag() { return new CompoundTag(); }
    @Override public void clearContent() {
        lootTable = null; lootTableSeed = 0; items.clear(); setChanged();
    }
    private void setOpen(Level level, BlockPos pos, boolean value) {
        if (level.isClientSide || isRemoved()) return;
        var state = getBlockState();
        if (value && state.getBlock() instanceof LabStorageBlock storage && storage.key.equals("lab_storage_crate")) {
            var above = pos.above();
            // Stacked crates still open their inventory; only the lid animation needs clearance.
            if (!level.hasChunkAt(above) || !level.getBlockState(above).getCollisionShape(level, above).isEmpty()) value = false;
        }
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) != value)
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, value), 3);
    }
    @Override public void startOpen(Player player) {
        if (!isRemoved() && isStorage() && !player.isSpectator()) {
            openers.incrementOpeners(player, getLevel(), getBlockPos(), getBlockState()); setChanged();
        }
    }
    @Override public void stopOpen(Player player) {
        if (!isRemoved() && isStorage() && !player.isSpectator() && openers.getOpenerCount() > 0)
            openers.decrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
    }
    public int openerCount() { return openers.getOpenerCount(); }
    public void recheckOpeners() {
        if (!isRemoved() && isStorage() && getLevel() instanceof ServerLevel)
            openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
    }
    @Override public void onLoad() {
        super.onLoad();
        if (isStorage() && getLevel() instanceof ServerLevel server && getBlockState().getValue(BlockStateProperties.OPEN))
            server.scheduleTick(getBlockPos(), getBlockState().getBlock(), 1);
    }
}
