package com.otectus.immersivejewelry.workstation;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.data.work.WorkProfile;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.util.Inventories;
import com.otectus.immersivejewelry.workstation.sim.SettingParams;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * The rules of bench work that are not the hands-on steps: setting and surface parameters, the state of a finished
 * piece's points when it is reworked, repair materials and caps, and experience. The economics (costs, caps, integrity,
 * experience) are those of 1.1 unchanged.
 */
public final class BenchPlanner {
    /** Seconds of steady work a whole surface takes at the built-in pace (a surface work profile scales from it). */
    public static final float SURFACE_SECONDS = 7F;


    @Nullable
    public static JewelryDesignRecipe design(Level level, @Nullable ResourceLocation id) {
        if (id == null) return null;
        return level.getRecipeManager().byKey(id).filter(r -> r instanceof JewelryDesignRecipe).map(r -> (JewelryDesignRecipe) r).orElse(null);
    }

    public static float pliersPrecision(ItemStack stack) {
        return stack.getItem() instanceof PliersItem p ? p.precision() : 1F;
    }

    public static SettingStyle style(@Nullable JewelryDesignRecipe design) {
        return design != null ? design.style() : SettingStyle.PRONG;
    }

    public static int points(@Nullable JewelryDesignRecipe design) {
        return design != null ? design.pointsPerStone() : 4;
    }

    /**
     * Setting parameters.
     *
     * @param precision        the docked pliers' precision for prongs; 1 for the bench's own bezel pusher
     * @param assistDifficulty per-player relief from {@code ImmersiveJewelryAPI.assistDifficulty}
     */
    public static SettingParams settingParams(JewelryDefinitions defs, @Nullable JewelryDesignRecipe design, int sockets, List<GemData> stones,
                                              float precision, WorkSessionManager.Preferences prefs, float assistDifficulty) {
        float size = 0F;
        float brittle = 0F;
        for (GemData g : stones) {
            size += g.size();
            GemDefinition def = defs.gemOrGeneric(g.gem());
            brittle = Math.max(brittle, def.brittleness());
        }
        size = stones.isEmpty() ? 1F : size / stones.size();
        boolean assist = ServerConfig.get(ServerConfig.ALLOW_ASSIST);
        SettingStyle style = style(design);
        WorkProfile profile = defs.workProfile(design != null ? design.workProfile() : null,
                style == SettingStyle.BEZEL ? WorkProfile.Interaction.BEZEL : WorkProfile.Interaction.PRONG);
        SettingParams.Tuning tuning = profile == null ? SettingParams.Tuning.DEFAULT
                : new SettingParams.Tuning(profile.toleranceScale(), profile.targetActiveSeconds());
        return SettingParams.create(new SettingParams.Inputs(style, sockets, points(design), design != null ? design.tightness() : 1F,
                style == SettingStyle.BEZEL ? 1F : precision, size, brittle,
                (float) ServerConfig.get(ServerConfig.SETTING_DIFFICULTY) * assistDifficulty,
                assist && prefs.widerWindows(), assist && prefs.gentlerRate(), tuning));
    }

    /** The surface work for a finish (or a repair) on a piece of {@code form}. */
    public static SurfaceParams surfaceParams(JewelryDefinitions defs, SurfaceParams.Mode mode, @Nullable FinishDefinition finish, long seed,
                                              float damage, WorkSessionManager.Preferences prefs) {
        WorkProfile.Interaction interaction = switch (mode) {
            case POLISH -> WorkProfile.Interaction.POLISH;
            case BURNISH -> WorkProfile.Interaction.BURNISH;
            case PATINA -> WorkProfile.Interaction.PATINA;
            case REPAIR -> WorkProfile.Interaction.REPAIR;
        };
        WorkProfile profile = defs.workProfile(finish != null ? finish.workProfile() : null, interaction);
        float scale = profile != null && profile.targetActiveSeconds() > 0F ? profile.targetActiveSeconds() / SURFACE_SECONDS : 1F;
        boolean assist = ServerConfig.get(ServerConfig.ALLOW_ASSIST);
        return SurfaceParams.create(mode, seed, damage, scale, assist && prefs.gentlerRate());
    }

    /**
     * The points of a finished piece as its defects describe them, for retightening or taking it apart: secure points
     * sit in the band, a loose piece has some points open, a bent one one point past the band. Deterministic from the
     * work's seed, so a packed and resumed rework finds the same prongs loose.
     */
    public static void restore(SettingSim sim, JewelryData piece, long seed, boolean removal) {
        SettingParams p = sim.params();
        Random random = new Random(seed ^ 0x2545F4914F6CDD1DL);
        float tol = p.tolerance();
        float[] closures = new float[p.sockets() * p.points()];
        float[] seats = new float[p.sockets()];
        boolean loose = piece.defects().contains(SettingDefect.LOOSE);
        boolean bent = piece.defects().contains(SettingDefect.BENT_PRONG);
        boolean crooked = piece.defects().contains(SettingDefect.CROOKED);
        int bentAt = bent ? random.nextInt(closures.length) : -1;
        for (int s = 0; s < p.sockets(); s++) {
            seats[s] = crooked ? 0.5F : 0.92F;
            int looseAt = loose ? random.nextInt(p.points()) : -1;
            for (int q = 0; q < p.points(); q++) {
                int i = s * p.points() + q;
                float c = 1F + (random.nextFloat() - 0.5F) * 0.6F * tol;
                if (removal) c = 1F;
                else if (q == looseAt || (loose && random.nextFloat() < 0.25F)) c = 1F - 2.2F * tol;
                else if (i == bentAt) c = 1F + 2F * tol;
                closures[i] = c;
            }
        }
        sim.restore(closures, seats);
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

    private BenchPlanner() {}
}
