package com.otectus.immersivejewelry.client.compat.ponder;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.client.guide.Lessons;
import com.otectus.immersivejewelry.client.presentation.anim.JewelerClip;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.otectus.immersivejewelry.client.compat.ponder.DemoHands.Style.CAREFUL;
import static com.otectus.immersivejewelry.client.compat.ponder.DemoHands.Style.HEAVY;
import static com.otectus.immersivejewelry.client.compat.ponder.DemoHands.Style.STILL;
import static com.otectus.immersivejewelry.client.compat.ponder.SceneKit.cut;
import static com.otectus.immersivejewelry.client.compat.ponder.SceneKit.rough;

/**
 * Every demonstration's storyboard. Each shows the real operation on the stations' own state (see {@link SceneKit}), with
 * a failure and its recovery where the lesson is about one. Captions are {@code immersive_jewelry.ponder.shared.<lesson>.<n>}
 * in the lang files; the resource test keeps the numbers used here and the lang keys in agreement. Long work is shown
 * faster than life, and the captions say so.
 */
final class LessonScenes {
    /** Steps per tick for "sped up" stretches. */
    private static final int FAST = 40;

    static PonderStoryBoard scene(Lessons lesson) {
        return switch (lesson) {
            case FIRST_PIECE -> SceneKit.board(lesson, SceneKit.Layout.WORKSHOP, LessonScenes::firstPiece);
            case WATCHING_AND_HANDOFF -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::watching);
            case APPRAISAL_AND_SOURCES -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, LessonScenes::appraisal);
            case LAPS_AND_TOOLS -> SceneKit.board(lesson, SceneKit.Layout.WORKSHOP, LessonScenes::tools);
            case ROUND_CUT -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, k -> cutLesson(k, "diamond", "round"));
            case BRILLIANT_CUT -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, k -> cutLesson(k, "diamond", "brilliant"));
            case EMERALD_CUT -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, k -> cutLesson(k, "emerald", "emerald"));
            case CABOCHON_CUT -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, k -> cutLesson(k, "lapis", "cabochon"));
            case CUT_MISTAKES_AND_RECUT -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, LessonScenes::mistakes);
            case PORTABLE_STONE -> SceneKit.board(lesson, SceneKit.Layout.WHEEL, LessonScenes::portable);
            case BLANK_ROUTES -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::blanks);
            case FOLIO_AND_SKETCH -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::folio);
            case SEAT_AND_PRONGS -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::prongs);
            case BEZEL_SETTING -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::bezel);
            case THREE_STONES -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::threeStones);
            case STAGING_AND_SUPPLIES -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::staging);
            case POLISHED_FINISH -> SceneKit.board(lesson, SceneKit.Layout.BENCH, k -> finish(k, "polished", new ItemStack(ModItems.POLISHING_COMPOUND.get(), 3)));
            case BURNISHED_FINISH -> SceneKit.board(lesson, SceneKit.Layout.BENCH, k -> finish(k, "burnished", ItemStack.EMPTY));
            case ANTIQUE_FINISH -> SceneKit.board(lesson, SceneKit.Layout.BENCH, k -> finish(k, "antique", new ItemStack(Items.GUNPOWDER, 2)));
            case QUALITY_AND_INSPECTION -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::quality);
            case RETIGHTEN_AND_REPAIR -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::retightenRepair);
            case REMOVE_AND_REUSE -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::remove);
            case REFINISH_AND_RESUME -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::refinishResume);
            case WEARING_AND_EFFECTS -> SceneKit.board(lesson, SceneKit.Layout.BENCH, LessonScenes::wearing);
            default -> SceneKit.board(lesson, SceneKit.Layout.BENCH, k -> atlas(k, lesson));
        };
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static ItemStack pliers() {
        return new ItemStack(ModItems.JEWELERS_PLIERS.get());
    }

    private static Item blankFor(String form) {
        return switch (form) {
            case "amulet" -> ModItems.AMULET_FRAME.get();
            case "bracelet" -> ModItems.BRACELET_BLANK.get();
            case "circlet" -> ModItems.CIRCLET_FRAME.get();
            default -> ModItems.RING_BLANK.get();
        };
    }

    /** A ready piece in the clamp: the stones set and secured (sped up), the finish about to begin. */
    private static void setPiece(SceneKit k, String design, List<GemData> stones) {
        k.dockItem(pliers());
        k.clampItem(SceneKit.blank(blankFor(form(design))));
        k.assemble(design, stones, 0.02F);
        k.set(60, CAREFUL, FAST);
    }

    private static String form(String design) {
        JewelryDesignRecipe recipe = SceneKit.design(design);
        return recipe == null ? "ring" : recipe.form().getPath();
    }

    // ------------------------------------------------------------------------------------------------ first steps

    private static void firstPiece(SceneKit k) {
        k.lap(ModItems.EMERY_LAP.get());
        k.jeweler(k.wheel);
        k.pose(JewelerClip.WHEEL_READY);
        k.click(k.quill(), 30, new ItemStack(Items.DIAMOND));
        k.mount(rough("diamond"));
        k.act(JewelerClip.WHEEL_MOUNT);
        k.say(0, k.quill());
        k.plan("round");
        k.say(1, k.rack());
        k.operate(true);
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(50, CAREFUL);
        k.say(2, k.lap());
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(100, CAREFUL, FAST);
        k.finishCut();
        k.act(JewelerClip.WHEEL_LIFT);
        k.good(3, k.lever());
        k.operate(false);
        k.takeStone();
        k.walkTo(k.bench);
        k.pose(JewelerClip.BENCH_READY);
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.dockItem(pliers());
        k.supplyItem(0, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 2));
        k.trayItem(0, SceneKit.stack(cut("diamond", "round", 0.82F)));
        k.say(4, k.clamp());
        k.operate(true);
        k.assemble("solitaire_ring", List.of(cut("diamond", "round", 0.82F)), 0.03F);
        k.act(JewelerClip.TWEEZERS_SEAT);
        k.idle(20);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.set(80, CAREFUL, FAST);
        k.good(5, k.clamp());
        k.beginFinish("polished");
        k.pose(JewelerClip.POLISH_WIPE);
        k.rub(80, CAREFUL, 20);
        k.completePiece();
        k.act(JewelerClip.INSPECT_PIECE);
        k.pose(JewelerClip.BENCH_READY);
        k.good(6, k.clamp());
        k.operate(false);
        k.note(7);
        k.example();
    }

    private static void watching(SceneKit k) {
        k.jeweler(k.bench);
        k.dockItem(pliers());
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.assemble("solitaire_ring", List.of(cut("ruby", "round", 0.78F)), 0.03F);
        k.operate(true);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.say(0, k.clamp());
        k.set(40, CAREFUL);
        k.note(1);
        k.operate(false);
        k.pose(null);
        k.say(2, k.clamp());
        k.operate(true);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.set(60, CAREFUL, FAST);
        k.note(3);
    }

    private static void appraisal(SceneKit k) {
        k.lap(ModItems.EMERY_LAP.get());
        k.jeweler(k.wheel);
        k.pose(JewelerClip.WHEEL_READY);
        k.click(k.quill(), 30, new ItemStack(Items.DIAMOND));
        k.mount(rough("diamond", 1.15F, 0.68F, 0.12F));
        k.act(JewelerClip.WHEEL_MOUNT);
        k.say(0, k.quill(), 90);
        k.takeStone();
        k.act(JewelerClip.WHEEL_MOUNT);
        k.say(1, k.quill());
        k.mount(rough("diamond", 1.15F, 0.68F, 0.12F));
        k.good(2, k.quill());
        k.note(3);
    }

    private static void tools(SceneKit k) {
        k.lap(ModItems.EMERY_LAP.get());
        k.say(0, k.lap());
        k.lap(ModItems.DIAMOND_LAP.get());
        k.say(1, k.lap());
        k.note(2);
        k.dockItem(pliers());
        k.say(3, k.dock());
        k.dockItem(new ItemStack(ModItems.FINE_PLIERS.get()));
        k.say(4, k.dock());
        k.mark(k.at(k.bench, 9.0F, 14.8F, 13.6F), 0.2, PonderPalette.WHITE, 80);
        k.say(5, k.at(k.bench, 9.0F, 14.8F, 13.6F));
        k.example();
    }

    // ------------------------------------------------------------------------------------------------ the wheel

    private static void cutLesson(SceneKit k, String gem, String cut) {
        k.lap(ModItems.EMERY_LAP.get());
        k.mount(rough(gem));
        k.jeweler(k.wheel);
        k.pose(JewelerClip.WHEEL_READY);
        k.plan(cut);
        k.say(0, k.rack());
        k.operate(true);
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(60, CAREFUL);
        k.say(1, k.lap(), 80);
        k.lift(20, false);
        k.act(JewelerClip.WHEEL_LIFT);
        k.say(2, k.quill());
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(120, CAREFUL, FAST);
        k.say(3, k.quill());
        k.finishCut();
        k.act(JewelerClip.WHEEL_LIFT);
        k.operate(false);
        k.good(4, k.quill());
    }

    private static void mistakes(SceneKit k) {
        k.lap(ModItems.EMERY_LAP.get());
        k.mount(rough("emerald", 1.3F, 0.62F, 0.3F));
        k.jeweler(k.wheel);
        k.plan("emerald");
        k.operate(true);
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(60, HEAVY);
        k.warn(0, k.lap());
        k.grind(60, HEAVY);
        k.warn(1, k.quill());
        k.act(JewelerClip.WHEEL_COOL);
        k.lift(30, true);
        k.say(2, k.cup());
        k.grind(80, HEAVY, FAST);
        k.finishCut();
        k.say(3, k.quill());
        k.note(4);
        k.plan("emerald");
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(100, CAREFUL, FAST);
        k.finishCut();
        k.operate(false);
        k.good(5, k.quill());
    }

    private static void portable(SceneKit k) {
        k.lap(ModItems.EMERY_LAP.get());
        k.mount(rough("sapphire"));
        k.jeweler(k.wheel);
        k.plan("round");
        k.operate(true);
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(80, CAREFUL);
        k.lift(10, false);
        k.operate(false);
        k.pose(null);
        k.say(0, k.quill());
        k.pocketStone();
        k.act(JewelerClip.WHEEL_MOUNT);
        k.note(1);
        k.remountStone();
        k.act(JewelerClip.WHEEL_MOUNT);
        k.good(2, k.quill());
        k.operate(true);
        k.pose(JewelerClip.WHEEL_GRIND);
        k.grind(100, CAREFUL, FAST);
        k.finishCut();
        k.operate(false);
        k.say(3, k.quill());
    }

    // ------------------------------------------------------------------------------------------------ the bench

    private static void blanks(SceneKit k) {
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.say(0, k.clamp());
        k.clampItem(SceneKit.blank(ModItems.AMULET_FRAME.get()));
        k.say(1, k.clamp());
        k.clampItem(SceneKit.blank(ModItems.BRACELET_BLANK.get()));
        k.say(2, k.clamp());
        k.clampItem(SceneKit.blank(ModItems.CIRCLET_FRAME.get()));
        k.say(3, k.clamp());
        k.note(4);
        k.note(5);
        k.note(6);
    }

    private static void folio(SceneKit k) {
        k.jeweler(k.bench);
        k.pose(JewelerClip.BENCH_READY);
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.mark(k.folio(), 0.25, PonderPalette.WHITE, 80);
        k.say(0, k.folio());
        k.warn(1, k.folio());
        k.click(k.folio(), 40, new ItemStack(ModItems.DESIGN_SKETCH.get()));
        k.say(2, k.folio(), 90);
        for (int i = 0; i < 3; i++) k.trayItem(i, SceneKit.stack(cut("diamond", "round", 0.7F + i * 0.06F)));
        k.say(3, k.tray());
    }

    /**
     * The staging drawer and the supply jars: real stacks put by hand, a shut drawer that hides and takes nothing, the jars'
     * counts, and preparing exactly the stones a design still needs. Its own demonstration bench only.
     */
    private static void staging(SceneKit k) {
        k.jeweler(k.bench);
        k.pose(JewelerClip.BENCH_READY);
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.mark(k.drawer(), 0.2, PonderPalette.WHITE, 70);
        k.say(0, k.drawer());
        k.click(k.drawer(), 30, null);
        k.drawerOpen(true);
        k.stageItem(0, SceneKit.stack(cut("ruby", "round", 0.82F)));
        k.stageItem(1, SceneKit.stack(cut("ruby", "round", 0.74F)));
        k.stageItem(2, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 4));
        k.say(1, k.drawer());
        k.supplyItem(0, new ItemStack(ModItems.POLISHING_COMPOUND.get(), 3));
        k.mark(k.supplies(), 0.2, PonderPalette.GREEN, 70);
        k.say(2, k.supplies());
        k.click(k.folio(), 30, null);
        k.stagedToTray(0);
        k.say(3, k.tray());
        k.drawerOpen(false);
        k.warn(4, k.drawer());
        k.note(5);
    }

    private static void prongs(SceneKit k) {
        k.jeweler(k.bench);
        k.dockItem(pliers());
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.trayItem(0, SceneKit.stack(cut("diamond", "round", 0.8F)));
        k.pose(JewelerClip.BENCH_READY);
        k.say(0, k.tray());
        k.operate(true);
        k.assemble("solitaire_ring", List.of(cut("diamond", "round", 0.8F)), 0.06F);
        k.act(JewelerClip.TWEEZERS_SEAT);
        k.say(1, k.clamp());
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.set(60, CAREFUL);
        k.say(2, k.clamp(), 90);
        k.set(80, CAREFUL, FAST);
        k.good(3, k.clamp());
        k.operate(false);
        k.warn(4, k.clamp());
    }

    private static void bezel(SceneKit k) {
        k.jeweler(k.bench);
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.operate(true);
        k.assemble("bezel_ring", List.of(cut("emerald", "cabochon", 0.78F)), 0.03F);
        k.pose(JewelerClip.BEZEL_PUSH);
        k.set(60, CAREFUL);
        k.say(0, k.clamp());
        k.set(60, CAREFUL);
        k.say(1, k.clamp());
        k.set(80, CAREFUL, FAST);
        k.operate(false);
        k.good(2, k.clamp());
    }

    private static void threeStones(SceneKit k) {
        k.jeweler(k.bench);
        List<GemData> stones = List.of(rough("diamond", 1.1F, 0.72F, 0.1F), rough("diamond", 0.9F, 0.6F, 0.25F), rough("diamond", 1.0F, 0.66F, 0.18F));
        List<GemData> cut = new ArrayList<>();
        for (int i = 0; i < stones.size(); i++) {
            GemData s = stones.get(i);
            cut.add(s.withCut(com.otectus.immersivejewelry.ImmersiveJewelry.id("round"), 0.7F + i * 0.07F, s.size() * 0.7F,
                    com.otectus.immersivejewelry.api.gem.GemDamage.NONE, 1F));
            k.trayItem(i, SceneKit.stack(cut.get(i)));
        }
        k.say(0, k.tray(), 90);
        k.dockItem(pliers());
        k.clampItem(SceneKit.blank(ModItems.AMULET_FRAME.get()));
        k.operate(true);
        k.assemble("halo_amulet", cut, 0.03F);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.set(100, CAREFUL, FAST);
        k.say(1, k.clamp());
        k.operate(false);
        k.good(2, k.clamp());
    }

    private static void atlas(SceneKit k, Lessons lesson) {
        String design = lesson.design != null ? lesson.design : "solitaire_ring";
        JewelryDesignRecipe recipe = SceneKit.design(design);
        int count = recipe != null ? recipe.stones() : 1;
        // The first allowed cut that a gem the design allows can take (a diamond is never shown as a cabochon).
        String gem = "diamond", cut = "round";
        search:
        for (net.minecraft.resources.ResourceLocation c : recipe != null && !recipe.allowedCuts().isEmpty() ? recipe.allowedCuts()
                : List.of(com.otectus.immersivejewelry.ImmersiveJewelry.id("round"))) {
            for (String g : new String[]{"diamond", "sapphire", "emerald", "amethyst", "lapis", "ruby"}) {
                net.minecraft.resources.ResourceLocation gemId = com.otectus.immersivejewelry.ImmersiveJewelry.id(g);
                if ((recipe == null || recipe.allowsGem(gemId)) && SceneKit.defs().gemOrGeneric(gemId).supports(c)) {
                    gem = g;
                    cut = c.getPath();
                    break search;
                }
            }
        }
        List<GemData> stones = new ArrayList<>();
        for (int i = 0; i < count; i++) stones.add(cut(gem, cut, 0.72F + i * 0.05F));
        k.jeweler(k.bench);
        k.operate(true);
        k.pose(recipe != null && recipe.style() == com.otectus.immersivejewelry.recipe.SettingStyle.BEZEL ? JewelerClip.BEZEL_PUSH : JewelerClip.PLIERS_SQUEEZE);
        setPiece(k, design, stones);
        k.operate(false);
        k.pose(JewelerClip.BENCH_READY);
        k.say(0, k.clamp(), 100);
    }

    private static void finish(SceneKit k, String finish, ItemStack material) {
        k.jeweler(k.bench);
        k.operate(true);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        setPiece(k, "pendant_amulet", List.of(cut("amethyst", "round", 0.8F)));
        if (!material.isEmpty()) k.supplyItem(0, material);
        k.say(0, material.isEmpty() ? k.clamp() : k.supplies());
        k.beginFinish(finish);
        JewelerClip loop = switch (finish) {
            case "burnished" -> JewelerClip.BURNISH_PASS;
            case "antique" -> JewelerClip.PATINA_DAB;
            default -> JewelerClip.POLISH_WIPE;
        };
        k.pose(loop);
        k.rub(50, CAREFUL);
        k.say(1, k.clamp());
        if (finish.equals("antique")) k.pose(JewelerClip.POLISH_WIPE);
        k.rub(50, CAREFUL, 12);
        k.say(2, material.isEmpty() ? k.clamp() : k.supplies());
        k.rub(60, CAREFUL, FAST);
        k.completePiece();
        k.act(JewelerClip.INSPECT_PIECE);
        k.operate(false);
        k.good(3, k.clamp());
    }

    private static void quality(SceneKit k) {
        k.clampItem(SceneKit.piece("solitaire_ring", List.of(cut("topaz", "brilliant", 0.86F)), "polished"));
        k.say(0, k.clamp(), 90);
        k.worn(1F, 0, EnumSet.of(SettingDefect.LOOSE));
        k.warn(1, k.clamp());
        k.note(2, 90);
        k.example();
    }

    private static void retightenRepair(SceneKit k) {
        k.jeweler(k.bench);
        k.dockItem(new ItemStack(ModItems.FINE_PLIERS.get()));
        k.clampItem(SceneKit.piece("solitaire_ring", List.of(cut("sapphire", "round", 0.8F)), "polished"));
        k.worn(0.62F, 1, EnumSet.of(SettingDefect.LOOSE));
        k.warn(0, k.clamp());
        k.operate(true);
        k.rework(BenchWork.Kind.RETIGHTEN, null);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.set(80, CAREFUL, FAST);
        k.completeRework();
        k.good(1, k.clamp());
        k.supplyItem(0, new ItemStack(Items.GOLD_NUGGET, 2));
        k.rework(BenchWork.Kind.REPAIR, null);
        k.pose(JewelerClip.REPAIR_TRUE);
        k.rub(80, CAREFUL, FAST);
        k.completeRework();
        k.operate(false);
        k.good(2, k.clamp());
        k.note(3);
    }

    private static void remove(SceneKit k) {
        k.jeweler(k.bench);
        k.dockItem(pliers());
        List<GemData> stones = List.of(cut("ruby", "round", 0.84F), cut("ruby", "round", 0.71F), cut("ruby", "round", 0.77F));
        k.clampItem(SceneKit.piece("halo_amulet", stones, "polished"));
        k.operate(true);
        k.rework(BenchWork.Kind.REMOVE, null);
        k.pose(JewelerClip.PRY_STONE);
        k.set(60, CAREFUL);
        k.say(0, k.clamp());
        k.set(60, CAREFUL, FAST);
        k.completeRework();
        k.operate(false);
        k.good(1, k.tray());
        k.say(2, k.clamp());
        k.note(3);
    }

    private static void refinishResume(SceneKit k) {
        k.jeweler(k.bench);
        k.clampItem(SceneKit.piece("cuff_bracelet", List.of(cut("onyx", "cabochon", 0.8F)), "polished"));
        k.operate(true);
        k.rework(BenchWork.Kind.REFINISH, "burnished");
        k.pose(JewelerClip.BURNISH_PASS);
        k.rub(50, CAREFUL);
        k.say(0, k.clamp());
        k.rub(60, CAREFUL, FAST);
        k.completeRework();
        k.good(1, k.clamp());
        k.collectFromClamp();
        k.dockItem(pliers());
        k.clampItem(SceneKit.blank(ModItems.RING_BLANK.get()));
        k.assemble("solitaire_ring", List.of(cut("quartz", "round", 0.75F)), 0.03F);
        k.pose(JewelerClip.PLIERS_SQUEEZE);
        k.set(40, CAREFUL);
        k.operate(false);
        k.pose(null);
        k.pack(k.clamp());
        k.say(2, k.clamp());
        k.unpack();
        k.good(3, k.clamp());
    }

    private static void wearing(SceneKit k) {
        k.clampItem(SceneKit.piece("solitaire_ring", List.of(cut("ruby", "round", 0.82F)), "polished"));
        k.note(0);
        k.note(1);
        k.say(2, k.clamp(), 100);
        k.worn(0F, 2, EnumSet.noneOf(SettingDefect.class));
        k.warn(3, k.clamp());
    }

    private LessonScenes() {}
}
