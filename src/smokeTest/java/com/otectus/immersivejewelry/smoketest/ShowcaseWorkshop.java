package com.otectus.immersivejewelry.smoketest;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.block.StationBlock;
import com.otectus.immersivejewelry.blockentity.JewelersBenchBlockEntity;
import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A warm, daylight workshop backdrop for the smoke-test capture route. */
final class ShowcaseWorkshop {
    private static final ResourceLocation GOLD = new ResourceLocation("minecraft", "gold");
    private static final ResourceLocation SILVER = new ResourceLocation("forge", "silver");

    /**
     * Builds around, but deliberately never rewrites, the two scripted primary stations at (-1, 0, -2) and (2, 0, -2).
     */
    static void build(ServerLevel level, BlockPos base) {
        floor(level, base);
        shell(level, base);
        furnishings(level, base);
        garden(level, base);
        secondaryStations(level, base);
        display(level, base);
    }

    private static void floor(ServerLevel level, BlockPos base) {
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 5; z++)
            put(level, base, x, -1, z, ((x * 3 + z) & 3) == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
        for (int x = -7; x <= 7; x++) {
            put(level, base, x, -1, -6, Blocks.DARK_OAK_PLANKS);
            put(level, base, x, -1, 5, Blocks.DARK_OAK_PLANKS);
        }
    }

    private static void shell(ServerLevel level, BlockPos base) {
        // Calcite plaster is broken into bays by dark timber, leaving broad rear windows for the garden.
        for (int x = -7; x <= 7; x++) {
            for (int y = 0; y <= 4; y++) put(level, base, x, y, -6, Blocks.CALCITE);
            put(level, base, x, 5, -6, log(Direction.Axis.X));
        }
        for (int z = -6; z <= 5; z++) for (int y = 0; y <= 4; y++) {
            put(level, base, -7, y, z, Blocks.CALCITE);
            put(level, base, 7, y, z, Blocks.CALCITE);
        }
        for (int x : new int[]{-7, -5, -1, 3, 7}) for (int y = 0; y <= 5; y++) put(level, base, x, y, -6, Blocks.DARK_OAK_LOG);
        for (int z : new int[]{-6, -2, 2, 5}) {
            for (int y = 0; y <= 4; y++) {
                put(level, base, -7, y, z, Blocks.DARK_OAK_LOG);
                put(level, base, 7, y, z, Blocks.DARK_OAK_LOG);
            }
        }
        for (int x = -7; x <= 7; x++) put(level, base, x, 5, 5, log(Direction.Axis.X));
        for (int z : new int[]{-4, 0, 4}) for (int x = -6; x <= 6; x++) put(level, base, x, 5, z, log(Direction.Axis.X));

        for (int x = -4; x <= 2; x++) for (int y = 2; y <= 3; y++) put(level, base, x, y, -6, Blocks.GLASS_PANE);
        for (int z = -1; z <= 1; z++) for (int y = 2; y <= 3; y++) put(level, base, -7, y, z, Blocks.GLASS_PANE);
        hang(level, base, -3, 4, 0);
        hang(level, base, 1, 4, 0);
        hang(level, base, 5, 4, -4);
    }

    private static void furnishings(ServerLevel level, BlockPos base) {
        // Side shelving keeps the central camera lane clear while giving the room useful workshop texture.
        for (int z = -5; z <= -3; z++) {
            put(level, base, -6, 0, z, Blocks.BARREL);
            put(level, base, -6, 1, z, Blocks.SPRUCE_PLANKS);
        }
        put(level, base, -6, 2, -5, Blocks.AMETHYST_CLUSTER);
        put(level, base, -6, 2, -4, Blocks.POTTED_AZURE_BLUET);
        put(level, base, -6, 2, -3, Blocks.CANDLE);
        for (int z = 1; z <= 4; z++) {
            put(level, base, 6, 0, z, Blocks.CHISELED_BOOKSHELF);
            put(level, base, 6, 1, z, Blocks.SPRUCE_PLANKS);
        }
        put(level, base, 6, 2, 2, Blocks.POTTED_FERN);
        put(level, base, 6, 2, 3, Blocks.SMALL_AMETHYST_BUD);
        put(level, base, 6, 2, 4, Blocks.CANDLE);
        for (int x = 4; x <= 6; x++) put(level, base, x, 0, -5, Blocks.DARK_OAK_PLANKS);
        put(level, base, 4, 1, -5, Blocks.POTTED_RED_TULIP);
        put(level, base, 5, 1, -5, Blocks.BUDDING_AMETHYST);
        put(level, base, 6, 1, -5, Blocks.AMETHYST_CLUSTER);
    }

    private static void garden(ServerLevel level, BlockPos base) {
        for (int x = -6; x <= 5; x++) for (int z = -11; z <= -7; z++) put(level, base, x, -1, z, Blocks.GRASS_BLOCK);
        for (int x : new int[]{-6, -3, 0, 3, 5}) put(level, base, x, 0, -8, (x & 1) == 0 ? Blocks.ALLIUM : Blocks.AZURE_BLUET);
        tree(level, base, -5, -10);
        tree(level, base, 2, -10);
    }

    private static void tree(ServerLevel level, BlockPos base, int x, int z) {
        for (int y = 0; y <= 3; y++) put(level, base, x, y, z, Blocks.BIRCH_LOG);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = 3; y <= 4; y++)
            put(level, base, x + dx, y, z + dz, Blocks.BIRCH_LEAVES);
        put(level, base, x, 5, z, Blocks.BIRCH_LEAVES);
    }

    private static void secondaryStations(ServerLevel level, BlockPos base) {
        BlockState wheelState = ModBlocks.LAPIDARY_WHEEL.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
        BlockState benchState = ModBlocks.JEWELERS_BENCH.get().defaultBlockState().setValue(StationBlock.FACING, Direction.SOUTH);
        BlockPos wheelPos = base.offset(-3, 0, -2);
        BlockPos benchPos = base.offset(4, 0, -2);
        level.setBlockAndUpdate(wheelPos, wheelState);
        level.setBlockAndUpdate(benchPos, benchState);
        JewelryDefinitions defs = JewelryDefinitions.server();
        if (level.getBlockEntity(wheelPos) instanceof LapidaryWheelBlockEntity wheel)
            wheel.setStone(RoughGemItem.create(GemData.rough(ImmersiveJewelry.id("emerald"), 1.05F, .78F, .12F)));
        if (level.getBlockEntity(benchPos) instanceof JewelersBenchBlockEntity bench)
            bench.setClamp(ClientSmokeTest.piece(defs, "amulet", "sapphire", "round", SILVER, "polished", .92F));
    }

    private static void display(ServerLevel level, BlockPos base) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        frame(level, base, 4, 2, -5, ClientSmokeTest.piece(defs, "ring", "diamond", "brilliant", GOLD, "polished", .95F));
        frame(level, base, 5, 2, -5, ClientSmokeTest.piece(defs, "amulet", "emerald", "emerald", SILVER, "polished", .90F));
        frame(level, base, 6, 2, -5, ClientSmokeTest.piece(defs, "bracelet", "ruby", "round", GOLD, "burnished", .88F));
    }

    private static void frame(ServerLevel level, BlockPos base, int x, int y, int z, ItemStack stack) {
        ItemFrame frame = new ItemFrame(level, base.offset(x, y, z), Direction.SOUTH);
        frame.setItem(stack, false);
        level.addFreshEntity(frame);
    }

    private static BlockState log(Direction.Axis axis) {
        return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static void hang(ServerLevel level, BlockPos base, int x, int y, int z) {
        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void put(ServerLevel level, BlockPos base, int x, int y, int z, Block block) {
        level.setBlockAndUpdate(base.offset(x, y, z), block.defaultBlockState());
    }

    private static void put(ServerLevel level, BlockPos base, int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(base.offset(x, y, z), state);
    }

    private ShowcaseWorkshop() {}
}
