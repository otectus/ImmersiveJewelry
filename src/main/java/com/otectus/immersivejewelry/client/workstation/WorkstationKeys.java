package com.otectus.immersivejewelry.client.workstation;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The keyboard frontend of the workstations: every hands-on action can be done without the mouse, through the same
 * server operations. All rebindable; none needs to be held together with a mouse button. They are read only while a
 * Jewelry station is focused (the station screen is open), so they never clash with walking.
 */
public final class WorkstationKeys {
    private static final String CATEGORY = "key.categories.immersive_jewelry.workstation";

    public static final KeyMapping RAISE = key("raise", GLFW.GLFW_KEY_UP);
    public static final KeyMapping LOWER = key("lower", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping LEFT = key("left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping RIGHT = key("right", GLFW.GLFW_KEY_RIGHT);
    /** Apply the tool: grind, grip, push, rub. Held, or toggled with the toggleToolUse option. */
    public static final KeyMapping APPLY = key("apply", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_X);
    public static final KeyMapping PREVIOUS = key("previous", GLFW.GLFW_KEY_Z);
    public static final KeyMapping TRIM_UP = key("trim_up", GLFW.GLFW_KEY_C);
    public static final KeyMapping TRIM_DOWN = key("trim_down", GLFW.GLFW_KEY_V);
    public static final KeyMapping TOOL = key("tool", GLFW.GLFW_KEY_T);
    public static final KeyMapping INSPECT = key("inspect", GLFW.GLFW_KEY_F);
    public static final KeyMapping COOL = key("cool", GLFW.GLFW_KEY_G);
    public static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping FOLIO = key("folio", GLFW.GLFW_KEY_B);
    public static final KeyMapping TURN = key("turn", GLFW.GLFW_KEY_R);
    /** The bench's staging drawer: its list of the four staged stacks, opening and shutting it (works with the drawer shut too). */
    public static final KeyMapping DRAWER = key("drawer", GLFW.GLFW_KEY_N);

    public static final List<KeyMapping> ALL = List.of(RAISE, LOWER, LEFT, RIGHT, APPLY, NEXT, PREVIOUS, TRIM_UP, TRIM_DOWN, TOOL, INSPECT,
            COOL, CONFIRM, FOLIO, TURN, DRAWER);

    /**
     * The handbook key (outside the station category: it works everywhere). In the world it opens the handbook; over an item
     * in an inventory, that item's entry; at a station, help for the step under way (held: its demonstration).
     */
    public static final KeyMapping HANDBOOK = new KeyMapping("key.immersive_jewelry.handbook", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.immersive_jewelry");

    /** The keys the handbook lists, in reading order. */
    public static final List<KeyMapping> HELP_ORDER = List.of(APPLY, RAISE, LOWER, LEFT, RIGHT, NEXT, PREVIOUS, TRIM_UP, TRIM_DOWN, TURN,
            TOOL, INSPECT, COOL, FOLIO, DRAWER, CONFIRM, HANDBOOK);

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.immersive_jewelry.workstation." + name, KeyConflictContext.GUI, InputConstants.Type.KEYSYM, glfw, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        ALL.forEach(event::register);
        event.register(HANDBOOK);
    }

    /** The bound key's short name for hints ("Space", "X"). */
    public static net.minecraft.network.chat.Component name(KeyMapping key) {
        return key.getTranslatedKeyMessage();
    }

    private WorkstationKeys() {}
}
