package com.otectus.immersivejewelry.recipe;

/** How a design holds its stones; decides the setting minigame's layout and how well the stone is protected. */
public enum SettingStyle {
    /** Claws pressed over the stone in a balanced order. */
    PRONG,
    /** A metal rim rubbed down all the way round: more points, gentler pressure, better protection. */
    BEZEL;

    public static SettingStyle byId(String id) {
        return "bezel".equals(id) ? BEZEL : PRONG;
    }

    public String id() {
        return this == BEZEL ? "bezel" : "prong";
    }
}
