package com.otectus.immersivejewelry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.client.workstation.render.WheelPainter;
import com.otectus.immersivejewelry.client.workstation.StationStates;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * The Lapidary Wheel's moving parts when its Diegesis scene is not drawn (beyond the scene's drawing range): the same
 * painter as the scene, at rest. While the scene is drawn, the scene is the only renderer of these parts, so nothing is
 * ever drawn twice and the idle station never disappears.
 */
public class LapidaryWheelRenderer implements BlockEntityRenderer<LapidaryWheelBlockEntity> {

    public LapidaryWheelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LapidaryWheelBlockEntity wheel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        // A demonstration's station (Ponder) is never the scene's: only a station of the world steps aside for its scene.
        if (!com.otectus.immersivejewelry.client.presentation.PresentationClock.isPreview(wheel.getLevel())
                && com.otectus.immersivejewelry.client.workstation.DiegesisAccess.isSceneDrawn(wheel.getBlockPos())) return;
        Direction facing = wheel.getBlockState().getValue(StationBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Authored north-facing (front at -z), as the scene and the block model.
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);
        boolean animate = ClientConfig.get(ClientConfig.WORKSTATION_ANIMATIONS);
        com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.drawing(wheel, () -> WheelPainter.draw(new Geo(pose, buffers, light), StationStates.wheel(wheel, animate ? wheel.renderAngle(partialTick) : 0F),
                wheel.getLevel() == null ? 0F : wheel.getLevel().getGameTime() + partialTick));
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
