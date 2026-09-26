package com.otectus.immersivejewelry.equipment.builtin;

import com.otectus.immersivejewelry.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/** The built-in jewelry slots above the player's inventory. */
public class JewelrySlotsMenu extends AbstractContainerMenu {
    public static final int JEWELRY_X = 44;
    public static final int JEWELRY_Y = 20;

    private final Player player;

    public JewelrySlotsMenu(int id, Inventory inventory) {
        super(ModMenus.JEWELRY_SLOTS.get(), id);
        this.player = inventory.player;
        IItemHandler slots = BuiltinSlots.get(player).<IItemHandler>cast().orElse(new ItemStackHandler(SlotType.TOTAL));
        for (int i = 0; i < SlotType.TOTAL; i++) {
            final int index = i;
            addSlot(new SlotItemHandler(slots, i, JEWELRY_X + i * 18 + (i >= 2 ? 6 : 0), JEWELRY_Y) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return BuiltinSlots.accepts(index, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
    }

    public Player player() {
        return player;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SlotType.TOTAL) {
            if (!moveItemStackTo(stack, SlotType.TOTAL, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < SlotType.TOTAL && !moved; i++) {
                if (!slots.get(i).hasItem() && BuiltinSlots.accepts(i, stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
