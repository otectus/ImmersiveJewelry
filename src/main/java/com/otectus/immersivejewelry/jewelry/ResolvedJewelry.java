package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.ExpressionDefinition;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.data.trait.TraitDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A piece's behaviour derived from its construction (spec 31): computed once per equip or tooltip, never per tick.
 *
 * @param basePower      power before per-category bias
 * @param stability      duration multiplier (cut and finish)
 * @param responsiveness cooldown divisor (metal)
 * @param expressions    the behaviours this piece expresses, each with its resolved power
 * @param inert          why the piece does nothing (broken, no stone), or null
 */
public record ResolvedJewelry(JewelryData data, FormDefinition form, MetalDefinition metal, @Nullable GemDefinition gem,
                              @Nullable CutDefinition cut, @Nullable FinishDefinition finish, @Nullable TraitDefinition trait,
                              float basePower, float stability, float responsiveness, List<Expression> expressions,
                              @Nullable String inert) {

    /** One behaviour with its power. {@code fromMetal} marks a metal's own small effect. */
    public record Expression(ExpressionDefinition definition, float power, boolean fromMetal) {}

    public boolean isActive() {
        return inert == null && !expressions.isEmpty();
    }
}
