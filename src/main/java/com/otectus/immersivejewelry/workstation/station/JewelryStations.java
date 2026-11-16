package com.otectus.immersivejewelry.workstation.station;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The adapter between Immersive Jewelry and Diegesis on the common side: the two workstations as Diegesis stations.
 * Diegesis owns the focus camera, the scene, picking and the inventory drawer; Jewelry owns everything that changes
 * an item or a work. The stations therefore declare no slots of their own (every item move goes through Jewelry's
 * operations) and are exclusive: one operator, everyone else watches the shared scene.
 *
 * <p>Using a station opens it only with nothing a walk-up action would place in the main hand and without sneaking;
 * otherwise the block's own use runs (mount, place, take). Both paths consult the same lease.</p>
 */
public final class JewelryStations {
    public static final ResourceLocation WHEEL_ID = ImmersiveJewelry.id("lapidary_wheel");
    public static final ResourceLocation BENCH_ID = ImmersiveJewelry.id("jewelers_bench");
    /** Reach in blocks from the eyes to the station's centre while working there. */
    public static final double REACH = 5.0;

    public static StationType WHEEL;
    public static StationType BENCH;

    public static void register() {
        WHEEL = Diegesis.station(WHEEL_ID)
                .block(ModBlocks.LAPIDARY_WHEEL)
                .schema(StationSchema.builder().build())
                .orientation(StationType.Orientation.BLOCK)
                .reach(REACH)
                .exclusive()
                .openWhen(JewelryStations::mayOpenWheel)
                .logic(WorkstationLogic::new)
                .register();
        BENCH = Diegesis.station(BENCH_ID)
                .block(ModBlocks.JEWELERS_BENCH)
                .schema(StationSchema.builder().build())
                .orientation(StationType.Orientation.BLOCK)
                .reach(REACH)
                .exclusive()
                .openWhen(JewelryStations::mayOpenBench)
                .logic(WorkstationLogic::new)
                .register();
    }

    static boolean mayOpenWheel(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive() || busy(player, pos)) return false;
        ItemStack held = player.getMainHandItem();
        return !WheelOperations.isWalkUpItem(held, JewelryDefinitions.get(player.level()));
    }

    static boolean mayOpenBench(Player player, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive() || busy(player, pos)) return false;
        return !BenchOperations.isWalkUpItem(player.getMainHandItem());
    }

    private static boolean busy(Player player, BlockPos pos) {
        return player.level().getBlockEntity(pos) instanceof StationBlockEntity station && station.isOperatedByOther(player.getUUID());
    }

    /** Ends the player's focus at whatever station they work (server side). */
    public static void close(ServerPlayer player) {
        Diegesis.close(player);
    }

    private JewelryStations() {}
}
