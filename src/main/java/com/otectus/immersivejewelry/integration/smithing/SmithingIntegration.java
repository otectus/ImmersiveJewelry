package com.otectus.immersivejewelry.integration.smithing;

import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.Optional;

/**
 * Immersive Smithing makes the metal (spec 5). {@link SmithingBlankRecipes} gives Smithing a blank recipe for every
 * material family and form; the quench is then Smithing's own, and this class copies the result into the blank's
 * jewelry data: the family, its tint and name, and {@code SmithingQuality = overall score / 100} (Faulty: at most
 * 0.15). Blanks tell Smithing's recycling their metal; finished jewelry is never melted (its stones would be lost).
 */
public final class SmithingIntegration {
    public static final float FAULTY_CAP = 0.15F;

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, ItemSmithedEvent.class, SmithingIntegration::onSmithed);
    }

    public static void commonSetup() {
        ImmersiveSmithingAPI.registerRecipeProvider(new SmithingBlankRecipes());
        ImmersiveSmithingAPI.registerExclusion(stack -> stack.getItem() instanceof JewelryItem);
        ImmersiveSmithingAPI.registerRecyclingProvider(SmithingIntegration::recycling);
    }

    private static void onSmithed(ItemSmithedEvent event) {
        ItemStack result = event.getItem();
        if (!(result.getItem() instanceof JewelryBlankItem)) return;
        QualityData quality = event.getQuality();
        float score = quality.overallScore() / 100F;
        if (quality.faulty()) score = Math.min(score, FAULTY_CAP);
        CompoundTag smithing = result.getTag() != null && result.getTag().contains(QualityData.ROOT, Tag.TAG_COMPOUND)
                ? result.getTag().getCompound(QualityData.ROOT).copy() : null;
        MetalRef previous = BlankData.get(result).map(BlankData::metal).orElse(MetalRef.of(event.getFamily()));
        MetalRef metal = new MetalRef(event.getFamily(), previous.nameJson(), previous.color());
        BlankData.of(metal, score).withSmithing(metal, score, smithing).apply(result);
    }

    private static Optional<RecyclingValue> recycling(ItemStack stack) {
        if (!(stack.getItem() instanceof JewelryBlankItem blank)) return Optional.empty();
        CompoundTag root = JewelryNbt.root(stack);
        if (root == null) return Optional.empty();
        BlankData data = BlankData.load(root);
        FormDefinition form = JewelryDefinitions.server().form(blank.form());
        int units = form != null ? form.metalUnits() : 9;
        return Optional.of(new RecyclingValue(data.metal().id(), Math.max(1, Math.round(units * data.integrity())), "immersive_jewelry blank"));
    }

    static boolean blacklisted(String family) {
        return CommonConfig.list(CommonConfig.SMITHING_METAL_BLACKLIST).contains(family);
    }

    private SmithingIntegration() {}
}
