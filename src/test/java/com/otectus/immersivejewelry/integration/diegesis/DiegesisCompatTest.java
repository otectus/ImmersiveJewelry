package com.otectus.immersivejewelry.integration.diegesis;

import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compatibility probe against the Diegesis Jewelry is built with (on the test classpath): every member it lists exists
 * there exactly as listed, so a typo can never switch a feature off by itself; and against lookups with members taken away
 * or changed, the way a newer Diegesis could look: only the features that need them go, with the features that need those.
 */
class DiegesisCompatTest {
    private static final String API = "com.pockyl.diegesis.api.";

    private static Class<?> real(String name) throws ClassNotFoundException {
        return Class.forName(name, false, DiegesisCompatTest.class.getClassLoader());
    }

    @Test
    void theTestedReleaseHasEveryMemberJewelryUses() {
        DiegesisCompat.Report report = DiegesisCompat.probe(DiegesisCompatTest::real, true);
        assertEquals(Map.of(), report.missing(), "members the probe lists but the tested Diegesis lacks");
        assertEquals(EnumSet.allOf(Feature.class), report.available());
    }

    @Test
    void aServerProbesOnlyTheCommonFeatures() {
        DiegesisCompat.Report report = DiegesisCompat.probe(DiegesisCompatTest::real, false);
        assertEquals(EnumSet.of(Feature.STATIONS, Feature.NATIVE_STORAGE), report.available());
    }

    /** A lookup that hides one class, as if a newer Diegesis had renamed or removed it. */
    private static DiegesisCompat.Lookup without(String hidden) {
        return name -> {
            if (name.equals(hidden)) throw new ClassNotFoundException(name);
            return real(name);
        };
    }

    @Test
    void aMissingOptionalMethodTurnsOffOnlyItsFeature() {
        // Matrix4fc is only a parameter of the drawer's surface transform: that one method no longer links.
        DiegesisCompat.Report report = DiegesisCompat.probe(without("org.joml.Matrix4fc"), true);
        assertEquals(EnumSet.complementOf(EnumSet.of(Feature.DRAWER_MOTION)), report.available());
        assertTrue(report.missing().get(Feature.DRAWER_MOTION).get(0).contains("setSurfaceTransform"));
    }

    @Test
    void aMissingHostTurnsOffNativeStorageButNotTheStations() {
        DiegesisCompat.Report report = DiegesisCompat.probe(without(API + "state.StationHost"), true);
        assertFalse(report.has(Feature.NATIVE_STORAGE));
        assertTrue(report.has(Feature.STATIONS) && report.has(Feature.CLIENT) && report.has(Feature.DRAWER_MOTION));
    }

    @Test
    void aMissingCoreClassTurnsOffEverythingThatNeedsIt() {
        DiegesisCompat.Report stations = DiegesisCompat.probe(without(API + "Diegesis"), true);
        assertEquals(Set.of(), stations.available(), "nothing works without the station registration");
        DiegesisCompat.Report client = DiegesisCompat.probe(without(API + "client.DiegesisClient"), true);
        assertEquals(EnumSet.of(Feature.STATIONS, Feature.NATIVE_STORAGE), client.available(), "the server half stands alone");
    }

    /** An interface Jewelry implements that gained an abstract method: Jewelry's class would no longer be complete. */
    interface GrownHost {
        Object stationState();

        Object somethingNew();
    }

    @Test
    void anInterfaceThatGainedAnAbstractMethodIsNotImplemented() {
        DiegesisCompat.Report report = DiegesisCompat.probe(name -> name.equals(API + "state.StationHost") ? GrownHost.class : real(name), true);
        assertFalse(report.has(Feature.NATIVE_STORAGE));
        assertTrue(report.missing().get(Feature.NATIVE_STORAGE).get(0).contains("somethingNew"));
    }

    /** The library's default {@code mayPlace} is permissive: when the hook Jewelry overrides is gone, its slots are never bound. */
    @Test
    void withoutTheSlotGateHookTheSlotsAreNotUsed() {
        DiegesisCompat.Report report = DiegesisCompat.probe(name -> name.equals(API + "station.StationLogic") ? Runnable.class : real(name), true);
        assertFalse(report.has(Feature.NATIVE_STORAGE));
        assertFalse(report.has(Feature.STATIONS), "the lifecycle hooks are gone too");
    }
}
