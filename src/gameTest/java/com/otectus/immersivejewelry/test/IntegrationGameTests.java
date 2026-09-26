package com.otectus.immersivejewelry.test;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.integration.Integrations;
import com.otectus.immersivejewelry.recipe.ModConditions;
import com.otectus.immersivejewelry.registry.ModVillagers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Optional integrations and village life (spec 5.4, 43): whichever mods are installed, the loop stays complete. */
@GameTestHolder(ImmersiveJewelry.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {

    @GameTest(template = "empty")
    public static void blanksHaveExactlyOneSource(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        Optional<? extends Recipe<?>> fallback = recipes.byKey(ImmersiveJewelry.id("fallback/ring_blank_gold"));
        Optional<? extends Recipe<?>> smithed = recipes.byKey(ImmersiveJewelry.id("smithing/ring/minecraft/gold"));
        if (ModList.get().isLoaded("immersive_smithing")) {
            h.assertTrue(Integrations.smithingActive(), "The Immersive Smithing integration is active");
            h.assertTrue(smithed.isPresent(), "Immersive Smithing forges ring blanks");
            h.assertTrue(fallback.isEmpty(), "and no crafting recipe bypasses it (spec 2.4)");
        } else {
            h.assertTrue(ModConditions.fallbackBlanksAllowed(), "Without Immersive Smithing the fallback is on");
            h.assertTrue(fallback.isPresent(), "and ring blanks are crafted from nuggets");
            ItemStack result = fallback.get().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(BlankData.get(result).map(b -> b.metal().id().equals(new ResourceLocation("minecraft", "gold"))).orElse(false), "in gold");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void theJewelerTradesAtEveryLevel(GameTestHelper h) {
        var trades = VillagerTrades.TRADES.get(ModVillagers.JEWELER.get());
        h.assertTrue(trades != null, "The Jeweler has trades");
        Villager villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setVillagerData(new VillagerData(villager.getVillagerData().getType(), ModVillagers.JEWELER.get(), 5));
        for (int level = 1; level <= 5; level++) {
            var listings = trades.get(level);
            h.assertTrue(listings != null && listings.length > 0, "Trades at level " + level);
            int offers = 0;
            for (var listing : listings) {
                MerchantOffer offer = listing.getOffer(villager, h.getLevel().random);
                if (offer != null) offers++;
            }
            h.assertTrue(offers > 0, "Level " + level + " offers something");
        }
        villager.discard();
        h.assertTrue(ModVillagers.JEWELERS_BENCH.get().is(com.otectus.immersivejewelry.registry.ModBlocks.JEWELERS_BENCH.get().defaultBlockState()),
                "The bench is the Jeweler's workstation");
        h.succeed();
    }
}
