package com.otectus.immersivejewelry.advancement;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Milestones of the craft (spec 58) that code awards: advancements with a single {@code minecraft:impossible}
 * criterion. Picking up a gem, crafting the stations and forging a blank use ordinary inventory criteria.
 * Nothing is ever locked behind an advancement.
 */
public final class ModAdvancements {
    public static final String FIRST_CUT = "first_facet";
    public static final String FLAWLESS = "flawless";
    public static final String FIRST_SETTING = "set_in_stone";
    public static final String FINE_JEWELRY = "fine_jewelry";
    public static final String MASTERWORK = "masterwork";
    public static final String RECOVERED = "recovered_stone";
    public static final String NEW_DESIGN = "new_design";
    public static final String REPAIRED = "good_as_new";

    public static void award(ServerPlayer player, String name) {
        if (player.getServer() == null) return;
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(ImmersiveJewelry.id(name));
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) return;
        for (String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
    }

    private ModAdvancements() {}
}
