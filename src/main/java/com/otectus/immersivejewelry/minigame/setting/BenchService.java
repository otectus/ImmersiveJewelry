package com.otectus.immersivejewelry.minigame.setting;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.network.packet.OpenBenchPacket;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Bench rules outside the minigame: what the player can choose, what each choice costs, how repairs work. */
public final class BenchService {

    public static OpenBenchPacket options(ServerPlayer player, JewelersBenchBlockEntity bench) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        ItemStack workpiece = bench.workpiece();
        List<OpenBenchPacket.Finish> finishes = new ArrayList<>();
        for (FinishDefinition f : defs.finishes()) {
            finishes.add(new OpenBenchPacket.Finish(f.id(), Inventories.display(f.cost(), f.costCount()), Inventories.has(player, f.cost(), f.costCount())));
        }
        if (workpiece.getItem() instanceof JewelryItem) {
            JewelryData data = JewelryData.get(workpiece).orElse(null);
            boolean stones = data != null && data.hasGem();
            Ingredient repair = data == null ? null : repairIngredient(defs, data.metal());
            float cap = data == null ? 1F : repairCap(data.reworks());
            boolean needsRepair = data != null && data.integrity() < cap - 0.001F;
            ItemStack cost = needsRepair ? Inventories.display(repair, 1) : ItemStack.EMPTY;
            JewelryDesignRecipe design = data == null ? null : design(player.level(), data.design());
            List<OpenBenchPacket.Finish> allowed = finishes.stream()
                    .filter(f -> design == null || design.allowsFinish(f.id()))
                    .filter(f -> data == null || !f.id().equals(data.finish())).toList();
            return new OpenBenchPacket(bench.getBlockPos(), workpiece.copy(), true, List.of(), List.of(), allowed, stones, stones,
                    cost, needsRepair && Inventories.has(player, repair, 1), cap);
        }
        ResourceLocation form = workpiece.getItem() instanceof JewelryBlankItem blank ? blank.form() : null;
        List<OpenBenchPacket.Design> designs = new ArrayList<>();
        for (JewelryDesignRecipe r : DesignKnowledge.available(player, player.level())) {
            if (!r.form().equals(form)) continue;
            designs.add(new OpenBenchPacket.Design(r.getId(), r.name(), r.stones(), r.style(), r.pointsPerStone(), r.tightness(),
                    r.allowedCuts(), r.allowedGems(), r.allowedFinishes()));
        }
        return new OpenBenchPacket(bench.getBlockPos(), workpiece.copy(), false, designs, stones(player), finishes, false, false,
                ItemStack.EMPTY, false, 1F);
    }

    /** Every cut stone in the player's main inventory, by slot. */
    public static List<OpenBenchPacket.Stone> stones(ServerPlayer player) {
        List<OpenBenchPacket.Stone> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size() && out.size() < OpenBenchPacket.MAX_ENTRIES; i++) {
            ItemStack s = inv.items.get(i);
            int slot = i;
            CutGemItem.data(s).ifPresent(d -> out.add(new OpenBenchPacket.Stone(slot, d, s.getCount())));
        }
        return out;
    }

    @Nullable
    public static JewelryDesignRecipe design(Level level, @Nullable ResourceLocation id) {
        if (id == null) return null;
        return level.getRecipeManager().byKey(id).filter(r -> r instanceof JewelryDesignRecipe).map(r -> (JewelryDesignRecipe) r).orElse(null);
    }

    /** What repairs a frame: the metal definition's repair ingredient, else its nugget tag, else its ingot tag. */
    @Nullable
    public static Ingredient repairIngredient(JewelryDefinitions defs, MetalRef metal) {
        MetalDefinition def = defs.metal(metal);
        if (!Inventories.isEmpty(def.repair())) return def.repair();
        Ingredient nuggets = Ingredient.of(ItemTags.create(new ResourceLocation("forge", "nuggets/" + metal.id().getPath())));
        if (!Inventories.isEmpty(nuggets)) return nuggets;
        Ingredient ingots = Ingredient.of(ItemTags.create(new ResourceLocation("forge", "ingots/" + metal.id().getPath())));
        return Inventories.isEmpty(ingots) ? null : ingots;
    }

    /** Repairs restore Integrity only up to a cap that falls with every rework of the frame. */
    public static float repairCap(int reworks) {
        return Math.max(0.5F, 1F - 0.06F * reworks);
    }

    public static float pliersPrecision(ItemStack stack) {
        return stack.getItem() instanceof PliersItem p ? p.precision() : 1F;
    }

    @Nullable
    public static InteractionHand pliersHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof PliersItem) return hand;
        }
        return null;
    }

    public static SettingParams params(SettingSession.Mode mode, @Nullable JewelryDesignRecipe design, List<GemData> stones, float precision,
                                       boolean larger, boolean slower, long seed) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        float size = 0F;
        float brittle = 0F;
        for (GemData g : stones) {
            size += g.size();
            GemDefinition def = defs.gemOrGeneric(g.gem());
            brittle = Math.max(brittle, def.brittleness());
        }
        size = stones.isEmpty() ? 1F : size / stones.size();
        boolean assist = ServerConfig.get(ServerConfig.ALLOW_ASSIST);
        SettingStyle style = design != null ? design.style() : SettingStyle.PRONG;
        int points = design != null ? design.pointsPerStone() : 4;
        SettingParams.Inputs in = new SettingParams.Inputs(style, Math.max(1, stones.size()), points,
                design != null ? design.tightness() : 1F, precision, size, brittle,
                (float) ServerConfig.get(ServerConfig.SETTING_DIFFICULTY), assist && larger, assist && slower,
                mode == SettingSession.Mode.REMOVE, ServerConfig.get(ServerConfig.SETTING_SECONDS_PER_PRONG));
        return SettingParams.create(in, seed);
    }

    /** Experience at the configured amount for Fine workmanship. */
    public static int experience(float workmanship, int stones) {
        int base = ServerConfig.get(ServerConfig.SETTING_EXPERIENCE);
        if (base <= 0) return 0;
        return Math.max(1, Math.round(base * (0.4F + workmanship) * (1F + 0.25F * (stones - 1))));
    }

    /** The blank a finished piece leaves when its stones come out; removal quality decides the extra frame wear. */
    public static BlankData frameAfterRemoval(JewelryData data, float removalQuality, boolean scratched) {
        float cost = (float) ServerConfig.get(ServerConfig.REWORK_INTEGRITY_COST) * (1.5F - removalQuality) * (scratched ? 1.5F : 1F);
        return data.toBlank(Math.max(0F, cost));
    }

    private BenchService() {}
}
