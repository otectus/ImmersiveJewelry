package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.config.ServerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import vazkii.patchouli.api.IComponentRenderContext;
import vazkii.patchouli.api.IVariable;

import java.util.function.UnaryOperator;

/**
 * This server's rules that change what a page says, from the server config Forge syncs to the client: sketch policy,
 * active slots per form, whether assists are allowed, wear and gem damage. Page JSON: {@code "topic": "designs"},
 * {@code "wearing"}, {@code "care"} or {@code "workshop"}.
 */
public class PolicyCard extends GuideCard {
    String topic;

    @Override
    protected void resolve(UnaryOperator<IVariable> lookup) {
        topic = variable(lookup, topic);
    }

    private static Component yes(boolean value) {
        return Component.translatable(value ? "book.immersive_jewelry.card.yes" : "book.immersive_jewelry.card.no");
    }

    @Override
    protected void draw(GuiGraphics g, IComponentRenderContext context, float partialTick, int mouseX, int mouseY) {
        int top = header(g, context, Component.translatable("book.immersive_jewelry.card.policy"), y);
        switch (topic == null ? "" : topic) {
            case "designs" -> top = fact(g, context, "require_sketches", yes(ServerConfig.get(ServerConfig.REQUIRE_DESIGN_SKETCHES)), top);
            case "wearing" -> {
                top = fact(g, context, "slots_ring", Component.literal(String.valueOf(ServerConfig.get(ServerConfig.MAX_RING_SLOTS))), top);
                top = fact(g, context, "slots_amulet", Component.literal(String.valueOf(ServerConfig.get(ServerConfig.MAX_AMULET_SLOTS))), top);
                top = fact(g, context, "slots_bracelet", Component.literal(String.valueOf(ServerConfig.get(ServerConfig.MAX_BRACELET_SLOTS))), top);
                top = fact(g, context, "slots_circlet", Component.literal(String.valueOf(ServerConfig.get(ServerConfig.MAX_CIRCLET_SLOTS))), top);
                top = fact(g, context, "curios", yes(net.minecraftforge.fml.ModList.get().isLoaded("curios")), top);
            }
            case "care" -> {
                top = fact(g, context, "wear", yes(ServerConfig.get(ServerConfig.ENABLE_WEAR)), top);
                top = fact(g, context, "gem_damage", yes(ServerConfig.get(ServerConfig.ENABLE_GEM_DAMAGE)), top);
                top = fact(g, context, "severe_hit", percent((float) ServerConfig.get(ServerConfig.SEVERE_HIT_FRACTION)), top);
                top = fact(g, context, "rework_cost", percent((float) ServerConfig.get(ServerConfig.REWORK_INTEGRITY_COST)), top);
            }
            default -> {
                top = fact(g, context, "assists", yes(ServerConfig.get(ServerConfig.ALLOW_ASSIST)), top);
                top = fact(g, context, "villagers", yes(ServerConfig.get(ServerConfig.ENABLE_VILLAGER_JEWELER)), top);
                top = fact(g, context, "villager_cap", percent((float) ServerConfig.get(ServerConfig.VILLAGER_MAX_WORKMANSHIP)), top);
            }
        }
        provenance(g, context, false, top + 2);
    }
}
