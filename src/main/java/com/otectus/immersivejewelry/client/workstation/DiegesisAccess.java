package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat;
import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.SceneDefinition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every static call Jewelry's client code makes into Diegesis, in one place. Each answers safely when the stations are not
 * registered or the client half of the library is unusable (not focused, no station, the scene not drawn, nothing hovered),
 * and a call that fails to link (a Diegesis whose method changed) turns {@link Feature#CLIENT} off instead of escaping into
 * the render thread. Renderers, the handbook, JEI and the workstation client go through here, never to {@code DiegesisClient}
 * directly. Calls are written as lambdas, never method references: a lambda's body links when it runs, inside the guard, where
 * a method reference would link while the argument is made, outside it.
 */
public final class DiegesisAccess {

    /** Whether the hands-on stations work on this client. */
    public static boolean ready() {
        return JewelryStations.available() && DiegesisCompat.has(Feature.CLIENT);
    }

    private static <T> T call(String what, T fallback, Supplier<T> call) {
        if (!ready()) return fallback;
        try {
            return call.get();
        } catch (LinkageError e) {
            DiegesisCompat.disable(Feature.CLIENT, what, e);
            return fallback;
        }
    }

    public static boolean isFocused() {
        return call("DiegesisClient.isFocused", false, () -> DiegesisClient.isFocused());
    }

    public static Optional<StationView> focusedStation() {
        return call("DiegesisClient.focusedStation", Optional.empty(), () -> DiegesisClient.focusedStation());
    }

    public static Optional<StationView> station(BlockPos pos) {
        return call("DiegesisClient.station", Optional.empty(), () -> DiegesisClient.station(pos));
    }

    public static boolean isStationScreen(Screen screen) {
        return screen != null && call("DiegesisClient.isStationScreen", false, () -> DiegesisClient.isStationScreen(screen));
    }

    public static ItemStack hoveredStack() {
        return call("DiegesisClient.hoveredStack", ItemStack.EMPTY, () -> DiegesisClient.hoveredStack());
    }

    /** Whether the scene draws the station at {@code pos} now (its block entity renderer then steps aside). */
    public static boolean isSceneDrawn(BlockPos pos) {
        return call("DiegesisClient.isSceneDrawn", false, () -> DiegesisClient.isSceneDrawn(pos));
    }

    static void registerBehavior(ResourceLocation station, StationBehavior.Factory factory) {
        call("DiegesisClient.registerBehavior", null, () -> {
            DiegesisClient.registerBehavior(station, factory);
            return null;
        });
    }

    static boolean registerScene(ResourceLocation station, SceneDefinition scene) {
        return call("DiegesisClient.registerScene", false, () -> {
            DiegesisClient.registerScene(station, scene);
            return true;
        });
    }

    public static void registerChildScreen(Predicate<Screen> test) {
        call("DiegesisClient.registerChildScreen", null, () -> {
            DiegesisClient.registerChildScreen(test);
            return null;
        });
    }

    private DiegesisAccess() {}
}
