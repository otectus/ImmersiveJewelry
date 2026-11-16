package com.otectus.immersivejewelry.client.presentation.anim;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.StationBlockEntity;
import com.otectus.immersivejewelry.config.ClientConfig;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The jeweler's body on playerAnimator (Forge 1.20.1, {@code 1.0.2-rc1+1.20}); with {@link JewelerLayer} the only code
 * that touches the library. One layer per client player, local and remote, created by the library's per-player factory and
 * stored as that player's associated animation {@link #LAYER}; only this layer is ever changed, other mods' layers never.
 *
 * <p>Every client tick each player is matched to the station they operate (the station's synced operator), and
 * {@link PoseRules} pick the clip from that station's synced state: the same pose for the operator and every watcher, a late
 * watcher included, and never from anyone's raw input. A watcher's own body is never animated by someone else's work. The
 * layer stops at once when the player stops operating (the lease ended: leaving, damage, a teleport, death, logout) and is
 * never kept while riding, swimming, sleeping, gliding or using an item. Root position and movement stay the game's: no
 * clip moves, locks or protects the player.</p>
 *
 * <p>Priority {@value #PRIORITY}: above idle weapon poses and the library's example layers, below combat attack layers
 * (2000), so a real attack takes the arms at once. Missing clips are reported once and leave the body plain.</p>
 */
public final class JewelerPoses {
    public static final ResourceLocation LAYER = ImmersiveJewelry.id("work");
    public static final int PRIORITY = 1100;
    private static final int FADE = 4;
    private static final int RESCAN_TICKS = 10;
    private static boolean registered;
    private static boolean failed;
    private static final Set<ResourceLocation> MISSING = new HashSet<>();
    private static final Map<AbstractClientPlayer, Track> TRACKS = new WeakHashMap<>();

    /** One player's pose state on this client. */
    private static final class Track {
        @Nullable
        BlockPos station;
        int rescan;
        @Nullable
        PoseRules.Snapshot last;
        @Nullable
        JewelerClip playing;
        @Nullable
        JewelerClip oneShot;
        int oneShotLeft;
    }

    /** Registers the per-player layer factory once, during client setup. */
    public static void register() {
        if (registered) return;
        registered = true;
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, JewelerLayer::new);
    }

    @Nullable
    private static JewelerLayer layer(AbstractClientPlayer player) {
        if (failed || !registered) return null;
        try {
            IAnimation animation = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
            return animation instanceof JewelerLayer layer ? layer : null;
        } catch (IllegalArgumentException | LinkageError e) {
            failed = true;
            ImmersiveJewelry.LOGGER.error("playerAnimator is not working; jeweler body animations are off", e);
            return null;
        }
    }

    /** The clip's asset (the reduced twin when asked, the full clip if a pack removed the twin), or null. */
    @Nullable
    public static KeyframeAnimation animation(JewelerClip clip, boolean reduced) {
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(clip.id(reduced));
        if (animation == null && reduced) animation = PlayerAnimationRegistry.getAnimation(clip.id(false));
        if (animation == null && MISSING.add(clip.id(false))) {
            ImmersiveJewelry.LOGGER.error("Jeweler body clip {} is missing (assets/immersive_jewelry/player_animation/); that pose is skipped",
                    clip.id(false));
        }
        return animation;
    }

    /**
     * Poses a player model (a demonstration's jeweler, which is no player) at {@code clip}'s frame {@code elapsed} ticks in,
     * looping or holding the last frame as the clip says. Nothing is registered anywhere. Only reached when playerAnimator runs.
     */
    public static void pose(net.minecraft.client.model.PlayerModel<AbstractClientPlayer> m, JewelerClip playing, double elapsed, boolean reduced) {
        KeyframeAnimation animation = animation(playing, reduced);
        if (animation == null) return;
        double t = Math.max(0, elapsed);
        int tick = (int) Math.floor(t);
        if (playing.loop && playing.length > 0) tick %= playing.length;
        else tick = Math.min(tick, playing.length);
        float delta = (float) (t - Math.floor(t));
        dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer keyframes = new dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer(animation, tick);
        if (!keyframes.isActive()) return;
        keyframes.setupAnim(delta);
        dev.kosmx.playerAnim.impl.animation.AnimationApplier applier = new dev.kosmx.playerAnim.impl.animation.AnimationApplier(keyframes);
        applier.setTickDelta(delta);
        applier.updatePart("head", m.head);
        applier.updatePart("torso", m.body);
        applier.updatePart("rightArm", m.rightArm);
        applier.updatePart("leftArm", m.leftArm);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !registered || failed) return;
        ClientConfig.BodyAnimation setting = ClientConfig.bodyAnimation();
        for (AbstractClientPlayer player : level.players()) {
            Track track = TRACKS.computeIfAbsent(player, p -> new Track());
            update(level, player, track, setting);
        }
    }

    private static void update(ClientLevel level, AbstractClientPlayer player, Track track, ClientConfig.BodyAnimation setting) {
        StationBlockEntity station = station(level, player, track);
        if (station == null || setting == ClientConfig.BodyAnimation.OFF || incompatible(player)) {
            if (track.playing != null) {
                JewelerLayer layer = layer(player);
                if (layer != null) {
                    if (station == null || setting == ClientConfig.BodyAnimation.OFF) layer.stop(FADE);
                    else layer.stopNow();
                }
            }
            track.playing = null;
            track.oneShot = null;
            track.last = station == null ? null : track.last;
            return;
        }
        PoseRules.Snapshot now = PoseRules.of(station);
        JewelerClip change = PoseRules.change(track.last, now);
        track.last = now;
        if (change != null) {
            track.oneShot = change;
            track.oneShotLeft = PoseRules.duration(change);
        } else if (track.oneShot != null && --track.oneShotLeft <= 0) {
            track.oneShot = null;
        }
        JewelerClip wanted = track.oneShot != null ? track.oneShot : PoseRules.held(now);
        Vec3 target = Vec3.atCenterOf(station.getBlockPos());
        JewelerLayer layer = layer(player);
        if (layer == null) return;
        if (wanted == track.playing && change == null) {
            layer.retarget(target);
            return;
        }
        KeyframeAnimation animation = animation(wanted, setting == ClientConfig.BodyAnimation.REDUCED);
        if (animation == null) return;
        layer.play(wanted, animation, FADE, target);
        track.playing = wanted;
    }

    /** Another action owns the body: riding, swimming, sleeping, gliding, using an item. */
    private static boolean incompatible(AbstractClientPlayer player) {
        return player.isPassenger() || player.isSwimming() || player.isSleeping() || player.isFallFlying() || player.isUsingItem()
                || !player.isAlive();
    }

    /** The station {@code player} operates (synced operator), found near them and re-checked every tick. */
    @Nullable
    private static StationBlockEntity station(ClientLevel level, AbstractClientPlayer player, Track track) {
        UUID id = player.getUUID();
        if (track.station != null) {
            BlockEntity entity = level.isLoaded(track.station) ? level.getBlockEntity(track.station) : null;
            if (entity instanceof StationBlockEntity station && id.equals(station.operator())
                    && station.getBlockPos().distToCenterSqr(player.position()) < 36) return station;
            track.station = null;
        }
        if (track.rescan-- > 0) return null;
        track.rescan = RESCAN_TICKS;
        ChunkPos centre = player.chunkPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centre.x + dx, centre.z + dz, false);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof StationBlockEntity station && id.equals(station.operator())
                            && station.getBlockPos().distToCenterSqr(player.position()) < 36) {
                        track.station = station.getBlockPos().immutable();
                        track.rescan = 0;
                        return station;
                    }
                }
            }
        }
        return null;
    }

    /** A resource reload: missing clips are reported again. */
    public static void reload() {
        MISSING.clear();
    }

    /** Leaving a world: every layer of ours stops, nothing about any player is kept. */
    public static void reset() {
        for (Map.Entry<AbstractClientPlayer, Track> entry : TRACKS.entrySet()) {
            if (entry.getValue().playing == null) continue;
            JewelerLayer layer = layer(entry.getKey());
            if (layer != null) layer.stopNow();
        }
        TRACKS.clear();
    }

    /** Whether our layer is moving {@code player}'s body now (the client checks). */
    public static boolean posing(AbstractClientPlayer player) {
        Track track = TRACKS.get(player);
        return track != null && track.playing != null;
    }

    private JewelerPoses() {}
}
