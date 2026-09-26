package com.otectus.immersivejewelry.effect.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;

/**
 * A situation a conditional effect waits for. {@code {"type": "in_darkness", "max_light": 4}},
 * {@code {"type": "submerged"}}, {@code {"type": "sprinting", "min_ticks": 40}}, {@code {"type": "low_health",
 * "below": 0.5}}, {@code "sunlight"}, {@code "in_nether"}, {@code "underground"}, {@code "raining"},
 * {@code "sneaking"}, {@code "always"}.
 */
record Condition(String type, int value, float fraction) {

    static Condition parse(JsonObject o, String key) {
        if (!o.has(key)) return new Condition("always", 0, 0F);
        JsonObject c;
        if (o.get(key).isJsonPrimitive()) {
            c = new JsonObject();
            c.addProperty("type", GsonHelper.getAsString(o, key));
        } else {
            c = GsonHelper.getAsJsonObject(o, key);
        }
        String type = GsonHelper.getAsString(c, "type");
        int value = switch (type) {
            case "in_darkness" -> Json.integer(c, "max_light", 4, 0, 15);
            case "sprinting" -> Json.integer(c, "min_ticks", 40, 0, 72000);
            default -> 0;
        };
        return check(new Condition(type, value, Json.num(c, "below", 0.5F, 0.01F, 1F)));
    }

    private static Condition check(Condition c) {
        switch (c.type) {
            case "always", "in_darkness", "submerged", "in_water", "sprinting", "low_health", "sunlight", "in_nether",
                 "underground", "raining", "sneaking" -> {
                return c;
            }
            default -> throw new JsonParseException("Unknown condition " + c.type);
        }
    }

    /** {@code sprintTicks}: how long the wearer has been sprinting without a break. */
    boolean test(ServerPlayer player, int sprintTicks) {
        Level level = player.level();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        return switch (type) {
            case "in_darkness" -> level.getMaxLocalRawBrightness(eyes) <= value;
            case "submerged" -> player.isEyeInFluid(FluidTags.WATER);
            case "in_water" -> player.isInWater();
            case "sprinting" -> player.isSprinting() && sprintTicks >= value;
            case "low_health" -> player.getHealth() <= player.getMaxHealth() * fraction;
            case "sunlight" -> level.isDay() && !level.isRaining() && level.canSeeSky(eyes);
            case "in_nether" -> level.dimension() == Level.NETHER;
            case "underground" -> !level.canSeeSky(eyes) && eyes.getY() < level.getSeaLevel() - 8;
            case "raining" -> level.isRainingAt(eyes);
            case "sneaking" -> player.isShiftKeyDown();
            default -> true;
        };
    }

    Component describe() {
        return Component.translatable("effect.immersive_jewelry.condition." + type);
    }
}
