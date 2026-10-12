package com.otectus.immersivejewelry.client.screen;

import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.minigame.StepClock;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.CancelSessionPacket;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntFunction;

/**
 * Shared machinery of the two minigame screens: the 20 ms step clock (started after the countdown), capturing the
 * input for each step, sending steps to the server in small batches, and cancelling the session when the screen is
 * closed before the server's verdict. The client's own simulation drives what the player sees; the server replays
 * the same inputs and decides the outcome.
 */
public abstract class MinigameScreen extends JewelryScreen {
    protected static final int BATCH = 5;

    protected final int sessionId;
    private final long startMs;
    protected int step;
    protected int unsentFrom;
    protected boolean localComplete;
    protected boolean awaitingResult;
    protected boolean finished;
    private boolean cancelled;
    protected boolean pressed;
    private boolean mouseDown;
    private boolean keyDown;
    /** A press that began since the last simulated step: a tap shorter than a frame still presses for one step. */
    private boolean pressLatched;
    protected float shake;

    /**
     * Scripted input for automated client runs: when set, it is asked for every step, in order, instead of reading the
     * mouse and keys, and returns {x, y, down} (x and y in fixed-point stone units; ignored by the setting screen).
     * Null in play. The smoke-test agent plays its reference bots through it, so the real client path is exercised.
     */
    @Nullable
    public static IntFunction<int[]> scriptedInput;

    protected MinigameScreen(Component title, int sessionId, int readyMs, int panelWidth, int panelHeight) {
        super(title, panelWidth, panelHeight);
        this.sessionId = sessionId;
        this.startMs = Util.getMillis() + readyMs;
    }

    public int sessionId() {
        return sessionId;
    }

    /** The server's verdict has arrived. */
    public boolean finished() {
        return finished;
    }

    protected abstract int maxSteps();

    /** Records the current input for {@code step} and advances the local simulation. Returns true when it completed. */
    protected abstract boolean simulate(int step, boolean pressed);

    /** Sends the steps from {@code first} (inclusive) to {@code end} (exclusive). */
    protected abstract void send(int first, int end);

    protected boolean counting() {
        return Util.getMillis() < startMs;
    }

    protected float countdown() {
        return (startMs - Util.getMillis()) / 1000F;
    }

    protected void advanceClock() {
        if (localComplete || finished || counting()) return;
        long target = Math.min(maxSteps(), (Util.getMillis() - startMs) / StepClock.STEP_MS);
        int budget = 25;
        while (step < target && budget-- > 0 && !localComplete) {
            pressed = mouseDown || keyDown || pressLatched;
            pressLatched = false;
            if (simulate(step, pressed)) localComplete = true;
            step++;
            if (step - unsentFrom >= BATCH) flush();
        }
        if (localComplete || step >= maxSteps()) {
            localComplete = true;
            flush();
            awaitingResult = true;
        }
    }

    protected void flush() {
        while (unsentFrom < step) {
            int end = Math.min(step, unsentFrom + 64);
            send(unsentFrom, end);
            unsentFrom = end;
        }
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        advanceClock();
        if (shake > 0F) shake = Math.max(0F, shake - 0.08F);
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    protected float shakeOffset(boolean vertical) {
        if (shake <= 0F || JewelryGui.reducedMotion()) return 0F;
        float strength = (float) ClientConfig.get(ClientConfig.SCREEN_SHAKE) * shake * 3F;
        long t = Util.getMillis();
        return (float) Math.sin(t * (vertical ? 0.071 : 0.093)) * strength;
    }

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        if (super.panelClicked(mouseX, mouseY, button)) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !finished) {
            mouseDown = true;
            pressLatched = true;
            return true;
        }
        return false;
    }

    @Override
    protected boolean panelReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) mouseDown = false;
        return super.panelReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE && !finished) {
            keyDown = true;
            pressLatched = true;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE) keyDown = false;
        return super.keyReleased(key, scan, modifiers);
    }

    /** The server ended the session: close without cancelling (there is nothing left to cancel). */
    public void closeByServer() {
        cancelled = true;
        onClose();
    }

    @Override
    public void removed() {
        if (!finished && !cancelled) {
            cancelled = true;
            ModNetwork.CHANNEL.sendToServer(new CancelSessionPacket(sessionId));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
