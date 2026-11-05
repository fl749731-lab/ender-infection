package dev.hui.resonantindustry;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;

/** Forge owns capability lifetimes; views resolve the loaded controller on every operation. */
public abstract class PlatformNodeEntity extends BlockEntity {
    private final Map<String,LazyOptional<?>> views=new HashMap<>();
    protected PlatformNodeEntity(BlockEntityType<?> type,BlockPos p,BlockState s){super(type,p,s);}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){
        var node=(IndustryNode)this;String key=cap.getName()+"/"+side;
        if(cap==ForgeCapabilities.ENERGY&&node.energyAccess(side)!=null)
            return views.computeIfAbsent(key,k->LazyOptional.of(()->Platform.energyView(node,side))).cast();
        if(cap==ForgeCapabilities.FLUID_HANDLER&&node.fluidAccess(side)!=null)
            return views.computeIfAbsent(key,k->LazyOptional.of(()->Platform.fluidView(node,side))).cast();
        if(cap==ForgeCapabilities.ITEM_HANDLER&&Set.of("item_input","item_output").contains(node.role()))
            return views.computeIfAbsent(key,k->LazyOptional.of(()->new net.minecraftforge.items.wrapper.SidedInvWrapper(node,side==null?Direction.UP:side))).cast();
        return super.getCapability(cap,side);
    }
    @Override public void invalidateCaps(){super.invalidateCaps();views.values().forEach(LazyOptional::invalidate);views.clear();}
}
