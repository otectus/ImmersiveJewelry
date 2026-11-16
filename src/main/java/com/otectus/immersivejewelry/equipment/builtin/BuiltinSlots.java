package com.otectus.immersivejewelry.equipment.builtin;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.integration.IJewelryEquipmentProvider;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.registry.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The built-in jewelry slots, a player capability used when Curios is not the equipment system. Five slots
 * ({@link SlotType}); a stack is accepted by a slot whose type matches its form's {@code slot}. Contents persist
 * with the player and drop on death unless {@code keepInventory} is on.
 */
public final class BuiltinSlots extends ItemStackHandler {
    public static final Capability<BuiltinSlots> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final net.minecraft.resources.ResourceLocation KEY = ImmersiveJewelry.id("jewelry_slots");

    public static final IJewelryEquipmentProvider PROVIDER = (player, out) -> get(player).ifPresent(slots -> {
        for (int i = 0; i < slots.getSlots(); i++) out.accept("builtin:" + i, slots.getStackInSlot(i));
    });

    @Nullable
    private final Player owner;

    public BuiltinSlots(@Nullable Player owner) {
        super(SlotType.TOTAL);
        this.owner = owner;
    }

    public static LazyOptional<BuiltinSlots> get(Player player) {
        return player.getCapability(CAPABILITY);
    }

    public static boolean accepts(int slot, ItemStack stack) {
        if (!(stack.getItem() instanceof JewelryItem item)) return false;
        FormDefinition form = JewelryDefinitions.current().form(item.form());
        String type = form != null ? form.slot() : FormDefinition.fallback(item.form(), item.form()).slot();
        SlotType slotType = SlotType.forIndex(slot);
        return slotType.id().equals(type) || ("charm".equals(type) && slotType == SlotType.NECKLACE);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return accepts(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (owner != null && !owner.level().isClientSide) {
            JewelryWearers.markDirty(owner);
            WornAppearanceSync.markDirty(owner);
        }
    }

    /** Puts the held piece into the first free matching slot. */
    public static boolean equipFromUse(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        BuiltinSlots slots = get(player).orElse(null);
        if (slots == null || held.isEmpty()) return false;
        for (int i = 0; i < slots.getSlots(); i++) {
            if (slots.getStackInSlot(i).isEmpty() && accepts(i, held)) {
                if (!player.level().isClientSide) {
                    slots.setStackInSlot(i, held.split(1));
                    player.level().playSound(null, player.blockPosition(), ModSounds.JEWELRY_EQUIP.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                }
                return true;
            }
        }
        return false;
    }

    /** Serializable capability provider attached to every player while the built-in slots are active. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final BuiltinSlots slots;
        private final LazyOptional<BuiltinSlots> optional;

        public Provider(Player player) {
            this.slots = new BuiltinSlots(player);
            this.optional = LazyOptional.of(() -> slots);
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return slots.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            slots.deserializeNBT(tag);
            if (slots.getSlots() != SlotType.TOTAL) {
                // Older layout: keep every stack, growing the handler if needed so nothing is lost.
                CompoundTag copy = tag.copy();
                copy.putInt("Size", Math.max(SlotType.TOTAL, slots.getSlots()));
                slots.deserializeNBT(copy);
            }
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
