package com.otectus.immersivejewelry.registry;

import com.google.common.collect.ImmutableSet;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The Jeweler: works at the Jeweler's Bench. Registered ids are stable; legacy Jewelcraft ids alias to them. */
public final class ModVillagers {
    public static final ResourceKey<PoiType> JEWELERS_BENCH_KEY = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ImmersiveJewelry.id("jewelers_bench"));

    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(ForgeRegistries.POI_TYPES, ImmersiveJewelry.MOD_ID);
    public static final RegistryObject<PoiType> JEWELERS_BENCH = POI_TYPES.register("jewelers_bench",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.JEWELERS_BENCH.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, ImmersiveJewelry.MOD_ID);
    public static final RegistryObject<VillagerProfession> JEWELER = PROFESSIONS.register("jeweler", () -> new VillagerProfession(
            "jeweler",
            holder -> holder.is(JEWELERS_BENCH_KEY),
            holder -> holder.is(JEWELERS_BENCH_KEY),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ModSounds.VILLAGER_WORK_JEWELER.get()));

    private ModVillagers() {}
}
