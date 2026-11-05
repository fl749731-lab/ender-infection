package dev.hui.enderinfection.processing;

import dev.hui.enderinfection.design.DesignContent;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** A real vanilla-synchronized menu: three machine slots and the player's 36. */
public final class LabProcessingMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    public LabProcessingMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(3), new SimpleContainerData(7)); }
    public LabProcessingMenu(int id, Inventory inventory, LabProcessingEntity processor) { this(id, inventory, processor, processor.data); }
    private LabProcessingMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(DesignContent.PROCESSING_MENU.get(), id); checkContainerSize(machine, 3); checkContainerDataCount(data, 7);
        this.machine = machine; this.data = data;
        addSlot(new Slot(machine, 0, 44, 43) { @Override public boolean mayPlace(ItemStack stack) { return machine instanceof LabProcessingEntity processor ? processor.accepts(0, stack) : true; } });
        addSlot(new Slot(machine, 1, 80, 43) { @Override public boolean mayPlace(ItemStack stack) { return machine instanceof LabProcessingEntity processor ? processor.accepts(1, stack) : true; } });
        addSlot(new Slot(machine, 2, 134, 43) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 109 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 167));
        addDataSlots(data);
    }
    public Container machine() { return machine; }
    public int progress() { return (data.get(0) & 65535) | (data.get(5) & 65535) << 16; }
    public int duration() { return (data.get(1) & 65535) | (data.get(6) & 65535) << 16; }
    public boolean powered() { return data.get(2) != 0; }
    public boolean centrifuge() { return data.get(3) != 0; }
    public int status() { return data.get(4); }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        if (index < 3) { if (!moveItemStackTo(source, 3, 39, true)) return ItemStack.EMPTY; }
        else {
            boolean moved = false;
            if (slots.get(0).mayPlace(source)) moved = moveItemStackTo(source, 0, 1, false);
            if (!source.isEmpty() && slots.get(1).mayPlace(source)) moved |= moveItemStackTo(source, 1, 2, false);
            if (!moved) {
                if (index < 30) { if (!moveItemStackTo(source, 30, 39, false)) return ItemStack.EMPTY; }
                else if (!moveItemStackTo(source, 3, 30, false)) return ItemStack.EMPTY;
            }
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source); return original;
    }
}
