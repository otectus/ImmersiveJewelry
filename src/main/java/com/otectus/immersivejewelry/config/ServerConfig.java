package com.otectus.immersivejewelry.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Gameplay configuration. SERVER configs are per world and synced to clients, so the client can read these values
 * once it has joined a world. Every read goes through the helpers at the bottom, which fall back to defaults while
 * no world is loaded.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue CUTTING_DIFFICULTY;
    public static final ForgeConfigSpec.DoubleValue SETTING_DIFFICULTY;
    public static final ForgeConfigSpec.IntValue CUTTING_SECONDS_PER_FACET;
    public static final ForgeConfigSpec.IntValue SETTING_SECONDS_PER_PRONG;
    public static final ForgeConfigSpec.BooleanValue ALLOW_ASSIST;
    public static final ForgeConfigSpec.IntValue READY_DELAY_MS;
    public static final ForgeConfigSpec.IntValue TIMING_TOLERANCE_MS;
    public static final ForgeConfigSpec.IntValue MAX_LATENCY_COMPENSATION_MS;
    public static final ForgeConfigSpec.DoubleValue MAX_STATION_DISTANCE;

    public static final ForgeConfigSpec.BooleanValue CATASTROPHIC_GEM_FAILURE;
    public static final ForgeConfigSpec.DoubleValue GEM_FAILURE_SEVERITY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> CUT_GRADE_THRESHOLDS;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_SMITHING;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_CUT;
    public static final ForgeConfigSpec.DoubleValue WEIGHT_SETTING;

    public static final ForgeConfigSpec.DoubleValue POWER_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_RING_SLOTS;
    public static final ForgeConfigSpec.IntValue MAX_AMULET_SLOTS;
    public static final ForgeConfigSpec.IntValue MAX_BRACELET_SLOTS;
    public static final ForgeConfigSpec.IntValue MAX_CIRCLET_SLOTS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_WEAR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GEM_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SEVERE_HIT_FRACTION;
    public static final ForgeConfigSpec.DoubleValue WEAR_PER_SEVERE_HIT;
    public static final ForgeConfigSpec.DoubleValue REWORK_INTEGRITY_COST;
    public static final ForgeConfigSpec.DoubleValue REPAIR_AMOUNT;

    public static final ForgeConfigSpec.BooleanValue SHOW_NUMERIC_QUALITY;

    public static final ForgeConfigSpec.BooleanValue REQUIRE_DESIGN_SKETCHES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_JEWELER;
    public static final ForgeConfigSpec.DoubleValue VILLAGER_MAX_WORKMANSHIP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GEM_LOOT;
    public static final ForgeConfigSpec.DoubleValue GEM_LOOT_CHANCE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue CUTTING_EXPERIENCE;
    public static final ForgeConfigSpec.IntValue SETTING_EXPERIENCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Minigame difficulty, time and validation").push("minigames");
        CUTTING_DIFFICULTY = b.comment("Gem cutting difficulty. Above 1 narrows facet windows and adds wobble; below 1 relaxes them.")
                .defineInRange("gemCuttingDifficultyMultiplier", 1.0, 0.25, 4.0);
        SETTING_DIFFICULTY = b.comment("Stone setting difficulty. Above 1 narrows pressure windows.")
                .defineInRange("stoneSettingDifficultyMultiplier", 1.0, 0.25, 4.0);
        CUTTING_SECONDS_PER_FACET = b.comment("Time allowed per facet. Running out grades the facets as they are.")
                .defineInRange("cuttingSecondsPerFacet", 9, 2, 120);
        SETTING_SECONDS_PER_PRONG = b.comment("Time allowed per prong. Running out leaves the remaining prongs loose.")
                .defineInRange("settingSecondsPerProng", 10, 2, 120);
        ALLOW_ASSIST = b.comment("Honour the client accessibility options for larger target zones and slower minigames.")
                .define("allowAccessibilityAssist", true);
        READY_DELAY_MS = b.comment("Countdown before a minigame starts, in milliseconds.")
                .defineInRange("readyDelayMs", 1200, 0, 10000);
        TIMING_TOLERANCE_MS = b.comment("Jitter tolerance applied to client-reported input times.")
                .defineInRange("timingToleranceMs", 120, 0, 2000);
        MAX_LATENCY_COMPENSATION_MS = b.comment("Upper bound of the latency compensation granted to a player's inputs.")
                .defineInRange("maxLatencyCompensationMs", 400, 0, 5000);
        MAX_STATION_DISTANCE = b.comment("Maximum distance in blocks between a player and the station they are working at.")
                .defineInRange("maxStationDistance", 6.0, 2.0, 32.0);
        b.pop();

        b.comment("Quality and failure").push("quality");
        CATASTROPHIC_GEM_FAILURE = b.comment("A ruined cut with several chips can shatter the stone into gem dust. Off: the stone always survives.")
                .define("catastrophicGemFailure", false);
        GEM_FAILURE_SEVERITY = b.comment("Scales the size a stone loses to chips and overcut facets.")
                .defineInRange("gemFailureSeverity", 1.0, 0.0, 3.0);
        CUT_GRADE_THRESHOLDS = b.comment("Lower bounds of Chipped, Acceptable, Fine, Excellent and Flawless cut quality (below the first is Ruined).")
                .defineList("cutGradeThresholds", List.of(0.20, 0.40, 0.60, 0.78, 0.92), o -> o instanceof Double d && d >= 0 && d <= 1);
        WEIGHT_SMITHING = b.comment("Workmanship = smithing x weight + cut x weight + setting x weight (weights are normalised).")
                .defineInRange("workmanshipWeightSmithing", 0.35, 0.0, 1.0);
        WEIGHT_CUT = b.defineInRange("workmanshipWeightCut", 0.35, 0.0, 1.0);
        WEIGHT_SETTING = b.defineInRange("workmanshipWeightSetting", 0.30, 0.0, 1.0);
        b.pop();

        b.comment("Jewelry effects").push("effects");
        POWER_MULTIPLIER = b.comment("Scales the strength of every jewelry effect.")
                .defineInRange("jewelryPowerMultiplier", 1.0, 0.0, 10.0);
        MAX_RING_SLOTS = b.comment("How many worn rings are active at once (extra pieces are inert). Curios slot counts are datapack data.")
                .defineInRange("maxRingSlots", 2, 0, 16);
        MAX_AMULET_SLOTS = b.defineInRange("maxAmuletSlots", 1, 0, 16);
        MAX_BRACELET_SLOTS = b.defineInRange("maxBraceletSlots", 1, 0, 16);
        MAX_CIRCLET_SLOTS = b.defineInRange("maxCircletSlots", 1, 0, 16);
        b.pop();

        b.comment("Wear. Jewelry has Integrity instead of durability; routine combat never wears it.").push("wear");
        ENABLE_WEAR = b.comment("Severe hits wear the Integrity of worn jewelry.")
                .define("enableJewelryWear", true);
        ENABLE_GEM_DAMAGE = b.comment("Severe hits on a very worn piece can scratch or chip its stones.")
                .define("enableGemDamage", true);
        SEVERE_HIT_FRACTION = b.comment("A hit is severe when it deals at least this share of the wearer's maximum health.")
                .defineInRange("severeHitFraction", 0.35, 0.05, 1.0);
        WEAR_PER_SEVERE_HIT = b.comment("Integrity lost by each worn piece per severe hit (before metal and finish).")
                .defineInRange("wearPerSevereHit", 0.02, 0.0, 1.0);
        REWORK_INTEGRITY_COST = b.comment("Integrity a frame loses every time its stones are removed.")
                .defineInRange("reworkIntegrityCost", 0.08, 0.0, 1.0);
        REPAIR_AMOUNT = b.comment("Integrity restored by one frame repair at the Jeweler's Bench.")
                .defineInRange("repairAmount", 0.3, 0.01, 1.0);
        b.pop();

        b.push("display");
        SHOW_NUMERIC_QUALITY = b.comment("Allow the exact percentages in advanced (Shift) tooltips.")
                .define("showNumericQuality", true);
        b.pop();

        b.comment("Villages, loot and progression").push("progression");
        REQUIRE_DESIGN_SKETCHES = b.comment("Advanced designs must be learned from a Design Sketch. Off: every design is known.")
                .define("requireDesignSketches", true);
        ENABLE_VILLAGER_JEWELER = b.comment("Jewelers offer trades. Existing offers are not changed.")
                .define("enableVillagerJeweler", true);
        VILLAGER_MAX_WORKMANSHIP = b.comment("Upper bound of the workmanship of jewelry a villager sells, so player masterwork stays unmatched.")
                .defineInRange("villagerMaxWorkmanship", 0.78, 0.0, 1.0);
        ENABLE_GEM_LOOT = b.comment("Rough gems and Design Sketches appear in treasure chests.")
                .define("enableGemLoot", true);
        GEM_LOOT_CHANCE_MULTIPLIER = b.defineInRange("gemLootChanceMultiplier", 1.0, 0.0, 10.0);
        CUTTING_EXPERIENCE = b.comment("Experience for a cut gem at Fine quality (more for better cuts, 0 = none).")
                .defineInRange("cuttingExperience", 3, 0, 100);
        SETTING_EXPERIENCE = b.comment("Experience for a finished piece at Fine workmanship (more for better work, 0 = none).")
                .defineInRange("settingExperience", 5, 0, 100);
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

    @SuppressWarnings("unchecked")
    public static List<Double> doubles(ForgeConfigSpec.ConfigValue<List<? extends Double>> value) {
        return (List<Double>) (SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    private ServerConfig() {}
}
