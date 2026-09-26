package com.otectus.immersivejewelry.recipe;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.integration.Integrations;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * {@code immersive_jewelry:fallback_blanks}: true when blanks may be crafted at a crafting table (spec 5.4 mode B):
 * Immersive Smithing is absent or its integration is off, {@code requireImmersiveSmithing} is false and
 * {@code enableFallbackBlankRecipes} is true. Common config, so it is known at the first datapack load.
 */
public final class ModConditions {
    public static final ResourceLocation FALLBACK_BLANKS = ImmersiveJewelry.id("fallback_blanks");
    public static final ResourceLocation SMITHING_BLANKS = ImmersiveJewelry.id("smithing_blanks");

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
            CraftingHelper.register(FallbackBlanks.SERIALIZER);
            CraftingHelper.register(SmithingBlanks.SERIALIZER);
        }
    }

    public static boolean fallbackBlanksAllowed() {
        return CommonConfig.get(CommonConfig.ENABLE_FALLBACK_BLANK_RECIPES) && !CommonConfig.get(CommonConfig.REQUIRE_SMITHING)
                && !Integrations.smithingLoaded();
    }

    /** {@code immersive_jewelry:smithing_blanks}: Immersive Smithing is installed and its integration enabled (spec 5.4 mode A). */
    public static final class SmithingBlanks implements ICondition {
        public static final SmithingBlanks INSTANCE = new SmithingBlanks();
        public static final IConditionSerializer<SmithingBlanks> SERIALIZER = new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, SmithingBlanks value) {}

            @Override
            public SmithingBlanks read(JsonObject json) {
                return INSTANCE;
            }

            @Override
            public ResourceLocation getID() {
                return SMITHING_BLANKS;
            }
        };

        @Override
        public ResourceLocation getID() {
            return SMITHING_BLANKS;
        }

        @Override
        public boolean test(IContext context) {
            return Integrations.smithingLoaded();
        }
    }

    public static final class FallbackBlanks implements ICondition {
        public static final FallbackBlanks INSTANCE = new FallbackBlanks();
        public static final IConditionSerializer<FallbackBlanks> SERIALIZER = new IConditionSerializer<>() {
            @Override
            public void write(JsonObject json, FallbackBlanks value) {}

            @Override
            public FallbackBlanks read(JsonObject json) {
                return INSTANCE;
            }

            @Override
            public ResourceLocation getID() {
                return FALLBACK_BLANKS;
            }
        };

        @Override
        public ResourceLocation getID() {
            return FALLBACK_BLANKS;
        }

        @Override
        public boolean test(IContext context) {
            return fallbackBlanksAllowed();
        }
    }

    private ModConditions() {}
}
