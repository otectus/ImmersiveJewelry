package com.otectus.immersivejewelry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.client.workstation.StationStates;
import com.otectus.immersivejewelry.client.workstation.render.BenchPainter;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.pockyl.diegesis.api.client.DiegesisClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * The Jeweler's Bench's work when its Diegesis scene is not drawn: the same painter as the scene, with no tool in hand.
 * While the scene is drawn it is the only renderer of these parts.
 */
public class JewelersBenchRenderer implements BlockEntityRenderer<JewelersBenchBlockEntity> {

    public JewelersBenchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(JewelersBenchBlockEntity bench, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // A demonstration's station (Ponder) is never the scene's: only a station of the world steps aside for its scene.
        if (!com.otectus.immersivejewelry.client.presentation.PresentationClock.isPreview(bench.getLevel())
                && DiegesisClient.isSceneDrawn(bench.getBlockPos())) return;
        Direction facing = bench.getBlockState().getValue(StationBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);
        com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.drawing(bench, () -> BenchPainter.draw(new Geo(pose, buffers, light), StationStates.bench(bench), 0F));
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
