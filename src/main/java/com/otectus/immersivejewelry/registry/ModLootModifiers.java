package com.otectus.immersivejewelry.registry;

import com.mojang.serialization.Codec;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.loot.TreasureGemsModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ImmersiveJewelry.MOD_ID);

    public static final RegistryObject<Codec<TreasureGemsModifier>> TREASURE_GEMS = LOOT_MODIFIERS.register("treasure_gems",
            () -> TreasureGemsModifier.CODEC);

    private ModLootModifiers() {}
}
