package com.otectus.immersivejewelry.client.presentation.worn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.equipment.builtin.SlotType;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The built-in slots' bridge to {@link WornJewelryRenderer}: draws what a player wears there from the synced snapshot.
 * Inactive while Curios is the equipment system (Curios draws its own slots through the same renderer, respecting its
 * cosmetic and visibility toggles). Invisible players show nothing.
 */
public final class WornJewelryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public WornJewelryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!JewelryEquipment.builtinActive() || player.isInvisible()) return;
        List<ItemStack> slots = WornAppearance.of(player.getId());
        int rings = 0;
        for (int i = 0; i < slots.size(); i++) {
            SlotType type = SlotType.forIndex(i);
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            WornJewelryRenderer.Place place = WornJewelryRenderer.place(type.id(), type == SlotType.RING ? rings++ : 0);
            WornJewelryRenderer.render(player, getParentModel(), place, stack, pose, buffers, light);
        }
    }
}
