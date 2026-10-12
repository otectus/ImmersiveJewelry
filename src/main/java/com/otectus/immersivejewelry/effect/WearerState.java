package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.api.event.JewelryEquipEvent;
import com.otectus.immersivejewelry.api.integration.IJewelryEffectContributor;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.ExpressionDefinition;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.jewelry.JewelryForms;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.jewelry.ResolvedJewelry;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.SyncProfilePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The active jewelry of one player (spec 31-32). Rebuilt only when something changed: an equipment event, a
 * datapack reload, respawn, or (as a safety net every 40 ticks) a changed signature of the worn stacks. Pieces whose
 * slot and composition are unchanged keep their cooldowns and state; effects are re-applied only when power moved.
 */
final class WearerState {
    private static final int SIGNATURE_INTERVAL = 40;

    final UUID id;
    boolean dirty = true;
    boolean fullReset;
    private int signature;
    private int ticks;
    List<ActivePiece> pieces = List.of();
    final EnumSet<JewelryHook> hooks = EnumSet.noneOf(JewelryHook.class);
    Map<String, Float> profile = Map.of();

    WearerState(UUID id) {
        this.id = id;
    }

    void tick(ServerPlayer player) {
        ticks++;
        if (dirty || (ticks % SIGNATURE_INTERVAL == 0 && signature(JewelryEquipment.worn(player)) != signature)) refresh(player);
        if (!hooks.contains(JewelryHook.TICK)) return;
        for (ActivePiece piece : pieces) {
            if (!piece.hooks.contains(JewelryHook.TICK)) continue;
            for (ActivePiece.Active a : piece.effects) {
                if (!a.effect.hooks().contains(JewelryHook.TICK)) continue;
                int interval = Math.max(1, a.effect.tickInterval());
                if ((ticks + a.index * 7) % interval == 0) run(a, () -> a.effect.tick(a));
            }
        }
    }

    void refresh(ServerPlayer player) {
        boolean reset = fullReset;
        dirty = false;
        fullReset = false;
        Map<String, ItemStack> worn = JewelryEquipment.worn(player);
        signature = signature(worn);
        JewelryDefinitions defs = JewelryDefinitions.server();

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        List<ActivePiece> next = new ArrayList<>();
        for (Map.Entry<String, ItemStack> e : worn.entrySet()) {
            JewelryData data = JewelryData.get(e.getValue()).orElse(null);
            if (data == null) continue;
            // A piece a wear gate refuses stays worn and inert, like a broken one, and does not use up a form slot.
            if (!ImmersiveJewelryAPI.mayWear(player, e.getValue(), data)) continue;
            int count = counts.merge(data.form(), 1, Integer::sum);
            if (count > maxActive(data.form())) continue;
            ResolvedJewelry resolved = JewelryResolver.resolve(data, defs);
            if (!resolved.isActive()) continue;
            ItemStack snapshot = e.getValue().copy();
            next.add(new ActivePiece(e.getKey(), key(e.getValue(), data), snapshot, data, resolved, contributions(e.getValue(), data)));
        }

        // Amplification: pieces lend power to the others (never to themselves).
        float total = 0F;
        float[] own = new float[next.size()];
        for (int i = 0; i < next.size(); i++) {
            for (ActivePiece.Active a : next.get(i).effects) {
                if (!a.contributed) own[i] += a.effect.amplifyOthers(a.expression.power());
            }
            total += own[i];
        }
        for (int i = 0; i < next.size(); i++) next.get(i).amplification = Math.min(1F, total - own[i]);

        Map<String, ActivePiece> previous = new HashMap<>();
        for (ActivePiece p : pieces) previous.put(p.slot, p);
        List<ActivePiece> added = new ArrayList<>();
        for (int i = 0; i < next.size(); i++) {
            ActivePiece now = next.get(i);
            ActivePiece before = previous.get(now.slot);
            if (before != null && before.key == now.key && !reset) {
                previous.remove(now.slot);
                now.inherit(before);
                if (Arrays.equals(before.powers(), now.powers())) {
                    // Same piece, same numbers: keep the old instance and its applied effects.
                    next.set(i, before);
                    before.effects.forEach(a -> a.player = player);
                    continue;
                }
                unequip(player, before, false);
                added.add(now);
            } else {
                added.add(now);
            }
        }
        for (ActivePiece removed : previous.values()) unequip(player, removed, true);
        for (ActivePiece piece : added) {
            boolean announce = pieces.stream().noneMatch(p -> p.slot.equals(piece.slot) && p.key == piece.key);
            equip(player, piece, announce);
        }
        pieces = List.copyOf(next);
        hooks.clear();
        for (ActivePiece p : pieces) hooks.addAll(p.hooks);
        updateProfile(player);
    }

    /** Removes every effect (logout, server stop). With {@code callbacks} false the player entity is already gone. */
    void clear(ServerPlayer player, boolean callbacks) {
        if (callbacks) for (ActivePiece p : pieces) unequip(player, p, false);
        pieces = List.of();
        hooks.clear();
        profile = Map.of();
    }

    void forEach(JewelryHook hook, Consumer<ActivePiece.Active> action) {
        if (!hooks.contains(hook)) return;
        for (ActivePiece piece : pieces) {
            if (!piece.hooks.contains(hook)) continue;
            for (ActivePiece.Active a : piece.effects) {
                if (a.effect.hooks().contains(hook)) run(a, () -> action.accept(a));
            }
        }
    }

    float profile(String key) {
        return profile.getOrDefault(key, 0F);
    }

    private void equip(ServerPlayer player, ActivePiece piece, boolean event) {
        for (ActivePiece.Active a : piece.effects) {
            a.player = player;
            run(a, () -> a.effect.onEquip(a));
        }
        if (event) MinecraftForge.EVENT_BUS.post(new JewelryEquipEvent.Equipped(player, piece.snapshot.copy(), piece.data, piece.slot));
    }

    private void unequip(ServerPlayer player, ActivePiece piece, boolean event) {
        for (ActivePiece.Active a : piece.effects) {
            a.player = player;
            run(a, () -> a.effect.onUnequip(a));
        }
        if (event) MinecraftForge.EVENT_BUS.post(new JewelryEquipEvent.Unequipped(player, piece.snapshot.copy(), piece.data, piece.slot));
    }

    private void updateProfile(ServerPlayer player) {
        Map<String, Float> sums = new HashMap<>();
        for (ActivePiece p : pieces) {
            for (ActivePiece.Active a : p.effects) a.effect.contributeProfile(a.power(), a.stability(), (k, v) -> sums.merge(k, v, Float::sum));
        }
        Map<String, Float> next = Map.copyOf(sums);
        if (!next.equals(profile)) {
            profile = next;
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncProfilePacket(next));
        }
    }

    private static void run(ActivePiece.Active a, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            ImmersiveJewelry.LOGGER.error("Jewelry effect {} failed", a.expression.definition().type(), e);
        }
    }

    private static int maxActive(ResourceLocation form) {
        if (form.equals(JewelryForms.RING)) return ServerConfig.get(ServerConfig.MAX_RING_SLOTS);
        if (form.equals(JewelryForms.AMULET)) return ServerConfig.get(ServerConfig.MAX_AMULET_SLOTS);
        if (form.equals(JewelryForms.BRACELET)) return ServerConfig.get(ServerConfig.MAX_BRACELET_SLOTS);
        if (form.equals(JewelryForms.CIRCLET)) return ServerConfig.get(ServerConfig.MAX_CIRCLET_SLOTS);
        return 1;
    }

    /**
     * Identity of a piece for state carry-over: its item and composition, ignoring Integrity (which wears). With effect
     * contributors registered, NBT outside the jewelry compound counts too, because contributions are read from it.
     */
    static int key(ItemStack stack, JewelryData data) {
        int key = Objects.hash(stack.getItem(), data.withIntegrity(1F));
        if (ImmersiveJewelryAPI.effectContributors().isEmpty() || stack.getTag() == null) return key;
        CompoundTag foreign = stack.getTag().copy();
        foreign.remove(JewelryNbt.ROOT);
        return 31 * key + foreign.hashCode();
    }

    /** Effects other mods add to an active piece. A contributor that throws is skipped for this piece and logged. */
    private static List<ResolvedJewelry.Expression> contributions(ItemStack stack, JewelryData data) {
        List<IJewelryEffectContributor> contributors = ImmersiveJewelryAPI.effectContributors();
        if (contributors.isEmpty()) return List.of();
        List<ResolvedJewelry.Expression> out = new ArrayList<>();
        for (IJewelryEffectContributor contributor : contributors) {
            try {
                for (IJewelryEffectContributor.ContributedEffect c : contributor.effects(stack, data)) {
                    out.add(new ResolvedJewelry.Expression(new ExpressionDefinition(c.id(), c.category(), null, c.effect()), c.power(), false));
                }
            } catch (RuntimeException e) {
                ImmersiveJewelry.LOGGER.error("Jewelry effect contributor {} failed for {}", contributor.getClass().getName(), stack, e);
            }
        }
        return out;
    }

    private static int signature(Map<String, ItemStack> worn) {
        int h = 1;
        for (Map.Entry<String, ItemStack> e : worn.entrySet()) {
            h = 31 * h + e.getKey().hashCode();
            h = 31 * h + e.getValue().getItem().hashCode();
            h = 31 * h + Objects.hashCode(e.getValue().getTag());
        }
        return h;
    }
}
