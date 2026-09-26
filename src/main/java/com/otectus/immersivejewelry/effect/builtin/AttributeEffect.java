package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivejewelry.api.effect.EffectDescription;
import com.otectus.immersivejewelry.api.effect.EffectUnavailableException;
import com.otectus.immersivejewelry.api.effect.JewelryContext;
import com.otectus.immersivejewelry.api.effect.JewelryEffect;
import com.otectus.immersivejewelry.api.effect.JewelryHook;
import com.otectus.immersivejewelry.data.Json;
import com.otectus.immersivejewelry.jewelry.DescriptionBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Supporting attribute modifiers, scaled by power (spec 19: used sparingly). Attributes of other mods (spell power,
 * mana) are named by id and skipped when absent. If none of them exists the expression's {@code fallback} is used
 * instead (see {@link com.otectus.immersivejewelry.data.ExpressionDefinition}), so a magic circlet still does something
 * in a pack without magic mods.
 */
final class AttributeEffect extends JewelryEffect {
    record Mod(Attribute attribute, double amount, AttributeModifier.Operation operation) {}

    private final List<Mod> modifiers;

    private AttributeEffect(List<Mod> modifiers) {
        this.modifiers = modifiers;
    }

    static JewelryEffect parse(JsonObject o) {
        List<Mod> mods = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "modifiers")) {
            JsonObject m = GsonHelper.convertToJsonObject(e, "modifiers");
            ResourceLocation id = Json.id(m, "attribute");
            if (!ForgeRegistries.ATTRIBUTES.containsKey(id)) continue; // another mod's attribute that is not installed
            mods.add(new Mod(ForgeRegistries.ATTRIBUTES.getValue(id), GsonHelper.getAsDouble(m, "amount"),
                    operation(Json.string(m, "operation", "addition"))));
        }
        if (mods.isEmpty()) throw new EffectUnavailableException("None of the attributes exist in this pack");
        return new AttributeEffect(List.copyOf(mods));
    }

    private static AttributeModifier.Operation operation(String name) {
        return switch (name) {
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            case "addition" -> AttributeModifier.Operation.ADDITION;
            default -> throw new JsonParseException("Unknown attribute operation " + name);
        };
    }

    @Override
    public Set<JewelryHook> hooks() {
        return Set.of();
    }

    @Override
    public void onEquip(JewelryContext ctx) {
        for (int i = 0; i < modifiers.size(); i++) {
            Mod m = modifiers.get(i);
            AttributeInstance instance = ctx.player().getAttribute(m.attribute());
            if (instance == null) continue;
            UUID id = uuid(ctx, i);
            instance.removeModifier(id);
            instance.addTransientModifier(new AttributeModifier(id, "Immersive Jewelry", m.amount() * ctx.power(), m.operation()));
        }
    }

    @Override
    public void onUnequip(JewelryContext ctx) {
        for (int i = 0; i < modifiers.size(); i++) {
            AttributeInstance instance = ctx.player().getAttribute(modifiers.get(i).attribute());
            if (instance != null) instance.removeModifier(uuid(ctx, i));
        }
    }

    static UUID uuid(JewelryContext ctx, int index) {
        return UUID.nameUUIDFromBytes(("immersive_jewelry:" + ctx.slot() + "/" + index).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void describe(EffectDescription out) {
        for (Mod m : modifiers) {
            double amount = m.amount() * out.power();
            String text = m.operation() == AttributeModifier.Operation.ADDITION
                    ? (amount >= 0 ? "+" : "") + DescriptionBuilder.trim((float) amount)
                    : (amount >= 0 ? "+" : "") + Math.round(amount * 100) + "%";
            out.line(Params.key("attribute", "line"), Component.literal(text).withStyle(ChatFormatting.WHITE),
                    Component.translatable(m.attribute().getDescriptionId()));
        }
    }
}
