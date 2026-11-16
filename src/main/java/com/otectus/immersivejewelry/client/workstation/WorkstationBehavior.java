package com.otectus.immersivejewelry.client.workstation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What both workstations' client behaviours share: drawing the work through the scene's {@code work} node, the
 * fixed-step input stream and its prediction for the operator, contextual hints, cues and sounds, and the keyboard
 * frontend. A behaviour exists for every station shown on this client (worked by us, worked by someone else, or idle
 * near us) and draws all of them the same way from the synced state; only the operator's adds prediction and input.
 */
abstract class WorkstationBehavior implements StationBehavior {
    private static final Map<StationView, WorkstationBehavior> BY_VIEW = new WeakHashMap<>();

    protected final StationView view;
    protected final BlockPos pos;
    protected final InputRecorder recorder = new InputRecorder();
    /** The scene node under the cursor (Diegesis' hover), also while a stack is carried over a node that takes items. */
    @Nullable
    protected String hovered;
    private Component lastHint = Component.empty();
    @Nullable
    private Component cue;
    private long cueUntil;
    private long lastFrame = Util.getNanos();

    protected WorkstationBehavior(StationView view) {
        this.view = view;
        this.pos = view.pos().immutable();
        BY_VIEW.put(view, this);
    }

    @Nullable
    static WorkstationBehavior of(StationView view) {
        return BY_VIEW.get(view);
    }

    /** Whether this client operates the station (holds its lease and works here now). */
    protected boolean operating() {
        return view.isFocused() && WorkstationClient.operates(pos);
    }

    // ------------------------------------------------------------------------------------------------ scene

    @Override
    public void onSceneReady(StationView view) {
        view.node("work").ifPresent(node -> node.setPainter(this::paint));
    }

    private void paint(PoseStack pose, MultiBufferSource buffers, int light) {
        net.minecraft.world.level.block.entity.BlockEntity entity = view.level().getBlockEntity(pos);
        com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs.drawing(
                entity instanceof com.otectus.immersivejewelry.blockentity.StationBlockEntity station ? station : null,
                () -> draw(new Geo(pose, buffers, light), Minecraft.getInstance().getFrameTime()));
    }

    /** Draws the station's moving parts and work in scene pixels (north-facing block space). */
    protected abstract void draw(Geo geo, float partialTick);

    @Override
    public void onOpen(StationView view) {
        lastHint = Component.empty();
        hovered = null;
        releaseAll();
        // The compact drawer lists what this station can use, unless the player wants everything there.
        view.setInventoryFilter(stack -> ClientConfig.get(ClientConfig.DRAWER_SHOWS_EVERYTHING) || usable(stack));
    }

    @Override
    public void onClose(StationView view) {
        releaseAll();
        flush(true);
        view.loopSound("work", null, 0F, 1F);
        view.loopSound("motor", null, 0F, 1F);
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        long now = Util.getNanos();
        lastFrame = now;
        update(deltaSeconds, now);
        if (operating()) {
            recorder.pump(sample(), active(), now, this::predict);
            flush(false);
            refreshHint(now);
        }
    }

    // ------------------------------------------------------------------------------------------------ input

    /** The control state right now, as one sample. */
    protected abstract WorkSample sample();

    /** Whether the operator is doing something (keeps the sample stream alive). */
    protected abstract boolean active();

    /** Applies one of our own samples to the predicted state. */
    protected abstract void predict(WorkSample sample);

    /** The current work's id (input and commands carry it), or 0. */
    protected abstract long workId();

    /** Per-frame animation, smoothing, particles and sounds. */
    protected abstract void update(float deltaSeconds, long now);

    /** The hint for the current situation (with key names), or empty. */
    protected abstract Component hint();

    protected void flush(boolean force) {
        recorder.flush(pos, WorkstationClient.token(pos), workId(), Util.getNanos(), force);
    }

    protected void command(WorkCommand command, CompoundTag args) {
        WorkstationClient.command(pos, workId(), command, args);
    }

    protected void command(WorkCommand command) {
        command(command, new CompoundTag());
    }

    /** Lets go of every handle, key and tool at once (focus lost, damage, leaving). */
    abstract void releaseAll();

    void onLeaseGranted() {
        recorder.reset();
        releaseAll();
    }

    void onLeaseEnded() {
        releaseAll();
    }

    boolean keyPressed(int key, int scan, int modifiers) {
        return false;
    }

    boolean keyReleased(int key, int scan) {
        return false;
    }

    /**
     * The mouse wheel over the scene, offered first to the station (Diegesis gives it to an open overlay instead, and not
     * during a stroke or over the drawer): {@code node} is the one under the cursor, {@code amount} positive away from the
     * player.
     */
    @Override
    public boolean onScroll(StationView view, @Nullable String node, double amount) {
        return operating() && scrolled(node, amount);
    }

    boolean scrolled(@Nullable String node, double amount) {
        return false;
    }

    protected static boolean is(KeyMapping mapping, int key, int scan) {
        return mapping.matches(key, scan);
    }

    // ------------------------------------------------------------------------------------------------ items

    /** Whether this station has a use for a stack (the drawer lists those). */
    protected abstract boolean usable(ItemStack stack);

    /** The real stack shown at a scene node (a stone in the tray, the piece in the clamp), or empty. */
    protected ItemStack shownStack(String node) {
        return ItemStack.EMPTY;
    }

    /** What is under the cursor: the drawer's stack there, or the station's own stack at the hovered node. */
    ItemStack stackUnderCursor() {
        ItemStack inDrawer = DiegesisClient.hoveredStack();
        if (!inDrawer.isEmpty()) return inDrawer;
        return hovered == null ? ItemStack.EMPTY : shownStack(hovered);
    }

    /** The operator's stack on the cursor while it is carried over the scene, or empty. */
    protected ItemStack carried() {
        Player player = Minecraft.getInstance().player;
        return player == null || !operating() ? ItemStack.EMPTY : player.containerMenu.getCarried();
    }

    /** The colour of a placement hint: the guide's gold, or yellow in high contrast. */
    protected static int hintColor() {
        return ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES) ? 0xC0FFFF00 : 0x90F4D27A;
    }

    // ------------------------------------------------------------------------------------------------ hints, cues, sound

    private void refreshHint(long now) {
        if (cue != null && now < cueUntil) return;
        if (cue != null) {
            cue = null;
            lastHint = Component.empty();
        }
        if (!ClientConfig.get(ClientConfig.WORK_HINTS)) return;
        Component hint = hint();
        if (!hint.getString().equals(lastHint.getString())) {
            lastHint = hint;
            view.showCaption(hint, 0F);
        }
    }

    /** A short event line (depth reached, prong secure, chip): replaces the hint for the feedback time. */
    protected void cue(Component text) {
        if (!ClientConfig.get(ClientConfig.VISUAL_CUES) || !operating()) return;
        cue = text;
        cueUntil = Util.getNanos() + ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS) * 1_000_000L;
        view.showCaption(text, ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS) / 1000F);
    }

    protected static Component key(KeyMapping key) {
        return WorkstationKeys.name(key);
    }

    protected void sound(@Nullable SoundEvent sound, String node, float volume, float pitch) {
        if (sound == null) return;
        view.playSound(sound, node, volume * (float) ClientConfig.get(ClientConfig.TOOL_SOUND_VOLUME), pitch);
    }

    protected void cueSound(@Nullable SoundEvent sound, String node, float volume, float pitch) {
        if (sound == null || !ClientConfig.get(ClientConfig.TIMING_CUE_SOUNDS)) return;
        view.playSound(sound, node, volume * (float) ClientConfig.get(ClientConfig.SOUND_CUE_VOLUME), pitch);
    }

    protected void loop(String name, @Nullable SoundEvent sound, float volume, float pitch) {
        view.loopSound(name, sound, sound == null ? 0F : volume * (float) ClientConfig.get(ClientConfig.TOOL_SOUND_VOLUME), pitch);
    }

    protected static float particles() {
        float vanilla = switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 1F;
            case DECREASED -> 0.5F;
            case MINIMAL -> 0F;
        };
        return (float) ClientConfig.get(ClientConfig.PARTICLE_DENSITY) * vanilla
                * (ClientConfig.get(ClientConfig.REDUCED_MOTION) ? 0.35F : 1F);
    }

    /** Event bursts obey the same density and comfort settings as continuous contact effects. */
    protected void burst(ResourceLocation effect, Vector3f at, Vector3f velocity, float spread, int count, int color) {
        float density = particles();
        if (effect.equals(WorkEffects.GLINT)) density *= (float) ClientConfig.get(ClientConfig.GEM_SPARKLE_INTENSITY);
        int scaled = Math.round(count * density);
        if (scaled > 0) view.spawnParticles(effect, at, velocity, spread, scaled, color);
    }

    /** Frame-rate independent smoothing towards a target. */
    protected static float smooth(float current, float target, float rate, float dt) {
        return current + (target - current) * (1F - (float) Math.exp(-rate * dt));
    }
}
