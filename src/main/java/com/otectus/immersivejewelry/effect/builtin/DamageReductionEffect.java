package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resistance to one kind of harm (onyx amulet against projectiles and blasts, quartz amulet against fire, silver
 * frames against the undead). Kinds: damage type ids, damage type tags, and the presets {@code fire},
 * {@code projectile}, {@code explosion}, {@code magic} and {@code undead} (attacks by undead creatures). The share
 * is capped at 60%.
 */
final class DamageReductionEffect extends JewelryEffect {
    private final List<ResourceKey<DamageType>> types;
    private final List<TagKey<DamageType>> tags;
    private final Set<String> presets;
    private final float reduction;
    private final String label;

    private DamageReductionEffect(List<ResourceKey<DamageType>> types, List<TagKey<DamageType>> tags, Set<String> presets,
                                  float reduction, String label) {
        this.types = types;
        this.tags = tags;
        this.presets = presets;
        this.reduction = reduction;
        this.label = label;
    }

    static JewelryEffect parse(JsonObject o) {
        List<ResourceKey<DamageType>> types = new ArrayList<>();
        for (var id : Json.ids(o, "damage_types")) types.add(ResourceKey.create(Registries.DAMAGE_TYPE, id));
        List<TagKey<DamageType>> tags = new ArrayList<>();
        for (var id : Json.ids(o, "damage_tags")) tags.add(TagKey.create(Registries.DAMAGE_TYPE, id));
        List<String> presets = new ArrayList<>();
        if (o.has("against")) for (JsonElement e : GsonHelper.getAsJsonArray(o, "against")) presets.add(GsonHelper.convertToString(e, "against"));
        String label = Json.string(o, "label", presets.isEmpty() ? "harm" : presets.get(0));
        return new DamageReductionEffect(List.copyOf(types), List.copyOf(tags), Set.copyOf(presets), Json.num(o, "reduction", 0.15F, 0F, 1F), label);
    }

    boolean matches(DamageSource source) {
        for (ResourceKey<DamageType> t : types) if (source.is(t)) return true;
        for (TagKey<DamageType> t : tags) if (source.is(t)) return true;
        if (presets.contains("fire") && source.is(DamageTypeTags.IS_FIRE)) return true;
        if (presets.contains("projectile") && source.is(DamageTypeTags.IS_PROJECTILE)) return true;
        if (presets.contains("explosion") && source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        if (presets.contains("magic") && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH))) return true;
        return presets.contains("undead") && source.getEntity() instanceof LivingEntity attacker && attacker.getMobType() == MobType.UNDEAD;
    }

    private float share(float power) {
        return Math.min(0.6F, reduction * power);
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of(JewelryHook.INCOMING_DAMAGE);
    }

    @Override
    public void onIncomingDamage(JewelryContext ctx, LivingHurtEvent event) {
        if (matches(event.getSource())) event.setAmount(event.getAmount() * (1F - share(ctx.power())));
    }

    @Override
    public void describe(EffectDescription out) {
        out.line(Params.key("damage_reduction", "desc"), out.percent(share(out.power())),
                Component.translatable("effect.immersive_jewelry.harm." + label));
    }
}
