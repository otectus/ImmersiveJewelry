package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ImmersiveJewelry.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LapidaryWheelBlockEntity>> LAPIDARY_WHEEL = BLOCK_ENTITIES.register("lapidary_wheel",
            () -> BlockEntityType.Builder.of(LapidaryWheelBlockEntity::new, ModBlocks.LAPIDARY_WHEEL.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<JewelersBenchBlockEntity>> JEWELERS_BENCH = BLOCK_ENTITIES.register("jewelers_bench",
            () -> BlockEntityType.Builder.of(JewelersBenchBlockEntity::create, ModBlocks.JEWELERS_BENCH.get()).build(null));

    private ModBlockEntities() {}
}
