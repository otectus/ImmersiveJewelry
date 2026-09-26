package com.otectus.immersivejewelry.api;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.effect.JewelryEffectType;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.integration.IJewelryEffectContributor;
import com.otectus.immersivejewelry.api.integration.IJewelryEquipmentProvider;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.api.jewelry.JewelryNbt;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import net.minecraft.resources.ResourceLocation;
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

    private static final Map<ResourceLocation, JewelryEffectType> EFFECT_TYPES = new ConcurrentHashMap<>();
    private static final List<IJewelryEquipmentProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<IJewelryEffectContributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();

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
                    default -> ImmersiveJewelry.LOGGER.warn("Unknown IMC method '{}' from {}", msg.method(), msg.senderModId());
                }
            } catch (ClassCastException e) {
                ImmersiveJewelry.LOGGER.error("IMC message '{}' from {} has the wrong payload type", msg.method(), msg.senderModId());
            }
        });
    }

    private ImmersiveJewelryAPI() {}
}
