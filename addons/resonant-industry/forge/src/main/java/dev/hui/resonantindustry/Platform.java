package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class Platform {
    public static boolean validFluid(String id){try{return !id.isEmpty()&&BuiltInRegistries.FLUID.containsKey(IndustryContent.id(id))&&!id.equals("minecraft:empty");}catch(IllegalArgumentException e){return false;}}
    public static void invalidate(IndustryNode n){n.invalidateCaps();n.reviveCaps();}
    public static ItemStack insertItems(Level level,BlockPos pos,Direction side,ItemStack stack){
        if(!IndustryNode.loaded(level,pos))return stack;
        var state=level.getBlockState(pos);
        // A vanilla double-chest capability also queries its connected half.
        if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE
                &&!IndustryNode.loaded(level,pos.relative(ChestBlock.getConnectedDirection(state))))return stack;
        var blockEntity=level.getBlockEntity(pos);
        var handler=blockEntity==null?null:blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElse(null);
        return handler==null?stack:ItemHandlerHelper.insertItemStacked(handler,stack,false);
    }
    public static EnergyAccess energy(Level level,BlockPos p,Direction side){
        if(!IndustryNode.loaded(level,p))return null;
        var be=level.getBlockEntity(p);var cap=be==null?null:be.getCapability(ForgeCapabilities.ENERGY,side).orElse(null);
        if(cap==null)return null;
        return new EnergyAccess(){public int stored(){return cap.getEnergyStored();}public int capacity(){return cap.getMaxEnergyStored();}public int receive(int a,boolean s){return cap.receiveEnergy(a,s);}public int extract(int a,boolean s){return cap.extractEnergy(a,s);}};
    }
    public static FluidAccess fluid(Level level,BlockPos p,Direction side){
        if(!IndustryNode.loaded(level,p))return null;
        var be=level.getBlockEntity(p);var cap=be==null?null:be.getCapability(ForgeCapabilities.FLUID_HANDLER,side).orElse(null);
        if(cap==null)return null;
        return new FluidAccess(){
            public String fluid(){var f=cap.getFluidInTank(0);return f.isEmpty()||!LabPlatform.plain(f)?"":BuiltInRegistries.FLUID.getKey(f.getFluid()).toString();}
            public int amount(){return LabPlatform.plain(cap.getFluidInTank(0))?cap.getFluidInTank(0).getAmount():0;}public int capacity(){return cap.getTankCapacity(0);}
            public int fill(String id,int a,boolean s){return validFluid(id)&&a>0?cap.fill(new FluidStack(BuiltInRegistries.FLUID.get(IndustryContent.id(id)),a),s?IFluidHandler.FluidAction.SIMULATE:IFluidHandler.FluidAction.EXECUTE):0;}
            public int drain(int a,boolean s){if(!LabPlatform.plain(cap.drain(a,IFluidHandler.FluidAction.SIMULATE)))return 0;return cap.drain(a,s?IFluidHandler.FluidAction.SIMULATE:IFluidHandler.FluidAction.EXECUTE).getAmount();}
        };
    }
    public static IEnergyStorage energyView(IndustryNode node,Direction side){
        var access=node.energyAccess(side);
        return new IEnergyStorage(){
            public int receiveEnergy(int a,boolean s){return access.receive(a,s);}public int extractEnergy(int a,boolean s){return access.extract(a,s);}
            public int getEnergyStored(){return access.stored();}public int getMaxEnergyStored(){return access.capacity();}
            public boolean canExtract(){return node.role().equals("cable")||node.role().equals("battery")&&(side==null||side==node.facing())||node.role().equals("energy_port")&&node.owner()!=null&&node.owner().definition().generator();}
            public boolean canReceive(){return node.role().equals("cable")||node.role().equals("pump")||node.role().equals("battery")&&side!=node.facing()||node.role().equals("energy_port")&&node.owner()!=null&&!node.owner().definition().generator();}
        };
    }
    public static IFluidHandler fluidView(IndustryNode node,Direction side){
        var access=node.fluidAccess(side);
        return new IFluidHandler(){
            public int getTanks(){return 1;}
            public FluidStack getFluidInTank(int tank){return tank==0&&validFluid(access.fluid())&&access.amount()>0?new FluidStack(BuiltInRegistries.FLUID.get(IndustryContent.id(access.fluid())),access.amount()):FluidStack.EMPTY;}
            public int getTankCapacity(int tank){return tank==0?access.capacity():0;}
            public boolean isFluidValid(int tank,FluidStack fluid){return tank==0&&!fluid.isEmpty()&&LabPlatform.plain(fluid)&&access.fill(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),1,true)==1;}
            public int fill(FluidStack fluid,FluidAction action){return fluid.isEmpty()||!LabPlatform.plain(fluid)?0:access.fill(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(),fluid.getAmount(),action.simulate());}
            public FluidStack drain(FluidStack fluid,FluidAction action){return fluid.isEmpty()||!LabPlatform.plain(fluid)||!BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString().equals(access.fluid())?FluidStack.EMPTY:drain(fluid.getAmount(),action);}
            public FluidStack drain(int amount,FluidAction action){if(amount<=0||access.amount()==0)return FluidStack.EMPTY;String id=access.fluid();int n=access.drain(amount,action.simulate());return n<=0?FluidStack.EMPTY:new FluidStack(BuiltInRegistries.FLUID.get(IndustryContent.id(id)),n);}
        };
    }
    private Platform(){}
}
