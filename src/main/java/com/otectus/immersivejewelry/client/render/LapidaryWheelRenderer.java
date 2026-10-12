package com.otectus.immersivejewelry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The Lapidary Wheel in motion (spec 6.2): the wheel disc is a separate model spun about its axle and tinted by the
 * installed lap; the stone sits in the quill beside it, trembling while it is being ground.
 */
public class LapidaryWheelRenderer implements BlockEntityRenderer<LapidaryWheelBlockEntity> {
    public static final ResourceLocation DISC = ImmersiveJewelry.id("block/lapidary_wheel_disc");
    /** Axle centre in block pixels (the disc model is drawn around it). */
    private static final float AXLE_X = 8F / 16F;
    private static final float AXLE_Y = 12F / 16F;
    private static final float AXLE_Z = 8F / 16F;
    private final RandomSource renderRandom = RandomSource.create();

    public LapidaryWheelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LapidaryWheelBlockEntity wheel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = wheel.getBlockState().getValue(StationBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Match the north-facing block model and its blockstate quarter turns.
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);

        boolean animate = ClientConfig.get(ClientConfig.WORKSTATION_ANIMATIONS);
        // The tick angle wraps at 2pi. Interpolating the wrapped endpoints makes the disc snap backwards.
        float angle = animate ? wheel.renderAngle(partialTick) : 0F;
        BakedModel disc = Minecraft.getInstance().getModelManager().getModel(DISC);
        int tint = wheel.tier().color;
        pose.pushPose();
        pose.translate(AXLE_X, AXLE_Y, AXLE_Z);
        pose.mulPose(Axis.ZP.rotation(angle));
        pose.translate(-AXLE_X, -AXLE_Y, -AXLE_Z);
        // Honour resource-pack render_type overrides (the shipped textures are opaque and use solid).
        renderRandom.setSeed(42L);
        for (RenderType layer : disc.getRenderTypes(wheel.getBlockState(), renderRandom, ModelData.EMPTY)) {
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(layer), wheel.getBlockState(), disc,
                    ((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, light, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, layer);
        }
        pose.popPose();

        ItemStack stone = wheel.stone();
        if (!stone.isEmpty()) {
            pose.pushPose();
            // The quill holds the stone against the wheel's face, just below the top of its rim.
            float tremble = animate && wheel.isWorking() ? Mth.sin((wheel.getLevel().getGameTime() + partialTick) * 2.1F) * 0.006F : 0F;
            pose.translate(0.5 + tremble, 15.1F / 16F, 6.15F / 16F);
            pose.mulPose(Axis.XP.rotationDegrees(15));
            pose.scale(0.3F, 0.3F, 0.3F);
            try (var colors = JewelryColors.cacheForRender(stone)) {
                Minecraft.getInstance().getItemRenderer().renderStatic(stone, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                        wheel.getLevel(), (int) wheel.getBlockPos().asLong());
            }
            pose.popPose();
        }
        pose.popPose();
    }
}
