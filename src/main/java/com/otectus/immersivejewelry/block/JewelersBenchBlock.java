package com.otectus.immersivejewelry.block;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.minigame.StationInteractions;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

/**
 * The Jeweler's Bench (spec 6.1): place a blank or a finished piece on it, then use it with Jeweler's Pliers to
 * choose a design, stones and finish and set them, or to rework the piece. The Jeweler's workstation.
 */
public class JewelersBenchBlock extends StationBlock {

    public JewelersBenchBlock(Properties properties) {
        super(properties, Shapes.or(
                Block.box(0, 11, 4.5, 16, 14, 15),
                Block.box(0, 11, 1, 5.5, 14, 4.5),
                Block.box(10.5, 11, 1, 16, 14, 4.5),
                Block.box(6.5, 12.65, -0.25, 9.5, 13.45, 4.5),
                Block.box(1, 0, 2, 3, 11, 4),
                Block.box(13, 0, 2, 15, 11, 4),
                Block.box(1, 0, 12, 3, 11, 14),
                Block.box(13, 0, 12, 15, 11, 14),
                Block.box(3, 8, 8, 13, 11, 14),
                Block.box(4.15, 7.5, 1.5, 11.85, 8.8, 6.5),
                Block.box(1.5, 14, 12.25, 14.5, 17.75, 15),
                Block.box(12, 14, 5.25, 14.5, 18, 7.75)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JewelersBenchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.JEWELERS_BENCH.get(), JewelersBenchBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof JewelersBenchBlockEntity bench) {
            SessionManager.onStationRemoved(level, pos);
            bench.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof JewelersBenchBlockEntity bench && player instanceof ServerPlayer sp) {
            StationInteractions.useBench(sp, bench, hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
