package com.otectus.immersivejewelry.block;

import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
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
 * The Lapidary Wheel: a faceting machine. Walk up with a stone to mount it (a raw one is appraised once), with a lap to
 * install it, sneak-use with an empty hand to take the stone (it keeps any work on it). Using it otherwise opens the
 * station scene, where the stone is ground by hand (see {@code workstation.WheelOperations}).
 */
public class LapidaryWheelBlock extends StationBlock {

    public LapidaryWheelBlock(Properties properties) {
        super(properties, Shapes.or(
                Block.box(1, 0, 1.5, 15, 1.5, 14.5),
                Block.box(1.5, 1.5, 2, 14.5, 10, 14),
                Block.box(0.5, 10, 0.5, 15.5, 11, 15.5),
                Block.box(3.5, 11, 3, 14.5, 13.1, 14),
                Block.box(0.6, 11, 10.1, 3, 17.6, 12.5),
                Block.box(5, 11, 14.1, 11, 13, 15.4)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LapidaryWheelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.LAPIDARY_WHEEL.get(), LapidaryWheelBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.LAPIDARY_WHEEL.get(), LapidaryWheelBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LapidaryWheelBlockEntity wheel) {
            WorkSessionManager.onStationRemoved(level, pos);
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
            WheelOperations.use(sp, wheel, hand);
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
