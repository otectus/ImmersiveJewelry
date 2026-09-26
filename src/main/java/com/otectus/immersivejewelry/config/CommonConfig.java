package com.otectus.immersivejewelry.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Pack-level choices that must be known before the first datapack load (recipe conditions read them) and on both
 * sides: integrations, the equipment system and migration. Everything that tunes gameplay lives in {@link ServerConfig}.
 */
public final class CommonConfig {
    public enum EquipmentSystem { AUTO, CURIOS, BUILTIN }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SMITHING_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_SMITHING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FALLBACK_BLANK_RECIPES;
    public static final ForgeConfigSpec.DoubleValue FALLBACK_SMITHING_QUALITY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SMITHING_METAL_BLACKLIST;

    public static final ForgeConfigSpec.EnumValue<EquipmentSystem> EQUIPMENT_SYSTEM;

    public static final ForgeConfigSpec.BooleanValue AUTO_DETECT_GEM_TAGS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_GEM_EXCLUSIONS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_LEGACY_CONVERSION;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Immersive Smithing. With it installed, jewelry blanks are forged at the Smith's Forge and shaped on the Smith's Anvil,",
                "and metal quality carries into the finished jewelry.").push("immersiveSmithing");
        ENABLE_SMITHING_INTEGRATION = b.comment("Forge jewelry blanks with Immersive Smithing when it is installed.")
                .define("enableImmersiveSmithingIntegration", true);
        REQUIRE_SMITHING = b.comment("Never offer crafting-table recipes for blanks, even without Immersive Smithing (for packs that require it).")
                .define("requireImmersiveSmithing", false);
        ENABLE_FALLBACK_BLANK_RECIPES = b.comment("Without the integration, blanks can be crafted from nuggets and ingots at a crafting table.")
                .define("enableFallbackBlankRecipes", true);
        FALLBACK_SMITHING_QUALITY = b.comment("Metalwork quality of a crafted (not smithed) blank, 0..1.")
                .defineInRange("fallbackSmithingQuality", 0.45, 0.0, 1.0);
        SMITHING_METAL_BLACKLIST = b.comment("Immersive Smithing material families (for example \"forge:lead\") that never become jewelry blanks.")
                .defineListAllowEmpty("metalBlacklist", List.of("forge:lead", "forge:uranium"), o -> o instanceof String);
        b.pop();

        b.comment("Where jewelry is worn").push("equipment");
        EQUIPMENT_SYSTEM = b.comment("AUTO: Curios slots when Curios is installed, otherwise the built-in jewelry slots.",
                        "CURIOS: only Curios (jewelry cannot be worn without it). BUILTIN: always the built-in slots.")
                .defineEnum("equipmentSystem", EquipmentSystem.AUTO);
        b.pop();

        b.comment("Gemstones").push("gems");
        AUTO_DETECT_GEM_TAGS = b.comment("Every forge:gems/<name> tag without a gem definition becomes a generic, cuttable gem.")
                .define("autoDetectGemTags", true);
        AUTO_GEM_EXCLUSIONS = b.comment("forge:gems/<name> names that are never treated as gemstones.")
                .defineListAllowEmpty("autoDetectExclusions", List.of("prismarine", "coal", "charcoal", "redstone", "glowstone", "experience"),
                        o -> o instanceof String);
        b.pop();

        b.comment("Jewelcraft Reforged worlds").push("migration");
        ENABLE_LEGACY_CONVERSION = b.comment("Convert Jewelcraft Reforged jewelry into Immersive Jewelry pieces when it is loaded.")
                .define("enableLegacyConversion", true);
        b.pop();

        b.push("debug");
        DEBUG_LOGGING = b.comment("Log definition loading, integration activation, conversions and data repairs in detail.")
                .define("debugLogging", false);
        b.pop();

        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static EquipmentSystem equipmentSystem() {
        return SPEC.isLoaded() ? EQUIPMENT_SYSTEM.get() : EQUIPMENT_SYSTEM.getDefault();
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        return (List<String>) (SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    private CommonConfig() {}
}
