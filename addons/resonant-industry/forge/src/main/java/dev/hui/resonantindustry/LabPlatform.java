package dev.hui.resonantindustry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Loader-specific serialization and native capability boundary. */
public final class LabPlatform {
    public static CompoundTag data(ItemStack s){return s.hasTag()?s.getTag().copy():new CompoundTag();}
    public static void data(ItemStack s,CompoundTag t){s.setTag(t.isEmpty()?null:t);}
    public static FluidStack read(ItemStack s,Level l){return FluidStack.loadFluidStackFromNBT(data(s).getCompound("LabSample"));}
    public static void write(ItemStack s,FluidStack f,Level l){var t=data(s);if(f.isEmpty()){t.remove("LabSample");t.remove("LabFluid");t.remove("LabAmount");}else{t.put("LabSample",f.writeToNBT(new CompoundTag()));t.putString("LabFluid",net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.getFluid()).toString());t.putInt("LabAmount",f.getAmount());}data(s,t);}
    public static IFluidHandler handler(Level l,BlockPos p,Direction side){if(!IndustryNode.loaded(l,p))return null;var be=l.getBlockEntity(p);return be==null?null:be.getCapability(ForgeCapabilities.FLUID_HANDLER,side).resolve().orElse(null);}
    public static net.minecraftforge.fluids.capability.IFluidHandlerItem itemHandler(ItemStack stack){var view=LabFluids.view(stack,null,()->{});return new net.minecraftforge.fluids.capability.IFluidHandlerItem(){public ItemStack getContainer(){return stack;}public int getTanks(){return view.getTanks();}public FluidStack getFluidInTank(int i){return view.getFluidInTank(i);}public int getTankCapacity(int i){return view.getTankCapacity(i);}public boolean isFluidValid(int i,FluidStack f){return view.isFluidValid(i,f);}public int fill(FluidStack f,FluidAction a){return view.fill(f,a);}public FluidStack drain(FluidStack f,FluidAction a){return view.drain(f,a);}public FluidStack drain(int n,FluidAction a){return view.drain(n,a);}};}
    public static net.minecraftforge.fluids.capability.IFluidHandlerItem capability(ItemStack stack){return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve().orElse(null);}
    public static boolean plain(FluidStack f){return !f.hasTag();}
    static void settleDistillation(LabBenchEntity heater, LabBenchEntity receiver, Runnable settle) {
        settle.run();
    }
    private LabPlatform(){}
}
