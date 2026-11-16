package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.client.workstation.render.GemProgress;
import com.otectus.immersivejewelry.client.workstation.render.Hands;
import com.otectus.immersivejewelry.client.workstation.render.WheelPainter;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.cut.CutPattern;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.workstation.CutPlanner;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.order.CutWork;
import com.otectus.immersivejewelry.workstation.sim.FacetParams;
import com.otectus.immersivejewelry.workstation.sim.FacetSim;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lapidary Wheel at work, on the client. The quill handle is grabbed and moved like the real thing: sideways sweeps
 * the stone across the lap, down lowers it until it meets the lap and then presses (the force), letting go lifts it.
 * The index collar, the cooling cup, the loupe, the release lever and the specimen rack are touched in the scene; every
 * one of them also has a key. The operator's own samples are simulated at once (prediction) and replayed on top of each
 * state the server confirms; everyone else sees the confirmed state, smoothed.
 */
final class WheelBehavior extends WorkstationBehavior {
    private static final float APPLY_HEIGHT = 0.25F;
    private static final long DIP_NANOS = 1_200_000_000L;
    private static final long CONFIRM_NANOS = 4_000_000_000L;

    // Controls (targets the handle moves towards).
    private float sweep;
    private float height = 1F;
    private int region;
    private int trim;
    private boolean holding;
    @Nullable
    private Vector3f strokeStart;
    private float startSweep;
    private float startHeight = 1F;
    private float strokeTravel;
    private boolean keyLower, keyRaise, keyLeft, keyRight, keyApply, toggledApply;
    private long dipUntil;
    private float assistedPhase;

    // Prediction.
    @Nullable
    private CutWork predicted;
    private int seenVersion = -1;
    private long knownWork;

    // Presentation.
    private float shownSweep;
    private float shownHeight = 1F;
    private float shownDip;
    private final GemProgress gemProgress = new GemProgress();
    @Nullable private GemProgress.Snapshot renderedProgress;
    private long presentedStoneSeed;
    private float shownSize = 1F;
    private float lever;
    private final float[] glints = new float[FacetParams.MAX_REGIONS];
    private boolean inspecting;
    private long roughUntil;
    private long recutUntil;
    @Nullable
    private ResourceLocation recutCut;
    private float particleCredit;
    private int lastChips;
    private final boolean[] lastInWindow = new boolean[FacetParams.MAX_REGIONS];
    private final List<ResourceLocation> specimens = new ArrayList<>();
    private float kSweep = 4F;
    private float kHeight = 3F;
    private final Vector3f right = new Vector3f(-1F, 0F, 0F);
    private final Vector3f up = new Vector3f(0F, 1F, 0F);

    WheelBehavior(StationView view) {
        super(view);
    }

    @Nullable
    private LapidaryWheelBlockEntity wheel() {
        return view.level().getBlockEntity(pos) instanceof LapidaryWheelBlockEntity wheel ? wheel : null;
    }

    /** The work as this client should show it: predicted for the operator, confirmed for everyone else. */
    @Nullable
    private CutWork shown() {
        if (operating() && predicted != null) return predicted;
        LapidaryWheelBlockEntity wheel = wheel();
        return wheel == null ? null : wheel.work().orElse(null);
    }

    @Override
    protected long workId() {
        CutWork work = shown();
        return work == null ? 0L : work.id();
    }

    // ------------------------------------------------------------------------------------------------ scene

    @Override
    protected net.minecraft.resources.ResourceLocation stationId() {
        return JewelryStations.WHEEL_ID;
    }

    /**
     * The handle's mapping follows the camera actually used (the authored shot, mirrored for the left working side): the
     * screen's right and up, and how far the grip moves on screen per unit of sweep and of height, so the handle follows the
     * pointer one to one from either side. The work itself stays in the wheel's own coordinates.
     */
    @Override
    protected void cameraChanged() {
        float[] camera = SceneLoader.INSTANCE.camera(JewelryStations.WHEEL_ID, mirror);
        if (camera != null) {
            Vector3f look = new Vector3f(camera[3] - camera[0], camera[4] - camera[1], camera[5] - camera[2]).normalize();
            right.set(look).cross(0F, 1F, 0F).normalize();
            up.set(right).cross(look).normalize();
        }
        Vector3f ds = WheelPainter.handleGrip(0.1F, 1F, 0F).sub(WheelPainter.handleGrip(-0.1F, 1F, 0F)).div(0.2F);
        Vector3f dh = WheelPainter.handleGrip(0F, 1F, 0F).sub(WheelPainter.handleGrip(0F, 0.5F, 0F)).div(0.5F);
        kSweep = nonZero(ds.dot(right), 3F);
        kHeight = nonZero(dh.dot(up), 3F);
    }

    @Override
    public void onSceneReady(StationView view) {
        super.onSceneReady(view);
        place("handle", WheelPainter.handleGrip(0F, 1F, 0F));
        place("collar", WheelPainter.collarAt(0F, 1F));
        place("stone", WheelPainter.stoneAt(0F, 1F, 0F).add(0F, 1F, 0F));
        place("cup", WheelPainter.CUP);
        place("loupe", WheelPainter.LOUPE);
        place("lever", new Vector3f(WheelPainter.LEVER).add(0F, 0.9F, 0F));
        place("lap", new Vector3f(WheelPainter.LAP).add(2.5F, 0F, 2.5F));
        for (int i = 0; i < WheelPainter.SPECIMENS; i++) place("specimen_" + i, WheelPainter.specimen(i, WheelPainter.SPECIMENS));
    }

    private static float nonZero(float v, float fallback) {
        return Math.abs(v) < 0.05F ? fallback : v;
    }

    private void place(String node, Vector3f at) {
        view.node(node).ifPresent(n -> n.moveTo(at.x, at.y, at.z, 0F));
    }

    @Override
    protected void draw(Geo geo, float partialTick) {
        LapidaryWheelBlockEntity wheel = wheel();
        if (wheel == null) return;
        CutWork work = shown();
        Hands.operator(wheel.operator());
        boolean animate = ClientConfig.get(ClientConfig.WORKSTATION_ANIMATIONS) || wheel.isWorking();
        renderedProgress = gemProgress.snapshot();
        WheelPainter.State state = new WheelPainter.State(stoneData(wheel), work, wheel.tier().color, animate ? wheel.renderAngle(partialTick) : 0F,
                shownSweep, shownHeight, shownDip, work != null ? work.sim().region() : 0, work != null ? work.sim().trim() : 0,
                ghostCut(), specimens, hoveredSpecimen(), glints, showGuide(work), lever, operating() && (holding || keyApply || toggledApply),
                stoneSeed(wheel.stone()), renderedProgress, shownSize);
        try {
            WheelPainter.Rig rig = WheelPainter.draw(geo, state, view.age());
            place("handle", rig.grip());
            place("collar", rig.collar());
            place("stone", rig.stone());
            WheelPainter.placeHint(geo, placement(wheel), rig, hintColor());
        } finally {
            Hands.operator(null);
        }
    }

    @Nullable
    static GemData stoneData(LapidaryWheelBlockEntity wheel) {
        ItemStack stone = wheel.stone();
        if (stone.getItem() instanceof CutGemItem) return CutGemItem.data(stone).orElse(null);
        if (stone.getItem() instanceof RoughGemItem) return RoughGemItem.data(stone).orElse(null);
        return null;
    }

    /** A stable seed for the stone's shape: its appraised identity, never its changing work. */
    static long stoneSeed(ItemStack stone) {
        GemData d = stone.getItem() instanceof CutGemItem ? CutGemItem.data(stone).orElse(null) : RoughGemItem.data(stone).orElse(null);
        if (d == null) return 0L;
        return d.gem().hashCode() * 31L + Float.floatToIntBits(d.originalSize()) * 17L + Float.floatToIntBits(d.naturalQuality());
    }

    private int hoveredSpecimen() {
        return hovered != null && hovered.startsWith("specimen_") ? Integer.parseInt(hovered.substring(9)) : -1;
    }

    @Nullable
    private ResourceLocation ghostCut() {
        int i = hoveredSpecimen();
        return i >= 0 && i < specimens.size() ? specimens.get(i) : null;
    }

    private boolean showGuide(@Nullable CutWork work) {
        if (ghostCut() != null) return true;
        return work != null && (!work.started() || ClientConfig.get(ClientConfig.NUMERIC_GUIDE) || inspecting);
    }

    // ------------------------------------------------------------------------------------------------ per frame

    @Override
    protected void update(float dt, long now) {
        LapidaryWheelBlockEntity wheel = wheel();
        if (wheel == null) return;
        refreshSpecimens(wheel);
        view.node("stone").ifPresent(n -> n.setTooltip(Component.empty()));
        rebase(wheel);
        CutWork work = shown();
        view.node("handle").ifPresent(n -> n.setTooltip(work == null
                ? Component.translatable("workstation.immersive_jewelry.node.handle") : Component.empty()));
        if (operating()) {
            keys(dt);
            if (work != null && knownWork != work.id()) {
                knownWork = work.id();
                region = work.sim().region();
                trim = work.sim().trim();
            }
        }
        float rate = ClientConfig.get(ClientConfig.REDUCED_MOTION) ? 30F : 18F;
        FacetSim sim = work != null ? work.sim() : null;
        updateGem(wheel, dt);
        float targetSweep = sim != null ? sim.sweep() : 0F;
        float targetHeight = sim != null ? sim.height() : 1F;
        shownSweep = smooth(shownSweep, targetSweep, rate, dt);
        shownHeight = smooth(shownHeight, targetHeight, rate, dt);
        boolean dipping = operating() && now < dipUntil;
        shownDip = smooth(shownDip, dipping ? 1F : 0F, 6F, dt);
        lever = smooth(lever, 0F, 4F, dt);
        for (int i = 0; i < glints.length; i++) glints[i] = Math.max(0F, glints[i] - dt * 1.6F);
        sounds(wheel, sim);
        particles(wheel, sim, dt);
        if (!operating()) observe(sim);
        if (inspecting && !view.currentGlance().isPresent()) inspecting = false;
    }

    private void updateGem(LapidaryWheelBlockEntity wheel, float dt) {
        GemData data = stoneData(wheel);
        long seed = stoneSeed(wheel.stone());
        if (data == null || seed != presentedStoneSeed) {
            gemProgress.clear();
            presentedStoneSeed = seed;
            shownSize = WheelPainter.stoneSize(data);
        }
        CutWork confirmed = wheel.work().orElse(null);
        if (confirmed != null) {
            int n = confirmed.params().regions(), chips = 0;
            float[] ratios = new float[n];
            for (int i = 0; i < n; i++) {
                ratios[i] = confirmed.sim().depth(i) / Math.max(0.05F, confirmed.params().target()[i]);
                if (confirmed.sim().chip(i) > 0F) chips |= 1 << i;
            }
            gemProgress.update(confirmed.id(), ratios, confirmed.sim().overallProgress(),
                    shown() != null ? shown().sim().region() : confirmed.sim().region(), chips, dt);
        } else if (data != null && data.isCut()) {
            gemProgress.finish(dt);
        } else gemProgress.clear();
        shownSize = smooth(shownSize, WheelPainter.stoneSize(data), 8F, dt);
    }

    private void refreshSpecimens(LapidaryWheelBlockEntity wheel) {
        specimens.clear();
        GemData data = stoneData(wheel);
        if (data == null) return;
        JewelryDefinitions defs = JewelryDefinitions.client();
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        for (CutDefinition cut : defs.cutsFor(gem)) {
            if (specimens.size() >= WheelPainter.SPECIMENS) break;
            specimens.add(cut.id());
        }
        for (int i = 0; i < WheelPainter.SPECIMENS; i++) {
            int index = i;
            view.node("specimen_" + i).ifPresent(n -> n.setEnabled(index < specimens.size()));
        }
    }

    /** A new confirmed state: the operator's prediction restarts from it and replays what the server has not seen yet. */
    private void rebase(LapidaryWheelBlockEntity wheel) {
        if (wheel.clientVersion() == seenVersion) return;
        seenVersion = wheel.clientVersion();
        CutWork confirmed = wheel.work().orElse(null);
        if (!operating() || confirmed == null) {
            predicted = null;
            return;
        }
        CutWork copy = CutWork.load(confirmed.save()).orElse(null);
        if (copy == null) {
            predicted = null;
            return;
        }
        recorder.replay(wheel.ackStep(), copy.sim()::step);
        predicted = copy;
    }

    /** What players around hear and see of someone else's work, from the confirmed states. */
    private void observe(@Nullable FacetSim sim) {
        if (sim == null) return;
        if (sim.chips() > lastChips) sound(ModSounds.WHEEL_CHIP.get(), "stone", 0.7F, 1.1F);
        lastChips = sim.chips();
        for (int i = 0; i < sim.params().regions(); i++) {
            boolean in = sim.inWindow(i);
            if (in && !lastInWindow[i]) glints[i] = 1F;
            lastInWindow[i] = in;
        }
    }

    private void sounds(LapidaryWheelBlockEntity wheel, @Nullable FacetSim sim) {
        boolean running = view.isInUse();
        loop("motor", ModSounds.WHEEL_HUM.get(), running ? 0.22F : 0F, 0.9F + 0.05F * wheel.tier().ordinal());
        float force = sim != null && sim.contact() ? sim.force() : 0F;
        float hard = 1F;
        GemData data = stoneData(wheel);
        if (data != null) hard = 0.85F + JewelryDefinitions.client().gemOrGeneric(data.gem()).hardness() / 40F;
        boolean near = sim != null && sim.region() < sim.params().regions()
                && sim.depth(sim.region()) >= sim.params().target()[sim.region()] - sim.params().window() - FacetSim.NEAR_MARGIN;
        loop("work", ModSounds.WHEEL_GRIND.get(), force > 0F ? 0.25F + 0.45F * force : 0F, hard * (0.85F + 0.3F * force) * (near ? 1.12F : 1F));
    }

    private void particles(LapidaryWheelBlockEntity wheel, @Nullable FacetSim sim, float dt) {
        if (sim == null || !sim.contact()) return;
        particleCredit += dt * 30F * sim.force() * particles();
        if (particleCredit < 1F) return;
        int n = (int) particleCredit;
        particleCredit -= n;
        Vector3f at = WheelPainter.stoneAt(shownSweep, Math.min(shownHeight, FacetSim.CONTACT_HEIGHT), 0F);
        Vector3f radial = new Vector3f(at).sub(WheelPainter.LAP);
        Vector3f tangent = new Vector3f(-radial.z, 0F, radial.x).normalize().mul(9F).add(0F, 2.5F, 0F);
        GemData data = stoneData(wheel);
        int color = data == null ? 0xFFB0A898 : 0xFF000000 | JewelryColors.gemColor(JewelryDefinitions.client(), data.gem());
        view.spawnParticles(WorkEffects.SLURRY, at, tangent, 0.3F, n, 0xC09A9488);
        if (Util.getNanos() % 3 == 0) view.spawnParticles(WorkEffects.STONE_DUST, at, new Vector3f(tangent).mul(0.3F), 0.4F, 1, color);
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    protected WorkSample sample() {
        float h = height;
        if (keyApply || toggledApply) h = APPLY_HEIGHT;
        boolean dip = Util.getNanos() < dipUntil;
        if (dip) h = 1F;
        return new WorkSample(SimIO.toSigned12(sweep), SimIO.toUnit12(h), 0, region, trim + WorkSample.TRIM_BIAS, dip ? WorkSample.FLAG_DIP : 0);
    }

    @Override
    protected boolean active() {
        return holding || keyApply || toggledApply || keyLower || keyRaise || keyLeft || keyRight || height < 0.99F
                || Util.getNanos() < dipUntil || (predicted != null && predicted.sim().contact());
    }

    @Override
    protected void predict(WorkSample sample) {
        if (predicted == null) return;
        FacetSim sim = predicted.sim();
        int events = sim.step(sample);
        if (events == 0) return;
        if ((events & FacetSim.EVENT_TOUCH) != 0) sound(ModSounds.WHEEL_MOUNT.get(), "stone", 0.25F, 1.6F);
        if ((events & FacetSim.EVENT_NEAR) != 0) {
            cueSound(ModSounds.UI_CUE.get(), "stone", 0.45F, 1.25F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.near"));
        }
        if ((events & FacetSim.EVENT_ENTER) != 0) {
            glints[sim.region()] = 1F;
            sound(ModSounds.WHEEL_FACET.get(), "stone", 0.55F, 1.15F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.face_done", sim.region() + 1).withStyle(ChatFormatting.GREEN));
            burst(WorkEffects.GLINT, WheelPainter.stoneAt(shownSweep, 0.6F, 0F), new Vector3f(0F, 2F, 0F), 0.4F, 3, 0xFFFFFFFF);
        }
        if ((events & FacetSim.EVENT_OVER) != 0) {
            sound(ModSounds.WHEEL_FACET.get(), "stone", 0.5F, 0.7F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.overcut").withStyle(ChatFormatting.GOLD));
        }
        if ((events & FacetSim.EVENT_CHIP) != 0) {
            sound(ModSounds.WHEEL_CHIP.get(), "stone", 0.8F, 1.0F);
            // The machine jolts (minigameScreenShake; never with reduced motion).
            float shake = (float) (double) ClientConfig.get(ClientConfig.SCREEN_SHAKE);
            if (shake > 0F && !ClientConfig.get(ClientConfig.REDUCED_MOTION)) view.node("work").ifPresent(n -> n.shake(0.35F * shake, 0.3F));
            cue(Component.translatable("workstation.immersive_jewelry.cue.chip").withStyle(ChatFormatting.RED));
            burst(WorkEffects.CHIP, WheelPainter.stoneAt(shownSweep, FacetSim.CONTACT_HEIGHT, 0F), new Vector3f(0F, 6F, 0F), 0.6F,
                    6, 0xFFE0E0E0);
        }
        if ((events & FacetSim.EVENT_HOT) != 0) {
            cueSound(ModSounds.UI_CUE.get(), "stone", 0.5F, 0.6F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.hot", key(WorkstationKeys.COOL)).withStyle(ChatFormatting.GOLD));
        }
        if ((events & FacetSim.EVENT_SCRATCH) != 0 && Util.getNanos() % 7 == 0) {
            cue(Component.translatable("workstation.immersive_jewelry.cue.too_hard").withStyle(ChatFormatting.GOLD));
        }
        if ((events & FacetSim.EVENT_ROCK) != 0 && Util.getNanos() % 7 == 0) {
            cue(Component.translatable("workstation.immersive_jewelry.cue.rocking").withStyle(ChatFormatting.GOLD));
        }
        if ((events & FacetSim.EVENT_INDEX) != 0) sound(ModSounds.UI_CUE.get(), "handle", 0.25F, 1.8F);
    }

    private void keys(float dt) {
        float h = ClientConfig.get(ClientConfig.REDUCED_MOTION) ? 0.5F : 0.7F;
        if (keyLower) height = Mth.clamp(height - h * dt, 0F, 1F);
        if (keyRaise) height = Mth.clamp(height + h * dt, 0F, 1F);
        if (keyLeft) sweep = Mth.clamp(sweep + 0.9F * dt, -1F, 1F);
        if (keyRight) sweep = Mth.clamp(sweep - 0.9F * dt, -1F, 1F);
        if ((keyApply || toggledApply) && ClientConfig.get(ClientConfig.ASSISTED_SWEEP) && !keyLeft && !keyRight) {
            // A smooth back-and-forth across the lap: within every face's sweep tolerance.
            assistedPhase += dt * 0.9F;
            sweep = Mth.sin(assistedPhase) * 0.75F;
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Hover hover) {
            hovered = hover.node();
            tooltip();
            return;
        }
        if (!operating()) return;
        if (event instanceof SceneEvent.Stroke stroke && "handle".equals(stroke.node())) {
            handle(stroke);
        } else if (event instanceof SceneEvent.Click click) {
            click(click.node(), click.button());
        }
    }

    private void handle(SceneEvent.Stroke stroke) {
        Vector3f point = new Vector3f(stroke.point());
        switch (stroke.phase()) {
            case START -> {
                holding = true;
                strokeStart = point;
                startSweep = sweep;
                startHeight = height;
                strokeTravel = 0F;
            }
            case MOVE -> {
                if (strokeStart == null) return;
                Vector3f d = new Vector3f(point).sub(strokeStart);
                float sensitivity = (float) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY);
                sweep = Mth.clamp(startSweep + d.dot(right) / kSweep * sensitivity, -1F, 1F);
                height = Mth.clamp(startHeight + d.dot(up) / kHeight * sensitivity, 0F, 1F);
                strokeTravel = Math.max(strokeTravel, d.length());
            }
            case END -> {
                if (ClientConfig.get(ClientConfig.STICKY_DRAG) && strokeTravel < 0.3F && holding && strokeStart != null && startHeight >= 0.99F) {
                    view.holdStroke();
                    return;
                }
                holding = false;
                strokeStart = null;
                height = 1F;
            }
            default -> {
            }
        }
    }

    private void click(String node, int button) {
        LapidaryWheelBlockEntity wheel = wheel();
        if (wheel == null) return;
        CutWork work = shown();
        if (node.startsWith("specimen_")) {
            int i = Integer.parseInt(node.substring(9));
            if (i < specimens.size()) plan(specimens.get(i), work);
        } else switch (node) {
            case "collar" -> turn(button == 1 ? -1 : 1);
            case "cup" -> cool();
            case "loupe" -> inspect();
            case "lever" -> finish(work);
            case "stone" -> {
                if (work == null && wheel.hasStone()) command(WorkCommand.TAKE_STONE);
            }
            default -> {
            }
        }
    }

    private void plan(ResourceLocation cut, @Nullable CutWork work) {
        long now = Util.getNanos();
        CompoundTag args = new CompoundTag();
        args.putString("Cut", cut.toString());
        if (work != null && work.started() && !cut.equals(work.cut())) {
            if (!cut.equals(recutCut) || now > recutUntil) {
                recutCut = cut;
                recutUntil = now + CONFIRM_NANOS;
                cue(Component.translatable("workstation.immersive_jewelry.cue.recut_confirm").withStyle(ChatFormatting.GOLD));
                return;
            }
            args.putBoolean("Confirm", true);
        }
        recutCut = null;
        command(WorkCommand.PLAN_CUT, args);
        sound(ModSounds.WHEEL_MOUNT.get(), "stone", 0.3F, 1.3F);
    }

    private void turn(int by) {
        CutWork work = shown();
        if (work == null) return;
        if (work.sim().contact()) {
            cue(Component.translatable("workstation.immersive_jewelry.cue.lift_to_index"));
            return;
        }
        int k = work.params().regions();
        region = Math.floorMod(region + by, k);
        sound(ModSounds.UI_CUE.get(), "handle", 0.3F, 1.7F);
    }

    private void adjustTrim(int by) {
        CutWork work = shown();
        if (work == null || work.sim().contact()) return;
        trim = Mth.clamp(trim + by, -FacetParams.MAX_TRIM, FacetParams.MAX_TRIM);
        sound(ModSounds.UI_CUE.get(), "handle", 0.2F, 2F);
    }

    private void cool() {
        CutWork work = shown();
        if (work == null || (predicted != null && predicted.sim().contact())) return;
        holding = false;
        height = 1F;
        dipUntil = Util.getNanos() + DIP_NANOS;
        sound(ModSounds.WHEEL_DIP.get(), "cup", 0.5F, 1F);
        // The library's own splash style: a look only, left out with a Diegesis that no longer has it.
        com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.run(com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat.Feature.PARTICLES,
                "the cooling cup's splash", () -> burst(com.pockyl.diegesis.api.client.DiegesisParticles.SPLASH,
                        new Vector3f(WheelPainter.CUP).add(0F, 0.4F, 0F), new Vector3f(0F, 3F, 0F), 0.6F, 4, 0xFFB8D8E8));
    }

    /** The loupe's notes are an overlay: the library offers no keys under it, so its own key reaches it this way. */
    @Override
    boolean overlayOpen() {
        return inspecting;
    }

    @Override
    boolean overlayKey(int key, int scan, int modifiers, boolean repeat) {
        if (!operating() || !is(WorkstationKeys.INSPECT, key, scan)) return false;
        if (!repeat) inspect();
        return true;
    }

    private void inspect() {
        if (inspecting) {
            view.glanceBack();
            view.showOverlay(null);
            inspecting = false;
            return;
        }
        inspecting = view.glance("loupe");
        if (!inspecting) inspecting = true;
        view.showOverlay(new InspectionOverlay(observations(), () -> {
            inspecting = false;
            view.glanceBack();
        }));
    }

    private void finish(@Nullable CutWork work) {
        if (work == null || !work.started()) {
            cue(Component.translatable("workstation.immersive_jewelry.cue.nothing_to_finish"));
            return;
        }
        long now = Util.getNanos();
        CompoundTag args = new CompoundTag();
        if (!work.sim().ready()) {
            if (now > roughUntil) {
                roughUntil = now + CONFIRM_NANOS;
                cue(Component.translatable("workstation.immersive_jewelry.cue.rough_confirm", key(WorkstationKeys.CONFIRM)).withStyle(ChatFormatting.GOLD));
                return;
            }
            args.putBoolean("Rough", true);
        }
        roughUntil = 0L;
        lever = 1F;
        command(WorkCommand.FINISH_CUT, args);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!operating() || stack.isEmpty()) return false;
        if (node.equals("stone") || node.equals("handle") || node.equals("lap")) {
            CompoundTag args = new CompoundTag();
            args.putInt("From", -1);
            command(WorkCommand.PLACE, args);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!operating()) return false;
        CompoundTag args = new CompoundTag();
        args.putInt("From", slot);
        command(WorkCommand.PLACE, args);
        return true;
    }

    @Override
    protected boolean usable(ItemStack stack) {
        return WheelOperations.isWalkUpItem(stack, JewelryDefinitions.client());
    }

    @Override
    protected ItemStack shownStack(String node) {
        LapidaryWheelBlockEntity wheel = wheel();
        if (wheel == null) return ItemStack.EMPTY;
        return switch (node) {
            case "stone" -> wheel.stone();
            case "lap" -> wheel.lap();
            default -> ItemStack.EMPTY;
        };
    }

    /** Where the carried stack would go if it were let go over the hovered placement node (stone, handle or lap). */
    WheelOperations.Placement placement(LapidaryWheelBlockEntity wheel) {
        ItemStack stack = carried();
        if (stack.isEmpty() || hovered == null || !(hovered.equals("stone") || hovered.equals("handle") || hovered.equals("lap"))) {
            return WheelOperations.Placement.NONE;
        }
        return WheelOperations.placement(wheel, stack, JewelryDefinitions.client());
    }

    // ------------------------------------------------------------------------------------------------ keyboard

    @Override
    boolean keyPressed(int key, int scan, int modifiers) {
        if (!operating()) return false;
        if (is(WorkstationKeys.LOWER, key, scan)) return keyLower = true;
        if (is(WorkstationKeys.RAISE, key, scan)) return keyRaise = true;
        if (is(WorkstationKeys.LEFT, key, scan)) return keyLeft = true;
        if (is(WorkstationKeys.RIGHT, key, scan)) return keyRight = true;
        if (is(WorkstationKeys.APPLY, key, scan)) {
            if (ClientConfig.get(ClientConfig.TOGGLE_TOOL_USE)) toggledApply = !toggledApply;
            else keyApply = true;
            return true;
        }
        if (is(WorkstationKeys.NEXT, key, scan)) {
            turn(1);
            return true;
        }
        if (is(WorkstationKeys.PREVIOUS, key, scan)) {
            turn(-1);
            return true;
        }
        if (is(WorkstationKeys.TRIM_UP, key, scan)) {
            adjustTrim(1);
            return true;
        }
        if (is(WorkstationKeys.TRIM_DOWN, key, scan)) {
            adjustTrim(-1);
            return true;
        }
        if (is(WorkstationKeys.COOL, key, scan)) {
            cool();
            return true;
        }
        if (is(WorkstationKeys.INSPECT, key, scan)) {
            inspect();
            return true;
        }
        if (is(WorkstationKeys.CONFIRM, key, scan)) {
            finish(shown());
            return true;
        }
        if (is(WorkstationKeys.TOOL, key, scan) || is(WorkstationKeys.TURN, key, scan)) {
            // Cycle the planned cut through the specimens before any work: keyboard planning. With no stone mounted
            // there is nothing to plan, and the key is free (a recipe viewer's lookup).
            if (specimens.isEmpty()) return false;
            CutWork work = shown();
            int at = work == null ? -1 : specimens.indexOf(work.cut());
            plan(specimens.get(Math.floorMod(at + 1, specimens.size())), work);
            return true;
        }
        return false;
    }

    @Override
    boolean keyReleased(int key, int scan) {
        if (is(WorkstationKeys.LOWER, key, scan)) keyLower = false;
        else if (is(WorkstationKeys.RAISE, key, scan)) keyRaise = false;
        else if (is(WorkstationKeys.LEFT, key, scan)) keyLeft = false;
        else if (is(WorkstationKeys.RIGHT, key, scan)) keyRight = false;
        else if (is(WorkstationKeys.APPLY, key, scan)) keyApply = false;
        else return false;
        return true;
    }

    @Override
    boolean scrolled(@Nullable String node, double amount) {
        if (node == null || amount == 0) return false;
        if (node.equals("collar") || node.equals("handle")) {
            if (Screen.hasShiftDown()) adjustTrim(amount > 0 ? 1 : -1);
            else turn(amount > 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    void releaseAll() {
        holding = false;
        strokeStart = null;
        keyLower = keyRaise = keyLeft = keyRight = keyApply = toggledApply = false;
        height = 1F;
        dipUntil = 0L;
    }

    // ------------------------------------------------------------------------------------------------ hints

    @Override
    protected Component hint() {
        LapidaryWheelBlockEntity wheel = wheel();
        if (wheel == null) return Component.empty();
        CutWork work = shown();
        if (!wheel.hasStone()) return Component.translatable("workstation.immersive_jewelry.hint.wheel.mount");
        if (stoneData(wheel) == null) return Component.translatable("workstation.immersive_jewelry.hint.wheel.take");
        if (work == null) {
            if (wheel.stone().getItem() instanceof CutGemItem && !holding) {
                return Component.translatable("workstation.immersive_jewelry.hint.wheel.recut_or_take", key(WorkstationKeys.TOOL));
            }
            return Component.translatable("workstation.immersive_jewelry.hint.wheel.plan", key(WorkstationKeys.TOOL));
        }
        FacetSim sim = work.sim();
        if (sim.hot()) return Component.translatable("workstation.immersive_jewelry.hint.wheel.hot", key(WorkstationKeys.COOL));
        if (sim.ready()) return Component.translatable("workstation.immersive_jewelry.hint.wheel.ready", key(WorkstationKeys.CONFIRM));
        if (sim.contact()) return Component.translatable("workstation.immersive_jewelry.hint.wheel.grinding");
        int next = sim.recommended();
        if (sim.inWindow(sim.region()) || sim.overcut(sim.region())) {
            return Component.translatable("workstation.immersive_jewelry.hint.wheel.next_face", next + 1, key(WorkstationKeys.NEXT));
        }
        if (!work.started()) {
            return Component.translatable("workstation.immersive_jewelry.hint.wheel.lower", key(WorkstationKeys.LOWER), key(WorkstationKeys.APPLY));
        }
        return Component.translatable("workstation.immersive_jewelry.hint.wheel.lower_again", key(WorkstationKeys.APPLY));
    }

    private void tooltip() {
        if (hovered == null) return;
        LapidaryWheelBlockEntity wheel = wheel();
        if (wheel == null) return;
        int specimen = hoveredSpecimen();
        if (specimen >= 0 && specimen < specimens.size()) {
            GemData data = stoneData(wheel);
            JewelryDefinitions defs = JewelryDefinitions.client();
            CutDefinition cut = defs.cut(specimens.get(specimen));
            if (data != null && cut != null) {
                GemDefinition gem = defs.gemOrGeneric(data.gem());
                boolean recut = wheel.stone().getItem() instanceof CutGemItem;
                float keeps = CutPlanner.retention(gem, cut, recut);
                MutableComponent text = Component.translatable("workstation.immersive_jewelry.tooltip.specimen", JewelryNames.cut(defs, cut.id()),
                        Math.round(keeps * 100F), Math.round(data.size() * keeps * 100F) / 100F, difficulty(CutPlanner.displayDifficulty(gem, cut)));
                view.node(hovered).ifPresent(n -> n.setTooltip(text));
            }
        } else if (hovered.equals("collar")) {
            CutWork work = shown();
            if (work != null) {
                view.node("collar").ifPresent(n -> n.setTooltip(Component.translatable("workstation.immersive_jewelry.tooltip.collar",
                        work.sim().region() + 1, work.params().regions(), regionName(work.params().pattern(), work.sim().region()))));
            }
        }
    }

    private static String difficulty(float value) {
        int dots = Mth.clamp(Math.round(value * 5F), 1, 5);
        return "●".repeat(dots) + "○".repeat(5 - dots);
    }

    static Component regionName(CutPattern pattern, int region) {
        return switch (pattern) {
            case STEP -> Component.translatable(region % 2 == 0 ? "workstation.immersive_jewelry.region.side" : "workstation.immersive_jewelry.region.corner");
            case DOME -> Component.translatable("workstation.immersive_jewelry.region.zone");
            default -> Component.translatable("workstation.immersive_jewelry.region.facet");
        };
    }

    /** What the loupe shows: plain observations about every region of the stone. */
    private List<Component> observations() {
        List<Component> out = new ArrayList<>();
        CutWork work = shown();
        LapidaryWheelBlockEntity wheel = wheel();
        GemData data = wheel == null ? null : stoneData(wheel);
        if (data == null) return out;
        if (work == null) {
            out.add(Component.translatable("workstation.immersive_jewelry.loupe.stone", data.isCut()
                    ? Component.translatable("workstation.immersive_jewelry.loupe.cut") : Component.translatable("workstation.immersive_jewelry.loupe.rough")));
            return out;
        }
        FacetSim sim = work.sim();
        FacetParams p = work.params();
        boolean numbers = ClientConfig.get(ClientConfig.NUMERIC_GUIDE);
        for (int i = 0; i < p.regions(); i++) {
            Component name = Component.translatable("workstation.immersive_jewelry.loupe.region", regionName(p.pattern(), i), i + 1);
            List<Component> notes = new ArrayList<>();
            float d = sim.depth(i);
            float lo = p.target()[i] - p.window();
            if (d < 0.02F) notes.add(Component.translatable("workstation.immersive_jewelry.loupe.untouched"));
            else if (d < lo) notes.add(Component.translatable("workstation.immersive_jewelry.loupe.shallow"));
            else if (sim.overcut(i)) notes.add(Component.translatable("workstation.immersive_jewelry.loupe.overcut"));
            else notes.add(Component.translatable("workstation.immersive_jewelry.loupe.good_depth"));
            if (d > 0.1F && sim.unevenness(i) > (p.pattern() == CutPattern.DOME ? 0.45F : 0.75F)) {
                notes.add(Component.translatable(p.pattern() == CutPattern.DOME ? "workstation.immersive_jewelry.loupe.flat_spot"
                        : "workstation.immersive_jewelry.loupe.uneven"));
            }
            if (sim.rock(i) > 0.12F) notes.add(Component.translatable("workstation.immersive_jewelry.loupe.rocked"));
            if (sim.scratch(i) > 0.05F) notes.add(Component.translatable("workstation.immersive_jewelry.loupe.scratched"));
            if (sim.chip(i) > 0F) notes.add(Component.translatable("workstation.immersive_jewelry.loupe.chipped").withStyle(ChatFormatting.RED));
            if (sim.azimuthError(i) > p.azimuthTolerance() && d > 0.1F) {
                notes.add(Component.translatable("workstation.immersive_jewelry.loupe.turned", key(WorkstationKeys.TRIM_UP), key(WorkstationKeys.TRIM_DOWN)));
            }
            MutableComponent line = name.copy().append(": ");
            for (int n = 0; n < notes.size(); n++) {
                if (n > 0) line.append(", ");
                line.append(notes.get(n));
            }
            if (numbers) line.append(Component.literal(String.format(" (%d%%)", Math.round(d / p.target()[i] * 100F))).withStyle(ChatFormatting.DARK_GRAY));
            out.add(line);
        }
        if (sim.chips() > 0) out.add(Component.translatable("workstation.immersive_jewelry.loupe.chips", sim.chips()).withStyle(ChatFormatting.RED));
        return out;
    }
}
