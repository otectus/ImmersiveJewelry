package com.otectus.immersivejewelry.block;

import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
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
 * The Lapidary Wheel (spec 6.2): mount a rough stone (or a cut one to recut), choose a cut and grind it. Sneak-use
 * with an empty hand takes the stone back; use with a lap installs it.
 */
public class LapidaryWheelBlock extends StationBlock {

    public LapidaryWheelBlock(Properties properties) {
        super(properties, Shapes.or(
                Block.box(1, 0, 1.5, 4, 2, 14.5),
                Block.box(12, 0, 1.5, 15, 2, 14.5),
                Block.box(2, 2, 3, 14, 7, 13),
                Block.box(3, 7, 6.5, 13, 17, 9.5),
                Block.box(6.625, 7, 4.175, 9.375, 13.3, 6),
                Block.box(6.625, 7, 10.075, 9.375, 13.3, 13.25),
                Block.box(12, 7, 3.8, 14.5, 15.8, 6),
                Block.box(0.6, 15, 9.85, 4.15, 18.25, 13.15)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LapidaryWheelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.LAPIDARY_WHEEL.get(), LapidaryWheelBlockEntity::clientTick) : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LapidaryWheelBlockEntity wheel) {
            SessionManager.onStationRemoved(level, pos);
            wheel.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof LapidaryWheelBlockEntity wheel && player instanceof ServerPlayer sp) {
            StationInteractions.useWheel(sp, wheel, hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LapidaryWheelBlockEntity wheel && wheel.hasStone() ? 15 : 0;
    }
}
