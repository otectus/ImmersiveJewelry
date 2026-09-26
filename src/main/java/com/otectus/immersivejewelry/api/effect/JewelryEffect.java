package com.otectus.immersivejewelry.api.effect;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Set;

/**
 * One behaviour of a worn piece, configured by data (a trait expression) and scaled by the piece's power. Instances
 * are immutable and shared by every piece using the same expression; per-piece state goes through
 * {@link JewelryContext#state}. All hooks run on the server thread. Only hooks listed in {@link #hooks()} are called.
 */
public abstract class JewelryEffect {

    public abstract Set<JewelryHook> hooks();

    /** Ticks between {@link #tick} calls when {@link JewelryHook#TICK} is hooked. */
    public int tickInterval() {
        return 20;
    }

    /** Tooltip lines with the piece's numbers. */
    public abstract void describe(EffectDescription out);

    public void onEquip(JewelryContext ctx) {}

    public void onUnequip(JewelryContext ctx) {}

    public void tick(JewelryContext ctx) {}

    public void onMeleeAttack(JewelryContext ctx, AttackEntityEvent event) {}

    public void onOutgoingDamage(JewelryContext ctx, LivingHurtEvent event) {}

    public void onIncomingDamage(JewelryContext ctx, LivingHurtEvent event) {}

    public void onDamageTaken(JewelryContext ctx, LivingDamageEvent event) {}

    public void onKill(JewelryContext ctx, LivingDeathEvent event) {}

    public void onCriticalHit(JewelryContext ctx, CriticalHitEvent event) {}

    public void onBreakSpeed(JewelryContext ctx, PlayerEvent.BreakSpeed event) {}

    public void onBlockBreak(JewelryContext ctx, BlockEvent.BreakEvent event) {}

    public void onXpPickup(JewelryContext ctx, PlayerXpEvent.PickupXp event) {}

    public void onExperienceDrop(JewelryContext ctx, LivingExperienceDropEvent event) {}

    public void onLooting(JewelryContext ctx, LootingLevelEvent event) {}

    public void onBonemeal(JewelryContext ctx, BonemealEvent event) {}

    public void onUseItemTick(JewelryContext ctx, LivingEntityUseItemEvent.Tick event) {}

    public void onVisibility(JewelryContext ctx, LivingEvent.LivingVisibilityEvent event) {}

    public void onEffectApplicable(JewelryContext ctx, MobEffectEvent.Applicable event) {}

    public void onFall(JewelryContext ctx, LivingFallEvent event) {}

    public void onTargeted(JewelryContext ctx, LivingChangeTargetEvent event) {}

    public void onGameEvent(JewelryContext ctx, VanillaGameEvent event) {}

    public void onProjectileFired(JewelryContext ctx, Projectile projectile) {}

    public void onJump(JewelryContext ctx, LivingEvent.LivingJumpEvent event) {}

    /**
     * Adds to the wearer's profile (see {@link JewelryProfile}) for results the client predicts. Called when
     * equipment changes; {@code out.accept(key, amount)} sums across pieces.
     */
    public void contributeProfile(float power, float stability, java.util.function.BiConsumer<String, Float> out) {}

    /**
     * Extra power this effect lends to the wearer's other pieces (a prism circlet), as a fraction: 0.1 = +10%.
     * Resolved when equipment changes, not per event.
     */
    public float amplifyOthers(float power) {
        return 0F;
    }
}
