package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.advancement.ModAdvancements;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.util.Feedback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Teaches one advanced design when used (bought from Jewelers or found in treasure). */
public class DesignSketchItem extends Item {

    public DesignSketchItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(ResourceLocation design) {
        ItemStack stack = new ItemStack(ModItems.DESIGN_SKETCH.get());
        CompoundTag root = new CompoundTag();
        root.putString("Design", design.toString());
        JewelryNbt.write(stack, root);
        return stack;
    }

    @Nullable
    public static ResourceLocation design(ItemStack stack) {
        CompoundTag root = JewelryNbt.root(stack);
        return root == null ? null : ResourceLocation.tryParse(root.getString("Design"));
    }

    @Nullable
    private static JewelryDesignRecipe recipe(@Nullable Level level, ItemStack stack) {
        ResourceLocation id = design(stack);
        if (level == null || id == null) return null;
        return level.getRecipeManager().byKey(id).filter(r -> r instanceof JewelryDesignRecipe)
                .map(r -> (JewelryDesignRecipe) r).orElse(null);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = design(stack);
        if (id == null) return super.getName(stack);
        return Component.translatable(getDescriptionId() + ".named",
                Component.translatable("design." + id.getNamespace() + "." + id.getPath().replace("designs/", "").replace('/', '.')));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        JewelryDesignRecipe design = recipe(level, stack);
        if (design == null) {
            Feedback.fail(player, "sketch_unknown");
            return InteractionResultHolder.fail(stack);
        }
        if (!DesignKnowledge.learn(player, design.getId())) {
            Feedback.info(player, "sketch_known", design.name());
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.blockPosition(), ModSounds.SKETCH_LEARN.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        Feedback.info(player, "sketch_learned", design.name());
        if (player instanceof ServerPlayer sp) ModAdvancements.award(sp, ModAdvancements.NEW_DESIGN);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        JewelryDesignRecipe design = recipe(level, stack);
        if (design != null) {
            lines.add(Component.translatable("tooltip.immersive_jewelry.sketch_design", design.stones(),
                    Component.translatable("setting_style.immersive_jewelry." + design.style().id())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("tooltip.immersive_jewelry.sketch").withStyle(ChatFormatting.DARK_GRAY));
    }
}
