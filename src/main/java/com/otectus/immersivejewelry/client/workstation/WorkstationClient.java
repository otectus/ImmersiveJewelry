package com.otectus.immersivejewelry.client.workstation;

import com.otectus.immersivejewelry.client.workstation.render.GemGeometry;
import com.otectus.immersivejewelry.client.workstation.render.Hands;
import com.otectus.immersivejewelry.config.ClientConfig;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.WorkCommandPacket;
import com.otectus.immersivejewelry.network.packet.WorkPrefsPacket;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.station.JewelryStations;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The client side of the workstations: behaviours and scenes registered with Diegesis, the operator lease this
 * client holds, the keyboard frontend while a station is focused, and the safety net on every way out (window focus
 * lost, another screen, leaving the world): held actions are let go and nothing keeps pressing.
 *
 * <p>Since Diegesis 1.0.0-beta.10 a screen another mod registers as a child screen (a recipe viewer's page) can open
 * over a station without ending it. The station is still focused and the lease still held then, so keys reach the
 * station only while its own screen is shown, and everything held is let go while anything else covers it. A key the
 * station has no use for at that moment may look up the stack under the cursor in a recipe viewer ({@link RecipeLookup}).</p>
 */
public final class WorkstationClient {
    @Nullable
    private static BlockPos leasePos;
    private static int leaseToken;
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> KNOWN = new java.util.HashSet<>();
    private static boolean requireSketches = true;

    public static void init(IEventBus modBus) {
        modBus.addListener(WorkstationClient::onClientSetup);
        modBus.addListener(WorkstationClient::onReloadListeners);
        modBus.addListener(WorkstationKeys::register);
        modBus.addListener(WorkstationClient::onConfig);
        MinecraftForge.EVENT_BUS.addListener(WorkstationClient::onKey);
        MinecraftForge.EVENT_BUS.addListener(WorkstationClient::onKeyReleased);
        MinecraftForge.EVENT_BUS.addListener(WorkstationClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(WorkstationClient::onLogin);
        MinecraftForge.EVENT_BUS.addListener(WorkstationClient::onLogout);
        MinecraftForge.EVENT_BUS.addListener(Hands::renderStage);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            DiegesisClient.registerBehavior(JewelryStations.WHEEL_ID, WheelBehavior::new);
            DiegesisClient.registerBehavior(JewelryStations.BENCH_ID, BenchBehavior::new);
            WorkEffects.register();
        });
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SceneLoader.INSTANCE);
        event.registerReloadListener((barrier, manager, prep, apply, prepExec, applyExec) ->
                barrier.wait(null).thenRunAsync(() -> {
                    GemGeometry.clear();
                    Hands.clear();
                }, applyExec));
    }

    private static void onConfig(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            SceneLoader.INSTANCE.refresh();
            sendPreferences();
        }
    }

    // ------------------------------------------------------------------------------------------------ the lease

    /** The server granted ({@code token} not 0) or ended this client's lease at {@code pos}. */
    public static void onLease(BlockPos pos, int token) {
        if (token == 0) {
            if (pos.equals(leasePos)) {
                leasePos = null;
                leaseToken = 0;
                behaviour(pos).ifPresent(WorkstationBehavior::onLeaseEnded);
            }
            return;
        }
        leasePos = pos.immutable();
        leaseToken = token;
        behaviour(pos).ifPresent(WorkstationBehavior::onLeaseGranted);
    }

    public static void onKnownDesigns(java.util.List<net.minecraft.resources.ResourceLocation> learned, boolean require) {
        KNOWN.clear();
        KNOWN.addAll(learned);
        requireSketches = require;
    }

    /** Whether this player may use a design, as far as the client knows (the server checks again). */
    public static boolean knows(com.otectus.immersivejewelry.recipe.JewelryDesignRecipe design) {
        Minecraft mc = Minecraft.getInstance();
        if (!design.sketch() || !requireSketches || (mc.player != null && mc.player.getAbilities().instabuild)) return true;
        return KNOWN.contains(design.getId());
    }

    /** The token of this client's lease at {@code pos}, or 0. */
    public static int token(BlockPos pos) {
        return pos.equals(leasePos) ? leaseToken : 0;
    }

    public static boolean operates(BlockPos pos) {
        return token(pos) != 0;
    }

    static void command(BlockPos pos, long workId, WorkCommand command, CompoundTag args) {
        int token = token(pos);
        if (token != 0) ModNetwork.CHANNEL.sendToServer(new WorkCommandPacket(pos, token, workId, command, args));
    }

    static void sendPreferences() {
        if (Minecraft.getInstance().getConnection() == null) return;
        ModNetwork.CHANNEL.sendToServer(new WorkPrefsPacket(ClientConfig.get(ClientConfig.LARGER_TARGETS),
                ClientConfig.get(ClientConfig.SLOWER_MINIGAMES)));
    }

    // ------------------------------------------------------------------------------------------------ focus

    /**
     * Test seam (the client smoke test): the Diegesis behaviour and view of the Jewelry station the local player works at.
     * Events given to it take the same path as the library's own.
     */
    public static Optional<StationView> focusedView() {
        return focused().map(b -> b.view);
    }

    /** Test seam: the behaviour of the station shown at {@code pos}, as Diegesis sees it. */
    public static Optional<com.pockyl.diegesis.api.client.StationBehavior> behaviourAt(BlockPos pos) {
        return behaviour(pos).map(b -> b);
    }

    /** The behaviour of the Jewelry station the local player works at, if any. */
    static Optional<WorkstationBehavior> focused() {
        if (!DiegesisClient.isFocused()) return Optional.empty();
        return DiegesisClient.focusedStation().flatMap(WorkstationClient::behaviourOf);
    }

    private static Optional<WorkstationBehavior> behaviour(BlockPos pos) {
        return DiegesisClient.station(pos).flatMap(WorkstationClient::behaviourOf);
    }

    private static Optional<WorkstationBehavior> behaviourOf(StationView view) {
        return Optional.ofNullable(WorkstationBehavior.of(view));
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!DiegesisClient.isStationScreen(event.getScreen())) return;
        focused().ifPresent(b -> {
            if (b.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
                event.setCanceled(true);
                return;
            }
            // The station's keys keep their meaning; one it has no use for now may look up what is under the cursor.
            ItemStack under = b.stackUnderCursor();
            if (!under.isEmpty() && recipeLookup.lookUp(event.getKeyCode(), event.getScanCode(), under)) event.setCanceled(true);
        });
    }

    private static void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        // Under a child screen nothing is held any more (the tick lets go), and its own keys stay its own.
        if (!DiegesisClient.isStationScreen(event.getScreen())) return;
        focused().ifPresent(b -> {
            if (b.keyReleased(event.getKeyCode(), event.getScanCode())) event.setCanceled(true);
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Losing the window, or a child screen over the station: every held action is let go at once.
        if (!mc.isWindowActive() || !DiegesisClient.isStationScreen(mc.screen)) focused().ifPresent(WorkstationBehavior::releaseAll);
    }

    // ------------------------------------------------------------------------------------------------ recipe viewers

    /** A recipe viewer's lookup at a station: show what a stack is made from or used for. */
    @FunctionalInterface
    public interface RecipeLookup {
        /** Whether the key is one of the viewer's lookup keys; if so, the viewer shows its page for {@code stack}. */
        boolean lookUp(int key, int scan, ItemStack stack);
    }

    private static RecipeLookup recipeLookup = (key, scan, stack) -> false;

    /** Set by the JEI plugin while JEI runs; null when it stops. */
    public static void setRecipeLookup(@Nullable RecipeLookup lookup) {
        recipeLookup = lookup != null ? lookup : (key, scan, stack) -> false;
    }

    /** The block entity of the Jewelry station the local player works at now, if any. */
    public static Optional<com.otectus.immersivejewelry.blockentity.StationBlockEntity> focusedStation() {
        return focused().flatMap(b -> b.view.level().getBlockEntity(b.pos) instanceof com.otectus.immersivejewelry.blockentity.StationBlockEntity
                station ? Optional.of(station) : Optional.empty());
    }

    /** Lets go of every handle, key and tool at the focused station at once (help, a book over the station). */
    public static void releaseHeld() {
        focused().ifPresent(b -> {
            b.releaseAll();
            b.flush(true);
        });
    }

    /** Whether the local player works at a Jewelry station now (a child screen may be open over it). */
    public static boolean atStation() {
        return focused().isPresent();
    }

    /**
     * The stack under the cursor at a Jewelry station whose screen is shown: a stack of the inventory drawer
     * ({@link DiegesisClient#hoveredStack}), or the real stack the station shows at the hovered node (a stone in the tray,
     * the piece in the clamp, the stone on the quill). Empty elsewhere. This is what a {@link RecipeLookup} is given.
     */
    public static ItemStack stackUnderCursor() {
        if (!DiegesisClient.isStationScreen(Minecraft.getInstance().screen)) return ItemStack.EMPTY;
        return focused().map(WorkstationBehavior::stackUnderCursor).orElse(ItemStack.EMPTY);
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        sendPreferences();
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        leasePos = null;
        leaseToken = 0;
        KNOWN.clear();
        GemGeometry.clear();
        Hands.clear();
    }

    private WorkstationClient() {}
}
