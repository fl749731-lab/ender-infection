package dev.hui.resonantindustry;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public final class LabBenchEntity extends BlockEntity implements Container,MenuProvider {
    public static final int[] DOSES={1,5,10,25,50,100,250,1000};
    private final NonNullList<ItemStack> items=NonNullList.withSize(6,ItemStack.EMPTY);
    private final ChemistryStation chemistry;
    private int source=0,target=1,doseIndex=3,result=0;
    public final ContainerData data=new ContainerData(){public int getCount(){return 4;}public int get(int i){return switch(i){case 0->source;case 1->target;case 2->doseIndex;case 3->result;default->0;};}public void set(int i,int v){switch(i){case 0->source=v;case 1->target=v;case 2->doseIndex=v;case 3->result=v;}}};
    public LabBenchEntity(BlockPos pos, BlockState state) {
        super(ResonantIndustry.benchType(), pos, state);
        var kind = ((LabBenchBlock) state.getBlock()).station();
        chemistry = kind.processesSamples() ? new ChemistryStation(this, kind) : null;
    }

    public ChemistryStation chemistry() { return chemistry; }

    void markReactionDirty() { super.setChanged(); }

    void replaceReactionVessel(int slot, ItemStack vessel) {
        items.set(slot, vessel);
    }

    LabBenchEntity connectedCondenser() {
        var facing = getBlockState().getValue(LabBenchBlock.FACING);
        var connection = worldPosition.relative(facing.getCounterClockWise());
        if (!IndustryNode.loaded(level, connection)) return null;
        if (!(level.getBlockEntity(connection) instanceof LabBenchEntity receiver)) return null;
        var state = receiver.getBlockState();
        return state.getBlock() instanceof LabBenchBlock block && block.station() == Chemistry.Station.CONDENSER
                && state.getValue(LabBenchBlock.FACING) == facing ? receiver : null;
    }
    public int dose(){return DOSES[doseIndex];}
    public boolean button(Player player,int id){
        if (!stillValid(player) || id < 0 || id > 16) return false;
        if (id >= 14) {
            if (chemistry == null) return false;
            if (id == 15) chemistry.stop();
            else if (!chemistry.start(id == 16)) return false;
            markReactionDirty();
            return true;
        }
        if(id<6)source=id;else if(id<12)target=id-6;else if(id==12)doseIndex=(doseIndex+1)%DOSES.length;
        else {result=source==target||getItem(source)==getItem(target)?0:LabFluids.transfer(LabFluids.view(getItem(source),level,this::setChanged),LabFluids.view(getItem(target),level,this::setChanged),dose());if(result==0)result=-1;}
        setChanged();return true;
    }
    /** Slot centers in the model's north-facing local coordinates. */
    public static Vec3 center(int slot){return new Vec3(.23+(slot%3)*.27,14/16.0,.30+(slot/3)*.40);}
    public Vec3 worldCenter(int slot){var v=center(slot).subtract(.5,0,.5);double a=Math.toRadians(180-getBlockState().getValue(LabBenchBlock.FACING).toYRot());return new Vec3(Math.cos(a)*v.x+Math.sin(a)*v.z+.5,v.y,-Math.sin(a)*v.x+Math.cos(a)*v.z+.5).add(Vec3.atLowerCornerOf(worldPosition));}
    public int hitSlot(Vec3 world){var v=world.subtract(Vec3.atLowerCornerOf(worldPosition)).subtract(.5,0,.5);double a=Math.toRadians(180-getBlockState().getValue(LabBenchBlock.FACING).toYRot());double x=Math.cos(a)*v.x-Math.sin(a)*v.z+.5,z=Math.sin(a)*v.x+Math.cos(a)*v.z+.5;int col=Math.max(0,Math.min(2,(int)Math.floor((x-.095)/.27)));return col+(z>=.5?3:0);}
    public void interact(ServerPlayer player,BlockHitResult hit){
        int slot=hitSlot(hit.getLocation());var held=player.getMainHandItem();var stored=getItem(slot);
        if(LabVesselItem.vessel(held)){
            if(stored.isEmpty()){setItem(slot,held.copy());player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
            else {LabFluids.report(player,LabFluids.interact(held,LabFluids.view(stored,level,this::setChanged),player.isShiftKeyDown(),level));setChanged();}return;
        }
        if(held.isEmpty()&&player.isShiftKeyDown()){player.setItemInHand(InteractionHand.MAIN_HAND,removeItemNoUpdate(slot));setChanged();return;}
        if(bucket(player,slot))return;
        source=slot;setChanged();player.openMenu(this);
    }
    public boolean bucket(Player player,int slot){
        var held=player.getMainHandItem();var handler=LabFluids.view(getItem(slot),level,this::setChanged);
        String id=held.is(Items.WATER_BUCKET)?"minecraft:water":held.is(Items.LAVA_BUCKET)?"minecraft:lava":held.is(IndustryContent.item("slurry_canister"))?"resonant_industry:nutrient_slurry":"";
        if(!id.isEmpty()){
            var f=new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(id)),1000);
            int n=handler.fill(f,IFluidHandler.FluidAction.SIMULATE);if(n==1000){handler.fill(f,IFluidHandler.FluidAction.EXECUTE);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));}LabFluids.report(player,n==1000?1000:0);return true;
        }
        if(held.is(Items.BUCKET)){
            var f=handler.drain(1000,IFluidHandler.FluidAction.SIMULATE);Item bucket=f.isEmpty()?Items.AIR:f.getFluid().getBucket();
            if(f.getAmount()!=1000||!LabPlatform.plain(f)||bucket==Items.AIR){LabFluids.report(player,0);return true;}
            handler.drain(f,IFluidHandler.FluidAction.EXECUTE);{held.shrink(1);var filled=new ItemStack(bucket);if(held.isEmpty())player.setItemInHand(InteractionHand.MAIN_HAND,filled);else if(!player.getInventory().add(filled))player.drop(filled,false);}LabFluids.report(player,1000);return true;
        }return false;
    }
    /** A live view resolves the selected slot for every operation, so no stale-item writes. */
    public IFluidHandler fluidView(){return new IFluidHandler(){
        private IFluidHandler active(){return LabFluids.view(isRemoved()?ItemStack.EMPTY:getItem(source),level,LabBenchEntity.this::setChanged);}
        public int getTanks(){return 1;}public FluidStack getFluidInTank(int i){return active().getFluidInTank(i);}public int getTankCapacity(int i){return active().getTankCapacity(i);}public boolean isFluidValid(int i,FluidStack f){return active().isFluidValid(i,f);}public int fill(FluidStack f,FluidAction a){return active().fill(f,a);}public FluidStack drain(FluidStack f,FluidAction a){return active().drain(f,a);}public FluidStack drain(int n,FluidAction a){return active().drain(n,a);}
    };}
    @Override public int getContainerSize(){return 6;}@Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){return i>=0&&i<6?items.get(i):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int i,int n){var s=ContainerHelper.removeItem(items,i,n);if(!s.isEmpty())setChanged();return s;}
    @Override public ItemStack removeItemNoUpdate(int i){return ContainerHelper.takeItem(items,i);}
    @Override public void setItem(int i,ItemStack s){if(!s.isEmpty()&&!canPlaceItem(i,s))throw new IllegalArgumentException("Only single lab vessels fit the bench");items.set(i,s);setChanged();}
    @Override public boolean canPlaceItem(int i,ItemStack s){return LabVesselItem.vessel(s);}
    @Override public int getMaxStackSize(){return 1;}
    @Override public void clearContent(){items.clear();setChanged();}
    @Override public boolean stillValid(Player p){return level!=null&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return chemistry == null ? new LabBenchMenu(id, inv, this, data)
                : new LabBenchMenu(id, inv, this, data, chemistry, chemistry.data());
    }
    @Override public void setChanged(){super.setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    private void state(CompoundTag t){t.putInt("LabSource",source);t.putInt("LabTarget",target);t.putInt("LabDoseIndex",doseIndex);}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);
        state(t);
        ContainerHelper.saveAllItems(t,items);
        if (chemistry != null) {
            var reaction = new CompoundTag();
            chemistry.save(reaction);
            ContainerHelper.saveAllItems(reaction, chemistry.items);
            t.put("Chemistry", reaction);
        }
    }
    @Override public void load(CompoundTag t){super.load(t);
        items.clear();
        ContainerHelper.loadAllItems(t,items);
        source = Math.max(0, Math.min(5, t.getInt("LabSource")));
        target = Math.max(0, Math.min(5, t.getInt("LabTarget")));
        doseIndex = Math.max(0, Math.min(DOSES.length - 1, t.getInt("LabDoseIndex")));
        for (int i = 0; i < 6; i++) if (!items.get(i).isEmpty() && !LabVesselItem.vessel(items.get(i))) items.set(i, ItemStack.EMPTY);
        if (chemistry != null) {
            var reaction = t.getCompound("Chemistry");
            chemistry.items.clear();
            ContainerHelper.loadAllItems(reaction, chemistry.items);
            chemistry.load(reaction);
        }
    }
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    // FORGE_CAP_START
    private final net.minecraftforge.common.util.LazyOptional<IFluidHandler> fluid=net.minecraftforge.common.util.LazyOptional.of(this::fluidView);
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,Direction side){if(cap==net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER&&!isRemoved())return fluid.cast();return super.getCapability(cap,side);}
    @Override public void invalidateCaps(){super.invalidateCaps();fluid.invalidate();}
    // FORGE_CAP_END
}
