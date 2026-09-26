package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.jewelry.ResolvedJewelry;
import com.otectus.immersivejewelry.util.StationEffects;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** One worn, active piece: its resolved behaviour and the live state of each of its effects. */
final class ActivePiece {
    final String slot;
    final int key;
    final ItemStack snapshot;
    final JewelryData data;
    final ResolvedJewelry resolved;
    final List<Active> effects = new ArrayList<>();
    final EnumSet<JewelryHook> hooks = EnumSet.noneOf(JewelryHook.class);
    float amplification;

    /** @param contributed effects other mods add (see {@code IJewelryEffectContributor}), after the trait effects */
    ActivePiece(String slot, int key, ItemStack snapshot, JewelryData data, ResolvedJewelry resolved,
                List<ResolvedJewelry.Expression> contributed) {
        this.slot = slot;
        this.key = key;
        this.snapshot = snapshot;
        this.data = data;
        this.resolved = resolved;
        int index = 0;
        for (ResolvedJewelry.Expression e : resolved.expressions()) add(new Active(this, e, index++, false));
        for (ResolvedJewelry.Expression e : contributed) add(new Active(this, e, index++, true));
    }

    private void add(Active a) {
        effects.add(a);
        hooks.addAll(a.effect.hooks());
    }

    /** Effective powers, for detecting a change that needs effects re-applied. */
    float[] powers() {
        float[] out = new float[effects.size()];
        for (int i = 0; i < out.length; i++) out[i] = effects.get(i).power();
        return out;
    }

    /** Carries cooldowns and state over from the previous version of this piece (same slot and composition). */
    void inherit(ActivePiece previous) {
        for (int i = 0; i < effects.size() && i < previous.effects.size(); i++) {
            Active now = effects.get(i);
            Active before = previous.effects.get(i);
            if (now.effect.getClass() == before.effect.getClass()) {
                now.cooldowns.putAll(before.cooldowns);
                now.state = before.state;
            }
        }
    }

    /** The context an effect runs in. */
    static final class Active implements JewelryContext {
        final ActivePiece piece;
        final ResolvedJewelry.Expression expression;
        final JewelryEffect effect;
        final int index;
        /** From another mod's data: its power is used as given, never amplified by other pieces. */
        final boolean contributed;
        final Map<String, Long> cooldowns = new HashMap<>();
        Object state;
        ServerPlayer player;

        Active(ActivePiece piece, ResolvedJewelry.Expression expression, int index, boolean contributed) {
            this.piece = piece;
            this.expression = expression;
            this.effect = expression.definition().effect();
            this.index = index;
            this.contributed = contributed;
        }

        @Override public ServerPlayer player() { return player; }
        @Override public JewelryData data() { return piece.data; }
        @Override public ResourceLocation form() { return piece.data.form(); }
        @Override public String slot() { return piece.slot + "/" + index; }
        @Override public float power() { return contributed ? expression.power() : expression.power() * (1F + piece.amplification); }
        @Override public float stability() { return piece.resolved.stability(); }
        @Override public float responsiveness() { return piece.resolved.responsiveness(); }
        @Override public long gameTime() { return player.level().getGameTime(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T state(Supplier<T> factory) {
            if (state == null) state = factory.get();
            return (T) state;
        }

        @Override
        public boolean ready(String key) {
            Long until = cooldowns.get(key);
            return until == null || gameTime() >= until;
        }

        @Override
        public void startCooldown(String key, int baseTicks) {
            cooldowns.put(key, gameTime() + cooldown(baseTicks));
        }

        @Override
        public void announce() {
            int color = piece.resolved.gem() != null ? piece.resolved.gem().color() : 0xFFFFFF;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.EFFECT_TRIGGER.get(),
                    SoundSource.PLAYERS, 0.45F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            StationEffects.sparkleAt(player.serverLevel(), player.getX(), player.getY() + 1.2, player.getZ(), color, 4);
        }
    }
}
