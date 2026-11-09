package dev.hui.resonantindustry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class LabPlatform {
    private static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS=net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE,"resonant_industry");
    private static final java.util.function.Supplier<net.minecraft.core.component.DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> SAMPLE=COMPONENTS.register("lab_sample",()->net.minecraft.core.component.DataComponentType.<net.neoforged.neoforge.fluids.SimpleFluidContent>builder().persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC).networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC).build());
    public static void register(net.neoforged.bus.api.IEventBus bus){COMPONENTS.register(bus);}
    public static CompoundTag data(ItemStack s){return s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    public static void data(ItemStack s,CompoundTag t){if(t.isEmpty())s.remove(DataComponents.CUSTOM_DATA);else s.set(DataComponents.CUSTOM_DATA,CustomData.of(t));}
    public static FluidStack read(ItemStack s,Level l){return s.getOrDefault(SAMPLE.get(),net.neoforged.neoforge.fluids.SimpleFluidContent.EMPTY).copy();}
    public static void write(ItemStack s,FluidStack f,Level l){var t=data(s);if(f.isEmpty()){s.remove(SAMPLE.get());t.remove("LabFluid");t.remove("LabAmount");}else{s.set(SAMPLE.get(),net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(f));t.putString("LabFluid",net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.getFluid()).toString());t.putInt("LabAmount",f.getAmount());}data(s,t);}
    public static net.neoforged.neoforge.fluids.capability.IFluidHandlerItem itemHandler(ItemStack stack){var view=LabFluids.view(stack,null,()->{});return new net.neoforged.neoforge.fluids.capability.IFluidHandlerItem(){public ItemStack getContainer(){return stack;}public int getTanks(){return view.getTanks();}public FluidStack getFluidInTank(int i){return view.getFluidInTank(i);}public int getTankCapacity(int i){return view.getTankCapacity(i);}public boolean isFluidValid(int i,FluidStack f){return view.isFluidValid(i,f);}public int fill(FluidStack f,FluidAction a){return view.fill(f,a);}public FluidStack drain(FluidStack f,FluidAction a){return view.drain(f,a);}public FluidStack drain(int n,FluidAction a){return view.drain(n,a);}};}
    public static IFluidHandler handler(Level l,BlockPos p,Direction side){return IndustryNode.loaded(l,p)?l.getCapability(Capabilities.FluidHandler.BLOCK,p,side):null;}
    public static net.neoforged.neoforge.fluids.capability.IFluidHandlerItem capability(ItemStack stack){return stack.getCapability(Capabilities.FluidHandler.ITEM);}
    public static boolean plain(FluidStack f){return f.getComponentsPatch().isEmpty();}
    static void settleDistillation(LabBenchEntity heater, LabBenchEntity receiver, Runnable settle) {
        DistillationJournal.settle(heater, receiver, settle);
    }
    static ChemistryStation.Status meteredHold(LabBenchEntity bench, boolean consume) {
        return MeteringPumpEntity.mixingHold(bench, consume);
    }
    private LabPlatform(){}
}
