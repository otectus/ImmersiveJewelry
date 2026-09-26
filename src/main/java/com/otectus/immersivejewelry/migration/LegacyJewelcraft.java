package com.otectus.immersivejewelry.migration;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.gem.GemDamage;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.config.CommonConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.item.JewelryItem;
import com.otectus.immersivejewelry.quality.Workmanship;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Jewelcraft Reforged migration (spec 50). Jewelcraft Reforged used the {@code jewelcraft} namespace with four modular
 * wearable ids, the Jeweler's Bench (block, item, POI) and the Jeweler profession.
 * <ul>
 *   <li>Registry aliases resolve every old id wherever it is looked up by name: stacks in inventories, containers and
 *   Curios slots, block states in chunks, POI records and villager professions.</li>
 *   <li>{@link #onMissingMappings} remaps the ids in the world's saved registry, so the world opens cleanly.</li>
 *   <li>{@link #convertInPlace} turns the legacy {@code JewelcraftMaterials} compound into Immersive Jewelry data the
 *   first time a stack is read. Names, enchantments and other NBT are untouched; the old compound is kept under
 *   {@code immersive_jewelry.Legacy}, so its inset item can be given back when the stone is removed.</li>
 * </ul>
 * The decorative jewelry box has no successor and becomes a chest. Nothing is ever deleted.
 */
public final class LegacyJewelcraft {
    public static final String NAMESPACE = "jewelcraft";
    public static final String LEGACY_TAG = "JewelcraftMaterials";
    public static final float DEFAULT_QUALITY = 0.65F;

    private static final Map<String, String> ITEMS = Map.of(
            "material_ring", "immersive_jewelry:ring",
            "material_amulet", "immersive_jewelry:amulet",
            "material_bracelet", "immersive_jewelry:bracelet",
            "material_circlet", "immersive_jewelry:circlet",
            "jeweler_bench", "immersive_jewelry:jewelers_bench",
            "jewelry_box", "minecraft:chest");
    private static final Map<String, String> BLOCKS = Map.of("jeweler_bench", "immersive_jewelry:jewelers_bench");
    private static final Map<String, String> POIS = Map.of("jeweler_bench", "immersive_jewelry:jewelers_bench");
    private static final Map<String, String> PROFESSIONS = Map.of("jeweler", "immersive_jewelry:jeweler");

    private static final Map<String, String> METALS = Map.of(
            "iron", "minecraft:iron", "gold", "minecraft:gold", "copper", "minecraft:copper", "netherite", "minecraft:netherite",
            "ancient_metal", "cataclysm:ancient_metal", "black_steel", "cataclysm:black_steel", "witherite", "cataclysm:witherite",
            "ignitium", "cataclysm:ignitium", "cursium", "cataclysm:cursium");
    private static final Map<String, String> GEMS = Map.of(
            "amethyst", "immersive_jewelry:amethyst", "diamond", "immersive_jewelry:diamond", "emerald", "immersive_jewelry:emerald",
            "lapis", "immersive_jewelry:lapis", "quartz", "immersive_jewelry:quartz", "nether_quartz", "immersive_jewelry:quartz",
            "onyx", "immersive_jewelry:onyx", "true_onyx", "immersive_jewelry:onyx");

    // ------------------------------------------------------------------------------------------------ registries

    public static void onRegister(RegisterEvent event) {
        alias(event, ForgeRegistries.Keys.ITEMS, ITEMS);
        alias(event, ForgeRegistries.Keys.BLOCKS, BLOCKS);
        alias(event, ForgeRegistries.Keys.POI_TYPES, POIS);
        alias(event, ForgeRegistries.Keys.VILLAGER_PROFESSIONS, PROFESSIONS);
    }

    private static <T> void alias(RegisterEvent event, ResourceKey<? extends net.minecraft.core.Registry<T>> key, Map<String, String> ids) {
        if (!event.getRegistryKey().equals(key)) return;
        IForgeRegistry<?> registry = event.getForgeRegistry();
        if (!(registry instanceof ForgeRegistry<?> forge)) return;
        ids.forEach((from, to) -> forge.addAlias(new ResourceLocation(NAMESPACE, from), new ResourceLocation(to)));
    }

    public static void onMissingMappings(MissingMappingsEvent event) {
        remap(event, ForgeRegistries.Keys.ITEMS, ForgeRegistries.ITEMS, ITEMS);
        remap(event, ForgeRegistries.Keys.BLOCKS, ForgeRegistries.BLOCKS, BLOCKS);
        remap(event, ForgeRegistries.Keys.POI_TYPES, ForgeRegistries.POI_TYPES, POIS);
        remap(event, ForgeRegistries.Keys.VILLAGER_PROFESSIONS, ForgeRegistries.VILLAGER_PROFESSIONS, PROFESSIONS);
    }

    private static <T> void remap(MissingMappingsEvent event, ResourceKey<? extends net.minecraft.core.Registry<T>> key,
                                  IForgeRegistry<T> registry, Map<String, String> ids) {
        for (MissingMappingsEvent.Mapping<T> mapping : event.getMappings(key, NAMESPACE)) {
            String target = ids.get(mapping.getKey().getPath());
            if (target == null) continue;
            T value = registry.getValue(new ResourceLocation(target));
            if (value == null) continue;
            mapping.remap(value);
            ImmersiveJewelry.LOGGER.info("Remapped legacy {} {} to {}", key.location().getPath(), mapping.getKey(), target);
        }
    }

    // ------------------------------------------------------------------------------------------------ item data

    /** Converts a legacy piece's tag in place. Idempotent: a tag that already has Immersive Jewelry data is left alone. */
    public static void convertInPlace(JewelryItem item, CompoundTag tag) {
        if (!tag.contains(LEGACY_TAG, Tag.TAG_COMPOUND) || tag.contains(JewelryNbt.ROOT, Tag.TAG_COMPOUND)) return;
        if (!CommonConfig.get(CommonConfig.ENABLE_LEGACY_CONVERSION)) return;
        CompoundTag legacy = tag.getCompound(LEGACY_TAG);
        JewelryData data = convert(item.form(), legacy);
        CompoundTag root = data.save();
        root.put("Legacy", legacy.copy());
        root.putInt("Version", JewelryNbt.VERSION);
        tag.put(JewelryNbt.ROOT, root);
        tag.remove(LEGACY_TAG);
        if (CommonConfig.get(CommonConfig.DEBUG_LOGGING)) {
            ImmersiveJewelry.LOGGER.info("Converted legacy Jewelcraft {} ({} / {})", item.form(), legacy.getString("metal"), legacy.getString("gem"));
        }
    }

    public static JewelryData convert(ResourceLocation form, CompoundTag legacy) {
        MetalRef metal = MetalRef.of(metalId(legacy.getString("metal")));
        ResourceLocation gemId = gemId(legacy);
        float q = DEFAULT_QUALITY;
        GemData stone = new GemData(gemId, 1F, 1F, q, 0.2F, ImmersiveJewelry.id("round"), q, GemDamage.NONE, 1F);
        return new JewelryData(form, metal, List.of(stone), null, ImmersiveJewelry.id("polished"), q, q,
                Workmanship.compute(q, q, q), 1F, 0, Set.of(), null, true, null);
    }

    public static ResourceLocation metalId(String legacy) {
        String mapped = METALS.get(legacy);
        ResourceLocation id = ResourceLocation.tryParse(mapped != null ? mapped : "forge:" + legacy);
        return id != null ? id : new ResourceLocation("minecraft", "iron");
    }

    /** The gem the legacy inset item is a source of, else the legacy gem name mapped, else {@code forge:<name>}. */
    public static ResourceLocation gemId(CompoundTag legacy) {
        if (legacy.contains("inset", Tag.TAG_COMPOUND)) {
            ItemStack inset = ItemStack.of(legacy.getCompound("inset"));
            GemDefinition gem = JewelryDefinitions.server().gemForSource(inset);
            if (gem != null) return gem.id();
        }
        String name = legacy.getString("gem");
        String mapped = GEMS.get(name);
        ResourceLocation id = ResourceLocation.tryParse(mapped != null ? mapped : "forge:" + name);
        return id != null ? id : ImmersiveJewelry.id("diamond");
    }

    /** The original inset item of a converted piece, if one was recorded. */
    public static ItemStack legacyInset(ItemStack piece) {
        CompoundTag root = JewelryNbt.root(piece);
        if (root == null || !root.contains("Legacy", Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        CompoundTag legacy = root.getCompound("Legacy");
        if (!legacy.contains("inset", Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        ItemStack inset = ItemStack.of(legacy.getCompound("inset"));
        if (!inset.isEmpty()) inset.setCount(1);
        return inset;
    }

    private LegacyJewelcraft() {}
}
