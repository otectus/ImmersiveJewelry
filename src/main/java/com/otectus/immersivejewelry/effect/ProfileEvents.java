package com.otectus.immersivejewelry.effect;

import com.otectus.immersivejewelry.ImmersiveJewelry;
import com.otectus.immersivejewelry.api.effect.JewelryProfile;
import com.otectus.immersivejewelry.registry.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Effects the client predicts: mining speed, jumping and drawing a bow. Both sides read the same profile numbers
 * (the server its own sums, the client the copy the server sent), so prediction and validation agree.
 */
@Mod.EventBusSubscriber(modid = ImmersiveJewelry.MOD_ID)
public final class ProfileEvents {

    static float profile(Player player, String key) {
        if (player instanceof ServerPlayer sp) return JewelryWearers.profile(sp, key);
        return player.level().isClientSide && player.isLocalPlayer() ? ClientProfile.get(key) : 0F;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        float bonus = profile(player, JewelryProfile.MINING_SPEED);
        if (player.getMainHandItem().isCorrectToolForDrops(event.getState())) bonus += profile(player, JewelryProfile.MINING_SPEED_CORRECT_TOOL);
        float speed = event.getNewSpeed() * (1F + Math.min(bonus, 3F));
        float underwater = Math.min(1F, profile(player, JewelryProfile.UNDERWATER_MINING));
        if (underwater > 0F && player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) {
            speed *= 1F + 4F * underwater; // vanilla divides by 5 underwater
        }
        if (speed != event.getNewSpeed()) event.setNewSpeed(speed);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        float boost = Math.min(0.3F, profile(player, JewelryProfile.JUMP_BOOST));
        if (boost <= 0F || player.isShiftKeyDown()) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, motion.y + boost, motion.z);
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !event.getItem().is(ModTags.QUICKDRAW)) return;
        float quickdraw = Math.min(0.5F, profile(player, JewelryProfile.QUICKDRAW));
        if (quickdraw <= 0.02F) return;
        // Every k-th tick of drawing counts twice; k depends only on the draw so far, identical on both sides.
        int k = Math.max(2, Math.round(1F / quickdraw));
        int drawn = event.getItem().getUseDuration() - event.getDuration();
        if (drawn > 0 && drawn % k == 0 && event.getDuration() > 1) event.setDuration(event.getDuration() - 1);
    }

    private ProfileEvents() {}
}
