package com.otectus.immersivejewelry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.WorkCommandPacket;
import com.otectus.immersivejewelry.workstation.WorkCommand;
import com.otectus.immersivejewelry.workstation.station.SceneComfort;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Protocol 5's command wire format and the scene comfort transform, without a running game. */
class StorageProtocolTest {

    @Test
    void protocolFiveKeepsEveryOlderCommandAtItsOrdinal() {
        assertEquals("5", ModNetwork.VERSION);
        WorkCommand[] expected = {WorkCommand.PLAN_CUT, WorkCommand.CLEAR_PLAN, WorkCommand.FINISH_CUT, WorkCommand.TAKE_STONE, WorkCommand.PLACE,
                WorkCommand.TAKE, WorkCommand.SELECT_DESIGN, WorkCommand.SELECT_FINISH, WorkCommand.SEAT, WorkCommand.LIFT, WorkCommand.CANCEL_PLAN,
                WorkCommand.BEGIN, WorkCommand.FINISH_WORK, WorkCommand.PACK, WorkCommand.DRAWER, WorkCommand.STAGE_IN, WorkCommand.MOVE_STAGED,
                WorkCommand.PREPARE};
        assertEquals(expected.length, WorkCommand.values().length);
        for (int i = 0; i < expected.length; i++) assertEquals(i, expected[i].ordinal(), expected[i].name());
    }

    @Test
    void aCommandRoundTripsWithItsSequence() {
        CompoundTag args = new CompoundTag();
        args.putInt("Index", 2);
        args.putString("To", "work");
        WorkCommandPacket packet = new WorkCommandPacket(new BlockPos(3, 64, -7), 42, 1234567, 99L, WorkCommand.MOVE_STAGED, args);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        WorkCommandPacket read = WorkCommandPacket.decode(buf);
        assertEquals(packet, read);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void theLargestLegitimateCommandsFitTheArgumentBudget() {
        CompoundTag seat = new CompoundTag();
        seat.putInt("Tray", 7);
        seat.putInt("Socket", 7);
        seat.putFloat("Dx", -1F);
        seat.putFloat("Dy", 1F);
        seat.putFloat("Turn", 0.5F);
        seat.putInt("Gen", Integer.MAX_VALUE);
        assertTrue(seat.sizeInBytes() <= WorkCommandPacket.MAX_ARGS_BYTES, "seat " + seat.sizeInBytes());
        CompoundTag prepare = new CompoundTag();
        prepare.putString("Design", "some_addon_namespace:designs/a_rather_long_design_name_for_a_pack");
        prepare.putString("Gem", "some_addon_namespace:a_rather_long_gem_name");
        prepare.put("Prefer", new net.minecraft.nbt.IntArrayTag(new int[com.otectus.immersivejewelry.workstation.BenchOperations.MAX_PREFERRED]));
        prepare.putBoolean("FromStaging", true);
        assertTrue(prepare.sizeInBytes() <= WorkCommandPacket.MAX_ARGS_BYTES, "prepare " + prepare.sizeInBytes());
        CompoundTag move = new CompoundTag();
        move.putInt("Index", 3);
        move.putInt("Count", 64);
        move.putString("To", "inventory");
        move.putInt("Gen", Integer.MAX_VALUE);
        assertTrue(move.sizeInBytes() <= WorkCommandPacket.MAX_ARGS_BYTES, "move " + move.sizeInBytes());
        // Something far larger is refused while decoding.
        CompoundTag huge = new CompoundTag();
        huge.put("Prefer", new net.minecraft.nbt.IntArrayTag(new int[4096]));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new WorkCommandPacket(BlockPos.ZERO, 1, 1, 0L, WorkCommand.PREPARE, huge).encode(buf);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> WorkCommandPacket.decode(buf));
    }

    private static JsonObject bench() throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/immersive_jewelry/diegesis/stations/jewelers_bench.json")))
                .getAsJsonObject();
    }

    @Test
    void calmAndLampOptionsChangePresentationOnly() throws IOException {
        JsonObject authored = SceneComfort.apply(bench(), SceneComfort.Options.AUTHORED);
        assertTrue(authored.getAsJsonObject("placing").has("physics"));
        JsonObject calm = SceneComfort.apply(bench(), new SceneComfort.Options(false, false, false, true, false));
        assertFalse(calm.getAsJsonObject("placing").has("physics"), "calm: stacks stand as put");
        assertEquals(authored.getAsJsonObject("placing").get("group"), calm.getAsJsonObject("placing").get("group"), "same group");
        assertEquals(authored.getAsJsonObject("placing").get("surfaces"), calm.getAsJsonObject("placing").get("surfaces"), "same surfaces");
        boolean lit = false;
        for (var n : calm.getAsJsonArray("nodes")) {
            JsonObject node = n.getAsJsonObject();
            if (node.get("id").getAsString().equals(SceneComfort.LAMP)) lit = node.has("light");
        }
        assertFalse(lit, "the lamp's light is off");
        for (JsonObject scene : new JsonObject[]{authored, calm}) {
            JsonObject neighbors = scene.getAsJsonObject("camera").getAsJsonObject("neighbors");
            assertFalse(neighbors.get("enabled").getAsBoolean() || neighbors.get("keys").getAsBoolean(), "never goes over to a neighbour");
            assertFalse(scene.getAsJsonObject("clear_view").get("blocks").getAsBoolean(), "nothing cleared");
            assertEquals(1, scene.getAsJsonObject("camera").get("interactive_at") == null ? 1
                    : scene.getAsJsonObject("camera").get("interactive_at").getAsInt());
        }
    }
}
