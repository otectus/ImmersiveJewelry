package com.otectus.immersivejewelry.client.compat.ponder;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.client.guide.HandbookNavigator;
import com.otectus.immersivejewelry.client.guide.Lessons;
import com.otectus.immersivejewelry.client.presentation.PresentationClock;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * The demonstrations (Ponder) for Jewelry: everything that touches Ponder lives in this package, and this class is its only
 * entry point, called once from client setup. It registers the plugin, lets the handbook's Watch buttons and the help key
 * open lessons ({@code PonderUI.of(item, tag)}, Ponder's own public way to start at a tagged scene), and gives Ponder's
 * levels their own presentation time (the scene's time, which follows pause, replay and speed), so rigs and poses in a
 * demonstration never use the world's clock.
 */
public final class JewelryPonder {
    public static void init() {
        PonderIndex.addPlugin(new JewelryPonderPlugin());
        HandbookNavigator.setLauncher(new HandbookNavigator.TutorialLauncher() {
            @Override
            public boolean open(Lessons lesson) {
                ResourceLocation component = JewelryPonderPlugin.components(lesson).get(0);
                PonderTag tag = PonderIndex.getTagAccess().getRegisteredTag(lesson.tagId());
                if (tag == null || !PonderIndex.getSceneAccess().doScenesExistForId(component)) return false;
                ScreenOpener.transitionTo(PonderUI.of(new ItemStack(JewelryPonderPlugin.item(component)), tag));
                return true;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean showing() {
                return Minecraft.getInstance().screen instanceof AbstractPonderScreen;
            }
        });
        PresentationClock.register(JewelryPonder::sceneTime);
        if (Minecraft.getInstance().getResourceManager() instanceof net.minecraft.server.packs.resources.ReloadableResourceManager manager) {
            manager.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) r -> reload());
        }
    }

    static OptionalDouble sceneTime(Level level) {
        if (!(level instanceof PonderLevel ponder) || ponder.scene == null) return OptionalDouble.empty();
        return OptionalDouble.of(ponder.scene.getCurrentTime() + PonderUI.getPartialTicks());
    }

    /** A resource reload: the demonstrating jeweler's model is rebuilt. */
    public static void reload() {
        JewelerActor.reload();
    }

    /** Test seam: every lesson compiled as Ponder compiles it when opened; problems are listed. */
    public static List<String> compileAll() {
        List<String> problems = new ArrayList<>();
        for (Lessons lesson : Lessons.values()) {
            try {
                List<PonderScene> scenes = PonderIndex.getSceneAccess().compile(JewelryPonderPlugin.components(lesson).get(0));
                if (scenes.stream().noneMatch(s -> s.getId().equals(lesson.sceneId()))) problems.add(lesson.id() + ": no scene");
            } catch (RuntimeException e) {
                ImmersiveJewelry.LOGGER.error("Demonstration {} could not be compiled", lesson.id(), e);
                problems.add(lesson.id() + ": " + e);
            }
        }
        return problems;
    }

    private JewelryPonder() {}
}
