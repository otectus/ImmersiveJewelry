package com.otectus.immersivejewelry.effect.builtin;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.effect.JewelryEffectType;

/** The effect types traits and metals can name, as {@code immersive_jewelry:<name>}. */
public final class BuiltinEffectTypes {

    public static void register() {
        add("attribute", AttributeEffect::parse);
        add("momentum", MomentumEffect::parse);
        add("critical_edge", CriticalEdgeEffect::parse);
        add("focused_strike", FocusedStrikeEffect::parse);
        add("mark_target", MarkTargetEffect::parse);
        add("projectile_focus", ProjectileFocusEffect::parse);
        add("trophy_experience", TrophyExperienceEffect::parse);
        add("looting_bonus", LootingBonusEffect::parse);
        add("bonemeal_boost", BonemealBoostEffect::parse);
        add("emergency_ward", EmergencyWardEffect::parse);
        add("absorption_shell", AbsorptionShellEffect::parse);
        add("regeneration_pulse", RegenerationPulseEffect::parse);
        add("damage_reduction", DamageReductionEffect::parse);
        add("cleanse", CleanseEffect::parse);
        add("effect_immunity", EffectImmunityEffect::parse);
        add("conditional_effect", ConditionalEffect::parse);
        add("charge_strike", ChargeStrikeEffect::parse);
        add("mining_focus", MiningFocusEffect::parse);
        add("harvest_restore", HarvestRestoreEffect::parse);
        add("restore_food", RestoreFoodEffect::parse);
        add("experience_boost", ExperienceBoostEffect::parse);
        add("muffle_vibrations", MuffleVibrationsEffect::parse);
        add("sense_hostiles", SenseHostilesEffect::parse);
        add("stealth", StealthEffect::parse);
        add("on_hurt", TriggeredSelfEffect::onHurt);
        add("on_kill", TriggeredSelfEffect::onKill);
        add("on_hit", OnHitEffect::parse);
        add("siphon", SiphonEffect::parse);
        add("fall_cushion", FallCushionEffect::parse);
        add("quickdraw", QuickdrawEffect::parse);
        add("amplify_jewelry", AmplifyJewelryEffect::parse);
        add("adaptive_ward", AdaptiveWardEffect::parse);
    }

    private static void add(String name, JewelryEffectType type) {
        ImmersiveJewelryAPI.registerEffectType(ImmersiveJewelry.id(name), type);
    }

    private BuiltinEffectTypes() {}
}
