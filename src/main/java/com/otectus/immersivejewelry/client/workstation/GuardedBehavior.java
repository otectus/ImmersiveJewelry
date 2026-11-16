package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * What Diegesis holds for a Jewelry station: the real behaviour behind a guard. Diegesis catches {@link RuntimeException}s from
 * station code, but a Diegesis whose API changed throws {@link LinkageError}s ({@code NoSuchMethodError}, {@code
 * AbstractMethodError}), which would end the game on the render thread. Here every callback is wrapped: the first such error
 * turns the client half of the integration off for the session ({@link DiegesisCompat.Feature#CLIENT}; the block entity
 * renderers draw the stations again), makes this and every station's behaviour inert, lets go of anything held, and tells the
 * player once that the work is kept and Escape leaves. Server-side work, leases and items are untouched.
 */
final class GuardedBehavior implements StationBehavior {
    private static boolean told;

    @Nullable
    private final WorkstationBehavior inner;

    private GuardedBehavior(@Nullable WorkstationBehavior inner) {
        this.inner = inner;
    }

    /** A guarded behaviour for a new station view; an inert one if even making the behaviour fails. */
    static StationBehavior wrap(Supplier<WorkstationBehavior> make) {
        try {
            return new GuardedBehavior(make.get());
        } catch (LinkageError e) {
            fail(null, "a station behaviour's start", e);
            return new GuardedBehavior(null);
        }
    }

    private boolean live() {
        return inner != null && DiegesisCompat.has(DiegesisCompat.Feature.CLIENT);
    }

    private static void fail(@Nullable WorkstationBehavior inner, String where, LinkageError e) {
        DiegesisCompat.disable(DiegesisCompat.Feature.CLIENT, where, e);
        if (inner != null) {
            try {
                inner.releaseAll();
                inner.forgetKeys();
            } catch (LinkageError | RuntimeException ignored) {
                // Nothing more can be let go of.
            }
        }
        tell();
    }

    /** Once a session: the station's hands-on half is off, the work is kept, Escape leaves. */
    static void tell() {
        if (told) return;
        told = true;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("workstation.immersive_jewelry.incompatible",
                    DiegesisCompat.installedVersion()).withStyle(ChatFormatting.GOLD), false);
        }
    }

    private void run(String where, Runnable action) {
        if (!live()) return;
        try {
            action.run();
        } catch (LinkageError e) {
            fail(inner, where, e);
        }
    }

    private boolean ask(String where, Supplier<Boolean> question) {
        if (!live()) return false;
        try {
            return question.get();
        } catch (LinkageError e) {
            fail(inner, where, e);
            return false;
        }
    }

    @Override
    public void onSceneReady(StationView view) {
        run("onSceneReady", () -> inner.onSceneReady(view));
    }

    @Override
    public void onOpen(StationView view) {
        if (!live()) {
            tell();
            return;
        }
        run("onOpen", () -> inner.onOpen(view));
    }

    @Override
    public void onClose(StationView view) {
        run("onClose", () -> inner.onClose(view));
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        run("frame", () -> inner.frame(view, deltaSeconds));
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        run("onEvent", () -> inner.onEvent(view, event));
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        return ask("onItemClick", () -> inner.onItemClick(view, node, stack, button, shift));
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return ask("onScroll", () -> inner.onScroll(view, node, amount));
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        return ask("onInventoryShiftClick", () -> inner.onInventoryShiftClick(view, slot, stack));
    }

    /** Diegesis 1.0.0-beta.11's key hook. With a Diegesis without it this is a plain method nobody calls (keys then take Forge's path). */
    @Override
    public boolean onKey(StationView view, int key, int scan, int modifiers) {
        return ask("onKey", () -> inner.onKey(view, key, scan, modifiers));
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return ask("onChar", () -> inner.onChar(view, character, modifiers));
    }
}
