package com.otectus.immersivejewelry.client.presentation.worn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.item.JewelryItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The one way a worn piece is drawn on a player, for both bridges (the built-in slots' layer and Curios' renderer): the
 * piece's own layered, tinted item model, small and attached to the already posed model part (so vanilla and playerAnimator
 * poses carry it): rings on the fingers, an amulet on the chest, a bracelet at the wrist, a circlet on the head. Drawing
 * never touches effects or equipment. Circlets give way to a helmet and amulets to a chestplate, as hair and collars do.
 */
public final class WornJewelryRenderer {
    public enum Place { RING_RIGHT, RING_LEFT, NECKLACE, BRACELET, HEAD }

    public static boolean visible(Place place) {
        if (!ClientConfig.get(ClientConfig.SHOW_WORN_JEWELRY)) return false;
        return switch (place) {
            case RING_RIGHT, RING_LEFT -> ClientConfig.get(ClientConfig.SHOW_WORN_RINGS);
            case NECKLACE -> ClientConfig.get(ClientConfig.SHOW_WORN_AMULETS);
            case BRACELET -> ClientConfig.get(ClientConfig.SHOW_WORN_BRACELETS);
            case HEAD -> ClientConfig.get(ClientConfig.SHOW_WORN_CIRCLETS);
        };
    }

    /** The place for a form's slot id ("ring", "necklace", "bracelet", "head"); the second ring goes on the left hand. */
    public static Place place(String slot, int ringIndex) {
        return switch (slot) {
            case "necklace", "charm" -> Place.NECKLACE;
            case "bracelet" -> Place.BRACELET;
            case "head" -> Place.HEAD;
            default -> ringIndex % 2 == 0 ? Place.RING_RIGHT : Place.RING_LEFT;
        };
    }

    public static void render(LivingEntity wearer, HumanoidModel<?> model, Place place, ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                              int light) {
        if (stack.isEmpty() || !(stack.getItem() instanceof JewelryItem) || !visible(place)) return;
        if (place == Place.HEAD && !wearer.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        if (place == Place.NECKLACE && !wearer.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return;
        pose.pushPose();
        switch (place) {
            case RING_RIGHT, RING_LEFT -> {
                HumanoidArm arm = place == Place.RING_RIGHT ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
                model.translateToHand(arm, pose);
                pose.translate((arm == HumanoidArm.RIGHT ? -1 : 1) / 16F, 0.6F, 0F);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.scale(0.16F, 0.16F, 0.16F);
            }
            case BRACELET -> {
                model.translateToHand(HumanoidArm.LEFT, pose);
                pose.translate(1 / 16F, 0.45F, 0F);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.scale(0.3F, 0.3F, 0.3F);
            }
            case NECKLACE -> {
                model.body.translateAndRotate(pose);
                pose.translate(0F, 0.32F, -0.14F);
                pose.mulPose(Axis.ZP.rotationDegrees(180F));
                pose.scale(0.32F, 0.32F, 0.32F);
            }
            case HEAD -> {
                model.head.translateAndRotate(pose);
                pose.translate(0F, -0.5F, 0F);
                pose.mulPose(Axis.XP.rotationDegrees(-90F));
                pose.scale(0.62F, 0.62F, 0.62F);
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(wearer, stack, ItemDisplayContext.FIXED, false, pose, buffers, wearer.level(), light,
                OverlayTexture.NO_OVERLAY, wearer.getId() + place.ordinal());
        pose.popPose();
    }

    private WornJewelryRenderer() {}
}
