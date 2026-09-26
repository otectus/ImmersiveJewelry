package com.otectus.immersivejewelry.api.effect;

/**
 * The game moments an effect listens to. Each active piece records the union of its effects' hooks, so a moment
 * costs nothing for players whose jewelry does not care about it.
 */
public enum JewelryHook {
    /** Periodic, every {@link JewelryEffect#tickInterval()} ticks. */
    TICK,
    /** The wearer starts a melee attack (attack charge is still readable here, not in the damage events). */
    MELEE_ATTACK,
    /** The wearer damages something (melee or their projectile), before armor. */
    OUTGOING_DAMAGE,
    /** The wearer is about to be hurt, before armor. */
    INCOMING_DAMAGE,
    /** The wearer took final damage (after armor and absorption), for low-health triggers. */
    DAMAGE_TAKEN,
    /** The wearer killed something. */
    KILL,
    /** The wearer's melee attack is deciding whether it is critical. */
    CRITICAL_HIT,
    /** Mining speed is being computed. */
    BREAK_SPEED,
    /** The wearer broke a block. */
    BLOCK_BREAK,
    /** The wearer picks up an experience orb. */
    XP_PICKUP,
    /** A creature the wearer killed is dropping experience. */
    EXPERIENCE_DROP,
    /** Looting level for a kill by the wearer. */
    LOOTING,
    /** The wearer uses bone meal. */
    BONEMEAL,
    /** The wearer is using an item (drawing a bow). */
    USE_ITEM_TICK,
    /** A mob is judging how visible the wearer is. */
    VISIBILITY,
    /** An effect is about to be applied to the wearer. */
    EFFECT_APPLICABLE,
    /** The wearer lands from a fall. */
    FALL,
    /** A mob chooses the wearer as its target. */
    TARGETED,
    /** The wearer caused a vanilla game event (a step, a splash). */
    GAME_EVENT,
    /** A projectile shot by the wearer enters the world. */
    PROJECTILE_FIRED,
    /** The wearer jumps. */
    JUMP
}
