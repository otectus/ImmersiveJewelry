package com.otectus.immersivejewelry;

import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Input pacing of an operator lease: never faster than real time, duplicates and overlaps ignored, no fast-forwarded backlog. */
class WorkLeaseTest {

    private static WorkSessionManager.Lease lease(long now) throws Exception {
        Constructor<WorkSessionManager.Lease> c = WorkSessionManager.Lease.class.getDeclaredConstructor(UUID.class,
                net.minecraft.core.GlobalPos.class, int.class, long.class);
        c.setAccessible(true);
        return c.newInstance(UUID.randomUUID(), null, 7, now);
    }

    @Test
    void cannotRunAheadOfTheClock() throws Exception {
        WorkSessionManager.Lease lease = lease(0L);
        int[] skip = new int[1];
        // 100 ms after the lease: room for 5 steps (plus a little tolerance), however many are claimed.
        int granted = lease.admit(0, 0, 10, 100L, 0, skip);
        assertEquals(5, granted);
        assertEquals(10, lease.nextStep(), "Refused steps are dropped, never applied later");
    }

    @Test
    void duplicatesAndOverlapsAreIgnored() throws Exception {
        WorkSessionManager.Lease lease = lease(0L);
        int[] skip = new int[1];
        assertEquals(5, lease.admit(0, 0, 5, 1000L, 0, skip));
        assertEquals(0, lease.admit(0, 0, 5, 1100L, 0, skip), "The same batch again does nothing");
        assertEquals(2, lease.admit(1, 2, 5, 1200L, 0, skip), "Steps 2..4 were applied already: only 5 and 6 are new");
        assertEquals(3, skip[0]);
    }

    @Test
    void aLateBacklogIsBounded() throws Exception {
        WorkSessionManager.Lease lease = lease(0L);
        int[] skip = new int[1];
        // Ten seconds of silence, then a flood: at most about a second's worth (plus latency allowance) gets through.
        int granted = 0;
        for (int seq = 0; seq < 100; seq++) granted += lease.admit(seq, seq * 10, 10, 10_000L, 0, skip);
        assertEquals(true, granted <= 60, "granted " + granted);
    }
}
