package com.otectus.immersivejewelry.client.workstation.render;

import com.mojang.math.Axis;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.api.jewelry.SettingDefect;
import com.otectus.immersivejewelry.client.JewelryColors;
import com.otectus.immersivejewelry.client.presentation.geo.JewelryRigs;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.JewelryBlankItem;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.recipe.SettingStyle;
import com.otectus.immersivejewelry.workstation.BenchOperations;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import com.otectus.immersivejewelry.workstation.sim.SettingSim;
import com.otectus.immersivejewelry.workstation.sim.SurfaceParams;
import com.otectus.immersivejewelry.workstation.sim.SurfaceSim;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Jeweler's Bench's moving parts in scene pixels: the padded clamp with the support for the piece's form, the piece
 * itself (frame, cups, prongs or bezel rims, stones and finish), the numbered stone tray, the supply jars, the pliers
 * dock, the rack of built-in tools, the folio and the pouch; and the tool in the worker's hand at its working pose. The
 * piece stands upright in the clamp facing the worker; its own frame is mapped into the scene by {@link #toScene}.
 */
public final class BenchPainter {
    public static final Vector3f PIECE = new Vector3f(8.0F, 17.0F, 7.6F);
    public static final float PIECE_SCALE = 0.5F;
    public static final ResourceLocation FELT = ImmersiveJewelry.id("block/bench_felt");
    public static final ResourceLocation LEATHER = ImmersiveJewelry.id("block/bench_leather");
    public static final ResourceLocation BRASS = ImmersiveJewelry.id("block/bench_brass");
    public static final ResourceLocation STEEL = ImmersiveJewelry.id("block/bench_steel");
    public static final ResourceLocation WOOD = ImmersiveJewelry.id("block/bench_wood");
    public static final Vector3f FOLIO = new Vector3f(13.9F, 14.3F, 7.2F);
    public static final Vector3f POUCH = new Vector3f(1.8F, 14.5F, 11.0F);
    public static final Vector3f DOCK = new Vector3f(2.0F, 14.2F, 6.4F);

    /** The tools on the bench. */
    public enum Tool {
        NONE, TWEEZERS, PLIERS, PUSHER, CLOTH, BURNISHER, BRUSH;

        /** Where the tool rests on the rack (pliers rest in the dock). */
        public Vector3f rest() {
            return switch (this) {
                case TWEEZERS -> new Vector3f(11.8F, 14.6F, 13.6F);
                case PUSHER -> new Vector3f(10.2F, 14.6F, 13.6F);
                case BURNISHER -> new Vector3f(8.6F, 14.6F, 13.6F);
                case BRUSH -> new Vector3f(7.0F, 14.6F, 13.6F);
                case CLOTH -> new Vector3f(5.2F, 14.4F, 13.4F);
                case PLIERS -> new Vector3f(DOCK);
                case NONE -> new Vector3f(PIECE);
            };
        }
    }

    /** Where tray position {@code i} is. */
    public static Vector3f tray(int i) {
        return new Vector3f(14.6F - (i % 4) * 1.2F, 14.3F, 1.6F + (i / 4) * 1.4F);
    }

    public static Vector3f supply(int i) {
        return new Vector3f(1.6F + i * 1.35F, 14.2F, 1.9F);
    }

    /** A point of the piece's own frame (x right, y up, z towards the worker) in scene pixels. */
    public static Vector3f toScene(float x, float y, float z) {
        // The piece faces the worker at -Z: a half turn about y, then the clamp position and scale.
        return new Vector3f(-x * PIECE_SCALE + PIECE.x, y * PIECE_SCALE + PIECE.y, -z * PIECE_SCALE + PIECE.z);
    }

    /** A scene point back into the piece's frame (the worker's pointer on the piece's plane). */
    public static Vector3f toPiece(Vector3f scene) {
        return new Vector3f(-(scene.x - PIECE.x) / PIECE_SCALE, (scene.y - PIECE.y) / PIECE_SCALE, -(scene.z - PIECE.z) / PIECE_SCALE);
    }

    /** Everything the bench draws this frame. */
    public record State(ItemStack clamp, ItemStack[] sockets, ItemStack[] tray, ItemStack[] supplies, ItemStack dock, @Nullable BenchWork work,
                        @Nullable DesignLayout layout, SettingStyle style, int points, float tolerance, Tool tool, @Nullable Vector3f toolAt,
                        float travel, boolean contact, int targetSocket, int targetPoint, int heldTray, float heldTurn, @Nullable Vector3f guide,
                        boolean highContrast, boolean hands, float relax, float tilt) {}

    public static void draw(Geo geo, State s, float time) {
        JewelryDefinitions defs = JewelryDefinitions.client();
        clamp(geo, s);
        piece(geo, s, defs);
        tray(geo, s, defs);
        supplies(geo, s);
        dock(geo, s);
        rack(geo, s);
        folio(geo);
        pouch(geo, s);
        held(geo, s, defs);
    }

    // ------------------------------------------------------------------------------------------------ the clamp and supports

    private static void clamp(Geo geo, State s) {
        // The vise is the bench's GeckoLib rig (jaws closing on the piece, the screw turning); plain parts only as fallback.
        if (JewelryRigs.vise(geo, s.clamp().isEmpty() ? "" : formPath(s.clamp()), s.relax())) {
            geo.plain();
            return;
        }
        // One watchmaker's vise, with small padded inserts suited to the frame. The opening of a ring stays open.
        geo.texture(STEEL);
        geo.box(5.8F, 14.0F, 7.0F, 10.2F, 14.35F, 9.0F, 0xFFB8C0C4);
        geo.box(6.5F, 14.35F, 7.45F, 9.5F, 14.65F, 8.65F, 0xFFCFD3D2);
        geo.rod(new Vector3f(5.5F, 14.55F, 8.1F), new Vector3f(10.5F, 14.55F, 8.1F), 0.23F, 0xFFFFFFFF);
        geo.texture(BRASS);
        geo.centred(10.4F, 14.55F, 8.1F, 0.25F, 0.32F, 0.32F, 0xFFD8C6A5);
        geo.rod(new Vector3f(10.55F, 14.05F, 8.1F), new Vector3f(10.55F, 15.05F, 8.1F), 0.15F, 0xFFFFFFFF);
        if (s.clamp().isEmpty()) { geo.plain(); return; }
        String form = formPath(s.clamp());
        float open = s.relax() * 0.08F;
        if (form.equals("circlet")) {
            // Narrow rear posts catch the two ends of the curved band, leaving its face and settings accessible.
            for (int side : new int[]{-1, 1}) {
                float x = PIECE.x + side * 2.05F;
                geo.texture(STEEL);
                geo.box(x - 0.2F, 14.35F, 8.15F, x + 0.2F, 17.75F, 8.5F, 0xFFB8C0C4);
                geo.texture(FELT);
                geo.box(x - 0.38F, 17.35F, 7.5F, x + 0.38F, 17.8F, 8.25F, 0xFFB2C2AD);
            }
        } else if (form.equals("bracelet")) {
            // Support the side arcs; a central jaw would cover the bracelet's low principal setting.
            for (int side : new int[]{-1, 1}) {
                float x = PIECE.x + side * 1.55F;
                geo.texture(STEEL);
                geo.box(x - 0.18F, 14.35F, 7.95F, x + 0.18F, 15.7F, 8.35F, 0xFFB8C0C4);
                geo.texture(FELT);
                geo.box(x - 0.33F, 15.55F, 7.5F, x + 0.33F, 15.82F, 8.2F, 0xFFB2C2AD);
            }
        } else {
            float y = form.equals("ring") ? 14.85F : 15.05F;
            // Front and rear jaws meet the bottom edge, rather than filling the space behind the whole piece.
            geo.texture(STEEL);
            geo.box(7.0F, 14.6F, 7.15F - open, 9.0F, y + 0.12F, 7.45F - open, 0xFFCFD3D2);
            geo.box(7.0F, 14.6F, 7.9F + open, 9.0F, y + 0.12F, 8.45F + open, 0xFFB8C0C4);
            geo.texture(FELT);
            geo.box(7.05F, y - 0.2F, 7.37F - open, 8.95F, y + 0.22F, 7.55F - open, 0xFFB2C2AD);
            geo.box(7.05F, y - 0.2F, 7.75F + open, 8.95F, y + 0.22F, 8.02F + open, 0xFFB2C2AD);
        }
        geo.plain();
    }

    private static String formPath(ItemStack stack) {
        if (stack.getItem() instanceof JewelryBlankItem blank) return blank.form().getPath();
        if (stack.getItem() instanceof JewelryItem item) return item.form().getPath();
        return "";
    }

    // ------------------------------------------------------------------------------------------------ the piece

    private static void piece(Geo geo, State s, JewelryDefinitions defs) {
        ItemStack frame = s.clamp();
        if (frame.isEmpty() || s.layout() == null) return;
        MetalRef metal = null;
        FinishDefinition finish = null;
        JewelryData finished = JewelryData.get(frame).orElse(null);
        if (finished != null) {
            metal = finished.metal();
            finish = defs.finish(finished.finish());
        } else {
            BlankData blank = BlankData.get(frame).orElse(null);
            if (blank != null) metal = blank.metal();
        }
        int metalColor = metal != null ? JewelryColors.metalColor(defs, metal) : 0xB8BCC0;
        BenchWork work = s.work();
        SurfaceSim surface = work != null ? work.surface() : null;
        FinishDefinition shownFinish = finish;
        if (work != null && work.finish() != null) shownFinish = defs.finish(work.finish());
        int recess = JewelryColors.recess(metalColor, shownFinish);
        int highlight = JewelryColors.highlight(metalColor, shownFinish);
        SurfaceParams.Mode finishedMode = finished != null && surface == null && finish != null
                ? SurfaceParams.Mode.forFinish(finish.visualProfile()) : null;
        float wear = finished != null ? (1F - finished.integrity()) * 0.6F : 0F;
        FrameRenderer.Look look = new FrameRenderer.Look(metalColor, recess, highlight, surface, finishedMode, wear);

        geo.pose().pushPose();
        geo.pose().translate(PIECE.x, PIECE.y, PIECE.z);
        geo.pose().mulPose(Axis.YP.rotationDegrees(180F));
        if (s.tilt() != 0F) geo.pose().mulPose(Axis.XP.rotationDegrees(s.tilt()));
        geo.pose().scale(PIECE_SCALE, PIECE_SCALE, PIECE_SCALE);
        FrameRenderer.body(geo, s.layout(), look);
        DesignLayout layout = s.layout();
        SettingSim sim = work != null ? work.setting() : null;
        int sockets = layout.sockets().size();
        for (int i = 0; i < sockets; i++) {
            GemData stone = stoneAt(s, finished, work, i);
            // An empty cup still shows its prongs open (or its rim) where the stone will go.
            boolean seated = stone != null;
            int index = i;
            FrameRenderer.Points points = new FrameRenderer.Points() {
                @Override
                public float closure(int socket, int point) {
                    if (sim != null && socket < sim.params().sockets() && point < sim.params().points()) return sim.closure(socket, point);
                    if (finished != null) return finished.defects().contains(SettingDefect.LOOSE) && point == 1 ? 0.8F : 1F;
                    return 0F;
                }

                @Override
                public boolean targeted(int socket, int point) {
                    return socket == s.targetSocket() && point == s.targetPoint() && (s.tool() == Tool.PLIERS || s.tool() == Tool.PUSHER);
                }
            };
            FrameRenderer.setting(geo, layout.socket(i), index, s.style(), s.points(), s.tolerance(), points, look, seated, s.highContrast());
            if (stone != null) stone(geo, layout.socket(i), stone, defs, sim, i);
        }
        geo.pose().popPose();
    }

    @Nullable
    private static GemData stoneAt(State s, @Nullable JewelryData finished, @Nullable BenchWork work, int i) {
        if (finished != null) {
            if (work != null && work.kind() == BenchWork.Kind.REMOVE && work.lifted(i)) return null;
            if (work != null && work.kind() == BenchWork.Kind.REMOVE && i < work.original().size()) return work.original().get(i);
            return i < finished.gems().size() ? finished.gems().get(i) : null;
        }
        return i < s.sockets().length ? CutGemItem.data(s.sockets()[i]).orElse(null) : null;
    }

    /** A stone in its cup, crown to the worker; a resting, poorly seated stone sits tilted until it is held. */
    private static void stone(Geo geo, DesignLayout.Socket socket, GemData data, JewelryDefinitions defs, @Nullable SettingSim sim, int i) {
        CutDefinition cut = defs.cut(data.cut());
        GemGeometry.Profile profile = cut != null ? GemGeometry.Profile.of(cut.visualProfile()) : GemGeometry.Profile.ROUND;
        GemDamage damage = sim != null && i < sim.params().sockets() && sim.chipped(i) ? data.damage().worse() : data.damage();
        Polyhedron shape = GemGeometry.finished(profile, data.gem().hashCode() * 31L + Float.floatToIntBits(data.originalSize()),
                cut != null ? cut.facets() : 6, GemRenderer.damageMask(damage));
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        float tilt = 0F;
        if (sim != null && i < sim.params().sockets()) {
            float seat = sim.seat(i);
            boolean held = !sim.untouched(i);
            if (!held && seat > 0F) tilt = (1F - seat) * 28F;
            else if (held) tilt = (1F - sim.alignment(i)) * 12F;
        }
        geo.pose().pushPose();
        geo.pose().translate(socket.x(), socket.y(), 0.05F);
        geo.pose().mulPose(Axis.ZP.rotation(socket.turn()));
        geo.pose().mulPose(Axis.XP.rotationDegrees(90F + tilt));
        GemRenderer.draw(geo, shape, socket.radius() / 1.05F, JewelryColors.gemBody(defs, data), gem.opaque(),
                profile == GemGeometry.Profile.CABOCHON, data.cutQuality(), GemRenderer.damage(damage));
        geo.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------------ the bench's things

    private static void tray(Geo geo, State s, JewelryDefinitions defs) {
        geo.texture(WOOD);
        geo.box(10.2F, 14.0F, 0.8F, 15.4F, 14.3F, 4.4F, 0xFFFFFFFF);
        for (int i = 0; i < s.tray().length; i++) {
            Vector3f at = tray(i);
            geo.texture(FELT);
            geo.box(at.x - 0.5F, 14.25F, at.z - 0.5F, at.x + 0.5F, 14.32F, at.z + 0.5F, 0xFFFFFFFF);
            if (i == s.heldTray() && s.tool() == Tool.TWEEZERS) continue;
            GemData data = CutGemItem.data(s.tray()[i]).orElse(null);
            if (data == null) continue;
            looseStone(geo, at.add(0F, 0.55F, 0F), data, defs, 0.5F, 0F);
        }
        geo.plain();
    }

    /** A loose cut stone standing crown up. */
    static void looseStone(Geo geo, Vector3f at, GemData data, JewelryDefinitions defs, float size, float turn) {
        CutDefinition cut = defs.cut(data.cut());
        GemGeometry.Profile profile = cut != null ? GemGeometry.Profile.of(cut.visualProfile()) : GemGeometry.Profile.ROUND;
        Polyhedron shape = GemGeometry.finished(profile, data.gem().hashCode() * 31L + Float.floatToIntBits(data.originalSize()),
                cut != null ? cut.facets() : 6, GemRenderer.damageMask(data.damage()));
        GemDefinition gem = defs.gemOrGeneric(data.gem());
        geo.pose().pushPose();
        geo.pose().translate(at.x, at.y, at.z);
        geo.pose().mulPose(Axis.YP.rotation(turn));
        GemRenderer.draw(geo, shape, size, JewelryColors.gemBody(defs, data), gem.opaque(), profile == GemGeometry.Profile.CABOCHON,
                data.cutQuality(), GemRenderer.damage(data.damage()));
        geo.pose().popPose();
    }

    private static void supplies(Geo geo, State s) {
        for (int i = 0; i < s.supplies().length; i++) {
            Vector3f at = supply(i);
            ItemStack stack = s.supplies()[i];
            geo.glass();
            geo.ring(at.x, at.z, 0.55F, 0.55F, 14.0F, 15.3F, 12, 0x60D8E8F0, false);
            geo.plain();
            geo.cylinder(at.x, at.z, 0.58F, 15.3F, 15.55F, 12, 0xFF8A6A48);
            if (stack.isEmpty()) continue;
            float fill = Math.min(1F, stack.getCount() / 16F);
            int color = supplyColor(stack);
            geo.cylinder(at.x, at.z, 0.48F, 14.05F, 14.1F + 1.1F * fill, 12, color);
        }
    }

    private static int supplyColor(ItemStack stack) {
        String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        if (path.contains("gunpowder")) return 0xFF3A3A3E;
        if (path.contains("compound")) return 0xFFE0C88C;
        if (path.contains("gold")) return 0xFFE6C24A;
        if (path.contains("copper")) return 0xFFC07040;
        if (path.contains("iron")) return 0xFFC8C8C8;
        return 0xFFB0A898;
    }

    private static void dock(Geo geo, State s) {
        geo.texture(LEATHER);
        geo.box(DOCK.x - 1.2F, 14.0F, DOCK.z - 1.6F, DOCK.x + 1.2F, 14.15F, DOCK.z + 1.6F, 0xFFFFFFFF);
        if (s.dock().isEmpty() || s.tool() == Tool.PLIERS) return;
        Tools.pliers(geo, new Vector3f(DOCK.x, 14.4F, DOCK.z), new Vector3f(0F, 0F, 1F), 0.3F, isFine(s.dock()));
    }

    static boolean isFine(ItemStack pliers) {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pliers.getItem()).getPath().startsWith("fine");
    }

    private static void rack(Geo geo, State s) {
        geo.texture(WOOD);
        geo.box(3.0F, 14.0F, 12.6F, 13.2F, 14.4F, 14.8F, 0xFFFFFFFF);
        // Felt channels and brass end stops give each implement a legible home on the wooden rack.
        for (Tool tool : new Tool[]{Tool.TWEEZERS, Tool.PUSHER, Tool.BURNISHER, Tool.BRUSH}) {
            Vector3f rest = tool.rest();
            geo.texture(FELT);
            geo.box(rest.x - 0.42F, 14.405F, 12.65F, rest.x + 0.42F, 14.43F, 14.7F, 0xFFBCC8B8);
            geo.texture(BRASS);
            geo.box(rest.x - 0.44F, 14.4F, 14.65F, rest.x + 0.44F, 14.55F, 14.78F, 0xFFD8C6A5);
        }
        for (Tool tool : new Tool[]{Tool.TWEEZERS, Tool.PUSHER, Tool.BURNISHER, Tool.BRUSH, Tool.CLOTH}) {
            if (tool == s.tool()) continue;
            Tools.resting(geo, tool, tool.rest());
        }
        geo.plain();
    }

    private static void folio(Geo geo) {
        geo.texture(LEATHER);
        geo.box(FOLIO.x - 1.5F, 14.0F, FOLIO.z - 2.0F, FOLIO.x + 1.5F, 14.5F, FOLIO.z + 2.0F, 0xFF8A4A2A);
        geo.plain();
        geo.box(FOLIO.x - 1.35F, 14.1F, FOLIO.z - 1.85F, FOLIO.x + 1.3F, 14.45F, FOLIO.z + 1.85F, 0xFFF0E6D0);
        geo.texture(BRASS);
        geo.box(FOLIO.x - 1.55F, 14.15F, FOLIO.z - 0.25F, FOLIO.x - 1.3F, 14.35F, FOLIO.z + 0.25F, 0xFFFFFFFF);
        geo.plain();
    }

    private static void pouch(Geo geo, State s) {
        geo.texture(LEATHER);
        float full = s.work() != null && s.work().irreversible() ? 1F : 0.6F;
        geo.box(POUCH.x - 0.9F, 14.0F, POUCH.z - 0.7F, POUCH.x + 0.9F, 14.0F + 1.4F * full, POUCH.z + 0.7F, 0xFFFFFFFF);
        geo.plain();
        geo.box(POUCH.x - 0.95F, 14.0F + 1.4F * full, POUCH.z - 0.2F, POUCH.x + 0.95F, 14.1F + 1.4F * full, POUCH.z + 0.2F, 0xFF3A2A1A);
    }

    /** The tool in the worker's hand at its working pose, with the hand and, for tweezers, the stone it carries. */
    private static void held(Geo geo, State s, JewelryDefinitions defs) {
        if (s.tool() == Tool.NONE || s.toolAt() == null) return;
        Vector3f at = s.toolAt();
        Vector3f back = Hands.toolBack(-1.4F, 1.1F, -1.6F);
        switch (s.tool()) {
            case TWEEZERS -> {
                Tools.tweezers(geo, at, back, s.heldTray() >= 0 ? 0.35F : 0.55F);
                if (s.heldTray() >= 0 && s.heldTray() < s.tray().length) {
                    GemData data = CutGemItem.data(s.tray()[s.heldTray()]).orElse(null);
                    if (data != null) {
                        geo.pose().pushPose();
                        geo.pose().translate(at.x, at.y - 0.35F, at.z);
                        geo.pose().mulPose(Axis.XP.rotationDegrees(-75F));
                        looseStone(geo, new Vector3f(), data, defs, 0.5F, s.heldTurn());
                        geo.pose().popPose();
                    }
                }
            }
            case PLIERS -> Tools.pliers(geo, at, back, 0.3F + 0.7F * (1F - s.travel()) * (s.contact() ? 0.6F : 1F), isFine(s.dock()));
            case PUSHER -> Tools.pusher(geo, at, back);
            case BURNISHER -> Tools.burnisher(geo, at, back);
            case BRUSH -> Tools.brush(geo, at, back, s.contact());
            case CLOTH -> Tools.cloth(geo, at, s.contact());
            default -> {
            }
        }
        if (s.hands()) Hands.hold(geo, new Vector3f(at).add(new Vector3f(back).mul(s.tool() == Tool.CLOTH ? 0.4F : 2.2F)), back, true);
        if (s.guide() != null) {
            geo.glass();
            Vector3f g = s.guide();
            int c = s.highContrast() ? 0xC0FFFF00 : 0x90F4D27A;
            geo.pose().pushPose();
            geo.pose().translate(g.x, g.y, g.z);
            geo.pose().mulPose(Axis.XP.rotationDegrees(-90F));
            geo.ring(0F, 0F, 0.9F, 0.9F, -0.03F, 0.03F, 20, c, false);
            geo.pose().popPose();
            geo.plain();
        }
    }

    // ------------------------------------------------------------------------------------------------ placement hint

    /**
     * Where a stack carried over the bench would go if let go now (a Diegesis {@code takes_items} node under it): a light
     * outline round that tray cell, the dock or the vise, or a ring round that jar. Only the operator sees it.
     */
    public static void placeHint(Geo geo, String group, int index, int argb) {
        geo.glass();
        switch (group) {
            case BenchOperations.TRAY -> {
                Vector3f at = tray(index);
                outline(geo, at.x - 0.56F, at.z - 0.56F, at.x + 0.56F, at.z + 0.56F, 14.33F, 0.08F, argb);
            }
            case BenchOperations.SUPPLY -> {
                Vector3f at = supply(index);
                geo.annulus(at.x, at.z, 0.6F, 0.67F, 15.57F, 16, argb);
            }
            case BenchOperations.DOCK -> outline(geo, DOCK.x - 1.3F, DOCK.z - 1.7F, DOCK.x + 1.3F, DOCK.z + 1.7F, 14.16F, 0.12F, argb);
            default -> outline(geo, 6.4F, 7.35F, 9.6F, 8.75F, 14.66F, 0.12F, argb);
        }
        geo.plain();
    }

    /** A flat rectangular outline of width {@code w} just above height {@code y}. */
    private static void outline(Geo geo, float x0, float z0, float x1, float z1, float y, float w, int argb) {
        float y1 = y + 0.04F;
        geo.box(x0, y, z0, x1, y1, z0 + w, argb);
        geo.box(x0, y, z1 - w, x1, y1, z1, argb);
        geo.box(x0, y, z0 + w, x0 + w, y1, z1 - w, argb);
        geo.box(x1 - w, y, z0 + w, x1, y1, z1 - w, argb);
    }

    private BenchPainter() {}
}
