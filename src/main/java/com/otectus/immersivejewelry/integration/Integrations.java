package com.otectus.immersivejewelry.integration;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.config.CommonConfig;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * The only door to optional mods (spec 5.4, 20, 47). Each integration class is referenced solely from a lambda or
 * method that runs after its {@link ModList} check, so no optional class is ever loaded without its mod.
 */
public final class Integrations {
    public static final String SMITHING = "immersive_smithing";
    public static final String CURIOS = "curios";

    private static boolean smithingActive;
    private static boolean curiosActive;

    public static void init(IEventBus modBus) {
        if (smithingLoaded()) {
            smithingActive = true;
            com.otectus.immersivejewelry.integration.smithing.SmithingIntegration.init(modBus);
            ImmersiveJewelry.LOGGER.info("Immersive Smithing integration active: jewelry blanks are forged");
        }
    }

    public static void commonSetup() {
        if (smithingActive) com.otectus.immersivejewelry.integration.smithing.SmithingIntegration.commonSetup();
    }

    /** Called by the equipment layer when Curios is the equipment system. */
    public static void enableCurios() {
        if (curiosActive || !ModList.get().isLoaded(CURIOS)) return;
        curiosActive = true;
        com.otectus.immersivejewelry.integration.curios.CuriosIntegration.init();
    }

    /** Immersive Smithing is installed and its integration enabled. */
    public static boolean smithingLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(SMITHING) && CommonConfig.get(CommonConfig.ENABLE_SMITHING_INTEGRATION);
    }

    public static boolean smithingActive() {
        return smithingActive;
    }

    public static boolean curiosActive() {
        return curiosActive;
    }

    private Integrations() {}
}
