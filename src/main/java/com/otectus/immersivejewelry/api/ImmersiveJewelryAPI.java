package com.otectus.immersivejewelry.api;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.effect.JewelryEffectType;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryAssistSource;
import com.otectus.immersivejewelry.api.integration.IJewelryCraftGate;
import com.otectus.immersivejewelry.api.integration.IJewelryEffectContributor;
import com.otectus.immersivejewelry.api.integration.IJewelryEquipmentProvider;
import com.otectus.immersivejewelry.api.integration.IJewelryWearGate;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Public entry point (spec 69). Datapacks describe gems, cuts, finishes, forms, metals and traits; code adds effect
 * types (behaviours traits can use), equipment providers and effect contributors (effects from another mod's data on
 * a piece). Register from your mod constructor or
 * {@code FMLCommonSetupEvent}, or send an IMC message to {@code immersive_jewelry}:
 * <ul>
 *   <li>{@value #IMC_EFFECT_TYPE}: a {@code Supplier<Map.Entry<ResourceLocation, JewelryEffectType>>}</li>
 *   <li>{@value #IMC_EQUIPMENT_PROVIDER}: a {@code Supplier<IJewelryEquipmentProvider>}</li>
 *   <li>{@value #IMC_EFFECT_CONTRIBUTOR}: a {@code Supplier<IJewelryEffectContributor>}</li>
 * </ul>
 * Events: {@code com.otectus.immersivejewelry.api.event} (crafted, cut, equipped/unequipped, reworked, broken).
 */
public final class ImmersiveJewelryAPI {
    public static final String IMC_EFFECT_TYPE = "effect_type";
    public static final String IMC_EQUIPMENT_PROVIDER = "equipment_provider";
    public static final String IMC_EFFECT_CONTRIBUTOR = "effect_contributor";
    public static final String IMC_CRAFT_GATE = "craft_gate";
    public static final String IMC_WEAR_GATE = "wear_gate";
    public static final String IMC_ASSIST_SOURCE = "assist_source";

    private static final Map<ResourceLocation, JewelryEffectType> EFFECT_TYPES = new ConcurrentHashMap<>();
    private static final List<IJewelryEquipmentProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<IJewelryEffectContributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();
    private static final List<IJewelryCraftGate> CRAFT_GATES = new CopyOnWriteArrayList<>();
    private static final List<IJewelryWearGate> WEAR_GATES = new CopyOnWriteArrayList<>();
    private static final List<IJewelryAssistSource> ASSIST_SOURCES = new CopyOnWriteArrayList<>();

    public static void registerEffectType(ResourceLocation id, JewelryEffectType type) {
        if (EFFECT_TYPES.putIfAbsent(id, type) != null) {
            ImmersiveJewelry.LOGGER.warn("Jewelry effect type {} registered twice; keeping the first", id);
        }
    }

    @Nullable
    public static JewelryEffectType effectType(ResourceLocation id) {
        return EFFECT_TYPES.get(id);
    }

    public static Map<ResourceLocation, JewelryEffectType> effectTypes() {
        return Collections.unmodifiableMap(EFFECT_TYPES);
    }

    public static void registerEquipmentProvider(IJewelryEquipmentProvider provider) {
        PROVIDERS.add(provider);
    }

    public static List<IJewelryEquipmentProvider> equipmentProviders() {
        return Collections.unmodifiableList(PROVIDERS);
    }

    public static void registerEffectContributor(IJewelryEffectContributor contributor) {
        CONTRIBUTORS.add(contributor);
    }

    public static List<IJewelryEffectContributor> effectContributors() {
        return Collections.unmodifiableList(CONTRIBUTORS);
    }

    // ---- 1.1.0: craft gates, wear gates and assistance (progression hooks) ---------------------------------------

    public static void registerCraftGate(IJewelryCraftGate gate) { if (gate != null) CRAFT_GATES.add(gate); }
    public static void unregisterCraftGate(IJewelryCraftGate gate) { CRAFT_GATES.remove(gate); }
    public static void registerWearGate(IJewelryWearGate gate) { if (gate != null) WEAR_GATES.add(gate); }
    public static void unregisterWearGate(IJewelryWearGate gate) { WEAR_GATES.remove(gate); }
    public static void registerAssistSource(IJewelryAssistSource source) { if (source != null) ASSIST_SOURCES.add(source); }
    public static void unregisterAssistSource(IJewelryAssistSource source) { ASSIST_SOURCES.remove(source); }
    public static List<IJewelryCraftGate> craftGates() { return Collections.unmodifiableList(CRAFT_GATES); }
    public static List<IJewelryWearGate> wearGates() { return Collections.unmodifiableList(WEAR_GATES); }
    public static List<IJewelryAssistSource> assistSources() { return Collections.unmodifiableList(ASSIST_SOURCES); }

    /** The first registered reason to refuse this setting, if any. A gate that throws is skipped. */
    public static Optional<Component> craftDenial(ServerPlayer jeweler, JewelryDesignRecipe design, BlankData blank, List<GemData> stones) {
        for (IJewelryCraftGate gate : CRAFT_GATES) {
            try {
                Optional<Component> reason = gate.deny(jeweler, design, blank, stones);
                if (reason != null && reason.isPresent()) return reason;
            } catch (RuntimeException e) {
                ImmersiveJewelry.LOGGER.error("Craft gate {} failed for {}", gate.getClass().getName(), design.id(), e);
            }
        }
        return Optional.empty();
    }

    /** Whether every registered wear gate lets {@code player} keep {@code piece} active. True with no gates; a gate that throws allows. */
    public static boolean mayWear(Player player, ItemStack piece, JewelryData data) {
        for (IJewelryWearGate gate : WEAR_GATES) {
            try {
                if (!gate.mayWear(player, piece, data)) return false;
            } catch (RuntimeException e) {
                ImmersiveJewelry.LOGGER.error("Wear gate {} failed for {}", gate.getClass().getName(), player.getGameProfile().getName(), e);
            }
        }
        return true;
    }

    /** {@link #mayWear(Player, ItemStack, JewelryData)} for a stack that may or may not be jewelry; non-jewelry is always allowed. */
    public static boolean mayWear(Player player, ItemStack piece) {
        Optional<JewelryData> data = JewelryData.get(piece);
        return data.isEmpty() || mayWear(player, piece, data.get());
    }

    /**
     * The difficulty multiplier assistance grants {@code player} for {@code kind}: the product of every source's answer,
     * clamped to {@code minAssistDifficulty..1.0}. Exactly {@code 1.0} with no sources.
     */
    public static float assistDifficulty(ServerPlayer player, IJewelryAssistSource.Kind kind) {
        double product = 1.0;
        for (IJewelryAssistSource source : ASSIST_SOURCES) {
            try {
                double value = source.difficultyMultiplier(player, kind);
                if (Double.isFinite(value) && value > 0) product *= value;
            } catch (RuntimeException e) {
                ImmersiveJewelry.LOGGER.error("Assist source {} failed for {}", source.getClass().getName(), player.getGameProfile().getName(), e);
            }
        }
        double floor = Math.min(1.0, ServerConfig.get(ServerConfig.MIN_ASSIST_DIFFICULTY));
        return (float) Math.max(floor, Math.min(1.0, product));
    }

    /** The composition of a finished piece. */
    public static Optional<JewelryData> jewelry(ItemStack stack) {
        return JewelryData.get(stack);
    }

    /** The data of a rough or cut stone. */
    public static Optional<GemData> gem(ItemStack stack) {
        return GemData.load(JewelryNbt.root(stack));
    }

    /** The data of a blank or frame. */
    public static Optional<BlankData> blank(ItemStack stack) {
        return BlankData.get(stack);
    }

    /** The resolved power of a piece (about 1.0 when well made), on the side of {@code level}. */
    public static float power(ItemStack stack, @Nullable Level level) {
        return JewelryData.get(stack).map(d -> JewelryResolver.resolve(d, JewelryDefinitions.get(level)).basePower()).orElse(0F);
    }

    /** Every jewelry stack the player wears, across all equipment systems (server side). */
    public static List<ItemStack> worn(Player player) {
        return JewelryEquipment.worn(player).values().stream().toList();
    }

    @SuppressWarnings("unchecked")
    public static void processIMC(InterModProcessEvent event) {
        InterModComms.getMessages(ImmersiveJewelry.MOD_ID).forEach(msg -> {
            try {
                Object payload = ((Supplier<Object>) msg.messageSupplier()).get();
                switch (msg.method()) {
                    case IMC_EFFECT_TYPE -> {
                        Map.Entry<ResourceLocation, JewelryEffectType> entry = (Map.Entry<ResourceLocation, JewelryEffectType>) payload;
                        registerEffectType(entry.getKey(), entry.getValue());
                    }
                    case IMC_EQUIPMENT_PROVIDER -> registerEquipmentProvider((IJewelryEquipmentProvider) payload);
                    case IMC_EFFECT_CONTRIBUTOR -> registerEffectContributor((IJewelryEffectContributor) payload);
                    case IMC_CRAFT_GATE -> registerCraftGate((IJewelryCraftGate) payload);
                    case IMC_WEAR_GATE -> registerWearGate((IJewelryWearGate) payload);
                    case IMC_ASSIST_SOURCE -> registerAssistSource((IJewelryAssistSource) payload);
                    default -> ImmersiveJewelry.LOGGER.warn("Unknown IMC method '{}' from {}", msg.method(), msg.senderModId());
                }
            } catch (ClassCastException e) {
                ImmersiveJewelry.LOGGER.error("IMC message '{}' from {} has the wrong payload type", msg.method(), msg.senderModId());
            }
        });
    }

    private ImmersiveJewelryAPI() {}
}
