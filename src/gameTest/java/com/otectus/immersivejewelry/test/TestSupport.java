package com.otectus.immersivejewelry.test;

import com.mojang.authlib.GameProfile;
import com.otectus.immersivejewelry.api.ImmersiveJewelryAPI;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryEquipmentProvider;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.minigame.JewelrySession;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.minigame.StepClock;
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

    /** Arrival time that lets a batch ending at {@code endStep} through the real-time guard. */
    static long arrival(JewelrySession session, int endStep) {
        return session.startMs() + (long) endStep * StepClock.STEP_MS + 10;
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
            SessionManager.onLogout(sp);
            com.otectus.immersivejewelry.effect.JewelryWearers.onLogout(sp);
        }
    }

    private TestSupport() {}
}
