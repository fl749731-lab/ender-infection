package dev.hui.resonantindustry;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class LabBenchBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    private final Chemistry.Station station;

    public LabBenchBlock() { this(Chemistry.Station.BENCH); }

    public LabBenchBlock(Chemistry.Station station) {
        super(Properties.of().strength(2.5f).noOcclusion().lightLevel(state -> state.getValue(ACTIVE) ? 5 : 0));
        this.station = station;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public Chemistry.Station station() { return station; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING, ACTIVE);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !station.processesSamples()) return null;
        return createTickerHelper(type, ResonantIndustry.benchType(), (world, pos, block, bench) -> bench.chemistry().tick());
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new LabBenchEntity(p,s);}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(0,0,0,16,14,16);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(!l.isClientSide&&l.getBlockEntity(p) instanceof LabBenchEntity bench)bench.interact((ServerPlayer)player,hit);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.is(next.getBlock())) return;
        if (level.getBlockEntity(pos) instanceof LabBenchEntity bench && !level.isClientSide) {
            Containers.dropContents(level, pos, bench);
            if (bench.chemistry() != null) Containers.dropContents(level, pos, bench.chemistry());
            bench.clearContent();
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
