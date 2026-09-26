package com.otectus.immersivejewelry.data;

import com.google.gson.JsonObject;
import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.jewelry.MetalRef;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.data.metal.MetalDefinition;
import com.otectus.immersivejewelry.data.trait.TraitDefinition;
import com.otectus.immersivejewelry.util.Inventories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * An immutable snapshot of every jewelry definition. The server builds one after each datapack load (file definitions
 * plus auto-detected gems) and sends its JSON to clients, which parse their own copy. The two copies are kept apart
 * because an integrated server shares this class with its client.
 */
public final class JewelryDefinitions {
    public static final JewelryDefinitions EMPTY = new JewelryDefinitions(new EnumMap<>(DefinitionKind.class), s -> {});

    private static volatile JewelryDefinitions server = EMPTY;
    private static volatile JewelryDefinitions client = EMPTY;

    private final Map<DefinitionKind, Map<ResourceLocation, String>> raw = new EnumMap<>(DefinitionKind.class);
    private final Map<ResourceLocation, FormDefinition> forms;
    private final Map<ResourceLocation, CutDefinition> cuts;
    private final Map<ResourceLocation, FinishDefinition> finishes;
    private final Map<ResourceLocation, MetalDefinition> metals;
    private final Map<ResourceLocation, TraitDefinition> traits;
    private final Map<ResourceLocation, GemDefinition> gems;
    private final Map<Item, FormDefinition> formsByItem = new HashMap<>();
    private final Map<Item, FormDefinition> formsByBlank = new HashMap<>();
    private volatile Map<Item, GemDefinition> gemsBySource;

    private JewelryDefinitions(Map<DefinitionKind, Map<ResourceLocation, JsonObject>> json, Consumer<String> errors) {
        for (DefinitionKind kind : DefinitionKind.values()) {
            Map<ResourceLocation, String> texts = new LinkedHashMap<>();
            json.getOrDefault(kind, Map.of()).forEach((id, o) -> texts.put(id, o.toString()));
            raw.put(kind, texts);
        }
        forms = parse(json, DefinitionKind.FORMS, FormDefinition::parse, FormDefinition::sortOrder, errors);
        cuts = parse(json, DefinitionKind.CUTS, CutDefinition::parse, CutDefinition::sortOrder, errors);
        finishes = parse(json, DefinitionKind.FINISHES, FinishDefinition::parse, FinishDefinition::sortOrder, errors);
        metals = parse(json, DefinitionKind.METALS, MetalDefinition::parse, m -> 0, errors);
        traits = parse(json, DefinitionKind.TRAITS, TraitDefinition::parse, t -> 0, errors);
        Map<ResourceLocation, GemDefinition> parsedGems = parse(json, DefinitionKind.GEMS, GemDefinition::parse, g -> g.auto() ? 1 : 0, errors);
        Map<ResourceLocation, GemDefinition> checked = new LinkedHashMap<>();
        parsedGems.forEach((id, gem) -> {
            if (!traits.containsKey(gem.trait())) {
                errors.accept("Gem " + id + " uses unknown trait " + gem.trait() + "; it keeps the generic trait");
            }
            for (ResourceLocation cut : gem.supportedCuts()) {
                if (!cuts.containsKey(cut)) errors.accept("Gem " + id + " lists unknown cut " + cut);
            }
            checked.put(id, gem);
        });
        gems = checked;
        for (FormDefinition form : forms.values()) {
            Item item = ForgeRegistries.ITEMS.getValue(form.item());
            Item blank = ForgeRegistries.ITEMS.getValue(form.blank());
            if (item == null || item == net.minecraft.world.item.Items.AIR) errors.accept("Form " + form.id() + " names unknown item " + form.item());
            else formsByItem.put(item, form);
            if (blank == null || blank == net.minecraft.world.item.Items.AIR) errors.accept("Form " + form.id() + " names unknown blank " + form.blank());
            else formsByBlank.put(blank, form);
        }
    }

    public static JewelryDefinitions build(Map<DefinitionKind, Map<ResourceLocation, JsonObject>> json, Consumer<String> errors) {
        return new JewelryDefinitions(json, errors);
    }

    // ------------------------------------------------------------------------------------------------ access

    public static JewelryDefinitions server() {
        return server;
    }

    public static JewelryDefinitions client() {
        return client;
    }

    public static void setServer(JewelryDefinitions definitions) {
        server = definitions;
    }

    public static void setClient(JewelryDefinitions definitions) {
        client = definitions;
    }

    /** The copy for the side the calling thread belongs to. */
    public static JewelryDefinitions current() {
        return EffectiveSide.get().isClient() ? client : server;
    }

    public static JewelryDefinitions get(@Nullable Level level) {
        if (level == null) return current();
        return level.isClientSide ? client : server;
    }

    public Map<DefinitionKind, Map<ResourceLocation, String>> raw() {
        return raw;
    }

    public boolean isEmpty() {
        return gems.isEmpty() && forms.isEmpty();
    }

    public Collection<FormDefinition> forms() { return forms.values(); }
    public Collection<CutDefinition> cuts() { return cuts.values(); }
    public Collection<FinishDefinition> finishes() { return finishes.values(); }
    public Collection<MetalDefinition> metals() { return metals.values(); }
    public Collection<TraitDefinition> traits() { return traits.values(); }
    public Collection<GemDefinition> gems() { return gems.values(); }

    @Nullable public FormDefinition form(@Nullable ResourceLocation id) { return id == null ? null : forms.get(id); }
    @Nullable public CutDefinition cut(@Nullable ResourceLocation id) { return id == null ? null : cuts.get(id); }
    @Nullable public FinishDefinition finish(@Nullable ResourceLocation id) { return id == null ? null : finishes.get(id); }
    @Nullable public TraitDefinition trait(@Nullable ResourceLocation id) { return id == null ? null : traits.get(id); }
    @Nullable public GemDefinition gem(@Nullable ResourceLocation id) { return id == null ? null : gems.get(id); }
    /** The gem's definition, or a neutral generic profile for an id no definition covers (a removed datapack gem). */
    public GemDefinition gemOrGeneric(ResourceLocation id) {
        GemDefinition gem = gems.get(id);
        return gem != null ? gem : GemDefinition.generic(id);
    }

    @Nullable public MetalDefinition metalDefinition(@Nullable ResourceLocation id) { return id == null ? null : metals.get(id); }

    /** The metal's definition, or a generic profile named and tinted from the reference. */
    public MetalDefinition metal(MetalRef ref) {
        MetalDefinition def = metals.get(ref.id());
        return def != null ? def : MetalDefinition.generic(ref);
    }

    @Nullable
    public FormDefinition formForItem(Item item) {
        return formsByItem.get(item);
    }

    @Nullable
    public FormDefinition formForBlank(Item item) {
        return formsByBlank.get(item);
    }

    /** The generic trait for gems whose own trait is missing. */
    @Nullable
    public TraitDefinition traitFor(GemDefinition gem) {
        TraitDefinition trait = traits.get(gem.trait());
        return trait != null ? trait : traits.get(GemDefinition.GENERIC_TRAIT);
    }

    /** The gem a raw item (a diamond, a modded ruby) is a rough stone of, or null. */
    @Nullable
    public GemDefinition gemForSource(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Map<Item, GemDefinition> map = gemsBySource;
        if (map == null) {
            map = new HashMap<>();
            for (GemDefinition gem : gems.values()) {
                for (ItemStack s : Inventories.items(gem.ingredient())) map.putIfAbsent(s.getItem(), gem);
            }
            gemsBySource = map;
        }
        return map.get(stack.getItem());
    }

    /** Tags changed: the source lookup is rebuilt on next use. */
    public void invalidateSources() {
        gemsBySource = null;
    }

    public List<CutDefinition> cutsFor(GemDefinition gem) {
        List<CutDefinition> out = new ArrayList<>();
        for (CutDefinition cut : cuts.values()) {
            if (gem.supports(cut.id())) out.add(cut);
        }
        return out;
    }

    private static <T> Map<ResourceLocation, T> parse(Map<DefinitionKind, Map<ResourceLocation, JsonObject>> json, DefinitionKind kind,
                                                      BiFunction<ResourceLocation, JsonObject, T> parser, ToIntFunction<T> order,
                                                      Consumer<String> errors) {
        List<Map.Entry<ResourceLocation, T>> parsed = new ArrayList<>();
        json.getOrDefault(kind, Map.of()).forEach((id, o) -> {
            try {
                parsed.add(Map.entry(id, parser.apply(id, o)));
            } catch (RuntimeException e) {
                errors.accept("Invalid " + kind.folder() + " definition " + id + ": " + e.getMessage());
            }
        });
        parsed.sort(Comparator.<Map.Entry<ResourceLocation, T>>comparingInt(e -> order.applyAsInt(e.getValue()))
                .thenComparing(e -> e.getKey().toString()));
        Map<ResourceLocation, T> out = new LinkedHashMap<>();
        parsed.forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    public static void logErrors(List<String> errors, String side) {
        if (errors.isEmpty()) return;
        ImmersiveJewelry.LOGGER.warn("{} jewelry definition problem(s) ({}):", errors.size(), side);
        errors.forEach(e -> ImmersiveJewelry.LOGGER.warn("  {}", e));
    }
}
