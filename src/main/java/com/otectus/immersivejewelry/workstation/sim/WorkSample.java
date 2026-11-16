package com.otectus.immersivejewelry.workstation.sim;

import net.minecraft.network.FriendlyByteBuf;

/**
 * One fixed 20 ms step of control input, quantized. The same record carries every station's controls; the operation
 * that consumes it decides what the fields mean:
 *
 * <ul>
 *   <li>Faceting: {@code a} sweep across the lap (12-bit, -1..1), {@code b} quill height (12-bit, 0..1, contact below
 *   half), {@code target} the index-collar detent, {@code aux} the fine trim + {@value #TRIM_BIAS}, {@link #FLAG_DIP} cooling
 *   in the basin.</li>
 *   <li>Setting: {@code a} handle travel (12-bit, 0..1), {@code b} the angle around a bezel (12-bit, 0..2 pi),
 *   {@code target} socket x 16 + point, {@code aux} the tool, {@link #FLAG_CONTACT}, {@link #FLAG_PRY}.</li>
 *   <li>Surface work: {@code a} position around the piece (12-bit, 0..1), {@code b} across it (12-bit, 0..1), {@code c}
 *   pressure (12-bit, 0..1), {@code aux} the tool, {@link #FLAG_CONTACT}.</li>
 * </ul>
 *
 * Clients send only these: never an outcome, a score or an item.
 */
public record WorkSample(int a, int b, int c, int target, int aux, int flags) {
    public static final int FLAG_CONTACT = 1, FLAG_DIP = 2, FLAG_PRY = 4;
    public static final int TRIM_BIAS = 8;
    public static final int BYTES = 8;
    /** A hand off every control: the quill lifted, no tool touching anything. */
    public static final WorkSample NEUTRAL = new WorkSample(2048, 4095, 0, 0, TRIM_BIAS, 0);

    public WorkSample {
        a = SimIO.clamp(a, 0, 4095);
        b = SimIO.clamp(b, 0, 4095);
        c = SimIO.clamp(c, 0, 4095);
        target = SimIO.clamp(target, 0, 255);
        aux = SimIO.clamp(aux, 0, 31);
        flags = flags & 7;
    }

    /**
     * The control state a fraction {@code t} of the way from {@code from} to {@code to}: continuous fields move linearly,
     * discrete ones (target, tool, flags) take {@code to}'s. A frame that covers several fixed steps spreads its motion
     * over them this way, so the same hand movement gives the same samples at any frame rate.
     */
    public static WorkSample between(WorkSample from, WorkSample to, float t) {
        return new WorkSample(Math.round(from.a + (to.a - from.a) * t), Math.round(from.b + (to.b - from.b) * t),
                Math.round(from.c + (to.c - from.c) * t), to.target, to.aux, to.flags);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public void write(FriendlyByteBuf buf) {
        // 3 x 12 bits in 36 bits, target, aux and flags in one more byte each.
        long packed = ((long) a << 24) | ((long) b << 12) | c;
        buf.writeByte((int) (packed >>> 32));
        buf.writeInt((int) packed);
        buf.writeByte(target);
        buf.writeByte(aux);
        buf.writeByte(flags);
    }

    public static WorkSample read(FriendlyByteBuf buf) {
        long high = buf.readUnsignedByte();
        long low = buf.readInt() & 0xFFFFFFFFL;
        long packed = (high << 32) | low;
        return new WorkSample((int) ((packed >>> 24) & 4095), (int) ((packed >>> 12) & 4095), (int) (packed & 4095),
                buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte());
    }
}
