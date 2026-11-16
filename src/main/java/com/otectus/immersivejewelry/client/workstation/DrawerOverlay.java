package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.pockyl.diegesis.api.client.ScreenOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The staging drawer's list: its four real stacks, by number, with what can be done to each. It is the way to every staged
 * stack that does not depend on the drawer's surfaces (a shut drawer, a resource pack that changed its geometry, a stack whose
 * model no longer fits, a player who works by keys): it shows the bench's own array, never a copy, and every button sends one
 * validated command naming the position and the generation it showed. Keys: up/down choose, confirm moves the chosen stack to
 * the work area, apply to the inventory, the tool key opens or shuts the drawer, the drawer key or Escape closes the list.
 */
final class DrawerOverlay implements ScreenOverlay {
    private final BenchBehavior bench;
    private final Runnable onClosed;
    private int selected;
    private final List<Button> buttons = new ArrayList<>();

    private record Button(int x, int y, int w, int h, Component label, Runnable action, boolean enabled) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    DrawerOverlay(BenchBehavior bench, Runnable onClosed) {
        this.bench = bench;
        this.onClosed = onClosed;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        JewelersBenchBlockEntity be = bench.bench();
        Font font = Minecraft.getInstance().font;
        boolean contrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        int w = Math.min(260, g.guiWidth() - 24);
        int h = 30 + JewelersBenchBlockEntity.STAGING * 18 + 26;
        int x0 = 10, y0 = 10;
        g.fill(x0, y0, x0 + w, y0 + h, contrast ? 0xF0000000 : 0xE6201810);
        g.renderOutline(x0, y0, w, h, contrast ? 0xFFFFFFFF : 0xFFB08A50);
        buttons.clear();
        if (be == null) return;
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.drawer.title"), x0 + 6, y0 + 6, 0xFFE8C46A, false);
        Component state = Component.translatable(be.drawerOpen() ? "workstation.immersive_jewelry.drawer.open"
                : "workstation.immersive_jewelry.drawer.shut");
        buttons.add(new Button(x0 + w - 86, y0 + 4, 80, 13, Component.translatable(be.drawerOpen()
                ? "workstation.immersive_jewelry.drawer.shut_it" : "workstation.immersive_jewelry.drawer.open_it"), bench::toggleDrawer, true));
        g.drawString(font, state, x0 + 6, y0 + 17, 0xFFB0A898, false);
        int y = y0 + 30;
        for (int i = 0; i < JewelersBenchBlockEntity.STAGING; i++) {
            ItemStack stack = be.staged(i);
            if (i == selected) g.fill(x0 + 4, y - 2, x0 + w - 4, y + 15, contrast ? 0xFF404000 : 0x60E8C46A);
            g.drawString(font, Component.literal((i + 1) + "."), x0 + 8, y + 3, 0xFFB0A898, false);
            if (stack.isEmpty()) {
                g.drawString(font, Component.translatable("workstation.immersive_jewelry.drawer.empty"), x0 + 40, y + 3, 0xFF8A8070, false);
            } else {
                g.renderItem(stack, x0 + 20, y - 1);
                g.renderItemDecorations(font, stack, x0 + 20, y - 1);
                String name = font.plainSubstrByWidth(stack.getHoverName().getString(), w - 170);
                g.drawString(font, name, x0 + 40, y + 3, contrast ? 0xFFFFFFFF : 0xFFF0E6D0, false);
                int index = i;
                buttons.add(new Button(x0 + w - 122, y, 58, 13, Component.translatable("workstation.immersive_jewelry.drawer.to_work"),
                        () -> bench.moveStaged(index, true), true));
                buttons.add(new Button(x0 + w - 62, y, 56, 13, Component.translatable("workstation.immersive_jewelry.drawer.to_inventory"),
                        () -> bench.moveStaged(index, false), true));
            }
            int index = i;
            buttons.add(new Button(x0 + 4, y - 2, 34, 17, Component.empty(), () -> selected = index, true));
            y += 18;
        }
        g.drawString(font, Component.translatable("workstation.immersive_jewelry.drawer.keys", WorkstationKeys.name(WorkstationKeys.CONFIRM),
                WorkstationKeys.name(WorkstationKeys.APPLY), WorkstationKeys.name(WorkstationKeys.TOOL)), x0 + 6, y0 + h - 12, 0xFF8A8070, false);
        for (Button b : buttons) {
            if (b.label().getString().isEmpty()) continue;
            boolean hover = b.over(mouseX, mouseY);
            g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hover ? (contrast ? 0xFF606000 : 0xC0705030) : (contrast ? 0xFF202020 : 0x90503820));
            g.renderOutline(b.x(), b.y(), b.w(), b.h(), contrast ? 0xFFFFFFFF : 0xFF8A6A40);
            g.drawString(font, b.label(), b.x() + (b.w() - font.width(b.label())) / 2, b.y() + (b.h() - 8) / 2,
                    contrast ? 0xFFFFFFFF : 0xFFF0E6D0, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button b : List.copyOf(buttons)) {
            if (b.over(mouseX, mouseY)) {
                if (b.enabled()) b.action().run();
                return true;
            }
        }
        int w = Math.min(260, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);
        return mouseX >= 10 && mouseX < 10 + w && mouseY >= 10 && mouseY < 10 + 30 + JewelersBenchBlockEntity.STAGING * 18 + 26;
    }

    @Override
    public void mouseScrolled(double mouseX, double mouseY, double amount) {
        selected = Mth.clamp(selected - (int) Math.signum(amount), 0, JewelersBenchBlockEntity.STAGING - 1);
    }

    /** Keys while the list is open; one-shot actions only on the first press of a key, never on its repeats. */
    boolean key(int key, int scan, boolean repeat) {
        if (WorkstationKeys.RAISE.matches(key, scan)) {
            selected = Math.max(0, selected - 1);
            return true;
        }
        if (WorkstationKeys.LOWER.matches(key, scan)) {
            selected = Math.min(JewelersBenchBlockEntity.STAGING - 1, selected + 1);
            return true;
        }
        @Nullable Runnable once = null;
        if (WorkstationKeys.CONFIRM.matches(key, scan)) once = () -> bench.moveStaged(selected, true);
        else if (WorkstationKeys.APPLY.matches(key, scan)) once = () -> bench.moveStaged(selected, false);
        else if (WorkstationKeys.TOOL.matches(key, scan)) once = bench::toggleDrawer;
        else if (WorkstationKeys.DRAWER.matches(key, scan)) once = bench::closeDrawerList;
        if (once == null) return false;
        if (!repeat) once.run();
        return true;
    }

    @Override
    public void onClosed() {
        onClosed.run();
    }
}
