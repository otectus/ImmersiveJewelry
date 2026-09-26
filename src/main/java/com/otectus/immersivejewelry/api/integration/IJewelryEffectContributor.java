package com.otectus.immersivejewelry.api.integration;

import com.otectus.immersivejewelry.api.effect.EffectCategory;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Adds effects to a worn piece beyond its stones' trait expressions, from data another mod keeps on the piece (for
 * example sockets or affixes). Contributed effects run exactly like trait effects, through the same hooks, cooldowns,
 * state and invalidation, and only while the piece is active: worn, not broken, and within its form's slot cap.
 *
 * <p>Their power is used as given. Jewelry that lends power to other pieces never amplifies a contributed effect, and
 * contributed effects never amplify other pieces, so another mod's values keep their own balance.
 *
 * <p>Called on the server thread whenever the wearer's jewelry is re-examined (equipment changes, datapack reload,
 * respawn), never per tick. Keep it cheap and free of side effects. A change to the piece's NBT outside the
 * {@code immersive_jewelry} compound re-applies the piece's effects, so contributions may depend on it.
 */
@FunctionalInterface
public interface IJewelryEffectContributor {

    /** The effects {@code piece} carries for this contributor; empty when it carries none. */
    List<ContributedEffect> effects(ItemStack piece, JewelryData data);

    /**
     * @param id       identifies the effect in logs and errors (for example {@code examplemod:affix/stalwart})
     * @param category the effect's category, as for trait expressions
     * @param effect   the behaviour; may be shared between pieces, per-piece state goes through the context
     * @param power    the strength the effect sees as {@code JewelryContext#power}
     */
    record ContributedEffect(ResourceLocation id, EffectCategory category, JewelryEffect effect, float power) {}
}
