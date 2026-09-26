package com.otectus.immersivejewelry.api.effect;

/**
 * Keys of the per-player profile: effects whose result the client predicts (mining speed, jumping, drawing a bow)
 * add to it through {@link JewelryEffect#contributeProfile}. The server sums the contributions of all active pieces,
 * uses the sums itself and sends them to the wearer, so both sides compute the same result.
 */
public final class JewelryProfile {
    /** Extra mining speed, as a fraction, with any tool. */
    public static final String MINING_SPEED = "mining_speed";
    /** Extra mining speed, as a fraction, when the held tool is correct for the block. */
    public static final String MINING_SPEED_CORRECT_TOOL = "mining_speed_correct_tool";
    /** Share (0..1) of the underwater mining penalty removed. */
    public static final String UNDERWATER_MINING = "underwater_mining";
    /** Extra jump velocity (0.1 is about half a block higher). */
    public static final String JUMP_BOOST = "jump_boost";
    /** Share by which drawing bows and crossbows (#immersive_jewelry:quickdraw) speeds up. */
    public static final String QUICKDRAW = "quickdraw";

    private JewelryProfile() {}
}
