package com.otectus.immersivejewelry.smoketest;

import net.minecraftforge.fml.common.Mod;

/**
 * Test-only companion mod used by {@code runClient -PsmokeTest}. It does nothing unless the system property
 * {@code immersive_jewelry.smoketest.output} names a directory for its result file.
 */
@Mod(SmokeTestAgent.MOD_ID)
public class SmokeTestAgent {
    public static final String MOD_ID = "immersive_jewelry_smoketest";
    public static final String OUTPUT_PROPERTY = "immersive_jewelry.smoketest.output";

    public SmokeTestAgent() {
    }
}
