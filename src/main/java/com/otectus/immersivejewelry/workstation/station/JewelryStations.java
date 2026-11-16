package com.otectus.immersivejewelry.workstation.station;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two workstations as Diegesis stations, on the common side: their ids and rules. This class imports nothing from
 * Diegesis, so it loads whatever Diegesis is installed; the library registration itself is {@link DiegesisStations}, reached
 * only when {@link DiegesisCompat} found what it needs. Diegesis owns the focus camera, the scene, picking and the inventory
 * drawer; Jewelry owns everything that changes a work. The stations are exclusive: one operator, everyone else watches.
 *
 * <p>The wheel declares no slots: its stone and lap move only through {@link WheelOperations}. The bench declares exactly two
 * groups when {@link DiegesisCompat.Feature#NATIVE_STORAGE} is on, both {@code kept()} and both bound to arrays its block entity
 * owns (one owner, never a second inventory): {@value #SUPPLIES}, its three fixed supply jars, and {@value #STAGING}, the four
 * loose stacks of the staging drawer ({@code placed()}). Native gestures reach those two groups only, and only through
 * {@code WorkstationLogic}; the clamp, the sockets, the tray and the dock stay Jewelry operations.</p>
 *
 * <p>Using a station opens it only with nothing a walk-up action would place in the main hand and without sneaking;
 * otherwise the block's own use runs (mount, place, take). Both paths consult the same lease. When the stations could not be
 * registered ({@link #available()} false), the block's own use is all there is, and it says so.</p>
 */
public final class JewelryStations {
    public static final ResourceLocation WHEEL_ID = ImmersiveJewelry.id("lapidary_wheel");
    public static final ResourceLocation BENCH_ID = ImmersiveJewelry.id("jewelers_bench");
    /** Reach in blocks from the eyes to the station's centre while working there. */
    public static final double REACH = 5.0;
    /** The bench's fixed supply jars (bound to {@code JewelersBenchBlockEntity}'s supplies). */
    public static final String SUPPLIES = "supplies";
    /** The bench's staging drawer: loose, hand-placed stacks (bound to {@code JewelersBenchBlockEntity}'s staging array). */
    public static final String STAGING = "staging";

    private static boolean registered;
    private static boolean nativeStorage;

    /**
     * Registers both stations with Diegesis, the same on both sides, during mod construction: only when the library has what
     * they need, and never letting a library change escape as a crash (the stations are then simply not registered).
     */
    public static void register() {
        if (!DiegesisCompat.has(DiegesisCompat.Feature.STATIONS)) return;
        boolean storage = DiegesisCompat.has(DiegesisCompat.Feature.NATIVE_STORAGE);
        try {
            DiegesisStations.register(storage);
            registered = true;
            nativeStorage = storage;
        } catch (LinkageError | RuntimeException e) {
            DiegesisCompat.disable(DiegesisCompat.Feature.STATIONS, "the station registration", e);
        }
    }

    /** Whether the hands-on stations are registered with Diegesis (else only the blocks' walk-up use works). */
    public static boolean available() {
        return registered && DiegesisCompat.has(DiegesisCompat.Feature.STATIONS);
    }

    /** Whether the bench's jars and drawer are library slots bound to the bench (else Jewelry commands only). */
    public static boolean nativeStorage() {
        return available() && nativeStorage && DiegesisCompat.has(DiegesisCompat.Feature.NATIVE_STORAGE);
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

    /** Ends the player's focus at whatever station they work (server side); nothing when the stations are not registered. */
    public static void close(ServerPlayer player) {
        if (available()) DiegesisCompat.run(DiegesisCompat.Feature.STATIONS, "closing a station", () -> DiegesisStations.close(player));
    }

    private JewelryStations() {}
}
