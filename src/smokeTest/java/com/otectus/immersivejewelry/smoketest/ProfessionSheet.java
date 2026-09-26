package com.otectus.immersivejewelry.smoketest;

import com.otectus.immersivejewelry.registry.ModVillagers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import org.joml.Quaternionf;

/** Test-only preview of both profession-layer meshes through Minecraft's entity renderer. */
final class ProfessionSheet extends Screen {
    private final Villager villager;
    private final ZombieVillager zombieVillager;

    ProfessionSheet(Minecraft minecraft) {
        super(Component.literal("Jeweler profession artwork"));
        villager = new Villager(EntityType.VILLAGER, minecraft.level);
        zombieVillager = new ZombieVillager(EntityType.ZOMBIE_VILLAGER, minecraft.level);
        configure(villager);
        configure(zombieVillager);
    }

    private static void configure(net.minecraft.world.entity.npc.VillagerDataHolder villager) {
        VillagerData data = villager.getVillagerData();
        villager.setVillagerData(new VillagerData(data.getType(), ModVillagers.JEWELER.get(), 5));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF242428);
        graphics.drawCenteredString(font, "JEWELER PROFESSION OVERLAYS", width / 2, 12, 0xFFECD7AC);
        graphics.drawCenteredString(font, "Front and three-quarter views use Minecraft's villager render layers", width / 2, 27, 0xFFB9AF9E);

        int bottom = height - 22;
        int entityY = Math.min(bottom - 22, 205);
        int[] centers = {width / 8, width * 3 / 8, width * 5 / 8, width * 7 / 8};
        String[] labels = {"Villager / front", "Villager / three-quarter", "Zombie villager / front", "Zombie villager / three-quarter"};
        for (int center : centers) graphics.fill(center - 70, 47, center + 70, bottom, 0xFF343236);

        renderEntity(graphics, centers[0], entityY, villager, 0F);
        renderEntity(graphics, centers[1], entityY, villager, 22F);
        renderEntity(graphics, centers[2], entityY, zombieVillager, 0F);
        renderEntity(graphics, centers[3], entityY, zombieVillager, 22F);
        for (int i = 0; i < labels.length; i++) graphics.drawCenteredString(font, labels[i], centers[i], bottom + 5, 0xFFDBCEC0);
    }

    private static void renderEntity(GuiGraphics graphics, int x, int y, LivingEntity entity, float yaw) {
        // InventoryScreen's mouse helper faces an entity toward the viewer at 180 degrees. Its X-axis quaternion is
        // camera pitch, so the three-quarter angle belongs on the entity's body/head yaw instead.
        float facing = 180F + yaw;
        entity.setYRot(facing);
        entity.setYBodyRot(facing);
        entity.setYHeadRot(facing);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        InventoryScreen.renderEntityInInventory(graphics, x, y, 64, pose, new Quaternionf(), entity);
    }
}
