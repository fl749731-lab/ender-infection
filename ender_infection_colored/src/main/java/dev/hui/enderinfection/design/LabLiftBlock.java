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
    public LabLiftBlock(Properties properties){super(properties);}
    public static BlockPos destination(Level level,BlockPos pos,boolean down){
        int direction=down?-1:1;
        for(int i=1;i<=40;i++){var target=pos.above(i*direction);if(level.getBlockState(target).getBlock() instanceof LabLiftBlock)return target;}
        return !down?destination(level,pos,true):null;
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
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
}
