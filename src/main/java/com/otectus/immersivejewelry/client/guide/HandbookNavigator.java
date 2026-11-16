package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.client.StationNotices;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.PatchouliAPI;

/**
 * Every way into the Jeweler's Handbook and its demonstrations, through Patchouli's public API only.
 *
 * <ul>
 *   <li>The handbook item and the handbook key open the book (Patchouli keeps the last page read) or, over an item in an
 *       inventory, that item's entry; nothing has to be carried.</li>
 *   <li>A "Watch" button remembers only where it was pressed (entry and page, a small navigation token) and opens the lesson;
 *       when the lesson closes, the book opens again at that page.</li>
 *   <li>Help at a station (the handbook key, or the folio's help) first lets go of every handle and tool through the
 *       station's own release, leaves the station (Diegesis brings the camera back and the server ends the lease, the work
 *       stays where it is), and opens the entry for the step under way once the camera is back; holding the key opens the
 *       demonstration instead. A return token (dimension, station, the work's revision) is kept: when the reader closes the
 *       book or the lesson, a notice says whether the station and the work are still as they were. Working again always takes
 *       a fresh use of the station; nothing resumes on its own and no held action survives.</li>
 * </ul>
 * Reading never pauses a server or the work of others at a station.
 */
public final class HandbookNavigator {
    public static final ResourceLocation BOOK = ImmersiveJewelry.id("jewelers_handbook");
    /** How long a station help request waits for the camera to come back before opening anyway. */
    private static final int HELP_WAIT_TICKS = 60;
    /** Holding the key this long opens the demonstration instead of the page (Ponder's own hold-to-watch habit). */
    public static final int HOLD_TO_WATCH_TICKS = 12;

    /** Opens a lesson (the Ponder adapter); false when it cannot. */
    public interface TutorialLauncher {
        boolean open(Lessons lesson);

        boolean available();

        /** Whether a lesson's screen is shown now. */
        boolean showing();
    }

    private static TutorialLauncher launcher = new TutorialLauncher() {
        @Override public boolean open(Lessons lesson) { return false; }
        @Override public boolean available() { return false; }
        @Override public boolean showing() { return false; }
    };

    private record Destination(ResourceLocation entry, int page) {}

    /** Where help was asked at a station: checked again when the reader comes back. */
    private record ReturnToken(ResourceKey<Level> dimension, BlockPos station, int revision) {}

    private static final class HelpRequest {
        final HelpTopics.Topic topic;
        boolean decided;
        boolean watch;
        int age;

        HelpRequest(HelpTopics.Topic topic, boolean decided, boolean watch) {
            this.topic = topic;
            this.decided = decided;
            this.watch = watch;
        }
    }

    @Nullable
    private static Destination returnTo;
    @Nullable
    private static ReturnToken token;
    @Nullable
    private static HelpRequest pending;
    private static boolean wasReading;
    private static boolean watching;
    /** Whether the lesson's screen has appeared yet (Ponder opens it through a short transition), and for how long we waited. */
    private static boolean lessonSeen;
    private static int lessonWait;

    public static void setLauncher(TutorialLauncher tutorials) {
        launcher = tutorials;
    }

    public static boolean tutorialsAvailable() {
        return launcher.available();
    }

    public static void openBook() {
        try {
            PatchouliAPI.get().openBookGUI(BOOK);
        } catch (RuntimeException e) {
            ImmersiveJewelry.LOGGER.error("Could not open the Jeweler's Handbook", e);
            StationNotices.show(Component.translatable("message.immersive_jewelry.handbook_unavailable"), true);
        }
    }

    public static void openEntry(ResourceLocation entry, int page) {
        try {
            PatchouliAPI.get().openBookEntry(BOOK, entry, Math.max(0, page));
        } catch (RuntimeException e) {
            ImmersiveJewelry.LOGGER.error("Could not open handbook entry {}", entry, e);
            openBook();
        }
    }

    /** Opens the topic: its page, or its demonstration when asked and available (else the page). */
    public static void open(HelpTopics.Topic topic, boolean watch) {
        if (watch && topic.lesson() != null && launcher.available()) {
            watch(topic.lesson(), topic.entry(), topic.page());
            return;
        }
        openEntry(topic.entry(), topic.page());
    }

    /** A "Watch" button: the lesson, then back to {@code entry}'s {@code page} when it closes. */
    public static void watch(Lessons lesson, @Nullable ResourceLocation entry, int page) {
        if (!launcher.available()) {
            StationNotices.show(Component.translatable("message.immersive_jewelry.tutorials_unavailable"), true);
            return;
        }
        returnTo = entry != null ? new Destination(entry, page) : null;
        if (launcher.open(lesson)) {
            watching = true;
            lessonSeen = false;
            lessonWait = 0;
        } else {
            returnTo = null;
            StationNotices.show(Component.translatable("message.immersive_jewelry.tutorial_missing"), true);
        }
    }

    /**
     * Help at a station: {@code release} lets go of everything held (the station's own path), the station's screen closes
     * (the lease ends, the work stays), and the topic opens once the camera is back. With {@code fromKey} a short press opens
     * the page and holding the key opens the demonstration.
     */
    public static void help(StationBlockEntity station, Runnable release, boolean fromKey, boolean watch) {
        HelpTopics.Topic topic = HelpTopics.station(station);
        try {
            release.run();
        } finally {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) token = new ReturnToken(mc.level.dimension(), station.getBlockPos().immutable(), revision(station));
            if (mc.screen != null && com.otectus.immersivejewelry.client.workstation.DiegesisAccess.isStationScreen(mc.screen)) mc.screen.onClose();
            else if (mc.screen != null) mc.setScreen(null);
            pending = new HelpRequest(topic, !fromKey, watch);
        }
    }

    private static int revision(StationBlockEntity station) {
        if (station instanceof com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity wheel) {
            return wheel.work().map(w -> w.revision()).orElse(-1);
        }
        if (station instanceof com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity bench && bench.work() != null) {
            return bench.work().revision();
        }
        return -1;
    }

    /** Client tick: a waiting help request is decided (tap or hold) and opens when the camera is back; returns are noticed. */
    public static void tick(boolean helpKeyHeld) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        boolean reading = BOOK.equals(PatchouliAPI.get().getOpenBookGui()) || launcher.showing();
        if (watching && launcher.showing()) lessonSeen = true;
        if (watching && !lessonSeen && ++lessonWait > 60) {
            // The lesson never appeared: nothing to return from.
            watching = false;
            returnTo = null;
        }
        if (watching && lessonSeen && !launcher.showing()) {
            watching = false;
            Destination destination = returnTo;
            returnTo = null;
            // Closed to the world: back to the page the lesson was opened from. Ponder's own back button returns by itself.
            if (destination != null && mc.screen == null) {
                openEntry(destination.entry(), destination.page());
                reading = true;
            }
        }
        if (wasReading && !reading && mc.screen == null && pending == null) returned(mc);
        wasReading = reading;
        HelpRequest request = pending;
        if (request == null) return;
        request.age++;
        if (!request.decided) {
            if (!helpKeyHeld) request.decided = true;
            else if (request.age >= HOLD_TO_WATCH_TICKS) {
                request.decided = true;
                request.watch = true;
            }
        }
        if (!request.decided) return;
        boolean back = !com.otectus.immersivejewelry.client.workstation.DiegesisAccess.isFocused() && mc.screen == null;
        if (!back && request.age <= HELP_WAIT_TICKS) return;
        pending = null;
        open(request.topic, request.watch);
        wasReading = true;
    }

    /** The reader is back in the world: say whether the station they left is as they left it. */
    private static void returned(Minecraft mc) {
        ReturnToken left = token;
        token = null;
        if (left == null) return;
        boolean same = mc.level.dimension().equals(left.dimension()) && mc.level.isLoaded(left.station())
                && mc.level.getBlockEntity(left.station()) instanceof StationBlockEntity station && revision(station) == left.revision();
        StationNotices.show(Component.translatable(same ? "message.immersive_jewelry.help_return" : "message.immersive_jewelry.help_return_changed"),
                false);
    }

    /** Whether a help request waits for the camera (the client checks). */
    public static boolean helpPending() {
        return pending != null;
    }

    /** Leaving a world: no destination or token survives it. */
    public static void reset() {
        returnTo = null;
        token = null;
        pending = null;
        wasReading = false;
        watching = false;
        lessonSeen = false;
    }

    private HandbookNavigator() {}
}
