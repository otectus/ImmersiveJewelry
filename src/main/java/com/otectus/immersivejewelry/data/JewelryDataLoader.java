package com.otectus.immersivejewelry.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;

import java.io.Reader;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads every definition folder on the server. Files may carry Forge {@code conditions}. The parsed snapshot is built
 * later ({@link DefinitionEvents}), after tags are bound, because automatic gem detection reads tags.
 */
public final class JewelryDataLoader extends SimplePreparableReloadListener<Map<DefinitionKind, Map<ResourceLocation, JsonObject>>> {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<DefinitionKind, Map<ResourceLocation, JsonObject>> files = new EnumMap<>(DefinitionKind.class);

    private final ICondition.IContext conditions;

    public JewelryDataLoader(ICondition.IContext conditions) {
        this.conditions = conditions;
    }

    /** The definition files of the last datapack load. */
    public static Map<DefinitionKind, Map<ResourceLocation, JsonObject>> files() {
        return files;
    }

    @Override
    protected Map<DefinitionKind, Map<ResourceLocation, JsonObject>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<DefinitionKind, Map<ResourceLocation, JsonObject>> out = new EnumMap<>(DefinitionKind.class);
        for (DefinitionKind kind : DefinitionKind.values()) {
            Map<ResourceLocation, JsonObject> entries = new LinkedHashMap<>();
            String dir = kind.directory();
            for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(dir, p -> p.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath().substring(dir.length() + 1, file.getPath().length() - ".json".length());
                ResourceLocation id = new ResourceLocation(file.getNamespace(), path);
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement json = GsonHelper.fromJson(GSON, reader, JsonElement.class);
                    if (json == null || !json.isJsonObject()) {
                        ImmersiveJewelry.LOGGER.warn("Skipping {} {}: not a JSON object", kind.folder(), id);
                        continue;
                    }
                    JsonObject object = json.getAsJsonObject();
                    if (!CraftingHelper.processConditions(object, "conditions", conditions)) continue;
                    object.remove("conditions");
                    entries.put(id, object);
                } catch (Exception ex) {
                    ImmersiveJewelry.LOGGER.warn("Skipping {} {}: {}", kind.folder(), id, ex.getMessage());
                }
            }
            out.put(kind, entries);
        }
        return out;
    }

    @Override
    protected void apply(Map<DefinitionKind, Map<ResourceLocation, JsonObject>> prepared, ResourceManager manager, ProfilerFiller profiler) {
        files = prepared;
    }
}
