package dev.hui.enderinfection.rift;

import dev.hui.enderinfection.InfectedContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class RiftAnchorBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active"),SUPPRESSED=BooleanProperty.create("suppressed");
    public RiftAnchorBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(ACTIVE,false).setValue(SUPPRESSED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(ACTIVE,SUPPRESSED);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new RiftAnchorEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState s,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,RiftContent.ANCHOR_ENTITY.get(),RiftAnchorEntity::tick);}
    @Override public InteractionResult use(BlockState s,Level level,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.getItemInHand(hand).is(RiftContent.SUPPRESSOR.get()))return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(level.getBlockEntity(p) instanceof RiftAnchorEntity anchor){
            var stack=player.getItemInHand(hand);
            if(stack.is(InfectedContent.VOID_CRYSTAL.get())&&anchor.energy()<=RiftAnchorEntity.CAPACITY-12){anchor.addEnergy(12);if(!player.isCreative())stack.shrink(1);}
            player.displayClientMessage(Component.translatable("message.ender_infection.anchor_status",anchor.energy(),RiftAnchorEntity.CAPACITY,anchor.statusName()),true);
        }
        return InteractionResult.CONSUME;
    }
}
