package com.otectus.immersivejewelry.jewelry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.resources.ResourceLocation;

/** Ids of the four built-in forms (spec 4). Their definitions are data in {@code immersive_jewelry/jewelry_forms}. */
public final class JewelryForms {
    public static final ResourceLocation RING = ImmersiveJewelry.id("ring");
    public static final ResourceLocation AMULET = ImmersiveJewelry.id("amulet");
    public static final ResourceLocation BRACELET = ImmersiveJewelry.id("bracelet");
    public static final ResourceLocation CIRCLET = ImmersiveJewelry.id("circlet");

    private JewelryForms() {}
}
