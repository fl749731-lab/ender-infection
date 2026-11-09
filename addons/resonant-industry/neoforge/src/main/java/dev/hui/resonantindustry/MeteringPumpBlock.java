package dev.hui.resonantindustry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

final class MeteringPumpBlock extends BaseEntityBlock {
    static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    static final IntegerProperty OXIDATION = IntegerProperty.create("oxidation", 0, 3);
    private static final MapCodec<MeteringPumpBlock> CODEC =
            Codec.BOOL.fieldOf("precision").xmap(MeteringPumpBlock::new, MeteringPumpBlock::precision);
    private final boolean precision;

    MeteringPumpBlock(boolean precision) {
        super(Properties.of().strength(3.5F).sound(SoundType.METAL));
        this.precision = precision;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OXIDATION, 0));
    }

    boolean precision() { return precision; }

    @Override protected MapCodec<MeteringPumpBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MeteringPumpEntity(pos, state); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OXIDATION);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, WorkshopContent.PUMP_ENTITY.get(),
                (world, pos, block, pump) -> pump.tick());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeteringPumpEntity pump) {
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
                pump.toggleProgram(player);
            } else {
                player.openMenu(pump);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.is(IndustryContent.item("wrench"))) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeteringPumpEntity pump) pump.cycleDose(player);
        } else if (stack.is(IndustryContent.item("vulcanized_seal")) || stack.is(IndustryContent.item("precision_pump_head"))) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeteringPumpEntity pump) pump.maintain(player, stack);
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
