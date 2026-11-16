package com.otectus.immersivejewelry.item;

import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.jewelry.JewelryNames;
import com.otectus.immersivejewelry.registry.ModItems;
import com.otectus.immersivejewelry.workstation.order.BenchWork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unfinished jewelry: bench work packed up to carry. It holds the frame, the stones committed to it (by socket) and the
 * work's state, and nothing else: it is inert (no effects, no equipment slot, no recycling) and only a Jeweler's Bench
 * unpacks it, restoring exactly what was packed. Packing moves ownership from the bench to this item; unpacking moves it
 * back and consumes the item, so the committed stones are never in two places.
 *
 * <p>Data: {@code immersive_jewelry} {@code {Version, Carrier: {Frame, Stones: [{Index, ...}], Work}}}.</p>
 */
public class UnfinishedJewelryItem extends Item {

    public UnfinishedJewelryItem(Properties properties) {
        super(properties);
    }

    /** What a carrier holds: the frame, the stones by socket (empty where none) and the raw work data. */
    public record Contents(ItemStack frame, List<ItemStack> stones, CompoundTag work) {}

    public static ItemStack create(ItemStack frame, List<ItemStack> stones, CompoundTag work) {
        ItemStack stack = new ItemStack(ModItems.UNFINISHED_JEWELRY.get());
        CompoundTag carrier = new CompoundTag();
        carrier.put("Frame", frame.save(new CompoundTag()));
        ListTag list = new ListTag();
        for (int i = 0; i < stones.size(); i++) {
            if (stones.get(i).isEmpty()) continue;
            CompoundTag entry = stones.get(i).save(new CompoundTag());
            entry.putByte("Index", (byte) i);
            list.add(entry);
        }
        carrier.put("Stones", list);
        carrier.put("Work", work.copy());
        CompoundTag root = new CompoundTag();
        root.put("Carrier", carrier);
        JewelryNbt.write(stack, root);
        return stack;
    }

    public static Optional<Contents> contents(ItemStack stack) {
        if (!(stack.getItem() instanceof UnfinishedJewelryItem)) return Optional.empty();
        CompoundTag root = JewelryNbt.root(stack);
        if (root == null || !root.contains("Carrier", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag carrier = root.getCompound("Carrier");
        ItemStack frame = ItemStack.of(carrier.getCompound("Frame"));
        if (frame.isEmpty()) return Optional.empty();
        List<ItemStack> stones = new ArrayList<>();
        for (int i = 0; i < 8; i++) stones.add(ItemStack.EMPTY);
        for (Tag t : carrier.getList("Stones", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            int i = entry.getByte("Index");
            if (i >= 0 && i < stones.size()) stones.set(i, ItemStack.of(entry));
        }
        return Optional.of(new Contents(frame, stones, carrier.getCompound("Work")));
    }

    @Override
    public Component getName(ItemStack stack) {
        return contents(stack).<Component>map(c -> Component.translatable(getDescriptionId() + ".named", c.frame().getHoverName()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished").withStyle(ChatFormatting.GRAY));
        contents(stack).ifPresent(c -> {
            BenchWork work = BenchWork.load(c.work()).orElse(null);
            if (work == null) {
                lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished.unreadable").withStyle(ChatFormatting.RED));
                return;
            }
            lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished.kind." + work.kind().id()).withStyle(ChatFormatting.GOLD));
            long stones = c.stones().stream().filter(s -> !s.isEmpty()).count();
            if (stones > 0) {
                var gem = c.stones().stream().filter(s -> !s.isEmpty()).findFirst().flatMap(CutGemItem::data).orElse(null);
                Component name = gem != null ? JewelryNames.gem(JewelryDefinitions.current(), gem.gem()) : Component.empty();
                lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished.stones", stones, name).withStyle(ChatFormatting.BLUE));
            }
            if (work.setting() != null && work.kind() != BenchWork.Kind.REMOVE) {
                int secured = 0;
                for (int s = 0; s < work.setting().params().sockets(); s++) if (work.setting().secured(s)) secured++;
                lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished.secured", secured, work.setting().params().sockets())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (work.surface() != null) {
                lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished.surface",
                        Math.round(work.surface().progress() * 100)).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        lines.add(Component.translatable("tooltip.immersive_jewelry.unfinished.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
