package com.otectus.immersivejewelry.jewelry;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Common code asks the client for a few presentation facts through these suppliers, which client setup replaces.
 * Nothing here references a client class, so items can use it on a dedicated server.
 */
public final class ClientHooks {
    public static volatile BooleanSupplier shiftDown = () -> false;
    public static volatile BooleanSupplier advancedTooltipsEnabled = () -> false;
    /** Ids of sketch designs in the client's recipe book, for the creative tab. */
    public static volatile Supplier<List<ResourceLocation>> sketchDesigns = List::of;

    private ClientHooks() {}
}
