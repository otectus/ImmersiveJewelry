package com.otectus.immersivejewelry.recipe;

import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which designs a player knows. Basic designs are always known; sketch designs are learned from a Design Sketch and
 * remembered in the player's persisted data (kept through death).
 */
public final class DesignKnowledge {
    private static final String KEY = "immersive_jewelry_designs";

    public static boolean knows(Player player, JewelryDesignRecipe design) {
        if (!design.sketch() || !ServerConfig.get(ServerConfig.REQUIRE_DESIGN_SKETCHES) || player.getAbilities().instabuild) return true;
        return learned(player).contains(StringTag.valueOf(design.getId().toString()));
    }

    /** Returns false if the design was already known. */
    public static boolean learn(Player player, ResourceLocation design) {
        ListTag list = learned(player);
        StringTag entry = StringTag.valueOf(design.toString());
        if (list.contains(entry)) return false;
        list.add(entry);
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }

    public static void forget(Player player, ResourceLocation design) {
        ListTag list = learned(player);
        list.remove(StringTag.valueOf(design.toString()));
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, list);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Every design this player may use, sorted for display. */
    public static List<JewelryDesignRecipe> available(Player player, Level level) {
        List<JewelryDesignRecipe> out = new ArrayList<>();
        for (JewelryDesignRecipe r : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get())) {
            if (knows(player, r)) out.add(r);
        }
        out.sort(Comparator.comparingInt(JewelryDesignRecipe::sortOrder).thenComparing(r -> r.getId().toString()));
        return out;
    }

    private static ListTag learned(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList(KEY, Tag.TAG_STRING);
    }

    private DesignKnowledge() {}
}
