package dev.hui.enderinfection.design;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** A usable vertical transfer platform. All destinations are existing clear landings. */
public final class LabLiftBlock extends Block {
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return netResult(use(state,level,pos,player,hand,hit));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return use(state,level,pos,player,InteractionHand.MAIN_HAND,hit);}

    public LabLiftBlock(Properties properties){super(properties);}
    public static BlockPos destination(Level level,BlockPos pos,boolean down){
        int direction=down?-1:1;
        for(int i=1;i<=40;i++){var target=pos.above(i*direction);if(level.getBlockState(target).getBlock() instanceof LabLiftBlock)return target;}
        return !down?destination(level,pos,true):null;
    }
    public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.isClientSide)return InteractionResult.SUCCESS;
        var target=destination(level,pos,player.isShiftKeyDown());
        if(target==null){player.displayClientMessage(Component.translatable("message.ender_infection.lift_end"),true);return InteractionResult.CONSUME;}
        var to=Vec3.atBottomCenterOf(target.above());
        var box=player.getBoundingBox().move(to.subtract(player.position()));
        if(!level.noCollision(player,box)){player.displayClientMessage(Component.translatable("message.ender_infection.lift_blocked"),true);return InteractionResult.CONSUME;}
        if(player instanceof ServerPlayer serverPlayer)serverPlayer.teleportTo(serverPlayer.serverLevel(),to.x,to.y,to.z,player.getYRot(),player.getXRot());
        else player.teleportTo(to.x,to.y,to.z);
        player.fallDistance=0;
        level.playSound(null,target,net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN,net.minecraft.sounds.SoundSource.BLOCKS,.6F,1.3F);
        return InteractionResult.CONSUME;
    }
    private static net.minecraft.world.ItemInteractionResult netResult(InteractionResult result){return switch(result){case SUCCESS -> net.minecraft.world.ItemInteractionResult.SUCCESS;case CONSUME,CONSUME_PARTIAL -> net.minecraft.world.ItemInteractionResult.CONSUME;case FAIL -> net.minecraft.world.ItemInteractionResult.FAIL;default -> net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;};}
}
