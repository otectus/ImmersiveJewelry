package com.otectus.immersivejewelry.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.DesignSketchItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.minigame.cutting.CuttingService;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Treasure chests can hold appraised rough gems (weighted by gem rarity, so a ruby turns up even in a pack without a
 * ruby mod) and, more rarely, a Design Sketch. Which tables and how often is data: see
 * {@code data/immersive_jewelry/loot_modifiers/*.json}. Server config can switch it off or scale the chances.
 */
public class TreasureGemsModifier extends LootModifier {
    public static final Codec<TreasureGemsModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).and(inst.group(
            Codec.FLOAT.fieldOf("gem_chance").forGetter(m -> m.gemChance),
            Codec.INT.optionalFieldOf("max_gems", 2).forGetter(m -> m.maxGems),
            Codec.FLOAT.optionalFieldOf("sketch_chance", 0F).forGetter(m -> m.sketchChance),
            Codec.INT.optionalFieldOf("min_rarity", 0).forGetter(m -> m.minRarity)))
            .apply(inst, TreasureGemsModifier::new));

    private final float gemChance;
    private final int maxGems;
    private final float sketchChance;
    private final int minRarity;

    public TreasureGemsModifier(LootItemCondition[] conditions, float gemChance, int maxGems, float sketchChance, int minRarity) {
        super(conditions);
        this.gemChance = gemChance;
        this.maxGems = maxGems;
        this.sketchChance = sketchChance;
        this.minRarity = minRarity;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!ServerConfig.get(ServerConfig.ENABLE_GEM_LOOT)) return loot;
        float scale = (float) ServerConfig.get(ServerConfig.GEM_LOOT_CHANCE_MULTIPLIER);
        RandomSource random = context.getRandom();
        List<GemDefinition> gems = new ArrayList<>();
        int total = 0;
        for (GemDefinition g : JewelryDefinitions.server().gems()) {
            if (g.auto() || g.rarity().ordinal() < minRarity) continue;
            gems.add(g);
            total += g.rarity().lootWeight();
        }
        if (total > 0) {
            for (int i = 0; i < maxGems; i++) {
                if (random.nextFloat() >= gemChance * scale) break;
                int pick = random.nextInt(total);
                for (GemDefinition g : gems) {
                    pick -= g.rarity().lootWeight();
                    if (pick < 0) {
                        GemData stone = CuttingService.appraise(g, random);
                        loot.add(RoughGemItem.create(stone));
                        break;
                    }
                }
            }
        }
        if (sketchChance > 0F && random.nextFloat() < sketchChance * scale) {
            List<JewelryDesignRecipe> sketches = context.getLevel().getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get())
                    .stream().filter(JewelryDesignRecipe::sketch).toList();
            if (!sketches.isEmpty()) loot.add(DesignSketchItem.create(sketches.get(random.nextInt(sketches.size())).getId()));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
