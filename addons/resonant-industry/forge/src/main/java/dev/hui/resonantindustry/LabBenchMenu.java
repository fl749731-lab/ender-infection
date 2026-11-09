package dev.hui.resonantindustry;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class LabBenchMenu extends AbstractContainerMenu {
    private final Container bench;private final ContainerData data;
    private final ContainerData chemistry;
    public LabBenchMenu(int id,Inventory inv){this(id,inv,new SimpleContainer(6),new SimpleContainerData(4));}
    public LabBenchMenu(int id,Inventory inv,Container bench,ContainerData data){
        this(id, inv, bench, data, null, null);
    }
    public static LabBenchMenu stationClient(int id, Inventory inv) {
        return new LabBenchMenu(id, inv, new SimpleContainer(6), new SimpleContainerData(4),
                new SimpleContainer(3), new SimpleContainerData(ChemistryStation.DATA_COUNT));
    }
    public LabBenchMenu(int id, Inventory inv, Container bench, ContainerData data,
                        Container stationItems, ContainerData chemistry) {
        super(chemistry == null ? ResonantIndustry.benchMenuType() : ResonantIndustry.stationMenuType(), id);
        this.bench = bench;
        this.data = data;
        this.chemistry = chemistry;
        for(int i=0;i<6;i++)addSlot(new Slot(bench,i,34+(i%3)*94,35+(i/3)*50){@Override public boolean mayPlace(ItemStack s){return LabVesselItem.vessel(s);}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,79+col*18,151+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,79+col*18,209));addDataSlots(data);
        if (chemistry != null) {
            addDataSlots(chemistry);
            for (int index = 0; index < 3; index++) {
                final int slot = index;
                addSlot(new Slot(stationItems, slot, 324 + 27 * slot, 69) {
                    @Override public boolean mayPlace(ItemStack stack) { return ChemistryStation.accepts(slot, stack, kind()); }
                    @Override public boolean isActive() { return slot != ChemistryStation.FUEL || heater(); }
                });
            }
        }
    }
    public int value(int i){return data.get(i);}
    public boolean station() { return chemistry != null; }
    public int chemistry(int index) { return chemistry == null ? 0 : chemistry.get(index); }
    public Chemistry.Station kind() {
        var kinds = Chemistry.Station.values();
        int id = chemistry(0);
        return id >= 0 && id < kinds.length ? kinds[id] : Chemistry.Station.BENCH;
    }
    public boolean heater() { return kind() == Chemistry.Station.HEATER; }
    @Override public boolean stillValid(Player p){return bench.stillValid(p);}
    @Override public boolean clickMenuButton(Player p,int id){return bench instanceof LabBenchEntity b&&b.button(p,id);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var s=slot.getItem();var old=s.copy();
        boolean moved;
        if (index < 6 || index >= 42) moved = moveItemStackTo(s, 6, 42, true);
        else if (LabVesselItem.vessel(s)) moved = moveItemStackTo(s, 0, 6, false);
        else if (station()) {
            int target = ChemistryStation.accepts(ChemistryStation.FUEL, s, kind()) ? 43 : 42;
            moved = slots.get(target).mayPlace(s) && moveItemStackTo(s, target, target + 1, false);
        } else moved = false;
        if(!moved)return ItemStack.EMPTY;if(s.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,s);return old;
    }
}
