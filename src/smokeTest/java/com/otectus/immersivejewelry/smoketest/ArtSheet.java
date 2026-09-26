package com.otectus.immersivejewelry.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.client.render.LapidaryWheelRenderer;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Test-only art review through Minecraft's real atlas, tint, override and perspective pipeline. */
final class ArtSheet extends Screen {
    private record Sample(String label, ItemStack stack) {}
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    private static final ItemDisplayContext[] CONTEXTS = {ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.GROUND};
    private static final String[] LABELS = {"First / right", "First / left", "Third person", "Dropped"};

    ArtSheet() { super(Component.literal("Jewelry artwork")); }

    private static List<Sample> samples() {
        var defs = JewelryDefinitions.client();
        List<Sample> out = new ArrayList<>();
        for (String form : new String[]{"ring", "amulet", "bracelet", "circlet"}) {
            out.add(new Sample(form, ClientSmokeTest.piece(defs, form, "ruby", "brilliant", GOLD, "polished", .9F)));
        }
        out.add(new Sample("Rough sapphire", RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("sapphire"), 1, .8F, .1F))));
        out.add(new Sample("Silver / emerald", ClientSmokeTest.piece(defs, "ring", "emerald", "emerald",
                new ResourceLocation("forge", "silver"), "antique", .8F)));
        for (String cut : new String[]{"round", "brilliant", "emerald", "cabochon"}) {
            out.add(new Sample(cut + " cut", CutGemItem.create(JewelryFactory.sampleCut(defs.gem(ImmersiveJewelry.id("sapphire")),
                    defs.cut(ImmersiveJewelry.id(cut)), .9F))));
        }
        out.add(new Sample("Ring blank", JewelryFactory.blank(ModItems.RING_BLANK.get(), JewelryFactory.metal(defs, GOLD), .8F)));
        out.add(new Sample("Amulet frame", JewelryFactory.blank(ModItems.AMULET_FRAME.get(), JewelryFactory.metal(defs, GOLD), .8F)));
        out.add(new Sample("Bracelet blank", JewelryFactory.blank(ModItems.BRACELET_BLANK.get(), JewelryFactory.metal(defs, GOLD), .8F)));
        out.add(new Sample("Circlet frame", JewelryFactory.blank(ModItems.CIRCLET_FRAME.get(), JewelryFactory.metal(defs, GOLD), .8F)));
        for (var entry : List.of(ModItems.JEWELERS_PLIERS, ModItems.FINE_PLIERS, ModItems.EMERY_LAP, ModItems.DIAMOND_LAP,
                ModItems.POLISHING_COMPOUND, ModItems.GEM_DUST, ModItems.DESIGN_SKETCH, ModItems.JEWELERS_HANDBOOK,
                ModItems.LAPIDARY_WHEEL, ModItems.JEWELERS_BENCH)) {
            ItemStack stack = new ItemStack(entry.get());
            out.add(new Sample(stack.getHoverName().getString(), stack));
        }
        return out;
    }

    static boolean checkModels(Minecraft mc) {
        List<ItemStack> stacks = new ArrayList<>(samples().stream().map(Sample::stack).toList());
        for (String form : new String[]{"ring", "amulet", "bracelet", "circlet"}) {
            for (String cut : new String[]{"round", "brilliant", "emerald", "cabochon"}) {
                stacks.add(ClientSmokeTest.piece(JewelryDefinitions.client(), form, "ruby", cut, GOLD, "polished", .9F));
            }
        }
        for (ItemStack stack : stacks) {
            var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
            if (model == mc.getModelManager().getMissingModel()) return false;
            for (ItemDisplayContext context : ItemDisplayContext.values()) {
                var resolved = model.applyTransform(context, new PoseStack(), context.name().endsWith("LEFT_HAND"));
                if (resolved.getParticleIcon().contents().name().getPath().equals("missingno")) return false;
                var quads = new ArrayList<>(resolved.getQuads(null, null, RandomSource.create(0)));
                for (Direction side : Direction.values()) quads.addAll(resolved.getQuads(null, side, RandomSource.create(0)));
                if (quads.isEmpty() || quads.stream().anyMatch(q -> q.getSprite().contents().name().getPath().equals("missingno"))) return false;
            }
        }
        for (var item : List.of(ModItems.JEWELERS_PLIERS.get(), ModItems.FINE_PLIERS.get())) {
            var model = mc.getItemRenderer().getModel(new ItemStack(item), mc.level, mc.player, 0);
            if (model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false)
                    == model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new PoseStack(), false)) return false;
        }
        return mc.getModelManager().getModel(LapidaryWheelRenderer.DISC) != mc.getModelManager().getMissingModel();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF242428);
        float scale = Math.min(width / 720F, height / 410F);
        g.pose().pushPose();
        g.pose().translate((width - 720 * scale) / 2, (height - 410 * scale) / 2, 0);
        g.pose().scale(scale, scale, scale);
        g.drawCenteredString(font, "IMMERSIVE JEWELRY / WORKSHOP ARTWORK", 360, 8, 0xECD7AC);
        List<Sample> samples = samples();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            int x = 4 + i % 6 * 119, y = 26 + i / 6 * 57;
            g.fill(x, y, x + 115, y + 53, 0xFF343236);
            g.pose().pushPose();
            g.pose().translate(x + 41, y + 3, 0);
            g.pose().scale(2, 2, 2);
            g.renderItem(sample.stack(), 0, 0);
            g.pose().popPose();
            g.drawCenteredString(font, sample.label(), x + 57, y + 40, 0xDBCEC0);
        }
        g.drawCenteredString(font, "SOLID PLIERS / DISPLAY CONTEXTS", 360, 270, 0xECD7AC);
        for (int tool = 0; tool < 2; tool++) {
            ItemStack stack = new ItemStack(tool == 0 ? ModItems.JEWELERS_PLIERS.get() : ModItems.FINE_PLIERS.get());
            for (int i = 0; i < CONTEXTS.length; i++) {
                int x = 45 + (tool * 4 + i) * 90;
                g.pose().pushPose();
                g.pose().translate(x, 336, 180);
                g.pose().scale(72, -72, 72);
                g.pose().mulPose(Axis.YP.rotationDegrees(25));
                minecraft.getItemRenderer().renderStatic(stack, CONTEXTS[i], LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, g.pose(), g.bufferSource(), minecraft.level, 0);
                g.flush();
                g.pose().popPose();
                g.drawCenteredString(font, LABELS[i], x, 386, 0xDBCEC0);
            }
        }
        g.pose().popPose();
    }
}
