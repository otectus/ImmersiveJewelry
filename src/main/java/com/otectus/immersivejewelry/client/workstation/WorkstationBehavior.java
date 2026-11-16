package com.otectus.immersivejewelry.client.workstation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What both workstations' client behaviours share: drawing the work through the scene's {@code work} node, the
 * fixed-step input stream and its prediction for the operator, contextual hints, cues and sounds, and the keyboard
 * frontend. A behaviour exists for every station shown on this client (worked by us, worked by someone else, or idle
 * near us) and draws all of them the same way from the synced state; only the operator's adds prediction and input.
 *
 * <p>Keys (Diegesis 1.0.0-beta.11): with nothing laid over the scene, a key reaches the station through the library's own
 * {@link #onKey} hook, before the library's keys; the station takes what it uses, and a key it has no use for may look up the
 * stack under the cursor in a recipe viewer. While one of the station's overlays is open (the folio, the loupe's notes, the
 * drawer's list) the library offers no keys at all, so {@code WorkstationClient} hands them to {@link #overlayKey} /
 * {@link #overlayChar} from Forge's screen events instead; releases always come that way (the library has no release hook).
 * A held key's repeats never run a one-shot action twice: only the press that started it does, and its repeats are swallowed
 * when it was taken.</p>
 *
 * <p>Camera side: the player's working side mirrors the camera across the block's middle ({@code StationView.mirrorCamera});
 * pointer mapping is always rebuilt from the camera actually used ({@link #cameraChanged}). A change of side waits until no
 * tool is pressed, lets everything go, then switches.</p>
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
    /** Keys down now (as this station heard them), and those of them whose first press the station took. */
    private final Set<Integer> down = new HashSet<>();
    private final Set<Integer> taken = new HashSet<>();
    /** The mirror plane this behaviour maps the pointer for (NaN: the authored shot). */
    protected float mirror = Float.NaN;

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
        applyMirror();
    }

    /** Sets the player's working side on the view and rebuilds the pointer mapping for it. */
    protected void applyMirror() {
        float plane = SceneLoader.mirrorPlane();
        mirror = Float.NaN;
        // A Diegesis without mirroring keeps the authored side; the mapping then follows the authored camera.
        DiegesisCompat.run(DiegesisCompat.Feature.CAMERA_MIRROR, "the working side", () -> {
            view.mirrorCamera(plane);
            mirror = view.cameraMirror();
        });
        cameraChanged();
    }

    /** The station's id (its scene's). */
    protected abstract net.minecraft.resources.ResourceLocation stationId();

    /** The camera the scene is seen through changed (scene ready, side switched): recompute what pointer mapping uses. */
    protected void cameraChanged() {}

    private static boolean samePlane(float a, float b) {
        return Float.isNaN(a) ? Float.isNaN(b) : a == b;
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
        forgetKeys();
        applyMirror();
        // The compact drawer lists what this station can use, unless the player wants everything there.
        view.setInventoryFilter(stack -> ClientConfig.get(ClientConfig.DRAWER_SHOWS_EVERYTHING) || usable(stack));
        if (!SceneLoader.INSTANCE.hasScene(stationId())) {
            // The installed Diegesis could not read even the simplest scene: say so; the work is kept, Escape leaves.
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("workstation.immersive_jewelry.no_scene",
                    DiegesisCompat.installedVersion()).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    @Override
    public void onClose(StationView view) {
        releaseAll();
        forgetKeys();
        flush(true);
        view.loopSound("work", null, 0F, 1F);
        view.loopSound("motor", null, 0F, 1F);
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        long now = Util.getNanos();
        lastFrame = now;
        // The working side changed in the options: never under a pressed tool; let go, switch, map anew.
        if (!samePlane(SceneLoader.mirrorPlane(), mirror) && !active()) {
            releaseAll();
            flush(true);
            applyMirror();
        }
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
        forgetKeys();
    }

    /** The client options changed while working here: let go, and say that scene options show on the next visit. */
    void onOptionsChanged() {
        releaseAll();
        forgetKeys();
        flush(true);
        if (operating()) view.showCaption(Component.translatable("workstation.immersive_jewelry.cue.options_next_visit"), 3F);
    }

    /** Forgets which keys are down (a child screen, focus lost, the lease or the station ended). */
    void forgetKeys() {
        down.clear();
        taken.clear();
    }

    /**
     * A key went down with nothing laid over the scene (Diegesis' hook, before its own keys). Taken: a key this station uses
     * now, or one a recipe viewer looks up the stack under the cursor with. A repeat runs nothing; it is swallowed when the
     * press was taken.
     */
    @Override
    public boolean onKey(StationView view, int key, int scan, int modifiers) {
        return stationKey(key, scan, modifiers);
    }

    /** A key for the station with nothing laid over the scene: from {@link #onKey}, or Forge's event with a Diegesis without it. */
    boolean stationKey(int key, int scan, int modifiers) {
        if (!operating()) return false;
        if (!down.add(key)) return taken.contains(key);
        boolean took = keyPressed(key, scan, modifiers);
        if (!took) {
            ItemStack under = stackUnderCursor();
            took = !under.isEmpty() && WorkstationClient.lookUp(key, scan, under);
        }
        if (took) taken.add(key);
        return took;
    }

    /** No field to type into while nothing is laid over the scene: characters stay the library's. */
    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return false;
    }

    /** Whether one of this station's overlays is open (it then gets the keys through {@link #overlayKey}). */
    boolean overlayOpen() {
        return false;
    }

    /** Whether a text field of an open overlay has the keyboard: every key and character is text then. */
    boolean textInputActive() {
        return false;
    }

    /** A key went down while one of this station's overlays is open; {@code repeat}: a held key's repeat. */
    boolean overlayKey(int key, int scan, int modifiers, boolean repeat) {
        return false;
    }

    /** A character was typed while one of this station's overlays is open. */
    boolean overlayChar(int codePoint, int modifiers) {
        return false;
    }

    /** Keys reaching an open overlay: tracks the press so a held key's repeats are known as such. */
    boolean overlayPressed(int key, int scan, int modifiers) {
        boolean repeat = !down.add(key);
        boolean took = overlayKey(key, scan, modifiers, repeat);
        if (took) taken.add(key);
        return took || repeat && taken.contains(key);
    }

    /** A key came up: always reaches the station, so a held action never sticks. */
    boolean keyUp(int key, int scan) {
        down.remove(key);
        taken.remove(key);
        return keyReleased(key, scan);
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
        ItemStack inDrawer = DiegesisAccess.hoveredStack();
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
