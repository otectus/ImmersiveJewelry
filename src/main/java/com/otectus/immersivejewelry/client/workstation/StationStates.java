package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.workstation.render.BenchPainter;
import com.otectus.immersivejewelry.client.workstation.render.DesignLayout;
import com.otectus.immersivejewelry.client.workstation.render.WheelPainter;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a station looks like from its synced state alone, with no one's hands on it: for the fallback renderers and as
 * the base the behaviours add their live controls to.
 */
public final class StationStates {

    public static WheelPainter.State wheel(LapidaryWheelBlockEntity wheel, float lapAngle) {
        CutWork work = wheel.work().orElse(null);
        float sweep = work != null ? work.sim().sweep() : 0F;
        float height = work != null ? work.sim().height() : 1F;
        return new WheelPainter.State(WheelBehavior.stoneData(wheel), work, wheel.tier().color, lapAngle, sweep, height, 0F,
                work != null ? work.sim().region() : 0, work != null ? work.sim().trim() : 0, null, List.of(), -1, new float[0], false, 0F,
                false, WheelBehavior.stoneSeed(wheel.stone()), null, WheelPainter.stoneSize(WheelBehavior.stoneData(wheel)));
    }

    public static BenchPainter.State bench(JewelersBenchBlockEntity bench) {
        BenchWork work = bench.work();
        ItemStack[] sockets = new ItemStack[JewelersBenchBlockEntity.SOCKETS];
        ItemStack[] tray = new ItemStack[JewelersBenchBlockEntity.TRAY];
        ItemStack[] supplies = new ItemStack[JewelersBenchBlockEntity.SUPPLIES];
        for (int i = 0; i < sockets.length; i++) sockets[i] = bench.socket(i);
        for (int i = 0; i < tray.length; i++) tray[i] = bench.tray(i);
        for (int i = 0; i < supplies.length; i++) supplies[i] = bench.supply(i);
        JewelryDesignRecipe design = design(bench, work);
        SettingSim sim = work != null ? work.setting() : null;
        SettingStyle style = sim != null ? sim.params().style() : design != null ? design.style() : SettingStyle.PRONG;
        int points = sim != null ? sim.params().points() : design != null ? design.pointsPerStone() : 4;
        return new BenchPainter.State(bench.clamp(), sockets, tray, supplies, bench.dock(), work, layout(bench, work), style, points,
                sim != null ? sim.params().tolerance() : 0.1F, BenchPainter.Tool.NONE, null, 0F, false, 0, 0, -1, 0F, null, false, false,
                work == null && bench.clamp().getItem() instanceof JewelryItem ? 1F : 0F, 0F);
    }

    @Nullable
    static JewelryDesignRecipe design(JewelersBenchBlockEntity bench, @Nullable BenchWork work) {
        ResourceLocation id = work != null ? work.design() : JewelryData.get(bench.clamp()).map(JewelryData::design).orElse(null);
        if (id == null || Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getRecipeManager().byKey(id).filter(r -> r instanceof JewelryDesignRecipe)
                .map(r -> (JewelryDesignRecipe) r).orElse(null);
    }

    @Nullable
    static DesignLayout layout(JewelersBenchBlockEntity bench, @Nullable BenchWork work) {
        ItemStack frame = bench.clamp();
        ResourceLocation form = frame.getItem() instanceof JewelryBlankItem blank ? blank.form()
                : frame.getItem() instanceof JewelryItem item ? item.form() : null;
        if (form == null) return null;
        JewelryDesignRecipe design = design(bench, work);
        JewelryData finished = JewelryData.get(frame).orElse(null);
        int stones = design != null ? design.stones() : finished != null ? Math.max(1, finished.gems().size()) : 1;
        if (design != null) return DesignLayout.of(form, design.getId(), stones, design.style());
        return DesignLayout.generic(form, stones, SettingStyle.PRONG);
    }

    private StationStates() {}
}
