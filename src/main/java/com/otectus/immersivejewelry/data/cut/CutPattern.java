package com.otectus.immersivejewelry.data.cut;

/** How a cut's facets are laid out around the stone in the cutting minigame. */
public enum CutPattern {
    /** Evenly spaced facets of equal width (round, brilliant). */
    RADIAL,
    /** Alternating long and short facets around a rectangle (emerald). */
    STEP,
    /** A few wide, soft passes over a dome (cabochon). */
    DOME;

    public static CutPattern byId(String id) {
        return switch (id) {
            case "step" -> STEP;
            case "dome" -> DOME;
            default -> RADIAL;
        };
    }
}
