package com.otectus.immersivejewelry.network.packet;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.item.JewelryItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a player wears in the built-in jewelry slots, as others need to draw it: one sanitized stack per slot (the item and
 * the look of its frame, stones and finish; never the maker, defects, history, foreign data or effect contributors).
 * Sent to the player and to those tracking them when the slots change and when tracking starts. Curios syncs its own
 * slots, so this is only used for the built-in ones. Presentation only: nothing reads it for effects or equipment.
 */
public record WornAppearancePacket(int entity, List<ItemStack> slots) {
    public static final int MAX_SLOTS = 8;

    /** The look of a worn piece and nothing else. */
    public static ItemStack sanitize(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof JewelryItem)) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(stack.getItem());
        JewelryData.get(stack).ifPresent(d -> new JewelryData(d.form(), d.metal(), d.gems(), d.design(), d.finish(), 0F, 0F, d.workmanship(),
                d.integrity(), 0, Set.of(), null, false, null).apply(out));
        return out;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entity);
        int n = Math.min(MAX_SLOTS, slots.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeItem(slots.get(i));
    }

    public static WornAppearancePacket decode(FriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SLOTS) throw new IllegalArgumentException("Too many worn slots: " + n);
        List<ItemStack> slots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) slots.add(buf.readItem());
        return new WornAppearancePacket(entity, slots);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.otectus.immersivejewelry.client.presentation.worn.WornAppearance.receive(entity, slots));
    }
}
