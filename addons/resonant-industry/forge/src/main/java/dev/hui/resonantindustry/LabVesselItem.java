package dev.hui.resonantindustry;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** A single physical vessel. Fluid and measurement settings travel with the item. */
public final class LabVesselItem extends Item {
    public final int capacity;public final int[] doses;public final int defaultDose;
    public LabVesselItem(int capacity,int defaultDose,int... doses){super(new Properties().stacksTo(1));this.capacity=capacity;this.defaultDose=defaultDose;this.doses=doses.clone();}
    // FORGE_CAP_START
    @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack,net.minecraft.nbt.CompoundTag tag){return new net.minecraftforge.common.capabilities.ICapabilityProvider(){
        private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.fluids.capability.IFluidHandlerItem> fluid=net.minecraftforge.common.util.LazyOptional.of(()->LabPlatform.itemHandler(stack));
        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,net.minecraft.core.Direction side){return cap==net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM?fluid.cast():net.minecraftforge.common.util.LazyOptional.empty();}
    };}
    // FORGE_CAP_END
    public static boolean vessel(ItemStack stack){return stack.getCount()==1&&stack.getItem() instanceof LabVesselItem;}
    @Override public boolean doesSneakBypassUse(ItemStack stack,net.minecraft.world.level.LevelReader level,net.minecraft.core.BlockPos pos,Player player){var be=level.getBlockEntity(pos);return be instanceof LabBenchEntity||be instanceof IndustryNode;}
    public static int capacity(ItemStack stack){return vessel(stack)?((LabVesselItem)stack.getItem()).capacity:0;}
    public static int dose(ItemStack stack){if(!vessel(stack))return 0;var item=(LabVesselItem)stack.getItem();int n=LabPlatform.data(stack).getInt("LabDose");return Arrays.stream(item.doses).anyMatch(d->d==n)?n:item.defaultDose;}
    public static void cycle(ItemStack stack){var item=(LabVesselItem)stack.getItem();int i=0;while(i<item.doses.length&&item.doses[i]!=dose(stack))i++;var tag=LabPlatform.data(stack);tag.putInt("LabDose",item.doses[(i+1)%item.doses.length]);LabPlatform.data(stack,tag);}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getPlayer()==null)return InteractionResult.PASS;
        if(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof LabBenchEntity bench){
            if(!context.getLevel().isClientSide&&context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)bench.interact(player,new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),context.isInside()));
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        var handler=LabPlatform.handler(context.getLevel(),context.getClickedPos(),context.getClickedFace());if(handler==null)return InteractionResult.PASS;
        if(!context.getLevel().isClientSide)LabFluids.report(context.getPlayer(),LabFluids.interact(context.getItemInHand(),handler,context.getPlayer().isShiftKeyDown(),context.getLevel()));
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);if(!level.isClientSide){
            if(player.isShiftKeyDown()){cycle(stack);player.displayClientMessage(Component.translatable("lab.resonant_industry.dose",dose(stack)),true);}
            else {var other=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);if(vessel(other))LabFluids.report(player,LabFluids.interact(stack,LabFluids.view(other,level,()->{}),false,level));}
        }return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        var data=LabPlatform.data(stack);String id=data.getString("LabFluid");
        lines.add(Component.translatable("lab.resonant_industry.contents",id.isEmpty()?Component.translatable("lab.resonant_industry.empty"):Component.translatable(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(id)).getFluidType().getDescriptionId()),Math.max(0,data.getInt("LabAmount")),capacity));
        lines.add(Component.translatable("lab.resonant_industry.dose",dose(stack)));
        lines.add(Component.translatable("lab.resonant_industry.vessel_help"));
    }
}
