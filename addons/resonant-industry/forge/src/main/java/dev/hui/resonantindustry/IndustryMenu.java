package dev.hui.resonantindustry;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class IndustryMenu extends AbstractContainerMenu {
    private final Container machine;private final ContainerData data;
    public static final int SLOT_COUNT=48;
    public IndustryMenu(int id,Inventory inv){this(id,inv,new SimpleContainer(IndustryNode.INVENTORY_SIZE),new SimpleContainerData(IndustryNode.DATA_SIZE));}
    public IndustryMenu(int id,Inventory inv,Container machine,ContainerData data){
        super(IndustryContent.MENU,id);this.machine=machine;this.data=data;checkContainerSize(machine,IndustryNode.INVENTORY_SIZE);checkContainerDataCount(data,IndustryNode.DATA_SIZE);
        addSlot(new Slot(machine,0,48,76){@Override public boolean mayPlace(ItemStack s){return machine.canPlaceItem(0,s);}});
        addSlot(new Slot(machine,2,130,76){@Override public boolean mayPlace(ItemStack s){return false;}});
        addSlot(new Slot(machine,3,156,76){@Override public boolean mayPlace(ItemStack s){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,30+col*18,149+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,30+col*18,207));
        for(int i=1;i<6;i++){int slot=IndustryNode.INPUT_SLOTS[i];addSlot(new Slot(machine,slot,234+(i-1)%3*22,48+(i-1)/3*22){@Override public boolean mayPlace(ItemStack s){return machine.canPlaceItem(slot,s);}});}
        for(int i=0;i<2;i++){int slot=IndustryNode.CATALYST_SLOTS[i];addSlot(new Slot(machine,slot,310+i*22,48){@Override public boolean mayPlace(ItemStack s){return machine.canPlaceItem(slot,s);}});}
        for(int i=2;i<4;i++)addSlot(new Slot(machine,IndustryNode.OUTPUT_SLOTS[i],310+(i-2)*22,80){@Override public boolean mayPlace(ItemStack s){return false;}});
        addDataSlots(data);
    }
    public int value(int i){return data.get(i)&65535;}
    public int energy(){return value(0)|(value(1)<<16);}public int capacity(){return value(2)|(value(3)<<16);}
    public int progress(){return value(4)|(value(12)<<16);}public int duration(){return value(5)|(value(13)<<16);}
    public int burn(){return value(10)|(value(14)<<16);}
    @Override public boolean stillValid(Player p){return machine.stillValid(p);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var before=stack.copy();boolean moved;
        if(index<3||index>=39)moved=moveItemStackTo(stack,3,39,true);
        else {
            moved=slots.get(0).mayPlace(stack)&&moveItemStackTo(stack,0,1,false);
            for(int i=39;i<46&&!stack.isEmpty();i++)if(slots.get(i).mayPlace(stack))moved=moveItemStackTo(stack,i,i+1,false)||moved;
            if(!moved)moved=index<30?moveItemStackTo(stack,30,39,false):moveItemStackTo(stack,3,30,false);
        }
        if(!moved||before.getCount()==stack.getCount())return ItemStack.EMPTY;if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return before;
    }
}
