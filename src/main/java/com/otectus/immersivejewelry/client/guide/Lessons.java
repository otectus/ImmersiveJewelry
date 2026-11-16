package com.otectus.immersivejewelry.client.guide;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The demonstrations (Ponder lessons): their ids, the item each is listed under first (the one the handbook and the help
 * key open it from), the index topic, and a hidden per-lesson tag that deep-links into it. The ten design-atlas lessons
 * share the {@code design_atlas} topic page. Ids name lang keys ({@code lesson.immersive_jewelry.<id>}) and scenes
 * ({@code immersive_jewelry:<id>}).
 */
public enum Lessons {
    FIRST_PIECE(Topic.FIRST_PIECE, Subject.HANDBOOK),
    WATCHING_AND_HANDOFF(Topic.FIRST_PIECE, Subject.BENCH),
    APPRAISAL_AND_SOURCES(Topic.LAPIDARY, Subject.ROUGH_GEM),
    LAPS_AND_TOOLS(Topic.LAPIDARY, Subject.EMERY_LAP),
    ROUND_CUT(Topic.LAPIDARY, Subject.WHEEL),
    BRILLIANT_CUT(Topic.LAPIDARY, Subject.WHEEL),
    EMERALD_CUT(Topic.LAPIDARY, Subject.WHEEL),
    CABOCHON_CUT(Topic.LAPIDARY, Subject.WHEEL),
    CUT_MISTAKES_AND_RECUT(Topic.LAPIDARY, Subject.CUT_GEM),
    PORTABLE_STONE(Topic.LAPIDARY, Subject.ROUGH_GEM),
    BLANK_ROUTES(Topic.SETTING, Subject.RING_BLANK),
    FOLIO_AND_SKETCH(Topic.SETTING, Subject.SKETCH),
    SEAT_AND_PRONGS(Topic.SETTING, Subject.PLIERS),
    BEZEL_SETTING(Topic.SETTING, Subject.BENCH),
    THREE_STONES(Topic.SETTING, Subject.BENCH),
    ATLAS_SOLITAIRE_RING(Topic.SETTING, Subject.SKETCH, "solitaire_ring"),
    ATLAS_BEZEL_RING(Topic.SETTING, Subject.SKETCH, "bezel_ring"),
    ATLAS_TRILOGY_RING(Topic.SETTING, Subject.SKETCH, "trilogy_ring"),
    ATLAS_PENDANT_AMULET(Topic.SETTING, Subject.SKETCH, "pendant_amulet"),
    ATLAS_LOCKET_AMULET(Topic.SETTING, Subject.SKETCH, "locket_amulet"),
    ATLAS_HALO_AMULET(Topic.SETTING, Subject.SKETCH, "halo_amulet"),
    ATLAS_CUFF_BRACELET(Topic.SETTING, Subject.SKETCH, "cuff_bracelet"),
    ATLAS_LINK_BRACELET(Topic.SETTING, Subject.SKETCH, "link_bracelet"),
    ATLAS_SIMPLE_CIRCLET(Topic.SETTING, Subject.SKETCH, "simple_circlet"),
    ATLAS_DIADEM(Topic.SETTING, Subject.SKETCH, "diadem"),
    POLISHED_FINISH(Topic.FINISHING, Subject.COMPOUND),
    BURNISHED_FINISH(Topic.FINISHING, Subject.BENCH),
    ANTIQUE_FINISH(Topic.FINISHING, Subject.BENCH),
    QUALITY_AND_INSPECTION(Topic.FINISHING, Subject.RING),
    RETIGHTEN_AND_REPAIR(Topic.CARE, Subject.FINE_PLIERS),
    REMOVE_AND_REUSE(Topic.CARE, Subject.PLIERS),
    REFINISH_AND_RESUME(Topic.CARE, Subject.UNFINISHED),
    WEARING_AND_EFFECTS(Topic.EQUIPMENT, Subject.RING);

    /** The index topics (Ponder tags shown in its index). */
    public enum Topic {
        FIRST_PIECE, LAPIDARY, SETTING, FINISHING, CARE, EQUIPMENT;

        public ResourceLocation id() {
            return ImmersiveJewelry.id(name().toLowerCase(Locale.ROOT));
        }
    }

    /** The item a lesson is listed under first. */
    public enum Subject {
        HANDBOOK(() -> ModItems.JEWELERS_HANDBOOK.get()), WHEEL(() -> ModItems.LAPIDARY_WHEEL.get()), BENCH(() -> ModItems.JEWELERS_BENCH.get()),
        PLIERS(() -> ModItems.JEWELERS_PLIERS.get()), FINE_PLIERS(() -> ModItems.FINE_PLIERS.get()), EMERY_LAP(() -> ModItems.EMERY_LAP.get()),
        ROUGH_GEM(() -> ModItems.ROUGH_GEM.get()), CUT_GEM(() -> ModItems.CUT_GEM.get()), SKETCH(() -> ModItems.DESIGN_SKETCH.get()),
        COMPOUND(() -> ModItems.POLISHING_COMPOUND.get()), RING(() -> ModItems.RING.get()), RING_BLANK(() -> ModItems.RING_BLANK.get()),
        UNFINISHED(() -> ModItems.UNFINISHED_JEWELRY.get());

        private final Supplier<Item> item;

        Subject(Supplier<Item> item) {
            this.item = item;
        }

        public Item item() {
            return item.get();
        }
    }

    public final Topic topic;
    public final Subject subject;
    /** Atlas lessons: the design path under {@code immersive_jewelry:designs/}. */
    @Nullable
    public final String design;

    Lessons(Topic topic, Subject subject) {
        this(topic, subject, null);
    }

    Lessons(Topic topic, Subject subject, @Nullable String design) {
        this.topic = topic;
        this.subject = subject;
        this.design = design;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ResourceLocation sceneId() {
        return ImmersiveJewelry.id(id());
    }

    /** The hidden tag that opens this lesson exactly. */
    public ResourceLocation tagId() {
        return ImmersiveJewelry.id("lesson/" + id());
    }

    @Nullable
    public static Lessons byId(@Nullable String id) {
        if (id == null) return null;
        for (Lessons lesson : values()) if (lesson.id().equals(id)) return lesson;
        return null;
    }

    /** The atlas lesson of a built-in design, or null. */
    @Nullable
    public static Lessons atlas(@Nullable ResourceLocation design) {
        if (design == null || !design.getNamespace().equals(ImmersiveJewelry.MOD_ID)) return null;
        String path = design.getPath().startsWith("designs/") ? design.getPath().substring(8) : design.getPath();
        for (Lessons lesson : values()) if (path.equals(lesson.design)) return lesson;
        return null;
    }
}
