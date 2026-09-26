package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.api.event.JewelryBrokenEvent;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.config.ServerConfig;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.finish.FinishDefinition;
import com.otectus.immersivejewelry.data.form.FormDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.util.Feedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrity wear (spec 40). Only a single severe hit wears jewelry, by a small amount that metal, finish, form and a
 * protective design resist; routine combat never touches it. On a piece already worn thin, a severe hit may also
 * scratch or chip a stone (brittle stones more easily). At zero Integrity the piece is broken and inert until repaired.
 */
public final class JewelryWear {

    public static void onDamage(ServerPlayer player, float amount) {
        if (!ServerConfig.get(ServerConfig.ENABLE_WEAR)) return;
        if (amount < player.getMaxHealth() * ServerConfig.get(ServerConfig.SEVERE_HIT_FRACTION)) return;
        float wear = (float) ServerConfig.get(ServerConfig.WEAR_PER_SEVERE_HIT);
        if (wear <= 0F) return;
        boolean changed = false;
        for (ItemStack stack : JewelryEquipment.worn(player).values()) {
            JewelryData data = JewelryData.get(stack).orElse(null);
            if (data == null || data.isBroken()) continue;
            JewelryData worn = wear(player, data, wear * Math.min(2F, amount / Math.max(1F, player.getMaxHealth() * 0.35F)));
            worn.apply(stack);
            changed = true;
            if (worn.isBroken()) {
                Feedback.info(player, "jewelry_broken", stack.getHoverName());
                player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6F, 1.4F);
                MinecraftForge.EVENT_BUS.post(new JewelryBrokenEvent(player, stack.copy()));
            }
        }
        if (changed) JewelryWearers.markDirty(player);
    }

    static JewelryData wear(ServerPlayer player, JewelryData data, float wear) {
        JewelryDefinitions defs = JewelryDefinitions.server();
        FormDefinition form = defs.form(data.form());
        FinishDefinition finish = defs.finish(data.finish());
        float resistance = defs.metal(data.metal()).integrity() * (finish != null ? finish.integrity() : 1F)
                * (form != null ? form.baseIntegrity() : 1F) * protection(player, data);
        float integrity = Math.max(0F, data.integrity() - wear / Math.max(0.1F, resistance));
        JewelryData next = data.withIntegrity(integrity);
        if (ServerConfig.get(ServerConfig.ENABLE_GEM_DAMAGE) && integrity < 0.3F && !data.gems().isEmpty()) {
            List<GemData> gems = new ArrayList<>(data.gems());
            int i = player.getRandom().nextInt(gems.size());
            GemData stone = gems.get(i);
            GemDefinition gem = defs.gemOrGeneric(stone.gem());
            float chance = (0.3F - integrity) * 2F * (0.4F + gem.brittleness()) / protection(player, data);
            if (player.getRandom().nextFloat() < chance) {
                gems.set(i, stone.withDamage(stone.damage().worse()));
                next = next.withGems(gems);
            }
        }
        return next;
    }

    private static float protection(ServerPlayer player, JewelryData data) {
        if (data.design() == null) return 1F;
        return player.level().getRecipeManager().byKey(data.design())
                .filter(r -> r instanceof JewelryDesignRecipe).map(r -> ((JewelryDesignRecipe) r).protection()).orElse(1F);
    }

    private JewelryWear() {}
}
