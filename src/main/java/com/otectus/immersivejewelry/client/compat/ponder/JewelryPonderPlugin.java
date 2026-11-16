package com.otectus.immersivejewelry.client.compat.ponder;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.client.guide.Lessons;
import com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs;
import com.otectus.immersivejewelry.registry.ModItems;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * The Jewelry demonstrations as one Ponder plugin: a scene per lesson ({@link Lessons}), listed under the items it teaches
 * (the stations, pliers, laps, stones, the handbook, the sketch, polishing compound and the forms and blanks), six topic
 * tags in Ponder's index, and a hidden tag per lesson that the handbook's Watch buttons and the help key open precisely
 * ({@code PonderUI.of(item, tag)} starts at the first scene carrying the tag). Works with standalone Ponder; Create is not
 * needed. All text is in the lang files ({@code immersive_jewelry.ponder.*}).
 */
final class JewelryPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return ImmersiveJewelry.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        for (Lessons lesson : Lessons.values()) {
            helper.forComponents(components(lesson)).addStoryBoard(structure(lesson), LessonScenes.scene(lesson), lesson.topic.id(), lesson.tagId());
        }
    }

    /** The structure a lesson plays on (must match the layout its storyboard uses). */
    static String structure(Lessons lesson) {
        return switch (lesson) {
            case FIRST_PIECE, LAPS_AND_TOOLS -> "workshop";
            case APPRAISAL_AND_SOURCES, ROUND_CUT, BRILLIANT_CUT, EMERALD_CUT, CABOCHON_CUT, CUT_MISTAKES_AND_RECUT, PORTABLE_STONE -> "wheel";
            default -> "bench";
        };
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        for (Lessons.Topic topic : Lessons.Topic.values()) {
            helper.registerTag(topic.id()).addToIndex().item(icon(topic), true, false)
                    .title(topic.name()).description(topic.name()).register();
        }
        for (Lessons lesson : Lessons.values()) {
            helper.registerTag(lesson.tagId()).item(lesson.subject.item(), true, false).title(lesson.id()).description(lesson.id()).register();
        }
        helper.addToTag(Lessons.Topic.FIRST_PIECE.id()).add(id(ModItems.JEWELERS_HANDBOOK.get())).add(id(ModItems.LAPIDARY_WHEEL.get()))
                .add(id(ModItems.JEWELERS_BENCH.get())).add(id(ModItems.RING.get()));
        helper.addToTag(Lessons.Topic.LAPIDARY.id()).add(id(ModItems.LAPIDARY_WHEEL.get())).add(id(ModItems.EMERY_LAP.get()))
                .add(id(ModItems.DIAMOND_LAP.get())).add(id(ModItems.ROUGH_GEM.get())).add(id(ModItems.CUT_GEM.get()));
        helper.addToTag(Lessons.Topic.SETTING.id()).add(id(ModItems.JEWELERS_BENCH.get())).add(id(ModItems.JEWELERS_PLIERS.get()))
                .add(id(ModItems.FINE_PLIERS.get())).add(id(ModItems.DESIGN_SKETCH.get())).add(id(ModItems.RING_BLANK.get()));
        helper.addToTag(Lessons.Topic.FINISHING.id()).add(id(ModItems.POLISHING_COMPOUND.get())).add(id(ModItems.JEWELERS_BENCH.get()));
        helper.addToTag(Lessons.Topic.CARE.id()).add(id(ModItems.FINE_PLIERS.get())).add(id(ModItems.UNFINISHED_JEWELRY.get()))
                .add(id(ModItems.JEWELERS_BENCH.get()));
        helper.addToTag(Lessons.Topic.EQUIPMENT.id()).add(id(ModItems.RING.get())).add(id(ModItems.AMULET.get()))
                .add(id(ModItems.BRACELET.get())).add(id(ModItems.CIRCLET.get()));
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("example", "Example: the numbers the mod ships with. Packs can change them; the handbook shows this server's.");
    }

    /** A replay or a closed scene: what Jewelry kept for a demonstration level goes with it. */
    @Override
    public void onPonderLevelRestore(PonderLevel level) {
        JewelryRigs.forget();
        PonderStore.clear();
    }

    /** The items a lesson is listed under; the first is the one the handbook opens it from. */
    static List<ResourceLocation> components(Lessons lesson) {
        ResourceLocation first = id(lesson.subject.item());
        return switch (lesson.subject) {
            case PLIERS, FINE_PLIERS -> List.of(first, id(ModItems.JEWELERS_BENCH.get()));
            case EMERY_LAP -> List.of(first, id(ModItems.DIAMOND_LAP.get()), id(ModItems.LAPIDARY_WHEEL.get()));
            case ROUGH_GEM, CUT_GEM -> List.of(first, id(ModItems.LAPIDARY_WHEEL.get()));
            case RING_BLANK -> List.of(first, id(ModItems.AMULET_FRAME.get()), id(ModItems.BRACELET_BLANK.get()), id(ModItems.CIRCLET_FRAME.get()));
            case RING -> List.of(first, id(ModItems.AMULET.get()), id(ModItems.BRACELET.get()), id(ModItems.CIRCLET.get()));
            case SKETCH, COMPOUND, UNFINISHED -> List.of(first, id(ModItems.JEWELERS_BENCH.get()));
            default -> List.of(first);
        };
    }

    private static Item icon(Lessons.Topic topic) {
        return switch (topic) {
            case FIRST_PIECE -> ModItems.JEWELERS_HANDBOOK.get();
            case LAPIDARY -> ModItems.LAPIDARY_WHEEL.get();
            case SETTING -> ModItems.JEWELERS_PLIERS.get();
            case FINISHING -> ModItems.POLISHING_COMPOUND.get();
            case CARE -> ModItems.FINE_PLIERS.get();
            case EQUIPMENT -> ModItems.RING.get();
        };
    }

    static Item item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item != null ? item : ModItems.JEWELERS_HANDBOOK.get();
    }

    private static ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
