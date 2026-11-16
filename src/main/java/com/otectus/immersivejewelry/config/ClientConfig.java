package com.otectus.immersivejewelry.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Presentation and accessibility. Purely local: nothing here changes an outcome, except the two assist wishes
 * ({@link #LARGER_TARGETS}, {@link #SLOWER_MINIGAMES}) the server may honour under its own assist policy. Comfort and
 * accessibility options carry no hidden quality cost.
 */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue REDUCED_MOTION;
    public static final ForgeConfigSpec.BooleanValue LARGER_TARGETS;
    public static final ForgeConfigSpec.BooleanValue SLOWER_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue COLORBLIND_SAFE_COLORS;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue TIMING_CUE_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_CUE_VOLUME;
    public static final ForgeConfigSpec.IntValue FEEDBACK_LABEL_MS;
    public static final ForgeConfigSpec.BooleanValue STATION_NOTICES;
    public static final ForgeConfigSpec.IntValue NOTICE_SECONDS;

    public static final ForgeConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ForgeConfigSpec.DoubleValue GEM_SPARKLE_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue WORKSTATION_ANIMATIONS;

    public static final ForgeConfigSpec.BooleanValue NO_ZOOM;
    public static final ForgeConfigSpec.BooleanValue INSTANT_TRANSITIONS;
    public static final ForgeConfigSpec.BooleanValue WORKSTATION_HANDS;
    public static final ForgeConfigSpec.DoubleValue WORKSTATION_ARM_OPACITY;
    public static final ForgeConfigSpec.BooleanValue TOGGLE_TOOL_USE;
    public static final ForgeConfigSpec.BooleanValue STICKY_DRAG;
    public static final ForgeConfigSpec.DoubleValue POINTER_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue TARGET_SCALE;
    public static final ForgeConfigSpec.BooleanValue NUMERIC_GUIDE;
    public static final ForgeConfigSpec.BooleanValue WORK_HINTS;
    public static final ForgeConfigSpec.BooleanValue VISUAL_CUES;
    public static final ForgeConfigSpec.DoubleValue TOOL_SOUND_VOLUME;
    public static final ForgeConfigSpec.BooleanValue GEM_TRANSLUCENCY;
    public static final ForgeConfigSpec.BooleanValue ASSISTED_SWEEP;
    public static final ForgeConfigSpec.BooleanValue DRAWER_SHOWS_EVERYTHING;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FOLIO_FAVORITES;
    public static final ForgeConfigSpec.EnumValue<WorkingSide> WORKING_SIDE;
    public static final ForgeConfigSpec.EnumValue<LooseItemMotion> LOOSE_ITEM_MOTION;
    public static final ForgeConfigSpec.BooleanValue WORK_LAMP;

    /** Which side of a station the camera looks from: the authored shot from the front right, or its mirror image. */
    public enum WorkingSide { RIGHT, LEFT }

    /** How loose stacks in the bench's staging drawer move: by the scene's physics, or standing exactly as put. */
    public enum LooseItemMotion { AUTO, NATURAL, CALM }

    public static final ForgeConfigSpec.EnumValue<BodyAnimation> BODY_ANIMATION;
    public static final ForgeConfigSpec.BooleanValue REDUCED_TUTORIAL_MOTION;
    public static final ForgeConfigSpec.BooleanValue SHOW_WORN_JEWELRY;
    public static final ForgeConfigSpec.BooleanValue SHOW_WORN_RINGS;
    public static final ForgeConfigSpec.BooleanValue SHOW_WORN_AMULETS;
    public static final ForgeConfigSpec.BooleanValue SHOW_WORN_BRACELETS;
    public static final ForgeConfigSpec.BooleanValue SHOW_WORN_CIRCLETS;

    /** How much of a jeweler's body moves with the work (playerAnimator), for you and for those you watch. */
    public enum BodyAnimation { FULL, REDUCED, OFF }

    public static final ForgeConfigSpec.BooleanValue SHOW_ADVANCED_TOOLTIPS;
    public static final ForgeConfigSpec.BooleanValue SHOW_GEM_SOURCE_HINTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_INVENTORY_BUTTON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Minigame accessibility").push("accessibility");
        SCREEN_SHAKE = b.comment("Strength of the jolt of the wheel when a stone chips (0 = none).")
                .defineInRange("minigameScreenShake", 1.0, 0.0, 1.0);
        REDUCED_MOTION = b.comment("Calmer stations: no jolt, no pulsing highlights, slower keyboard handles, steady animations.")
                .define("reducedMotion", false);
        LARGER_TARGETS = b.comment("Ask the server for wider depth windows and a wider secure band at the workstations (when the server",
                        "allows assists). Applies to work planned from then on.")
                .define("largerTargets", false);
        SLOWER_MINIGAMES = b.comment("Ask the server for a gentler work rate at the workstations (when the server allows assists).")
                .define("slowerMinigames", false);
        COLORBLIND_SAFE_COLORS = b.comment("Blue and orange instead of green and red.")
                .define("colorblindSafeColors", false);
        HIGH_CONTRAST_MINIGAMES = b.comment("White and yellow markers on dark fields.")
                .define("highContrastMinigames", false);
        TIMING_CUE_SOUNDS = b.comment("Soft cue tones at the stations: a face reaching its depth, a stone secured, a stone running warm.")
                .define("timingCueSounds", true);
        SOUND_CUE_VOLUME = b.defineInRange("soundCueVolume", 0.8, 0.0, 1.0);
        FEEDBACK_LABEL_MS = b.comment("How long a station cue (\"Face done\", \"Stone secured\", \"Chipped\") stays readable, in",
                        "milliseconds. A newer cue always replaces it at once.")
                .defineInRange("feedbackLabelMs", 2500, 500, 10000);
        STATION_NOTICES = b.comment("Show station messages (what a station needs, errors) above the hotbar, where they stay for",
                        "noticeSeconds and several can show at once. Off: the vanilla action bar, which the next message replaces.")
                .define("stationNotices", true);
        NOTICE_SECONDS = b.comment("How long a station message stays above the hotbar.")
                .defineInRange("noticeSeconds", 8, 2, 60);
        b.pop();

        b.comment("Workstations: camera, controls and feedback while working at the Lapidary Wheel or the Jeweler's Bench").push("workstation");
        NO_ZOOM = b.comment("Keep your own field of view at the stations and inspect with the loupe without magnifying.")
                .define("noZoom", false);
        INSTANT_TRANSITIONS = b.comment("The camera moves to and from a station, and to the loupe, at once instead of easing.")
                .define("instantTransitions", false);
        WORKSTATION_HANDS = b.comment("Show your vanilla-sized arms holding the tools. Off: the tools alone (for skins or player-model mods that clash).")
                .define("workstationHands", true);
        WORKSTATION_ARM_OPACITY = b.comment("Opacity of workstation arms: 0 = hidden, 1 = opaque skin. Translucent arms fade towards the shoulder;",
                        "sleeves use a lighter overlay so the work stays visible. Arm proportions always match vanilla.")
                .defineInRange("workstationArmOpacity", 0.45, 0.0, 1.0);
        TOGGLE_TOOL_USE = b.comment("Tool use with the keyboard key toggles instead of being held.")
                .define("toggleToolUse", false);
        STICKY_DRAG = b.comment("A click picks up a handle or tool and the next click puts it down, instead of holding the mouse button.")
                .define("stickyDrag", false);
        POINTER_SENSITIVITY = b.comment("How far a handle or tool moves for a given mouse movement.")
                .defineInRange("pointerSensitivity", 1.0, 0.25, 4.0);
        TARGET_SCALE = b.comment("Size of the zones that pick a prong, socket, facet or surface (larger never overlaps two targets).")
                .defineInRange("targetScale", 1.0, 0.75, 2.0);
        NUMERIC_GUIDE = b.comment("Show numbers next to the work: depth, force, closure and coverage.")
                .define("numericGuide", false);
        WORK_HINTS = b.comment("Short hints with the key to press: \"Lift away\", \"Close this prong\".")
                .define("workHints", true);
        VISUAL_CUES = b.comment("On-screen symbols for the important contact sounds (depth reached, secure contact, chip, heat).")
                .define("visualCues", true);
        TOOL_SOUND_VOLUME = b.comment("Volume of the tool and contact sounds (the cue tones have soundCueVolume).")
                .defineInRange("toolSoundVolume", 1.0, 0.0, 1.0);
        GEM_TRANSLUCENCY = b.comment("Draw gemstones see-through at the stations (opaque stones stay opaque). Off: solid, shader-free.")
                .define("gemTranslucency", false);
        ASSISTED_SWEEP = b.comment("Keyboard grinding sweeps the stone across the lap by itself while contact is held.")
                .define("assistedSweep", true);
        DRAWER_SHOWS_EVERYTHING = b.comment("The inventory drawer at a station lists every stack you carry. Off: only what that station can use.")
                .define("drawerShowsEverything", false);
        FOLIO_FAVORITES = b.comment("Designs starred in the bench's design folio.")
                .defineListAllowEmpty(java.util.List.of("folioFavorites"), java.util.List::of, o -> o instanceof String);
        WORKING_SIDE = b.comment("The side the station camera looks from: RIGHT is the authored shot from the front right, LEFT its mirror image."
                        + " Changes only the view: sockets, numbers, tools and the work are the same either way.")
                .defineEnum("workingSide", WorkingSide.RIGHT);
        LOOSE_ITEM_MOTION = b.comment("Loose stacks in the bench's staging drawer: NATURAL settle, tip and push each other (presentation only),"
                        + " CALM stand exactly where they are put, AUTO is CALM with reduced motion and NATURAL otherwise.")
                .defineEnum("looseItemMotion", LooseItemMotion.AUTO);
        WORK_LAMP = b.comment("The bench's work lamp lights the close-up of the bench (a steady scene light; it never lights the world).")
                .define("workLamp", true);
        b.pop();

        b.comment("Visual effects").push("visuals");
        PARTICLE_DENSITY = b.comment("Multiplier for gem dust, sparks and sparkles.")
                .defineInRange("particleDensity", 1.0, 0.0, 2.0);
        GEM_SPARKLE_INTENSITY = b.comment("How strongly finely cut gems sparkle on items and in the world (0 = none).")
                .defineInRange("gemSparkleIntensity", 1.0, 0.0, 1.0);
        WORKSTATION_ANIMATIONS = b.comment("Decorative workstation animation: the lap's idle spin, settling stones, the tilt of a finished piece.")
                .define("enableWorkstationAnimations", true);
        b.pop();

        b.comment("Presentation: body poses, tutorials and worn jewelry (local only, never an outcome)").push("presentation");
        BODY_ANIMATION = b.comment("Jewelers' body poses while they work at a station (you, seen from outside, and the players you watch).",
                        "FULL: authored poses; REDUCED: smaller, calmer motion; OFF: no body animation (the work is unaffected).")
                .defineEnum("bodyAnimation", BodyAnimation.FULL);
        REDUCED_TUTORIAL_MOTION = b.comment("Demonstrations (Ponder) keep the camera still and use the calmer poses. reducedMotion implies it.")
                .define("reducedTutorialMotion", false);
        SHOW_WORN_JEWELRY = b.comment("Draw worn jewelry on players (rings, amulets, bracelets, circlets). Hidden or cosmetic-off slots stay hidden.")
                .define("showWornJewelry", true);
        SHOW_WORN_RINGS = b.define("showWornRings", true);
        SHOW_WORN_AMULETS = b.define("showWornAmulets", true);
        SHOW_WORN_BRACELETS = b.define("showWornBracelets", true);
        SHOW_WORN_CIRCLETS = b.comment("Per form: draw that form on players (showWornJewelry must be on).")
                .define("showWornCirclets", true);
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

    public static <E extends Enum<E>> E get(ForgeConfigSpec.EnumValue<E> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** Calmer demonstrations: the tutorial option or the general reduced-motion option. */
    public static boolean reducedTutorialMotion() {
        return get(REDUCED_TUTORIAL_MOTION) || get(REDUCED_MOTION);
    }

    /** The body animation level, reduced whenever reduced motion is asked for. */
    public static BodyAnimation bodyAnimation() {
        BodyAnimation level = get(BODY_ANIMATION);
        return level == BodyAnimation.FULL && get(REDUCED_MOTION) ? BodyAnimation.REDUCED : level;
    }

    /** Whether staged stacks stand still (calm loose items): asked for, or by reduced motion. */
    public static boolean calmLooseItems() {
        LooseItemMotion motion = get(LOOSE_ITEM_MOTION);
        return motion == LooseItemMotion.CALM || motion == LooseItemMotion.AUTO && get(REDUCED_MOTION);
    }

    private ClientConfig() {}
}
