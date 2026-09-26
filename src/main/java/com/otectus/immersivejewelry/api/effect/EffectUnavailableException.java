package com.otectus.immersivejewelry.api.effect;

import com.google.gson.JsonParseException;

/**
 * Thrown by a {@link JewelryEffectType} whose behaviour cannot exist in this pack (an attribute of a mod that is not
 * installed). If the expression has a {@code fallback}, that expression is used instead, name and all.
 */
public class EffectUnavailableException extends JsonParseException {
    public EffectUnavailableException(String message) {
        super(message);
    }
}
