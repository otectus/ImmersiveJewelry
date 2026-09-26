package com.otectus.immersivejewelry.effect;

import java.util.Map;

/** The local player's profile, as last sent by the server. Common code; only written by the client packet handler. */
public final class ClientProfile {
    private static volatile Map<String, Float> values = Map.of();

    public static void set(Map<String, Float> map) {
        values = Map.copyOf(map);
    }

    public static float get(String key) {
        return values.getOrDefault(key, 0F);
    }

    public static void clear() {
        values = Map.of();
    }

    private ClientProfile() {}
}
