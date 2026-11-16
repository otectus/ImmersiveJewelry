package com.otectus.immersivejewelry.client.presentation.worn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Curios' bridge to {@link WornJewelryRenderer}: one renderer per jewelry item, registered with Curios (which calls it only
 * for visible, non-hidden slots and handles cosmetic slots). Reached only when Curios is loaded.
 */
public final class CuriosWornRenderers {
    public static void register() {
        for (Item item : new Item[]{ModItems.RING.get(), ModItems.AMULET.get(), ModItems.BRACELET.get(), ModItems.CIRCLET.get()}) {
            CuriosRendererRegistry.register(item, Renderer::new);
        }
    }

    private static final class Renderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot, PoseStack pose,
                                                                              RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                                                                              float limbSwing, float limbSwingAmount, float partialTicks,
                                                                              float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HumanoidModel<?> model)) return;
            WornJewelryRenderer.render(slot.entity(), model, WornJewelryRenderer.place(slot.identifier(), slot.index()), stack, pose, buffers, light);
        }
    }

    private CuriosWornRenderers() {}
}
