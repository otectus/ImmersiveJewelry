package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.client.workstation.render.BenchPainter;
import com.otectus.immersivejewelry.client.workstation.render.DesignLayout;
import com.otectus.immersivejewelry.client.workstation.render.FrameRenderer;
import com.otectus.immersivejewelry.client.workstation.render.Geo;
import com.otectus.immersivejewelry.client.workstation.render.Hands;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.integration.diegesis.DiegesisCompat;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.registry.ModSounds;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SimIO;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Jeweler's Bench at work, on the client. Tools are picked up from the bench and used on the piece in the clamp:
 * the tweezers carry a tray stone and let it go into a cup (where it is let go decides how well it sits), the pliers
 * grip one prong and their handle travel closes it, the pusher works round a bezel rim, the cloth, burnisher and brush
 * work the surface. The pointer is followed onto the piece's own plane, so targets and contact are where they are drawn.
 * Every action has a key as well. The operator's samples are predicted and replayed on every confirmed state.
 *
 * <p>Storage (1.3): the three supply jars are Diegesis slots bound to the bench's own supplies (picked up, put down, split,
 * scrolled, spread and shift-clicked as any container slot, the library's counts facing the camera), so no Jewelry click
 * handles them any more. The staging drawer in front of the vise holds four loose stacks on two surfaces of the scene's
 * {@code placing}: its surfaces, the stacks on them and its handle slide with the drawer, and they are hidden and take nothing
 * while it is shut (the server refuses them then as well). Its list ({@link DrawerOverlay}) reaches every staged stack by keys,
 * open or shut. The tray, the clamp, the sockets and the dock stay Jewelry's own.</p>
 */
final class BenchBehavior extends WorkstationBehavior {
    private static final float TRAVEL_PX = 1.6F;

    private BenchPainter.Tool tool = BenchPainter.Tool.NONE;
    private int heldTray = -1;
    private float heldTurn;
    @Nullable
    private Vector3f pointer;
    @Nullable
    private Vector3f pressScene;
    private boolean pressing;
    private boolean pry;
    private float travel;
    private float theta;
    private float u;
    private float v = 0.5F;
    private final FinishingSweep finishingSweep = new FinishingSweep();
    private int targetSocket;
    private int targetPoint;
    private boolean keyApply, toggledApply, keyLower, keyRaise, keyLeft, keyRight;

    @Nullable
    private BenchWork predicted;
    private int seenVersion = -1;
    private float relax;
    private float tilt;
    private float particleCredit;
    @Nullable
    private FolioOverlay folio;
    private final Map<Item, Boolean> usableItems = new HashMap<>();
    private int usableVersion = Integer.MIN_VALUE;
    private final Vector3f camera = new Vector3f(8F, 27F, -5F);
    private final Vector3f up = new Vector3f(0F, 1F, 0F);
    /** The drawer's shown position, 0 shut .. 1 open, easing to the bench's wanted one. */
    private float drawer;
    /** Whether the drawer's surfaces were last set shown (a surface's visibility lasts until set again). */
    @Nullable
    private Boolean surfacesShown;
    private final Matrix4f surfaceMove = new Matrix4f();
    @Nullable
    private DrawerOverlay drawerList;

    BenchBehavior(StationView view) {
        super(view);
    }

    @Nullable
    JewelersBenchBlockEntity bench() {
        return view.level().getBlockEntity(pos) instanceof JewelersBenchBlockEntity bench ? bench : null;
    }

    @Nullable
    private BenchWork shown() {
        if (operating() && predicted != null) return predicted;
        JewelersBenchBlockEntity bench = bench();
        return bench == null ? null : bench.work();
    }

    @Override
    protected long workId() {
        BenchWork work = shown();
        return work == null ? 0L : work.id();
    }

    // ------------------------------------------------------------------------------------------------ scene

    @Override
    protected net.minecraft.resources.ResourceLocation stationId() {
        return JewelryStations.BENCH_ID;
    }

    @Override
    public void onSceneReady(StationView view) {
        super.onSceneReady(view);
        // The scene's nodes are new (first show, F3+T): every drawer setting is put back from the bench's real state.
        JewelersBenchBlockEntity bench = bench();
        drawer = bench != null && bench.drawerOpen() ? 1F : 0F;
        surfacesShown = null;
        applyDrawer();
        place("piece", BenchPainter.PIECE);
        place("folio", BenchPainter.FOLIO);
        place("pouch", BenchPainter.POUCH);
        place("dock", BenchPainter.DOCK);
        place("loupe", new Vector3f(3.6F, 14.6F, 13.6F));
        for (int i = 0; i < JewelersBenchBlockEntity.TRAY; i++) place("tray_" + i, BenchPainter.tray(i));
        for (int i = 0; i < JewelersBenchBlockEntity.SUPPLIES; i++) place("supply_" + i, jarSlots() ? BenchPainter.supplySlot(i) : BenchPainter.supply(i));
        place(com.otectus.immersivejewelry.workstation.station.SceneComfort.LAMP, BenchPainter.LAMP_HEAD);
        for (BenchPainter.Tool t : new BenchPainter.Tool[]{BenchPainter.Tool.TWEEZERS, BenchPainter.Tool.PUSHER, BenchPainter.Tool.BURNISHER,
                BenchPainter.Tool.BRUSH, BenchPainter.Tool.CLOTH}) {
            place("tool_" + t.name().toLowerCase(java.util.Locale.ROOT), t.rest());
        }
    }

    private void place(String node, Vector3f at) {
        view.node(node).ifPresent(n -> n.moveTo(at.x, at.y, at.z, 0F));
    }

    /** The pointer mapping follows the camera actually used: the authored shot, mirrored for the left working side. */
    @Override
    protected void cameraChanged() {
        float[] cam = SceneLoader.INSTANCE.camera(JewelryStations.BENCH_ID, mirror);
        if (cam == null) return;
        camera.set(cam[0], cam[1], cam[2]);
        Vector3f look = new Vector3f(cam[3] - cam[0], cam[4] - cam[1], cam[5] - cam[2]).normalize();
        Vector3f right = new Vector3f(look).cross(0F, 1F, 0F).normalize();
        up.set(right).cross(look).normalize();
    }

    // ------------------------------------------------------------------------------------------------ the staging drawer

    /**
     * Puts the drawer where it is shown now: both surfaces (written open) moved back by what is still shut, with the stacks on
     * them; the handle with them. Shown and taking stacks only once it is open at all; the items are hidden every frame it is
     * shut away (the library forgets that each frame), and nothing is said about counts of a shut drawer.
     */
    private void applyDrawer() {
        if (physicalDrawer()) {
            float shut = (1F - drawer) * BenchPainter.DRAWER_TRAVEL;
            surfaceMove.identity().translate(0F, 0F, shut);
            boolean shown = drawer > 0.02F;
            DiegesisCompat.run(DiegesisCompat.Feature.DRAWER_MOTION, "the staging drawer", () -> {
                for (int i = 0; i < BenchPainter.DRAWER_SURFACES; i++) view.setSurfaceTransform(i, surfaceMove);
                if (surfacesShown == null || surfacesShown != shown) {
                    for (int i = 0; i < BenchPainter.DRAWER_SURFACES; i++) view.setSurfaceVisible(i, shown);
                    surfacesShown = shown;
                }
                view.setPlacingVisible(shown);
            });
        }
        Vector3f handle = BenchPainter.drawerHandle(drawer);
        view.node("drawer").ifPresent(n -> n.moveTo(handle.x, handle.y, handle.z, 0F));
    }

    /**
     * Whether the drawer is physical here: the registered scene has its surfaces and the library can move and hide them. Else
     * the drawer is its list, and the painter draws the staged stacks in it.
     */
    boolean physicalDrawer() {
        return SceneLoader.INSTANCE.hasPlacing(JewelryStations.BENCH_ID) && DiegesisCompat.has(DiegesisCompat.Feature.DRAWER_MOTION);
    }

    /** Whether the jars are the library's slots here (else Jewelry's own clicks and placements move their stacks). */
    boolean jarSlots() {
        return SceneLoader.INSTANCE.hasJarSlots(JewelryStations.BENCH_ID);
    }

    /** The drawer's shown position (0 shut .. 1 open). */
    float drawerShown() {
        return drawer;
    }

    void toggleDrawer() {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return;
        CompoundTag args = new CompoundTag();
        args.putBoolean("Open", !bench.drawerOpen());
        command(WorkCommand.DRAWER, args);
        sound(ModSounds.BENCH_PLACE.get(), "drawer", 0.35F, bench.drawerOpen() ? 0.7F : 0.8F);
    }

    void openDrawerList() {
        if (bench() == null) return;
        closeFolio();
        drawerList = new DrawerOverlay(this, () -> drawerList = null);
        view.showOverlay(drawerList);
    }

    void closeDrawerList() {
        if (drawerList != null) view.showOverlay(null);
        drawerList = null;
    }

    /** Moves (part of) staged stack {@code index} to the work area or the inventory, naming the generation it was seen with. */
    void moveStaged(int index, boolean toWork) {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null || bench.staged(index).isEmpty()) return;
        CompoundTag args = new CompoundTag();
        args.putInt("Index", index);
        args.putInt("Count", 0);
        args.putString("To", toWork ? "work" : "inventory");
        args.putInt("Gen", bench.generation(BenchOperations.STAGING, index));
        command(WorkCommand.MOVE_STAGED, args);
    }
    @Override
    protected void draw(Geo geo, float partialTick) {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return;
        Hands.operator(bench.operator());
        try {
            BenchPainter.draw(geo, state(bench), view.age());
            Target place = placement(bench);
            if (place != null) BenchPainter.placeHint(geo, place.group(), place.index(), hintColor());
        } finally {
            Hands.operator(null);
        }
    }

    private static ItemStack[] staged(JewelersBenchBlockEntity bench) {
        ItemStack[] out = new ItemStack[JewelersBenchBlockEntity.STAGING];
        for (int i = 0; i < out.length; i++) out[i] = bench.staged(i);
        return out;
    }

    BenchPainter.State state(JewelersBenchBlockEntity bench) {
        BenchWork work = shown();
        ItemStack[] sockets = new ItemStack[JewelersBenchBlockEntity.SOCKETS];
        ItemStack[] tray = new ItemStack[JewelersBenchBlockEntity.TRAY];
        ItemStack[] supplies = new ItemStack[JewelersBenchBlockEntity.SUPPLIES];
        for (int i = 0; i < sockets.length; i++) sockets[i] = bench.socket(i);
        for (int i = 0; i < tray.length; i++) tray[i] = bench.tray(i);
        for (int i = 0; i < supplies.length; i++) supplies[i] = bench.supply(i);
        DesignLayout layout = layout(bench, work);
        SettingSim sim = work != null ? work.setting() : null;
        SettingStyle style = sim != null ? sim.params().style() : design(bench, work) != null ? design(bench, work).style() : SettingStyle.PRONG;
        int points = sim != null ? sim.params().points() : design(bench, work) != null ? design(bench, work).pointsPerStone() : 4;
        float tolerance = sim != null ? sim.params().tolerance() : 0.1F;
        boolean mine = operating();
        return new BenchPainter.State(bench.clamp(), sockets, tray, supplies, bench.dock(), work, layout, style, points, tolerance,
                mine ? tool : BenchPainter.Tool.NONE, mine ? toolAt() : null, travel, pressing || keyApply || toggledApply, targetSocket, targetPoint,
                mine ? heldTray : -1, heldTurn, mine ? guide(bench) : null, ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES),
                mine && tool != BenchPainter.Tool.NONE, relax, tilt, drawer, jarSlots(), physicalDrawer() ? null : staged(bench));
    }

    @Nullable
    private JewelryDesignRecipe design(JewelersBenchBlockEntity bench, @Nullable BenchWork work) {
        return StationStates.design(bench, work);
    }

    @Nullable
    DesignLayout layout(JewelersBenchBlockEntity bench, @Nullable BenchWork work) {
        return StationStates.layout(bench, work);
    }

    // ------------------------------------------------------------------------------------------------ per frame

    @Override
    protected void update(float dt, long now) {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return;
        rebase(bench);
        // Everyone sees the drawer move to the bench's wanted position; reduced motion or instant transitions skip the slide.
        float wanted = bench.drawerOpen() ? 1F : 0F;
        boolean instant = ClientConfig.get(ClientConfig.REDUCED_MOTION) || ClientConfig.get(ClientConfig.INSTANT_TRANSITIONS);
        drawer = instant ? wanted : Math.abs(wanted - drawer) < 0.01F ? wanted : smooth(drawer, wanted, 9F, dt);
        applyDrawer();
        if (operating()) keys(dt, bench);
        BenchWork work = shown();
        boolean finished = work == null && bench.clamp().getItem() instanceof JewelryItem;
        relax = smooth(relax, finished ? 1F : 0F, 3F, dt);
        // A clamped piece stays still. Inspection is supplied by the loupe, not by rocking inside closed jaws.
        tilt = smooth(tilt, 0F, 2F, dt);
        view.node("piece").ifPresent(n -> n.setTooltip(Component.empty()));
        sounds(work);
        particles(work, dt);
    }

    private void rebase(JewelersBenchBlockEntity bench) {
        if (bench.clientVersion() == seenVersion) return;
        seenVersion = bench.clientVersion();
        BenchWork confirmed = bench.work();
        if (!operating() || confirmed == null) {
            predicted = null;
            return;
        }
        BenchWork copy = confirmed.copy();
        recorder.replay(bench.ackStep(), s -> step(copy, s));
        predicted = copy;
        if (heldTray >= 0 && CutGemItem.data(bench.tray(heldTray)).isEmpty()) heldTray = -1;
    }

    private static int step(BenchWork work, WorkSample s) {
        if (work.stage() == BenchWork.Stage.FINISHING && work.surface() != null) return work.surface().step(s) << 16;
        if (work.setting() != null && work.committed() || work.kind() != BenchWork.Kind.ASSEMBLE) {
            return work.setting() != null ? work.setting().step(s) : 0;
        }
        return 0;
    }

    /** The tool's loop while it touches: cloth, burnisher or brush on the surface, the creak of pliers or pusher closing. */
    private void sounds(@Nullable BenchWork work) {
        SurfaceSim surface = work != null ? work.surface() : null;
        if (surface != null && surface.contact()) {
            int id = surface.params().mode().tool(surface.phase());
            loop("work", id == SurfaceParams.TOOL_CLOTH ? ModSounds.BENCH_CLOTH.get() : id == SurfaceParams.TOOL_BURNISHER
                    ? ModSounds.BENCH_BURNISH.get() : ModSounds.BENCH_BRUSH.get(), 0.35F, 1F);
            return;
        }
        boolean closing = (tool == BenchPainter.Tool.PLIERS || tool == BenchPainter.Tool.PUSHER) && (pressing || keyApply || toggledApply)
                && work != null && work.setting() != null && travel > 0.02F;
        if (closing) {
            float pitch = tool == BenchPainter.Tool.PUSHER ? 0.7F : 0.85F;
            loop("work", ModSounds.BENCH_SQUEEZE.get(), 0.12F + 0.25F * travel, pitch + 0.35F * travel);
            return;
        }
        loop("work", null, 0F, 1F);
    }

    private void particles(@Nullable BenchWork work, float dt) {
        if (work == null || work.surface() == null || !work.surface().contact()) return;
        particleCredit += dt * 12F * particles();
        if (particleCredit < 1F) return;
        int n = (int) particleCredit;
        particleCredit -= n;
        Vector3f at = toolAt();
        if (at == null) return;
        int color = work.surface().params().mode() == SurfaceParams.Mode.PATINA && work.surface().phase() == 0 ? 0xC03A3A3E : 0xC0E8E0D0;
        view.spawnParticles(WorkEffects.FLECK, at, new Vector3f(0F, 1.5F, 0F), 0.3F, n, color);
    }

    // ------------------------------------------------------------------------------------------------ pointer and targets

    /** The pointer on the piece's plane, in the piece's frame, from a stroke point on the camera-facing plane. */
    @Nullable
    private Vector3f onPiece(Vector3f strokePoint) {
        Vector3f dir = new Vector3f(strokePoint).sub(camera).normalize();
        Vector3f normal = new Vector3f(0F, 0F, -1F);
        float denom = dir.dot(normal);
        if (Math.abs(denom) < 1.0E-4F) return null;
        float t = new Vector3f(BenchPainter.PIECE).sub(camera).dot(normal) / denom;
        Vector3f hit = new Vector3f(camera).add(new Vector3f(dir).mul(t));
        return BenchPainter.toPiece(hit);
    }

    /** Finds the prong, rim sector or socket nearest the pointer, within the (scaled) capture distance. */
    private void target(JewelersBenchBlockEntity bench, Vector3f local) {
        BenchWork work = shown();
        DesignLayout layout = layout(bench, work);
        if (layout == null) return;
        float scale = (float) ClientConfig.get(ClientConfig.TARGET_SCALE);
        SettingSim sim = work != null ? work.setting() : null;
        int points = sim != null ? sim.params().points() : 4;
        SettingStyle style = sim != null ? sim.params().style() : SettingStyle.PRONG;
        float best = Float.MAX_VALUE;
        for (int s = 0; s < layout.sockets().size(); s++) {
            DesignLayout.Socket socket = layout.socket(s);
            if (tool == BenchPainter.Tool.PLIERS && style == SettingStyle.PRONG) {
                for (int q = 0; q < points; q++) {
                    float a = Mth.PI / points + q * Mth.TWO_PI / points + socket.turn();
                    float px = socket.x() + Mth.cos(a) * (socket.radius() + 0.1F);
                    float py = socket.y() + Mth.sin(a) * (socket.radius() + 0.1F);
                    float d = Mth.sqrt((local.x - px) * (local.x - px) + (local.y - py) * (local.y - py));
                    if (d < best && d < 0.9F * scale) {
                        best = d;
                        targetSocket = s;
                        targetPoint = q;
                    }
                }
            } else {
                float d = Mth.sqrt((local.x - socket.x()) * (local.x - socket.x()) + (local.y - socket.y()) * (local.y - socket.y()));
                if (d < best && d < (socket.radius() + 1.3F) * scale) {
                    best = d;
                    targetSocket = s;
                    float a = (float) Math.atan2(local.y - socket.y(), local.x - socket.x());
                    if (a < 0F) a += Mth.TWO_PI;
                    theta = a;
                    targetPoint = Math.floorMod(Math.round(a / Mth.TWO_PI * points), points);
                }
            }
        }
    }

    /** Where the tool's working end is, in scene pixels. */
    @Nullable
    private Vector3f toolAt() {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null || tool == BenchPainter.Tool.NONE) return null;
        BenchWork work = shown();
        DesignLayout layout = layout(bench, work);
        if (tool == BenchPainter.Tool.PLIERS && layout != null && targetSocket < layout.sockets().size()) {
            DesignLayout.Socket socket = layout.socket(targetSocket);
            int points = work != null && work.setting() != null ? work.setting().params().points() : 4;
            float a = Mth.PI / points + targetPoint * Mth.TWO_PI / points + socket.turn();
            float r = socket.radius() + 0.25F;
            return BenchPainter.toScene(socket.x() + Mth.cos(a) * r, socket.y() + Mth.sin(a) * r, 0.75F);
        }
        if (tool == BenchPainter.Tool.PUSHER && layout != null && targetSocket < layout.sockets().size()) {
            DesignLayout.Socket socket = layout.socket(targetSocket);
            float r = socket.radius() + 0.2F - travel * 0.35F;
            return BenchPainter.toScene(socket.x() + Mth.cos(theta) * r, socket.y() + Mth.sin(theta) * r, 0.7F);
        }
        if (pointer != null) return BenchPainter.toScene(pointer.x, pointer.y, tool == BenchPainter.Tool.TWEEZERS ? 1.2F : 0.75F);
        return tool.rest();
    }

    /** The soft alignment guide: only near a valid, free socket while the tweezers carry a stone. */
    @Nullable
    private Vector3f guide(JewelersBenchBlockEntity bench) {
        if (tool != BenchPainter.Tool.TWEEZERS || heldTray < 0 || pointer == null) return null;
        int socket = nearestFreeSocket(bench, pointer);
        if (socket < 0) return null;
        DesignLayout.Socket s = layout(bench, shown()).socket(socket);
        return BenchPainter.toScene(s.x(), s.y(), 0.9F);
    }

    private int nearestFreeSocket(JewelersBenchBlockEntity bench, Vector3f local) {
        BenchWork work = shown();
        DesignLayout layout = layout(bench, work);
        if (layout == null) return -1;
        float scale = (float) ClientConfig.get(ClientConfig.TARGET_SCALE);
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < layout.sockets().size(); i++) {
            if (!bench.socket(i).isEmpty()) continue;
            DesignLayout.Socket s = layout.socket(i);
            float capture = s.radius() * 1.6F * scale;
            float d = Mth.sqrt((local.x - s.x()) * (local.x - s.x()) + (local.y - s.y()) * (local.y - s.y()));
            if (d < capture && d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    protected WorkSample sample() {
        boolean contact = pressing || keyApply || toggledApply;
        switch (tool) {
            case PLIERS -> {
                int flags = (contact ? WorkSample.FLAG_CONTACT : 0) | (pry ? WorkSample.FLAG_PRY : 0);
                return new WorkSample(SimIO.toUnit12(contact ? travel : 0F), 0, 0, targetSocket * 16 + targetPoint, SettingSim.TOOL_PLIERS, flags);
            }
            case PUSHER -> {
                int flags = (contact ? WorkSample.FLAG_CONTACT : 0) | (pry ? WorkSample.FLAG_PRY : 0);
                return new WorkSample(SimIO.toUnit12(contact ? travel : 0F), SimIO.toUnit12(theta / Mth.TWO_PI), 0, targetSocket * 16,
                        SettingSim.TOOL_PUSHER, flags);
            }
            case CLOTH, BURNISHER, BRUSH -> {
                int id = tool == BenchPainter.Tool.CLOTH ? SurfaceParams.TOOL_CLOTH : tool == BenchPainter.Tool.BURNISHER
                        ? SurfaceParams.TOOL_BURNISHER : SurfaceParams.TOOL_BRUSH;
                return new WorkSample(SimIO.toUnit12(u), SimIO.toUnit12(v), contact ? 2457 : 0, 0, id, contact ? WorkSample.FLAG_CONTACT : 0);
            }
            default -> {
                return new WorkSample(0, 0, 0, 0, 0, 0);
            }
        }
    }

    @Override
    protected boolean active() {
        return pressing || keyApply || toggledApply || keyLower || keyRaise || keyLeft || keyRight || travel > 0.001F;
    }

    @Override
    protected void predict(WorkSample sample) {
        if (predicted == null) return;
        int events = step(predicted, sample);
        if (events == 0) return;
        int surface = events >>> 16;
        int setting = events & 0xFFFF;
        if ((setting & SettingSim.EVENT_CONTACT) != 0) {
            sound(ModSounds.BENCH_PRONG.get(), "piece", 0.5F, 1.2F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.secure").withStyle(ChatFormatting.GREEN));
        }
        if ((setting & SettingSim.EVENT_SECURED) != 0) {
            cueSound(ModSounds.UI_CUE.get(), "piece", 0.5F, 1.4F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.stone_secured").withStyle(ChatFormatting.GREEN));
            burst(WorkEffects.GLINT, settingAt(), new Vector3f(0F, 1F, -0.5F), 0.25F, 3, 0xFFFFE5AF);
        }
        if ((setting & SettingSim.EVENT_OVER) != 0) {
            sound(ModSounds.BENCH_BEND.get(), "piece", 0.6F, 0.9F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.overbent").withStyle(ChatFormatting.GOLD));
        }
        if ((setting & SettingSim.EVENT_CHIP) != 0) {
            sound(ModSounds.WHEEL_CHIP.get(), "piece", 0.7F, 1.4F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.chip").withStyle(ChatFormatting.RED));
            burst(WorkEffects.CHIP, settingAt(), new Vector3f(0F, 3F, -2F), 0.4F, 4, 0xFFD1D8D8);
        }
        if ((setting & SettingSim.EVENT_OPENED) != 0) {
            sound(ModSounds.BENCH_PRONG.get(), "piece", 0.5F, 0.8F);
            cue(Component.translatable("workstation.immersive_jewelry.cue.opened"));
        }
        if ((setting & SettingSim.EVENT_GRIP) != 0) sound(ModSounds.BENCH_BEND.get(), "piece", 0.2F, 1.8F);
        if ((surface & SurfaceSim.EVENT_PHASE) != 0) cue(Component.translatable("workstation.immersive_jewelry.cue.patina_wipe"));
        if ((surface & SurfaceSim.EVENT_DONE) != 0) {
            sound(ModSounds.BENCH_FINISH.get(), "piece", 0.6F, 1.1F);
            burst(WorkEffects.GLINT, BenchPainter.PIECE, new Vector3f(0F, 1.5F, 0F), 1.4F, 8, 0xFFFFE5AF);
        }
    }

    private Vector3f settingAt() {
        JewelersBenchBlockEntity bench = bench();
        DesignLayout layout = bench != null ? layout(bench, predicted) : null;
        int socket = predicted != null && predicted.setting() != null ? predicted.setting().gripSocket() : -1;
        if (layout == null || socket < 0 || socket >= layout.sockets().size()) return new Vector3f(BenchPainter.PIECE);
        var at = layout.socket(socket);
        return BenchPainter.toScene(at.x(), at.y(), at.radius() * 0.4F);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Hover hover) {
            hovered = hover.node();
            return;
        }
        if (!operating()) return;
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return;
        if (event instanceof SceneEvent.Stroke stroke && "piece".equals(stroke.node())) {
            stroke(bench, stroke);
        } else if (event instanceof SceneEvent.Click click) {
            click(bench, click.node(), click.button());
        }
    }

    private void stroke(JewelersBenchBlockEntity bench, SceneEvent.Stroke stroke) {
        Vector3f point = new Vector3f(stroke.point());
        Vector3f local = onPiece(point);
        if (local == null) return;
        BenchWork work = shown();
        switch (stroke.phase()) {
            case HOVER -> {
                // Diegesis sends hover even when the mouse is stationary. It must not reset a keyboard-driven
                // stroke or retarget the prong being worked on each frame. A mouse press still takes its own path.
                boolean surfaceTool = tool.ordinal() >= BenchPainter.Tool.CLOTH.ordinal();
                if (keyLeft || keyRight || keyLower || keyRaise
                        || ((keyApply || toggledApply) && (!surfaceTool || ClientConfig.get(ClientConfig.ASSISTED_SWEEP)))) return;
                pointer = local;
                if (!pressing) target(bench, local);
                if (surfaceTool) surface(bench, work, local);
            }
            case START -> {
                pointer = local;
                pressScene = point;
                if (tool == BenchPainter.Tool.TWEEZERS) {
                    tweezers(bench, work, local);
                    return;
                }
                if (tool == BenchPainter.Tool.NONE) return;
                target(bench, local);
                pry = stroke.button() == 1 || Screen.hasShiftDown() || (work != null && work.kind() == BenchWork.Kind.REMOVE);
                pressing = true;
                travel = 0F;
                if (tool.ordinal() >= BenchPainter.Tool.CLOTH.ordinal()) surface(bench, work, local);
            }
            case MOVE -> {
                pointer = local;
                if (!pressing || pressScene == null) return;
                float sensitivity = (float) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY);
                if (tool == BenchPainter.Tool.PLIERS) {
                    float down = new Vector3f(pressScene).sub(point).dot(up);
                    travel = Mth.clamp(down / TRAVEL_PX * sensitivity, 0F, 1F);
                } else if (tool == BenchPainter.Tool.PUSHER) {
                    DesignLayout layout = layout(bench, work);
                    if (layout != null && targetSocket < layout.sockets().size()) {
                        DesignLayout.Socket s = layout.socket(targetSocket);
                        float dx = local.x - s.x(), dy = local.y - s.y();
                        float a = (float) Math.atan2(dy, dx);
                        theta = a < 0F ? a + Mth.TWO_PI : a;
                        float rim = s.radius() + 0.15F;
                        travel = Mth.clamp((rim - Mth.sqrt(dx * dx + dy * dy)) / (rim * 0.7F) * sensitivity, 0F, 1F);
                    }
                } else {
                    surface(bench, work, local);
                }
            }
            case END -> {
                pressing = false;
                travel = 0F;
                pressScene = null;
            }
        }
    }

    private void surface(JewelersBenchBlockEntity bench, @Nullable BenchWork work, Vector3f local) {
        DesignLayout layout = layout(bench, work);
        if (layout == null) return;
        float[] uv = FrameRenderer.surfaceAt(layout.body(), local.x, local.y);
        if (uv == null) {
            if (pressing) pressing = false;
            return;
        }
        u = uv[0];
        v = uv[1];
    }

    /** A tweezers click on the piece: let a carried stone go into the nearest free cup, or lift a stone nothing holds yet. */
    private void tweezers(JewelersBenchBlockEntity bench, @Nullable BenchWork work, Vector3f local) {
        DesignLayout layout = layout(bench, work);
        if (layout == null) return;
        if (heldTray >= 0) {
            int socket = nearestFreeSocket(bench, local);
            if (socket < 0) {
                cue(Component.translatable("workstation.immersive_jewelry.cue.no_socket"));
                return;
            }
            DesignLayout.Socket s = layout.socket(socket);
            float capture = s.radius() * 1.6F * (float) ClientConfig.get(ClientConfig.TARGET_SCALE);
            seat(socket, (local.x - s.x()) / capture, (local.y - s.y()) / capture);
            return;
        }
        // Nothing carried: lift the stone under the tweezers if it is free to come out.
        for (int i = 0; i < layout.sockets().size(); i++) {
            DesignLayout.Socket s = layout.socket(i);
            float d = Mth.sqrt((local.x - s.x()) * (local.x - s.x()) + (local.y - s.y()) * (local.y - s.y()));
            if (d < s.radius() * 1.2F) {
                CompoundTag args = new CompoundTag();
                args.putInt("Socket", i);
                args.putInt("Gen", bench.generation(BenchOperations.SOCKET, i));
                command(WorkCommand.LIFT, args);
                sound(ModSounds.BENCH_SEAT.get(), "piece", 0.4F, 1.6F);
                return;
            }
        }
    }

    private void seat(int socket, float dx, float dy) {
        CompoundTag args = new CompoundTag();
        args.putInt("Tray", heldTray);
        args.putInt("Socket", socket);
        JewelersBenchBlockEntity bench = bench();
        if (bench != null) args.putInt("Gen", bench.generation(BenchOperations.TRAY, heldTray));
        args.putFloat("Dx", Mth.clamp(dx, -1F, 1F));
        args.putFloat("Dy", Mth.clamp(dy, -1F, 1F));
        float turn = Mth.wrapDegrees(heldTurn * Mth.RAD_TO_DEG) / 90F;
        args.putFloat("Turn", Mth.clamp(turn, -1F, 1F));
        command(WorkCommand.SEAT, args);
        heldTray = -1;
        heldTurn = 0F;
        sound(ModSounds.BENCH_SEAT.get(), "piece", 0.6F, 1.25F);
    }

    private void click(JewelersBenchBlockEntity bench, String node, int button) {
        if (node.startsWith("tray_")) {
            int i = Integer.parseInt(node.substring(5));
            if (tool == BenchPainter.Tool.TWEEZERS) {
                if (heldTray == i) heldTray = -1;
                else if (!CutGemItem.data(bench.tray(i)).isEmpty()) {
                    heldTray = i;
                    sound(ModSounds.BENCH_SEAT.get(), "tray_" + i, 0.35F, 1.8F);
                }
            } else if (!bench.tray(i).isEmpty()) {
                take(BenchOperations.TRAY, i);
            }
        } else if (node.startsWith("supply_") && !jarSlots()) {
            // Jars that are no library slots (a Diegesis without native storage): a click takes the stack back.
            take(BenchOperations.SUPPLY, Integer.parseInt(node.substring(7)));
        } else if (node.startsWith("tool_")) {
            pick(BenchPainter.Tool.valueOf(node.substring(5).toUpperCase(java.util.Locale.ROOT)));
        } else switch (node) {
            case "dock" -> {
                if (bench.dock().isEmpty()) cue(Component.translatable("workstation.immersive_jewelry.cue.dock_pliers"));
                else if (button == 1) take(BenchOperations.DOCK, 0);
                else pick(BenchPainter.Tool.PLIERS);
            }
            case "folio" -> openFolio();
            case "drawer" -> {
                if (button == 1) openDrawerList();
                else toggleDrawer();
            }
            case "pouch" -> pack();
            case "loupe" -> inspect(bench);
            case "piece" -> {
                if (tool == BenchPainter.Tool.NONE && shown() == null && bench.clamp().getItem() instanceof JewelryItem) take(BenchOperations.CLAMP, 0);
            }
            default -> {
            }
        }
    }

    /** Packs committed work, naming the clamp's generation it saw. */
    void pack() {
        JewelersBenchBlockEntity bench = bench();
        CompoundTag args = new CompoundTag();
        if (bench != null) args.putInt("Gen", bench.generation(BenchOperations.CLAMP, 0));
        command(WorkCommand.PACK, args);
    }

    private void take(String target, int index) {
        JewelersBenchBlockEntity bench = bench();
        CompoundTag args = new CompoundTag();
        args.putString("Target", target);
        args.putInt("Index", index);
        if (bench != null) args.putInt("Gen", bench.generation(target, index));
        command(WorkCommand.TAKE, args);
    }

    private void pick(BenchPainter.Tool picked) {
        pressing = false;
        travel = 0F;
        // Taking up a finishing tool when every stone is held is the worker's word that the setting is done.
        BenchWork work = shown();
        if (picked.ordinal() >= BenchPainter.Tool.CLOTH.ordinal() && work != null && work.kind() == BenchWork.Kind.ASSEMBLE
                && work.stage() == BenchWork.Stage.SETTING && work.setting() != null && work.setting().allSecured()) {
            command(WorkCommand.FINISH_WORK);
        }
        if (tool == picked) {
            tool = BenchPainter.Tool.NONE;
            heldTray = -1;
            sound(ModSounds.BENCH_PLACE.get(), "piece", 0.3F, 1.6F);
            return;
        }
        tool = picked;
        heldTray = -1;
        sound(ModSounds.BENCH_PLACE.get(), "piece", 0.3F, 1.4F);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!operating() || stack.isEmpty()) return false;
        Target target = Target.of(node, jarSlots());
        if (target == null) return false;
        CompoundTag args = new CompoundTag();
        args.putInt("From", -1);
        args.putString("Target", target.group());
        args.putInt("Index", target.index());
        command(WorkCommand.PLACE, args);
        return true;
    }

    /**
     * Shift-click on an inventory stack: where the bench's work area takes it (the tray, a supply jar, the clamp, the dock),
     * as before; a stack the work area has no place for goes into the open staging drawer, but only when the drawer shows room
     * for it ({@code hasRoomFor}, a look at the surfaces, not a promise: the server moves only what fits and leaves the rest
     * where it was). The library's own shift-click never runs here, so one click is one transfer.
     */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!operating()) return false;
        JewelersBenchBlockEntity bench = bench();
        if (bench != null && !workAreaTakes(bench, stack) && BenchOperations.isStageable(stack)) {
            if (!bench.drawerOpen() || drawer < 0.98F) {
                cue(Component.translatable("workstation.immersive_jewelry.cue.open_drawer", key(WorkstationKeys.DRAWER)));
            } else if (!roomFor(stack)) {
                cue(Component.translatable("workstation.immersive_jewelry.cue.drawer_no_room"));
            } else {
                CompoundTag args = new CompoundTag();
                args.putInt("From", slot);
                args.putInt("Count", 0);
                command(WorkCommand.STAGE_IN, args);
            }
            return true;
        }
        CompoundTag args = new CompoundTag();
        args.putInt("From", slot);
        args.putString("Target", "");
        args.putInt("Index", -1);
        command(WorkCommand.PLACE, args);
        return true;
    }

    /**
     * Whether the drawer shows room for one more stack: the library's look at its surfaces, when the drawer is physical and the
     * library can be asked; otherwise yes (the drawer is a list of four places, and the server moves only what fits).
     */
    private boolean roomFor(ItemStack stack) {
        if (!physicalDrawer() || !DiegesisCompat.has(DiegesisCompat.Feature.ROOM_QUERY)) return true;
        boolean[] room = {true};
        DiegesisCompat.run(DiegesisCompat.Feature.ROOM_QUERY, "the drawer's room query", () -> room[0] = view.hasRoomFor(stack));
        return room[0];
    }

    /** Whether any place of the work area would take the stack now (what a shift-click there would do). */
    private static boolean workAreaTakes(JewelersBenchBlockEntity bench, ItemStack stack) {
        for (String target : new String[]{BenchOperations.CLAMP, BenchOperations.DOCK, BenchOperations.TRAY, BenchOperations.SUPPLY}) {
            if (BenchOperations.accepts(bench, stack, target, -1)) return true;
        }
        return false;
    }

    /**
     * The drawer asks this for every stack each frame. Supplies are matched by ingredient (the clamped piece's repair metal
     * among them), so the answer is kept per item until the bench changes; a stack with NBT is always asked afresh.
     */
    @Override
    protected boolean usable(ItemStack stack) {
        JewelersBenchBlockEntity bench = bench();
        int version = bench == null ? -1 : bench.clientVersion();
        if (version != usableVersion) {
            usableItems.clear();
            usableVersion = version;
        }
        if (stack.hasTag()) return BenchOperations.isWorkItem(stack, bench);
        return usableItems.computeIfAbsent(stack.getItem(), item -> BenchOperations.isWorkItem(stack, bench));
    }

    @Override
    protected ItemStack shownStack(String node) {
        JewelersBenchBlockEntity bench = bench();
        Target target = Target.of(node, jarSlots());
        if (bench == null || target == null) return ItemStack.EMPTY;
        return switch (target.group()) {
            case BenchOperations.TRAY -> bench.tray(target.index());
            case BenchOperations.SUPPLY -> bench.supply(target.index());
            case BenchOperations.DOCK -> bench.dock();
            default -> bench.clamp();
        };
    }

    /**
     * A custody target ({@link BenchOperations} group and position) that a placement node of the scene stands for. The supply
     * jars are one only when they are no library slots ({@code jarSlots} false); as library slots, the library's click is their
     * only transfer.
     */
    record Target(String group, int index) {
        @Nullable
        static Target of(String node, boolean jarSlots) {
            if (node.startsWith("tray_")) return new Target(BenchOperations.TRAY, Integer.parseInt(node.substring(5)));
            if (!jarSlots && node.startsWith("supply_")) return new Target(BenchOperations.SUPPLY, Integer.parseInt(node.substring(7)));
            if (node.equals("dock")) return new Target(BenchOperations.DOCK, 0);
            if (node.equals("piece")) return new Target(BenchOperations.CLAMP, 0);
            return null;
        }
    }

    /** Where the carried stack would go if it were let go now: the hovered placement node, when the bench takes it there. */
    @Nullable
    Target placement(JewelersBenchBlockEntity bench) {
        ItemStack stack = carried();
        if (stack.isEmpty() || hovered == null) return null;
        Target target = Target.of(hovered, jarSlots());
        return target != null && BenchOperations.accepts(bench, stack, target.group(), target.index()) ? target : null;
    }

    // ------------------------------------------------------------------------------------------------ folio and loupe

    void openFolio() {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return;
        releaseAll();
        drawerList = null;
        folio = new FolioOverlay(this, () -> folio = null);
        view.showOverlay(folio);
    }

    void closeFolio() {
        if (folio != null) view.showOverlay(null);
        folio = null;
    }

    void send(WorkCommand command, CompoundTag args) {
        command(command, args);
    }


    private void inspect(JewelersBenchBlockEntity bench) {
        view.glance("loupe");
        folio = null;
        drawerList = null;
        inspecting = true;
        view.showOverlay(new InspectionOverlay(observations(bench), () -> {
            inspecting = false;
            view.glanceBack();
        }));
    }

    private boolean inspecting;

    @Override
    boolean overlayOpen() {
        return folio != null || drawerList != null || inspecting;
    }

    @Override
    boolean textInputActive() {
        return folio != null && folio.typing();
    }

    @Override
    boolean overlayKey(int key, int scan, int modifiers, boolean repeat) {
        if (!operating()) return false;
        if (folio != null) return folio.key(key, scan, modifiers, repeat);
        if (drawerList != null) return drawerList.key(key, scan, repeat);
        // The loupe's notes: its own key puts them away again; the library closes them on Escape.
        if (inspecting && !repeat && is(WorkstationKeys.INSPECT, key, scan)) {
            view.showOverlay(null);
            return true;
        }
        return false;
    }

    @Override
    boolean overlayChar(int codePoint, int modifiers) {
        return folio != null && folio.typed(codePoint, modifiers);
    }

    private List<Component> observations(JewelersBenchBlockEntity bench) {
        List<Component> out = new ArrayList<>();
        BenchWork work = shown();
        JewelryData finished = JewelryData.get(bench.clamp()).orElse(null);
        SettingSim sim = work != null ? work.setting() : null;
        if (sim != null) {
            for (int s = 0; s < sim.params().sockets(); s++) {
                if (!sim.seated(s)) {
                    out.add(Component.translatable("workstation.immersive_jewelry.loupe.socket_empty", s + 1));
                    continue;
                }
                int loose = 0, bent = 0;
                for (int q = 0; q < sim.params().points(); q++) {
                    float c = sim.closure(s, q);
                    if (c < 1F - sim.params().tolerance()) loose++;
                    else if (c > 1F + sim.params().tolerance()) bent++;
                }
                Component state = loose > 0 ? Component.translatable("workstation.immersive_jewelry.loupe.points_loose", loose)
                        : bent > 0 ? Component.translatable("workstation.immersive_jewelry.loupe.points_bent", bent)
                        : Component.translatable("workstation.immersive_jewelry.loupe.points_secure");
                Component seat = sim.seat(s) < 0.6F ? Component.translatable("workstation.immersive_jewelry.loupe.seat_off")
                        : Component.translatable("workstation.immersive_jewelry.loupe.seat_good");
                Component level = sim.alignment(s) < 0.7F ? Component.translatable("workstation.immersive_jewelry.loupe.tilted")
                        : Component.translatable("workstation.immersive_jewelry.loupe.level");
                out.add(Component.translatable("workstation.immersive_jewelry.loupe.socket", s + 1, state, seat, level));
                if (sim.chipped(s)) out.add(Component.translatable("workstation.immersive_jewelry.loupe.socket_chipped", s + 1).withStyle(ChatFormatting.RED));
            }
        }
        if (work != null && work.surface() != null) {
            out.add(Component.translatable("workstation.immersive_jewelry.loupe.surface", Math.round(work.surface().progress() * 100F),
                    Math.round(work.surface().evenness() * 100F)));
        }
        if (finished != null && work == null) {
            out.add(Component.translatable("workstation.immersive_jewelry.loupe.finished",
                    com.otectus.immersivejewelry.quality.WorkmanshipGrade.of(finished.workmanship()).displayName()));
            for (var defect : finished.defects()) out.add(defect.displayName());
        }
        if (out.isEmpty()) out.add(Component.translatable("workstation.immersive_jewelry.loupe.nothing"));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ keyboard

    private void keys(float dt, JewelersBenchBlockEntity bench) {
        float rate = 0.6F;
        if (tool.ordinal() < BenchPainter.Tool.CLOTH.ordinal()) finishingSweep.reset();
        if (tool == BenchPainter.Tool.PLIERS || tool == BenchPainter.Tool.PUSHER) {
            if (keyLower) travel = Mth.clamp(travel + rate * dt, 0F, 1F);
            if (keyRaise) travel = Mth.clamp(travel - rate * dt, 0F, 1F);
            if (tool == BenchPainter.Tool.PUSHER) {
                if (keyLeft) theta = (theta + 0.9F * dt) % Mth.TWO_PI;
                if (keyRight) theta = (theta - 0.9F * dt + Mth.TWO_PI) % Mth.TWO_PI;
            }
        } else if (tool.ordinal() >= BenchPainter.Tool.CLOTH.ordinal()) {
            boolean moving = keyLeft || keyRight || keyLower || keyRaise;
            if (keyLeft) u = (u + 0.25F * dt) % 1F;
            if (keyRight) u = (u - 0.25F * dt + 1F) % 1F;
            if (keyRaise) v = Mth.clamp(v + 0.5F * dt, 0F, 1F);
            if (keyLower) v = Mth.clamp(v - 0.5F * dt, 0F, 1F);
            if ((keyApply || toggledApply) && !moving && ClientConfig.get(ClientConfig.ASSISTED_SWEEP)) {
                // The crosswise motion has its own phase. Tying it to wrapped u retraces the same narrow band on
                // every lap and can never cover enough of the frame to finish. Ease across the full width instead.
                FinishingSweep.Point next = finishingSweep.step(u, v, dt);
                u = next.u();
                v = next.v();
            } else finishingSweep.reset();
            DesignLayout layout = layout(bench, shown());
            if (layout != null) pointer = surfacePoint(layout, u, v);
        }
    }

    @Nullable
    private static Vector3f surfacePoint(DesignLayout layout, float u, float v) {
        FrameRenderer.Surface s = FrameRenderer.surface(layout.body());
        float a = s.a0() + (s.a1() - s.a0()) * u;
        float r = Mth.lerp(v, s.rIn(), s.rOut());
        return new Vector3f(s.cx() + Mth.cos(a) * r * s.sx(), s.cy() + Mth.sin(a) * r * s.sy(), 0F);
    }

    @Override
    boolean keyPressed(int key, int scan, int modifiers) {
        if (!operating()) return false;
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return false;
        if (is(WorkstationKeys.LOWER, key, scan)) return keyLower = true;
        if (is(WorkstationKeys.RAISE, key, scan)) return keyRaise = true;
        if (is(WorkstationKeys.LEFT, key, scan)) return keyLeft = true;
        if (is(WorkstationKeys.RIGHT, key, scan)) return keyRight = true;
        if (is(WorkstationKeys.APPLY, key, scan)) {
            if (ClientConfig.get(ClientConfig.TOGGLE_TOOL_USE)) toggledApply = !toggledApply;
            else keyApply = true;
            if (toggledApply || keyApply) pry = Screen.hasShiftDown() || (shown() != null && shown().kind() == BenchWork.Kind.REMOVE);
            return true;
        }
        if (is(WorkstationKeys.TOOL, key, scan)) {
            cycleTool(bench);
            return true;
        }
        if (is(WorkstationKeys.NEXT, key, scan) || is(WorkstationKeys.PREVIOUS, key, scan)) {
            cycleTarget(bench, is(WorkstationKeys.NEXT, key, scan) ? 1 : -1);
            return true;
        }
        if (is(WorkstationKeys.TURN, key, scan) && tool == BenchPainter.Tool.TWEEZERS && heldTray >= 0) {
            // Turns the stone in the tweezers; with nothing held the key is free (a recipe viewer's lookup).
            heldTurn = (heldTurn + Mth.PI / 12F) % Mth.TWO_PI;
            return true;
        }
        if (is(WorkstationKeys.CONFIRM, key, scan)) {
            confirm(bench);
            return true;
        }
        if (is(WorkstationKeys.FOLIO, key, scan)) {
            openFolio();
            return true;
        }
        if (is(WorkstationKeys.INSPECT, key, scan)) {
            inspect(bench);
            return true;
        }
        if (is(WorkstationKeys.DRAWER, key, scan)) {
            openDrawerList();
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
        else if (is(WorkstationKeys.APPLY, key, scan)) {
            keyApply = false;
            if (!toggledApply) travel = 0F;
        } else return false;
        return true;
    }

    @Override
    boolean scrolled(@Nullable String node, double amount) {
        if (tool == BenchPainter.Tool.TWEEZERS && heldTray >= 0 && amount != 0) {
            heldTurn = (heldTurn + (amount > 0 ? 1 : -1) * Mth.PI / 12F + Mth.TWO_PI) % Mth.TWO_PI;
            return true;
        }
        return false;
    }

    private void cycleTool(JewelersBenchBlockEntity bench) {
        BenchWork work = shown();
        List<BenchPainter.Tool> tools = new ArrayList<>();
        if (work == null || work.stage() == BenchWork.Stage.PLAN || work.kind() == BenchWork.Kind.REMOVE) tools.add(BenchPainter.Tool.TWEEZERS);
        if (work != null && (work.stage() == BenchWork.Stage.PLAN || work.stage() == BenchWork.Stage.SETTING)) {
            SettingSim sim = work.setting();
            if (sim == null || sim.params().style() == SettingStyle.PRONG) {
                if (!bench.dock().isEmpty()) tools.add(BenchPainter.Tool.PLIERS);
            } else {
                tools.add(BenchPainter.Tool.PUSHER);
            }
        }
        if (work != null && work.kind() == BenchWork.Kind.ASSEMBLE && work.stage() == BenchWork.Stage.SETTING && work.setting() != null
                && work.setting().allSecured()) {
            tools.add(BenchPainter.Tool.CLOTH);
        }
        if (work != null && work.stage() == BenchWork.Stage.FINISHING && work.surface() != null) {
            int id = work.surface().params().mode().tool(work.surface().phase());
            tools.add(id == SurfaceParams.TOOL_CLOTH ? BenchPainter.Tool.CLOTH : id == SurfaceParams.TOOL_BURNISHER ? BenchPainter.Tool.BURNISHER
                    : BenchPainter.Tool.BRUSH);
        }
        tools.add(BenchPainter.Tool.NONE);
        int at = tools.indexOf(tool);
        BenchPainter.Tool next = tools.get(Math.floorMod(at + 1, tools.size()));
        if (next == BenchPainter.Tool.NONE) {
            tool = BenchPainter.Tool.NONE;
            heldTray = -1;
        } else {
            pick(next);
        }
        cue(Component.translatable("workstation.immersive_jewelry.tool." + tool.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void cycleTarget(JewelersBenchBlockEntity bench, int by) {
        BenchWork work = shown();
        DesignLayout layout = layout(bench, work);
        if (layout == null) return;
        int sockets = layout.sockets().size();
        if (tool == BenchPainter.Tool.TWEEZERS) {
            if (heldTray < 0) {
                for (int n = 1; n <= JewelersBenchBlockEntity.TRAY; n++) {
                    int i = Math.floorMod(heldTray + by * n, JewelersBenchBlockEntity.TRAY);
                    if (CutGemItem.data(bench.tray(i)).isPresent()) {
                        heldTray = i;
                        return;
                    }
                }
            } else {
                targetSocket = Math.floorMod(targetSocket + by, sockets);
                pointer = new Vector3f(layout.socket(targetSocket).x(), layout.socket(targetSocket).y(), 0F);
            }
            return;
        }
        int points = work != null && work.setting() != null ? work.setting().params().points() : 4;
        if (tool == BenchPainter.Tool.PLIERS) {
            int flat = targetSocket * points + targetPoint + by;
            flat = Math.floorMod(flat, sockets * points);
            targetSocket = flat / points;
            targetPoint = flat % points;
        } else {
            targetSocket = Math.floorMod(targetSocket + by, sockets);
        }
    }

    private void confirm(JewelersBenchBlockEntity bench) {
        BenchWork work = shown();
        if (tool == BenchPainter.Tool.TWEEZERS && heldTray >= 0) {
            DesignLayout layout = layout(bench, work);
            if (layout != null && targetSocket < layout.sockets().size() && bench.socket(targetSocket).isEmpty()) seat(targetSocket, 0F, 0F);
            return;
        }
        if (work != null && (work.kind() == BenchWork.Kind.RETIGHTEN
                || work.kind() == BenchWork.Kind.ASSEMBLE && work.stage() == BenchWork.Stage.SETTING)) {
            releaseAll();
            command(WorkCommand.FINISH_WORK);
            return;
        }
        if (work != null && work.kind() == BenchWork.Kind.REMOVE && work.setting() != null && work.setting().opened(targetSocket)) {
            CompoundTag args = new CompoundTag();
            args.putInt("Socket", targetSocket);
            command(WorkCommand.LIFT, args);
        }
    }

    @Override
    void releaseAll() {
        pressing = false;
        travel = 0F;
        keyApply = toggledApply = keyLower = keyRaise = keyLeft = keyRight = false;
        pressScene = null;
    }

    @Override
    public void onClose(StationView view) {
        super.onClose(view);
        tool = BenchPainter.Tool.NONE;
        heldTray = -1;
        folio = null;
        drawerList = null;
        inspecting = false;
    }

    // ------------------------------------------------------------------------------------------------ hints

    @Override
    protected Component hint() {
        JewelersBenchBlockEntity bench = bench();
        if (bench == null) return Component.empty();
        BenchWork work = shown();
        ItemStack frame = bench.clamp();
        if (frame.isEmpty()) return Component.translatable("workstation.immersive_jewelry.hint.bench.frame");
        if (work == null) {
            if (frame.getItem() instanceof JewelryItem) {
                return Component.translatable("workstation.immersive_jewelry.hint.bench.finished", key(WorkstationKeys.FOLIO));
            }
            return Component.translatable("workstation.immersive_jewelry.hint.bench.design", key(WorkstationKeys.FOLIO));
        }
        return switch (work.stage()) {
            case PLAN -> {
                int stones = work.setting() != null ? work.setting().params().sockets() : 1;
                if (bench.seatedCount() < stones) {
                    yield tool == BenchPainter.Tool.TWEEZERS
                            ? Component.translatable(heldTray >= 0 ? "workstation.immersive_jewelry.hint.bench.seat" : "workstation.immersive_jewelry.hint.bench.pick",
                            key(WorkstationKeys.CONFIRM))
                            : Component.translatable("workstation.immersive_jewelry.hint.bench.tweezers", key(WorkstationKeys.TOOL));
                }
                yield Component.translatable(work.setting() != null && work.setting().params().style() == SettingStyle.BEZEL
                        ? "workstation.immersive_jewelry.hint.bench.close_bezel" : "workstation.immersive_jewelry.hint.bench.close_prong",
                        key(WorkstationKeys.TOOL));
            }
            case SETTING -> {
                if (work.kind() == BenchWork.Kind.REMOVE) yield Component.translatable("workstation.immersive_jewelry.hint.bench.remove");
                if (work.kind() == BenchWork.Kind.RETIGHTEN) yield Component.translatable("workstation.immersive_jewelry.hint.bench.retighten",
                        key(WorkstationKeys.CONFIRM));
                if (work.setting() != null && work.setting().allSecured() && !work.setting().gripping()) {
                    yield Component.translatable("workstation.immersive_jewelry.hint.bench.secured", key(WorkstationKeys.CONFIRM));
                }
                yield Component.translatable(work.setting() != null && work.setting().params().style() == SettingStyle.BEZEL
                        ? "workstation.immersive_jewelry.hint.bench.bezel_work" : "workstation.immersive_jewelry.hint.bench.prong_work",
                        key(WorkstationKeys.APPLY));
            }
            case FINISHING -> {
                SurfaceSim surface = work.surface();
                if (surface == null) yield Component.empty();
                String mode = surface.params().mode().name().toLowerCase(java.util.Locale.ROOT);
                yield Component.translatable("workstation.immersive_jewelry.hint.bench.finish_" + mode + (surface.phase() > 0 ? "_wipe" : ""),
                        Math.round(surface.progress() * 100F));
            }
        };
    }

    JewelryDefinitions definitions() {
        return JewelryDefinitions.client();
    }

    @Nullable
    BenchWork work() {
        return shown();
    }
}
