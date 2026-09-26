package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.equipment.builtin.JewelrySlotsMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** Opens the built-in jewelry slots (inventory button or key). */
public record OpenJewelrySlotsPacket() {

    public void encode(FriendlyByteBuf buf) {}

    public static OpenJewelrySlotsPacket decode(FriendlyByteBuf buf) {
        return new OpenJewelrySlotsPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null || !JewelryEquipment.builtinActive()) return;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new JewelrySlotsMenu(id, inventory),
                    Component.translatable("container.immersive_jewelry.jewelry_slots")));
        });
        context.setPacketHandled(true);
    }
}
