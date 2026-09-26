package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Routes game events to the active jewelry effects that hook them. A player without such an effect costs a map
 * lookup and a bitmask test; nothing scans inventories or parses NBT here.
 */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class EffectEvents {

    // ------------------------------------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) JewelryWearers.tick(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        JewelryWearers.markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) JewelryWearers.onLogout(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) JewelryWearers.onClone(player);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        JewelryWearers.markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JewelryWearers.onServerStopping(event.getServer());
    }

    // ------------------------------------------------------------------------------------------------ combat

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.MELEE_ATTACK, a -> a.effect.onMeleeAttack(a, event));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer target) {
            JewelryWearers.dispatch(target, JewelryHook.INCOMING_DAMAGE, a -> a.effect.onIncomingDamage(a, event));
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer attacker && source != event.getEntity()) {
            JewelryWearers.dispatch(attacker, JewelryHook.OUTGOING_DAMAGE, a -> a.effect.onOutgoingDamage(a, event));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target) || event.getAmount() <= 0F) return;
        JewelryWearers.dispatch(target, JewelryHook.DAMAGE_TAKEN, a -> a.effect.onDamageTaken(a, event));
        JewelryWear.onDamage(target, event.getAmount());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()) {
            JewelryWearers.dispatch(killer, JewelryHook.KILL, a -> a.effect.onKill(a, event));
        }
    }

    @SubscribeEvent
    public static void onCritical(CriticalHitEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.CRITICAL_HIT, a -> a.effect.onCriticalHit(a, event));
        }
    }

    @SubscribeEvent
    public static void onLooting(LootingLevelEvent event) {
        if (event.getDamageSource() != null && event.getDamageSource().getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.LOOTING, a -> a.effect.onLooting(a, event));
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.EXPERIENCE_DROP, a -> a.effect.onExperienceDrop(a, event));
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.TARGETED, a -> a.effect.onTargeted(a, event));
        }
    }

    @SubscribeEvent
    public static void onProjectile(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide || !(event.getEntity() instanceof Projectile projectile)) return;
        if (projectile.getOwner() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.PROJECTILE_FIRED, a -> a.effect.onProjectileFired(a, projectile));
        }
    }

    // ------------------------------------------------------------------------------------------------ utility

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.BLOCK_BREAK, a -> a.effect.onBlockBreak(a, event));
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.BREAK_SPEED, a -> a.effect.onBreakSpeed(a, event));
        }
    }

    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.XP_PICKUP, a -> a.effect.onXpPickup(a, event));
        }
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.BONEMEAL, a -> a.effect.onBonemeal(a, event));
        }
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.VISIBILITY, a -> a.effect.onVisibility(a, event));
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.EFFECT_APPLICABLE, a -> a.effect.onEffectApplicable(a, event));
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.FALL, a -> a.effect.onFall(a, event));
        }
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (event.getCause() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.GAME_EVENT, a -> a.effect.onGameEvent(a, event));
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JewelryWearers.dispatch(player, JewelryHook.JUMP, a -> a.effect.onJump(a, event));
        }
    }

    private EffectEvents() {}
}
