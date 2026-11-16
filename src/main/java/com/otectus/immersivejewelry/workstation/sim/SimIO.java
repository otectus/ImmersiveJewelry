package com.otectus.immersivejewelry.workstation.sim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Bounded reading and writing of simulation state. Everything read from NBT or the network is length-checked against
 * the expected size and every number is made finite and clamped, so a corrupt save or a hostile packet can never put
 * a simulation into a state its own steps could not reach.
 */
public final class SimIO {
    /** Upper bound of any array a simulation keeps (8 sockets x 12 points, 16 regions x 8 bins, 24 x 6 cells). */
    public static final int MAX_ARRAY = 160;

    public static float finite(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Moves {@code from} towards {@code to} by at most {@code maxStep}: a handle cannot jump faster than a hand moves it. */
    public static float approach(float from, float to, float maxStep) {
        if (to > from + maxStep) return from + maxStep;
        if (to < from - maxStep) return from - maxStep;
        return to;
    }

    public static void putFloats(CompoundTag tag, String key, float[] values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        tag.put(key, list);
    }

    /** Reads exactly {@code size} values (missing ones are {@code fallback}), each clamped to {@code min..max}. */
    public static float[] getFloats(CompoundTag tag, String key, int size, float fallback, float min, float max) {
        float[] out = new float[size];
        ListTag list = tag.getList(key, Tag.TAG_FLOAT);
        for (int i = 0; i < size; i++) out[i] = i < list.size() ? finite(list.getFloat(i), min, max) : fallback;
        return out;
    }

    public static void putInts(CompoundTag tag, String key, int[] values) {
        tag.putIntArray(key, values.clone());
    }

    public static int[] getInts(CompoundTag tag, String key, int size, int fallback, int min, int max) {
        int[] raw = tag.getIntArray(key);
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = i < raw.length ? clamp(raw[i], min, max) : fallback;
        return out;
    }

    public static void writeFloats(FriendlyByteBuf buf, float[] values) {
        buf.writeVarInt(values.length);
        for (float v : values) buf.writeFloat(v);
    }

    public static float[] readFloats(FriendlyByteBuf buf, int expected, float min, float max) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ARRAY) throw new IllegalArgumentException("array of " + n);
        float[] out = new float[expected];
        for (int i = 0; i < n; i++) {
            float v = buf.readFloat();
            if (i < expected) out[i] = finite(v, min, max);
        }
        return out;
    }

    public static void writeInts(FriendlyByteBuf buf, int[] values) {
        buf.writeVarInt(values.length);
        for (int v : values) buf.writeVarInt(v);
    }

    public static int[] readInts(FriendlyByteBuf buf, int expected, int min, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ARRAY) throw new IllegalArgumentException("array of " + n);
        int[] out = new int[expected];
        for (int i = 0; i < n; i++) {
            int v = buf.readVarInt();
            if (i < expected) out[i] = clamp(v, min, max);
        }
        return out;
    }

    /** Maps a 12-bit sample to {@code -1..1}. */
    public static float signed12(int value) {
        return clamp(value, 0, 4095) / 4095F * 2F - 1F;
    }

    /** Maps a 12-bit sample to {@code 0..1}. */
    public static float unit12(int value) {
        return clamp(value, 0, 4095) / 4095F;
    }

    public static int toSigned12(float value) {
        return clamp(Math.round((clamp(value, -1F, 1F) + 1F) * 0.5F * 4095F), 0, 4095);
    }

    public static int toUnit12(float value) {
        return clamp(Math.round(clamp(value, 0F, 1F) * 4095F), 0, 4095);
    }

    private SimIO() {}
}
