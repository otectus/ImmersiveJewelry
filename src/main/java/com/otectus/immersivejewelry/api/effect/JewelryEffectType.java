package com.otectus.immersivejewelry.api.effect;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Builds a {@link JewelryEffect} from a trait expression's JSON parameters. Register with
 * {@link com.otectus.immersivejewelry.api.ImmersiveJewelryAPI#registerEffectType}. Parsing happens on both sides
 * after every datapack reload, so it must not touch the world.
 */
@FunctionalInterface
public interface JewelryEffectType {
    JewelryEffect parse(JsonObject json) throws JsonParseException;
}
