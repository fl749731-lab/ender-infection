package dev.hui.resonantindustry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.*;

/** Full FluidStacks are retained, including another mod's chemical components. */
public final class LabFluids {
    public static FluidStack read(ItemStack stack,Level level){
        if(!LabVesselItem.vessel(stack))return FluidStack.EMPTY;
        var fluid=LabPlatform.read(stack,level);return fluid.getAmount()>LabVesselItem.capacity(stack)?FluidStack.EMPTY:fluid;
    }
    public static void write(ItemStack stack,FluidStack fluid,Level level){
        if(!LabVesselItem.vessel(stack)||fluid.getAmount()<0||fluid.getAmount()>LabVesselItem.capacity(stack))throw new IllegalArgumentException("Invalid lab sample");
        LabPlatform.write(stack,fluid,level);
    }
    public static IFluidHandler view(ItemStack stack,Level level,Runnable changed){return new IFluidHandler(){
        public int getTanks(){return 1;}
        public FluidStack getFluidInTank(int i){return i==0?read(stack,level):FluidStack.EMPTY;}
        public int getTankCapacity(int i){return i==0?LabVesselItem.capacity(stack):0;}
        public boolean isFluidValid(int i,FluidStack fluid){var old=read(stack,level);return i==0&&LabVesselItem.vessel(stack)&&!fluid.isEmpty()&&(old.isEmpty()||old.isFluidEqual(fluid));}
        public int fill(FluidStack fluid,FluidAction action){
            if(!isFluidValid(0,fluid))return 0;var old=read(stack,level);int n=Math.min(fluid.getAmount(),getTankCapacity(0)-old.getAmount());
            if(n>0&&action.execute()){var next=fluid.copy();next.setAmount(old.getAmount()+n);write(stack,next,level);changed.run();}return n;
        }
        public FluidStack drain(FluidStack fluid,FluidAction action){var old=read(stack,level);return fluid.isEmpty()||!old.isFluidEqual(fluid)?FluidStack.EMPTY:drain(fluid.getAmount(),action);}
        public FluidStack drain(int amount,FluidAction action){var old=read(stack,level);int n=Math.min(Math.max(0,amount),old.getAmount());if(n==0)return FluidStack.EMPTY;var result=old.copy();result.setAmount(n);if(action.execute()){old.shrink(n);write(stack,old,level);changed.run();}return result;}
    };}
    /** Exact-dose transfer: all preconditions are checked before either side changes. */
    public static int transfer(IFluidHandler source,IFluidHandler target,int amount){
        if(source==null||target==null||source==target||amount<=0)return 0;
        var offered=source.drain(amount,SIMULATE);if(offered.getAmount()!=amount||target.fill(offered,SIMULATE)!=amount)return 0;
        var taken=source.drain(offered,EXECUTE);if(taken.isEmpty())return 0;
        int filled=target.fill(taken,EXECUTE);
        if(filled<taken.getAmount()){var refund=taken.copy();refund.shrink(filled);int returned=source.fill(refund,EXECUTE);if(returned!=refund.getAmount())throw new IllegalStateException("Fluid handler violated simulation contract");}
        return filled;
    }
    public static int interact(ItemStack held,IFluidHandler other,boolean sample,Level level){
        if(!LabVesselItem.vessel(held)||other==null)return 0;var own=view(held,level,()->{});var fluid=read(held,level);
        if(sample||fluid.isEmpty()){
            int amount=sample?LabVesselItem.dose(held):LabVesselItem.capacity(held);
            if(!sample){var offered=other.drain(amount,SIMULATE);amount=Math.min(offered.getAmount(),own.fill(offered,SIMULATE));}
            return transfer(other,own,amount);
        }return transfer(own,other,LabVesselItem.dose(held));
    }
    public static void report(Player player,int n){player.displayClientMessage(Component.translatable(n>0?"lab.resonant_industry.transferred":"lab.resonant_industry.blocked",n),true);}
    private LabFluids(){}
}
