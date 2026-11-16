package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.item.DesignSketchItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.item.LapItem;
import com.otectus.immersivejewelry.item.PliersItem;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Where help leads: the handbook entry (and the demonstration) for what is in front of the player now. At a station that
 * is the step under way (a stone to mount, a cut, the folio, prongs or a bezel, a finish, a rework); for an item it is the
 * entry about that item. Every entry named here exists in the book (the resource test checks it).
 */
public final class HelpTopics {
    /** An entry, a page in it, and the lesson that demonstrates it (or null). */
    public record Topic(ResourceLocation entry, int page, @Nullable Lessons lesson) {}

    public static final String[] ENTRIES = {
            "start/welcome", "start/first_piece", "start/stations", "start/controls", "start/leaving",
            "stones/sources", "stones/appraisal", "stones/gems",
            "wheel/wheel", "wheel/cutting", "wheel/cuts", "wheel/recutting",
            "metal/blanks", "metal/forms", "metal/metals",
            "designs/folio", "designs/sketches", "designs/atlas", "designs/preparation",
            "setting/bench", "setting/prongs", "setting/bezels", "setting/multi_stone", "setting/defects",
            "finishing/finishes", "finishing/refinishing",
            "wearing/slots", "wearing/effects", "wearing/gates",
            "care/care", "care/rework", "care/storage",
            "workshop/jeweler", "workshop/multiplayer", "workshop/integrations",
            "trouble/common", "trouble/packs"};

    private static Topic of(String entry, @Nullable Lessons lesson) {
        return new Topic(ImmersiveJewelry.id(entry), 0, lesson);
    }

    /** The topic for the step under way at a station. */
    public static Topic station(StationBlockEntity station) {
        if (station instanceof LapidaryWheelBlockEntity wheel) {
            CutWork work = wheel.work().orElse(null);
            if (!wheel.hasStone()) return of("wheel/wheel", Lessons.APPRAISAL_AND_SOURCES);
            if (work == null) return of("wheel/cuts", Lessons.ROUND_CUT);
            if (work.recut()) return of("wheel/recutting", Lessons.CUT_MISTAKES_AND_RECUT);
            Lessons lesson = switch (work.cut().getPath()) {
                case "brilliant" -> Lessons.BRILLIANT_CUT;
                case "emerald" -> Lessons.EMERALD_CUT;
                case "cabochon" -> Lessons.CABOCHON_CUT;
                default -> Lessons.ROUND_CUT;
            };
            return of("wheel/cutting", lesson);
        }
        JewelersBenchBlockEntity bench = (JewelersBenchBlockEntity) station;
        BenchWork work = bench.work();
        if (work == null) {
            if (bench.clamp().getItem() instanceof JewelryItem) return of("care/rework", Lessons.RETIGHTEN_AND_REPAIR);
            return of(bench.clamp().isEmpty() ? "setting/bench" : "designs/folio", Lessons.FOLIO_AND_SKETCH);
        }
        return switch (work.kind()) {
            case REMOVE -> of("care/rework", Lessons.REMOVE_AND_REUSE);
            case REPAIR, RETIGHTEN -> of("care/rework", Lessons.RETIGHTEN_AND_REPAIR);
            case REFINISH -> of("finishing/refinishing", Lessons.REFINISH_AND_RESUME);
            case ASSEMBLE -> switch (work.stage()) {
                case PLAN -> of("designs/folio", Lessons.FOLIO_AND_SKETCH);
                case SETTING -> work.setting() != null && work.setting().params().style() == SettingStyle.BEZEL
                        ? of("setting/bezels", Lessons.BEZEL_SETTING)
                        : of("setting/prongs", work.setting() != null && work.setting().params().sockets() > 1 ? Lessons.THREE_STONES : Lessons.SEAT_AND_PRONGS);
                case FINISHING -> of("finishing/finishes", finishLesson(work.finish()));
            };
        };
    }

    private static Lessons finishLesson(@Nullable ResourceLocation finish) {
        FinishDefinition def = JewelryDefinitions.client().finish(finish);
        SurfaceParams.Mode mode = def != null ? SurfaceParams.Mode.forFinish(def.visualProfile()) : SurfaceParams.Mode.POLISH;
        return switch (mode) {
            case BURNISH -> Lessons.BURNISHED_FINISH;
            case PATINA -> Lessons.ANTIQUE_FINISH;
            default -> Lessons.POLISHED_FINISH;
        };
    }

    /** The topic about an item, or null when the handbook has nothing particular on it. */
    @Nullable
    public static Topic item(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(ModItems.LAPIDARY_WHEEL.get())) return of("wheel/wheel", Lessons.ROUND_CUT);
        if (stack.is(ModItems.JEWELERS_BENCH.get())) return of("setting/bench", Lessons.SEAT_AND_PRONGS);
        if (stack.getItem() instanceof LapItem) return of("start/stations", Lessons.LAPS_AND_TOOLS);
        if (stack.getItem() instanceof PliersItem) return of("setting/prongs", Lessons.SEAT_AND_PRONGS);
        if (stack.is(ModItems.ROUGH_GEM.get())) return of("stones/appraisal", Lessons.APPRAISAL_AND_SOURCES);
        if (stack.is(ModItems.CUT_GEM.get())) return of("wheel/cuts", Lessons.CUT_MISTAKES_AND_RECUT);
        if (stack.is(ModItems.GEM_DUST.get())) return of("wheel/recutting", Lessons.CUT_MISTAKES_AND_RECUT);
        if (stack.is(ModItems.POLISHING_COMPOUND.get())) return of("finishing/finishes", Lessons.POLISHED_FINISH);
        if (stack.getItem() instanceof DesignSketchItem) return of("designs/sketches", Lessons.FOLIO_AND_SKETCH);
        if (stack.getItem() instanceof JewelryBlankItem) return of("metal/blanks", Lessons.BLANK_ROUTES);
        if (stack.getItem() instanceof JewelryItem) return of("wearing/effects", Lessons.WEARING_AND_EFFECTS);
        if (stack.is(ModItems.UNFINISHED_JEWELRY.get())) return of("start/leaving", Lessons.REFINISH_AND_RESUME);
        if (stack.is(ModItems.JEWELERS_HANDBOOK.get())) return of("start/welcome", Lessons.FIRST_PIECE);
        if (JewelryDefinitions.client().gemForSource(stack) != null) return of("stones/sources", Lessons.APPRAISAL_AND_SOURCES);
        return null;
    }

    private HelpTopics() {}
}
