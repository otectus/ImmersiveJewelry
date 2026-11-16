package com.otectus.immersivejewelry.workstation.station;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Diegesis registration of the two stations. Only {@link JewelryStations#register()} reaches this class, and only after
 * {@code DiegesisCompat} found every member it uses; nothing else should name it, so a Diegesis without them never loads it.
 */
public final class DiegesisStations {
    public static StationType WHEEL;
    public static StationType BENCH;
    /**
     * The bench's schema, built once: the library recognises a {@code StationHost}'s state by this very instance. With native
     * storage, the jars and the drawer; without it, nothing (every move is then a Jewelry command).
     */
    public static StationSchema BENCH_SCHEMA;

    static void register(boolean nativeStorage) {
        StationSchema.Builder bench = StationSchema.builder();
        if (nativeStorage) {
            bench.slots(JewelryStations.SUPPLIES, JewelersBenchBlockEntity.SUPPLIES, SlotRules.DEFAULT.kept().accepts(BenchOperations::maybeSupply));
            bench.slots(JewelryStations.STAGING, JewelersBenchBlockEntity.STAGING, SlotRules.DEFAULT.kept().placed().accepts(BenchOperations::isStageable));
        }
        BENCH_SCHEMA = bench.build();
        WHEEL = Diegesis.station(JewelryStations.WHEEL_ID)
                .block(ModBlocks.LAPIDARY_WHEEL)
                .schema(StationSchema.builder().build())
                .orientation(StationType.Orientation.BLOCK)
                .reach(JewelryStations.REACH)
                .exclusive()
                .openWhen(JewelryStations::mayOpenWheel)
                .logic(WorkstationLogic::new)
                .register();
        BENCH = Diegesis.station(JewelryStations.BENCH_ID)
                .block(ModBlocks.JEWELERS_BENCH)
                .schema(BENCH_SCHEMA)
                .orientation(StationType.Orientation.BLOCK)
                .reach(JewelryStations.REACH)
                .exclusive()
                .openWhen(JewelryStations::mayOpenBench)
                .logic(WorkstationLogic::new)
                .register();
    }

    static void close(ServerPlayer player) {
        Diegesis.close(player);
    }

    private DiegesisStations() {}
}
