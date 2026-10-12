package com.otectus.immersivejewelry.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation and accessibility. Never changes an outcome except through the server-validated assist flags. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ForgeConfigSpec.BooleanValue LARGER_TARGETS;
    public static final ForgeConfigSpec.BooleanValue SLOWER_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue COLORBLIND_SAFE_COLORS;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue LARGE_MINIGAME_MARKERS;
    public static final ForgeConfigSpec.BooleanValue TIMING_CUE_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_CUE_VOLUME;
    public static final ForgeConfigSpec.IntValue FEEDBACK_LABEL_MS;
    public static final ForgeConfigSpec.BooleanValue STATION_NOTICES;
    public static final ForgeConfigSpec.IntValue NOTICE_SECONDS;

    public static final ForgeConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ForgeConfigSpec.DoubleValue GEM_SPARKLE_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue WORKSTATION_ANIMATIONS;

    public static final ForgeConfigSpec.BooleanValue SHOW_ADVANCED_TOOLTIPS;
    public static final ForgeConfigSpec.BooleanValue SHOW_GEM_SOURCE_HINTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_INVENTORY_BUTTON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Minigame accessibility").push("accessibility");
        SCREEN_SHAKE = b.comment("Strength of the screen shake when a stone chips (0 = none).")
                .defineInRange("minigameScreenShake", 1.0, 0.0, 1.0);
        REDUCED_MOTION = b.comment("Calmer screens: no shake, no pulsing highlights, steady animations.")
                .define("reducedMotion", false);
        LARGER_TARGETS = b.comment("Ask the server for wider facet and pressure windows (when the server allows assists).")
                .define("largerTargets", false);
        SLOWER_MINIGAMES = b.comment("Ask the server for slower grinding and pressure (when the server allows assists).")
                .define("slowerMinigames", false);
        COLORBLIND_SAFE_COLORS = b.comment("Blue and orange instead of green and red.")
                .define("colorblindSafeColors", false);
        HIGH_CONTRAST_MINIGAMES = b.comment("White and yellow markers on dark fields.")
                .define("highContrastMinigames", false);
        LARGE_MINIGAME_MARKERS = b.comment("Larger cursors, prongs and gauge needles.")
                .define("largeMinigameMarkers", false);
        TIMING_CUE_SOUNDS = b.comment("A soft tone when a gauge enters its target window.")
                .define("timingCueSounds", true);
        SOUND_CUE_VOLUME = b.defineInRange("soundCueVolume", 0.8, 0.0, 1.0);
        FEEDBACK_LABEL_MS = b.comment("How long a cutting or setting event (\"Facet cut\", \"Prong set\", \"Chipped\") stays readable, in",
                        "milliseconds. A newer event always replaces it at once.")
                .defineInRange("feedbackLabelMs", 2500, 500, 10000);
        STATION_NOTICES = b.comment("Show station messages (what a station needs, errors) above the hotbar, where they stay for",
                        "noticeSeconds and several can show at once. Off: the vanilla action bar, which the next message replaces.")
                .define("stationNotices", true);
        NOTICE_SECONDS = b.comment("How long a station message stays above the hotbar.")
                .defineInRange("noticeSeconds", 8, 2, 60);
        b.pop();

        b.comment("Visual effects").push("visuals");
        PARTICLE_DENSITY = b.comment("Multiplier for gem dust, sparks and sparkles.")
                .defineInRange("particleDensity", 1.0, 0.0, 2.0);
        GEM_SPARKLE_INTENSITY = b.comment("How strongly finely cut gems sparkle on items and in the world (0 = none).")
                .defineInRange("gemSparkleIntensity", 1.0, 0.0, 1.0);
        WORKSTATION_ANIMATIONS = b.comment("Spin the Lapidary Wheel and animate the bench workpiece.")
                .define("enableWorkstationAnimations", true);
        b.pop();

        b.comment("Tooltips and interface").push("interface");
        SHOW_ADVANCED_TOOLTIPS = b.comment("Holding Shift shows the percentages behind each quality label (if the server allows it).")
                .define("showAdvancedTooltips", true);
        SHOW_GEM_SOURCE_HINTS = b.comment("Items that can be cut at a Lapidary Wheel say so in their tooltip.")
                .define("showGemSourceHints", true);
        SHOW_INVENTORY_BUTTON = b.comment("The jewelry-slots button on the inventory screen (built-in slots only).")
                .define("showInventoryButton", true);
        b.pop();

        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ClientConfig() {}
}
