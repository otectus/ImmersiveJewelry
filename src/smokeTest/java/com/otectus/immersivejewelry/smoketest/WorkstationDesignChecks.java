package com.otectus.immersivejewelry.smoketest;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in focused-bench gallery of every authored completed design. */
final class WorkstationDesignChecks {
    private static final boolean ENABLED = System.getenv("IJ_GEM_CONTINUITY") != null
            || System.getenv("IJ_DESIGN_VISUALS_ONLY") != null || ShowcaseCapture.enabled();
    private static final int TICKS_PER_DESIGN = 24;
    private static final List<String> DESIGNS = List.of("solitaire_ring", "bezel_ring", "trilogy_ring",
            "pendant_amulet", "locket_amulet", "halo_amulet", "cuff_bracelet", "link_bracelet",
            "simple_circlet", "diadem");
    private static final List<String> GEMS = List.of("ruby", "sapphire", "emerald", "amethyst");
    private static boolean clearing;

    static boolean enabled() { return ENABLED; }

    static boolean tick(Minecraft mc, Map<String, Object> checks, int tick) {
        int index = tick / TICKS_PER_DESIGN;
        int local = tick % TICKS_PER_DESIGN;
        if (index < DESIGNS.size()) {
            String name = DESIGNS.get(index);
            if (local == 0) stage(mc, name, index);
            if (local == 18) {
                ResourceLocation expected = ImmersiveJewelry.id("designs/" + name);
                JewelryDesignRecipe recipe = design(mc, expected);
                JewelryData shown = bench(mc) == null ? null : JewelryData.get(bench(mc).clamp()).orElse(null);
                boolean matches = recipe != null && shown != null && expected.equals(shown.design())
                        && recipe.form().equals(shown.form()) && shown.gems().size() == recipe.stones();
                checks.put("design_gallery_" + name, matches);
                ClientSmokeTest.shot(mc, "smoketest_design_" + name);
            }
            return false;
        }
        if (!clearing) {
            clearing = true;
            ClientSmokeTest.server(mc, (server, player) -> {
                if (server.overworld().getBlockEntity(ClientSmokeTest.bench()) instanceof JewelersBenchBlockEntity bench)
                    bench.setClamp(ItemStack.EMPTY);
            });
        }
        boolean empty = bench(mc) != null && bench(mc).clamp().isEmpty();
        if (empty) checks.put("design_gallery_clamp_restored", true);
        return empty && tick >= DESIGNS.size() * TICKS_PER_DESIGN + 4;
    }

    private static void stage(Minecraft mc, String name, int index) {
        ResourceLocation designId = ImmersiveJewelry.id("designs/" + name);
        ClientSmokeTest.server(mc, (server, player) -> {
            JewelryDesignRecipe design = server.overworld().getRecipeManager().byKey(designId)
                    .filter(JewelryDesignRecipe.class::isInstance).map(JewelryDesignRecipe.class::cast).orElseThrow();
            JewelryDefinitions defs = JewelryDefinitions.server();
            var gem = defs.gem(ImmersiveJewelry.id(GEMS.get(index % GEMS.size())));
            ResourceLocation cutId = design.allowedCuts().isEmpty() ? ImmersiveJewelry.id("round") : design.allowedCuts().get(0);
            var cut = defs.cut(cutId);
            List<GemData> stones = new ArrayList<>();
            for (int i = 0; i < design.stones(); i++) stones.add(JewelryFactory.sampleCut(gem, cut, .9F));
            ItemStack piece = JewelryFactory.jewelry(design.form(), JewelryFactory.metal(defs, ClientSmokeTest.GOLD),
                    stones, designId, ImmersiveJewelry.id("polished"), .9F, .9F, "Visual check");
            if (server.overworld().getBlockEntity(ClientSmokeTest.bench()) instanceof JewelersBenchBlockEntity bench)
                bench.setClamp(piece);
        });
    }

    private static JewelryDesignRecipe design(Minecraft mc, ResourceLocation id) {
        return mc.level == null ? null : mc.level.getRecipeManager().byKey(id)
                .filter(JewelryDesignRecipe.class::isInstance).map(JewelryDesignRecipe.class::cast).orElse(null);
    }

    private static JewelersBenchBlockEntity bench(Minecraft mc) {
        return mc.level != null && mc.level.getBlockEntity(ClientSmokeTest.bench()) instanceof JewelersBenchBlockEntity bench
                ? bench : null;
    }

    private WorkstationDesignChecks() {}
}
