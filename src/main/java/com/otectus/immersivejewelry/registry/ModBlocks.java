package com.otectus.immersivejewelry.registry;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.block.JewelersBenchBlock;
import com.otectus.immersivejewelry.block.LapidaryWheelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ImmersiveJewelry.MOD_ID);

    public static final RegistryObject<LapidaryWheelBlock> LAPIDARY_WHEEL = BLOCKS.register("lapidary_wheel",
            () -> new LapidaryWheelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<JewelersBenchBlock> JEWELERS_BENCH = BLOCKS.register("jewelers_bench",
            () -> new JewelersBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));

    private ModBlocks() {}
}
