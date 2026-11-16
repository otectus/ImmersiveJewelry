package com.otectus.immersivejewelry.test;

import com.mojang.authlib.GameProfile;
import com.otectus.immersivejewelry.network.packet.WorkCommandPacket;
import com.otectus.immersivejewelry.network.packet.WorkInputPacket;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.WorkInputService;
import com.otectus.immersivejewelry.workstation.WorkSessionManager;
import com.otectus.immersivejewelry.workstation.sim.WorkSample;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryEquipmentProvider;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class TestSupport {
    static final BlockPos STATION = new BlockPos(2, 1, 2);
    static final ResourceLocation RUBY = id("ruby");
    static final ResourceLocation DIAMOND = id("diamond");

    /** An equipment system for tests (the API path other accessory mods use). */
    static final Map<UUID, List<ItemStack>> WORN = new ConcurrentHashMap<>();
    private static boolean providerRegistered;

    static ResourceLocation id(String path) {
        return new ResourceLocation("immersive_jewelry", path);
    }

    static FakePlayer player(GameTestHelper h) {
        if (!providerRegistered) {
            providerRegistered = true;
            IJewelryEquipmentProvider provider = (p, out) -> {
                List<ItemStack> list = WORN.get(p.getUUID());
                if (list != null) for (int i = 0; i < list.size(); i++) out.accept("test:" + i, list.get(i));
            };
            ImmersiveJewelryAPI.registerEquipmentProvider(provider);
        }
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "JewelerTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        BlockPos pos = h.absolutePos(STATION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    static void wear(Player player, ItemStack... stacks) {
        WORN.put(player.getUUID(), new ArrayList<>(List.of(stacks)));
    }

    static InteractionResult use(GameTestHelper h, Player player) {
        BlockPos pos = h.absolutePos(STATION);
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.99);
        return h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.SOUTH, pos, false));
    }

    /**
     * A player working a station through the real operator path: the lease the station grants, batches of control samples
     * through {@link WorkInputService} on a test clock (20 ms per step, so the pacing guard sees real-time input), and
     * discrete commands through the same service the network uses.
     */
    static final class Operator {
        final ServerPlayer player;
        final BlockPos pos;
        final WorkSessionManager.Lease lease;
        long clock = Util.getMillis();
        int seq;
        int step;

        Operator(ServerPlayer player, BlockPos pos, WorkSessionManager.Lease lease) {
            this.player = player;
            this.pos = pos;
            this.lease = lease;
        }

        void feed(long workId, WorkSample... samples) {
            feed(workId, List.of(samples));
        }

        void feed(long workId, List<WorkSample> samples) {
            for (int i = 0; i < samples.size(); i += WorkInputPacket.MAX_SAMPLES) {
                List<WorkSample> batch = samples.subList(i, Math.min(samples.size(), i + WorkInputPacket.MAX_SAMPLES));
                clock += 20L * batch.size();
                WorkInputService.input(player, new WorkInputPacket(pos, lease.token, workId, ++seq, step, new ArrayList<>(batch)), clock);
                step += batch.size();
            }
        }

        int commandSeq;

        /** A discrete command, numbered as the client numbers them, 60 ms of test clock after the last (inside the budget). */
        void command(long workId, WorkCommand command, CompoundTag args) {
            clock += 60L;
            WorkInputService.command(player, new WorkCommandPacket(pos, lease.token, ++commandSeq, workId, command, args), clock);
        }

        /** A command with a chosen sequence number at the current test clock (repeats, floods). */
        void commandAt(int seq, WorkCommand command, CompoundTag args) {
            WorkInputService.command(player, new WorkCommandPacket(pos, lease.token, seq, 0L, command, args), clock);
        }

        void command(long workId, WorkCommand command) {
            command(workId, command, new CompoundTag());
        }
    }

    static Operator operate(GameTestHelper h, ServerPlayer player, BlockPos relative) {
        BlockPos pos = h.absolutePos(relative);
        var be = (com.otectus.immersivejewelry.blockentity.StationBlockEntity) h.getLevel().getBlockEntity(pos);
        WorkSessionManager.Lease lease = WorkSessionManager.acquire(player, be);
        if (lease == null) throw new IllegalStateException("No lease at " + pos);
        return new Operator(player, pos, lease);
    }

    static CompoundTag args(Object... pairs) {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof Integer v) tag.putInt(key, v);
            else if (value instanceof Float v) tag.putFloat(key, v);
            else if (value instanceof Boolean v) tag.putBoolean(key, v);
            else tag.putString(key, String.valueOf(value));
        }
        return tag;
    }

    /** Counts every item of {@code item} in the player's inventory and the given containers' item entities around {@code pos}. */
    static int count(GameTestHelper h, Player player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    static GemData cutStone(ResourceLocation gem, ResourceLocation cut, float quality) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemDefinition g = defs.gem(gem);
        CutDefinition c = defs.cut(cut);
        return JewelryFactory.sampleCut(g, c, quality);
    }

    static void cleanup(Player player) {
        WORN.remove(player.getUUID());
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            WorkSessionManager.onLogout(sp);
            com.otectus.immersivejewelry.effect.JewelryWearers.onLogout(sp);
        }
    }

    /**
     * The items a station dropped just now: item entities spawned this tick ({@code tickCount} 0) at its drop point, one block
     * over its middle. All tests run as one batch on a grid, so an item flung from a neighbouring test can land near a station;
     * counting only what appeared at this station's own drop point this tick keeps such strays out, while a station that
     * dropped something twice is still seen twice.
     */
    static java.util.List<net.minecraft.world.entity.item.ItemEntity> freshDrops(GameTestHelper h, BlockPos station) {
        BlockPos pos = station;
        net.minecraft.world.phys.AABB at = new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2,
                pos.getZ() + 1);
        return h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, at, e -> e.tickCount == 0);
    }

    private TestSupport() {}
}
