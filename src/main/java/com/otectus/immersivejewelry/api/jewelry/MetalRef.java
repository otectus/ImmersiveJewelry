package com.otectus.immersivejewelry.api.jewelry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The metal of a frame: an Immersive Smithing material family id (for example {@code minecraft:gold} or
 * {@code forge:silver}), plus the family's display name and tint as they were when the metal was worked. The stored
 * name and tint make a piece render and read correctly even where no jewelry metal definition exists.
 *
 * @param id       material family id
 * @param nameJson display name as component JSON, or null
 * @param color    RGB tint, or -1 when unknown
 */
public record MetalRef(ResourceLocation id, @Nullable String nameJson, int color) {
    public static final MetalRef UNKNOWN = new MetalRef(new ResourceLocation("minecraft", "iron"), null, -1);

    public static MetalRef of(ResourceLocation id) {
        return new MetalRef(id, null, -1);
    }

    public static MetalRef of(ResourceLocation id, @Nullable Component name, int color) {
        return new MetalRef(id, name == null ? null : Component.Serializer.toJson(name), color);
    }

    @Nullable
    public Component storedName() {
        if (nameJson == null) return null;
        try {
            return Component.Serializer.fromJson(nameJson);
        } catch (RuntimeException e) {
            return null;
        }
    }

    void save(CompoundTag tag) {
        tag.putString("Metal", id.toString());
        if (nameJson != null) tag.putString("MetalName", nameJson);
        if (color >= 0) tag.putInt("MetalColor", color);
    }

    static MetalRef load(CompoundTag tag) {
        ResourceLocation id = tag.contains("Metal", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Metal")) : null;
        if (id == null) return UNKNOWN;
        String name = tag.contains("MetalName", Tag.TAG_STRING) ? tag.getString("MetalName") : null;
        if (name != null && name.length() > 4096) name = null;
        int color = tag.contains("MetalColor", Tag.TAG_INT) ? tag.getInt("MetalColor") & 0xFFFFFF : -1;
        return new MetalRef(id, name, color);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeNullable(nameJson, (b, s) -> b.writeUtf(s, 4096));
        buf.writeInt(color);
    }

    public static MetalRef read(FriendlyByteBuf buf) {
        return new MetalRef(buf.readResourceLocation(), buf.readNullable(b -> b.readUtf(4096)), buf.readInt());
    }
}
