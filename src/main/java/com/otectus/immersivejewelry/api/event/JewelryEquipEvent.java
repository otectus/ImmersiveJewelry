package com.otectus.immersivejewelry.api.event;

import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted when a worn piece becomes active or stops being active (put on, taken off, broken, repaired, or the
 * datapacks changed). Fired once per change after effects are applied or removed. Server side.
 */
public abstract class JewelryEquipEvent extends PlayerEvent {
    private final ItemStack item;
    private final JewelryData data;
    private final String slot;

    protected JewelryEquipEvent(Player player, ItemStack item, JewelryData data, String slot) {
        super(player);
        this.item = item;
        this.data = data;
        this.slot = slot;
    }

    /** A copy of the worn stack. */
    public ItemStack getItem() { return item; }
    public JewelryData getData() { return data; }
    public String getSlot() { return slot; }

    public static class Equipped extends JewelryEquipEvent {
        public Equipped(Player player, ItemStack item, JewelryData data, String slot) {
            super(player, item, data, slot);
        }
    }

    public static class Unequipped extends JewelryEquipEvent {
        public Unequipped(Player player, ItemStack item, JewelryData data, String slot) {
            super(player, item, data, slot);
        }
    }
}
