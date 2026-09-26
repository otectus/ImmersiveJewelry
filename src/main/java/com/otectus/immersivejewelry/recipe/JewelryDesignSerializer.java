package com.otectus.immersivejewelry.recipe;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.data.Json;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JewelryDesignSerializer implements RecipeSerializer<JewelryDesignRecipe> {

    @Override
    public JewelryDesignRecipe fromJson(ResourceLocation id, JsonObject o) {
        JsonObject setting = o.has("setting") ? GsonHelper.getAsJsonObject(o, "setting") : new JsonObject();
        SettingStyle style = SettingStyle.byId(Json.string(setting, "style", "prong"));
        int stones = Json.integer(o, "stones", Json.integer(o, "gem_slots", 1, 1, 8), 1, 8);
        String key = id.getNamespace() + "." + id.getPath().replace('/', '.').replace("designs.", "");
        return new JewelryDesignRecipe(id, Json.id(o, "form"), stones, style,
                Json.integer(setting, "points", style == SettingStyle.BEZEL ? 6 : 4, 2, 12),
                Json.num(setting, "tightness", 1F, 0.25F, 4F),
                Json.num(o, "protection", style == SettingStyle.BEZEL ? 1.3F : 1F, 0.1F, 5F),
                List.copyOf(Json.ids(o, "allowed_cuts")),
                o.has("allowed_gems") && o.get("allowed_gems").isJsonArray() ? List.copyOf(Json.ids(o, "allowed_gems")) : List.of(),
                List.copyOf(Json.ids(o, "allowed_finishes")),
                Json.bool(o, "sketch", false),
                Json.integer(o, "sort_order", 100, -1000, 1000),
                Json.name(o, "name", "design." + key, Json.titleCase(id)));
    }

    @Override
    public @Nullable JewelryDesignRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        return new JewelryDesignRecipe(id, buf.readResourceLocation(), buf.readVarInt(), buf.readEnum(SettingStyle.class), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readList(FriendlyByteBuf::readResourceLocation),
                buf.readList(FriendlyByteBuf::readResourceLocation), buf.readList(FriendlyByteBuf::readResourceLocation),
                buf.readBoolean(), buf.readVarInt(), buf.readComponent());
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, JewelryDesignRecipe r) {
        buf.writeResourceLocation(r.form());
        buf.writeVarInt(r.stones());
        buf.writeEnum(r.style());
        buf.writeVarInt(r.pointsPerStone());
        buf.writeFloat(r.tightness());
        buf.writeFloat(r.protection());
        buf.writeCollection(r.allowedCuts(), FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(r.allowedGems(), FriendlyByteBuf::writeResourceLocation);
        buf.writeCollection(r.allowedFinishes(), FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(r.sketch());
        buf.writeVarInt(r.sortOrder());
        buf.writeComponent(r.name());
    }
}
