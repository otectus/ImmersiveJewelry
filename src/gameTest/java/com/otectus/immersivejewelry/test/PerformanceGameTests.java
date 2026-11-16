package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.network.packet.WorkInputPacket;
import com.otectus.immersivejewelry.registry.ModBlocks;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The handoff's server budget (section 14.3), measured: eight operators grinding at eight wheels at once, every one of
 * them sending its fixed-step stream at the real pace (two or three steps a tick, batched as the client batches), and
 * the size of what that costs on the wire. Logs {@code PERF ...} lines; fails only past the budget (1 ms a tick).
 */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    private static final BlockPos[] WHEELS = {new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(4, 1, 0), new BlockPos(0, 1, 2),
            new BlockPos(4, 1, 2), new BlockPos(0, 1, 4), new BlockPos(2, 1, 4), new BlockPos(4, 1, 4)};
    private static final int WARMUP = 40;
    private static final int MEASURED = 160;

    private record Station(LapidaryWheelBlockEntity wheel, TestSupport.Operator op, List<WorkSample> pending) {}

    @GameTest(template = "empty")
    public static void eightOperatorsStayWithinTheServerBudget(GameTestHelper h) {
        List<Station> stations = new ArrayList<>();
        for (int i = 0; i < WHEELS.length; i++) {
            h.setBlock(WHEELS[i], ModBlocks.LAPIDARY_WHEEL.get());
            BlockPos abs = h.absolutePos(WHEELS[i]);
            LapidaryWheelBlockEntity wheel = (LapidaryWheelBlockEntity) h.getLevel().getBlockEntity(abs);
            wheel.setStone(RoughGemItem.create(GemData.rough(TestSupport.DIAMOND, 1F, 0.7F, 0.1F)));
            FakePlayer player = TestSupport.player(h);
            player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5 + (WHEELS[i].getZ() == 4 ? -1.5 : 1.5));
            WorkSessionManager.Lease lease = WorkSessionManager.acquire(player, wheel);
            h.assertTrue(lease != null, "lease " + i);
            WheelOperations.planCut(player, wheel, ImmersiveJewelry.id("brilliant"), false);
            h.assertTrue(wheel.work().isPresent(), "planned " + i);
            stations.add(new Station(wheel, new TestSupport.Operator(player, abs, lease), new ArrayList<>()));
        }
        // Leases belong to listed players: the server lifts a FakePlayer's at its next tick. So the 200 ticks' worth of
        // input (on the operators' own 20 ms test clocks, exactly as paced as real input) is replayed within this tick,
        // and the time is divided by the ticks it stands for.
        long nanos = 0L;
        int samples = 0;
        for (int tick = 0; tick < WARMUP + MEASURED; tick++) {
            // 50 steps a second is two and a half a tick; the client sends five at a time, ten batches a second.
            int steps = tick % 2 == 0 ? 2 : 3;
            long start = System.nanoTime();
            for (Station s : stations) {
                for (int k = 0; k < steps; k++) {
                    float sweep = (float) Math.sin((tick * 2.5 + k) * 0.07) * 0.7F;
                    s.pending().add(new WorkSample(SimIO.toSigned12(sweep), SimIO.toUnit12(0.25F), 0, 0, WorkSample.TRIM_BIAS, 0));
                }
                if (s.pending().size() >= 5) {
                    CutWork work = s.wheel().work().orElseThrow();
                    s.op().feed(work.id(), List.copyOf(s.pending()));
                    if (tick >= WARMUP) samples += s.pending().size();
                    s.pending().clear();
                }
                // What the station's own tick does while it is worked (a sync at most every second tick).
                LapidaryWheelBlockEntity.serverTick(h.getLevel(), s.wheel().getBlockPos(), s.wheel().getBlockState(), s.wheel());
            }
            if (tick >= WARMUP) nanos += System.nanoTime() - start;
        }
        double perTickMs = nanos / 1e6 / MEASURED;
        LapidaryWheelBlockEntity first = stations.get(0).wheel();
        int tagBytes = nbtBytes(first);
        int packetBytes = packetBytes(stations.get(0));
        // The station syncs at most every second tick while worked: ten updates a second to each watching player.
        ImmersiveJewelry.LOGGER.info("PERF eight operators: {} ms per server tick over {} ticks ({} samples applied); "
                        + "a worked wheel's update tag {} bytes (~{} B/s to each watcher); one input batch {} bytes (~{} B/s per operator)",
                String.format(java.util.Locale.ROOT, "%.4f", perTickMs), MEASURED, samples, tagBytes, tagBytes * 10, packetBytes, packetBytes * 10);
        float progress = first.work().map(w -> w.sim().overallProgress()).orElse(0F);
        for (Station s : stations) {
            int stepped = s.wheel().work().map(w -> w.sim().steps()).orElse(-1);
            h.assertTrue(stepped == s.op().step, "every paced sample was admitted and stepped (" + stepped + " of " + s.op().step + ")");
        }
        h.assertTrue(progress > 0.05F, "the operators really ground (" + progress + ")");
        h.assertTrue(perTickMs < 1.0, "eight operators cost " + perTickMs + " ms a tick (budget 1 ms)");
        for (Station s : stations) WorkSessionManager.release(s.op().player.getUUID(), h.getLevel().getServer(), null, false);
        h.succeed();
    }

    private static int nbtBytes(LapidaryWheelBlockEntity wheel) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.write(wheel.getUpdateTag(), data);
            data.flush();
            return out.size();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int packetBytes(Station s) {
        List<WorkSample> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) five.add(new WorkSample(2048, SimIO.toUnit12(0.25F), 0, 0, WorkSample.TRIM_BIAS, 0));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new WorkInputPacket(s.op().pos, s.op().lease.token, 1L, 1, 0, five).encode(buf);
        return buf.readableBytes() + 1; // plus the channel's packet discriminator
    }

    private PerformanceGameTests() {}
}
