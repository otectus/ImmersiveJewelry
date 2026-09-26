package com.otectus.immersivejewelry.villager;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.Appraisal;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.gem.GemRarity;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.DesignSketchItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import com.otectus.immersivejewelry.registry.ModVillagers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Jeweler's trades (spec 43). Stones, tools and sketches; finished jewelry only at Master level, and then capped
 * below player masterwork ({@code villagerMaxWorkmanship}). Offers are generated from the live definitions when a
 * Jeweler levels up, so datapack gems appear in trades too. Existing offers are never rewritten.
 */
public final class JewelerTrades {

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagers.JEWELER.get()) return;
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();
        add(trades, 1, (trader, random) -> rough(random, GemRarity.COMMON, 3, 5, 12, 2));
        add(trades, 1, (trader, random) -> offer(new ItemStack(Items.EMERALD, 1), new ItemStack(ModItems.POLISHING_COMPOUND.get(), 4), 16, 1));
        add(trades, 1, (trader, random) -> offer(new ItemStack(Items.GOLD_NUGGET, 18), new ItemStack(Items.EMERALD), 16, 2));
        add(trades, 1, (trader, random) -> offer(new ItemStack(Items.EMERALD, 5), new ItemStack(ModItems.JEWELERS_PLIERS.get()), 4, 3));

        add(trades, 2, (trader, random) -> cut(random, GemRarity.COMMON, 0.4F, 0.62F, 6, 9, 10));
        add(trades, 2, (trader, random) -> offer(new ItemStack(Items.AMETHYST_SHARD, 6), new ItemStack(Items.EMERALD), 16, 5));
        add(trades, 2, (trader, random) -> offer(new ItemStack(Items.EMERALD, 3), new ItemStack(ModItems.JEWELERS_HANDBOOK.get()), 4, 5));

        add(trades, 3, (trader, random) -> rough(random, GemRarity.UNCOMMON, 8, 12, 8, 10));
        add(trades, 3, (trader, random) -> offer(new ItemStack(Items.EMERALD, 10), new ItemStack(ModItems.EMERY_LAP.get()), 3, 10));
        add(trades, 3, (trader, random) -> offer(new ItemStack(Items.EMERALD, 14), new ItemStack(ModItems.FINE_PLIERS.get()), 3, 10));

        add(trades, 4, (trader, random) -> rough(random, GemRarity.RARE, 15, 24, 6, 15));
        // Recipe viewers (Just Enough Resources) ask for offers with no trader; the designs come from its world.
        add(trades, 4, (trader, random) -> trader == null ? null
                : sketch(trader.level().getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()), random, 20));
        add(trades, 4, (trader, random) -> cut(random, GemRarity.UNCOMMON, 0.6F, 0.82F, 16, 22, 15));

        add(trades, 5, (trader, random) -> exceptional(random));
        add(trades, 5, (trader, random) -> offer(new ItemStack(Items.EMERALD, 24), new ItemStack(ModItems.DIAMOND_LAP.get()), 2, 30));
        add(trades, 5, (trader, random) -> jewelry(random));
        add(trades, 5, (trader, random) -> trader == null ? null
                : sketch(trader.level().getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()), random, 28));
    }

    private static void add(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades, int level, VillagerTrades.ItemListing listing) {
        trades.computeIfAbsent(level, l -> new ArrayList<>()).add((trader, random) ->
                ServerConfig.get(ServerConfig.ENABLE_VILLAGER_JEWELER) ? listing.getOffer(trader, random) : null);
    }

    private static MerchantOffer offer(ItemStack cost, ItemStack result, int uses, int xp) {
        return new MerchantOffer(cost, result, uses, xp, 0.05F);
    }

    @Nullable
    private static GemDefinition pick(RandomSource random, GemRarity rarity) {
        List<GemDefinition> gems = JewelryDefinitions.server().gems().stream().filter(g -> !g.auto() && g.rarity() == rarity).toList();
        if (gems.isEmpty()) gems = JewelryDefinitions.server().gems().stream().filter(g -> !g.auto()).toList();
        return gems.isEmpty() ? null : gems.get(random.nextInt(gems.size()));
    }

    @Nullable
    private static MerchantOffer rough(RandomSource random, GemRarity rarity, int min, int max, int uses, int xp) {
        GemDefinition gem = pick(random, rarity);
        if (gem == null) return null;
        GemData stone = com.otectus.immersivejewelry.minigame.cutting.CuttingService.appraise(gem, random);
        return offer(new ItemStack(Items.EMERALD, Mth.nextInt(random, min, max)), RoughGemItem.create(stone), uses, xp);
    }

    @Nullable
    private static MerchantOffer cut(RandomSource random, GemRarity rarity, float qMin, float qMax, int min, int max, int xp) {
        GemDefinition gem = pick(random, rarity);
        if (gem == null) return null;
        CutDefinition cut = JewelryFactory.preferredCut(JewelryDefinitions.server(), gem);
        if (cut == null) return null;
        GemData stone = JewelryFactory.sampleCut(gem, cut, Mth.randomBetween(random, qMin, qMax));
        return offer(new ItemStack(Items.EMERALD, Mth.nextInt(random, min, max)), CutGemItem.create(stone), 4, xp);
    }

    @Nullable
    private static MerchantOffer exceptional(RandomSource random) {
        GemDefinition gem = pick(random, random.nextBoolean() ? GemRarity.RARE : GemRarity.EPIC);
        if (gem == null) return null;
        Appraisal a = gem.appraisal();
        GemData stone = GemData.rough(gem.id(), Mth.randomBetween(random, (a.sizeMin() + a.sizeMax()) / 2F, a.sizeMax()),
                Mth.randomBetween(random, 0.78F, 0.95F), Mth.randomBetween(random, 0F, 0.12F));
        return offer(new ItemStack(Items.EMERALD, Mth.nextInt(random, 28, 40)), RoughGemItem.create(stone), 2, 30);
    }

    @Nullable
    private static MerchantOffer sketch(List<JewelryDesignRecipe> designs, RandomSource random, int price) {
        List<JewelryDesignRecipe> sketches = designs.stream().filter(JewelryDesignRecipe::sketch).toList();
        if (sketches.isEmpty()) return null;
        ResourceLocation id = sketches.get(random.nextInt(sketches.size())).getId();
        return offer(new ItemStack(Items.EMERALD, price), DesignSketchItem.create(id), 1, 20);
    }

    /** Village work: good, never great (spec 43). */
    @Nullable
    private static MerchantOffer jewelry(RandomSource random) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        List<FormDefinition> forms = new ArrayList<>(defs.forms());
        GemDefinition gem = pick(random, GemRarity.UNCOMMON);
        if (forms.isEmpty() || gem == null) return null;
        FormDefinition form = forms.get(random.nextInt(forms.size()));
        CutDefinition cut = JewelryFactory.preferredCut(defs, gem);
        if (cut == null) return null;
        float cap = (float) ServerConfig.get(ServerConfig.VILLAGER_MAX_WORKMANSHIP);
        float q = Math.min(cap, Mth.randomBetween(random, 0.55F, 0.78F));
        ResourceLocation metal = random.nextBoolean() ? new ResourceLocation("minecraft", "gold") : new ResourceLocation("forge", "silver");
        MetalRef ref = JewelryFactory.metal(defs, defs.metalDefinition(metal) != null ? metal : new ResourceLocation("minecraft", "gold"));
        ItemStack piece = JewelryFactory.jewelry(form.id(), ref, List.of(JewelryFactory.sampleCut(gem, cut, q)), null,
                ImmersiveJewelry.id("polished"), q, q, null);
        if (piece.isEmpty()) return null;
        return offer(new ItemStack(Items.EMERALD, Mth.nextInt(random, 26, 36)), piece, 1, 30);
    }

    private JewelerTrades() {}
}
