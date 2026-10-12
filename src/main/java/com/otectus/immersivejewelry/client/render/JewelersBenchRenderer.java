package com.otectus.immersivejewelry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The bench's visible workpiece (spec 6.1): the blank or piece lies on the bench pad; while stones are being set the
 * loose stone hovers over it and settles lower as prongs are secured.
 */
public class JewelersBenchRenderer implements BlockEntityRenderer<JewelersBenchBlockEntity> {

    public JewelersBenchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(JewelersBenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack piece = bench.workpiece();
        ItemStack stone = bench.displayStone();
        if (piece.isEmpty() && stone.isEmpty()) return;
        Direction facing = bench.getBlockState().getValue(StationBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Authored models face north; Direction's yaw zero faces south.
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        int seed = (int) bench.getBlockPos().asLong();
        if (!piece.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 14.42F / 16F, 7.75F / 16F);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.42F, 0.42F, 0.42F);
            try (var colors = JewelryColors.cacheForRender(piece)) {
                Minecraft.getInstance().getItemRenderer().renderStatic(piece, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        bench.getLevel(), seed);
            }
            pose.popPose();
        }
        if (!stone.isEmpty()) {
            boolean animate = ClientConfig.get(ClientConfig.WORKSTATION_ANIMATIONS);
            float time = bench.getLevel() == null ? 0F : bench.getLevel().getGameTime() + partialTick;
            float hover = (1F - bench.progress()) * 0.12F + (animate ? (float) Math.sin(time * 0.1) * 0.01F : 0F);
            pose.pushPose();
            pose.translate(0.5, 14.95F / 16F + hover, 7.75F / 16F);
            if (animate) pose.mulPose(Axis.YP.rotationDegrees(time * 2F));
            pose.scale(0.22F, 0.22F, 0.22F);
            try (var colors = JewelryColors.cacheForRender(stone)) {
                Minecraft.getInstance().getItemRenderer().renderStatic(stone, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        bench.getLevel(), seed + 1);
            }
            pose.popPose();
        }
        pose.popPose();
    }
}
