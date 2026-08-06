package com.zzq.survival_toolbox.client.event;

import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.client.DynamicLightManager;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD 事件处理器
 * <p>
 * 负责渲染自适应护盾条和处理动态光源。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class HudEventHandler {

    private static final DynamicLightManager lightManager = new DynamicLightManager();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        lightManager.onClientTick(event);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DynamicLightManager.clearAll();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        Player player = mc.player;
        float[] shieldValues = getTotalShield(player);
        float current = shieldValues[0];
        float max = shieldValues[1];
        if (max <= 0) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int width = ModConfig.CLIENT.shieldBarWidth.get();
        int height = ModConfig.CLIENT.shieldBarHeight.get();
        if (width <= 0 || height <= 0) return;

        int x = ModConfig.CLIENT.shieldBarX.get();
        int y = ModConfig.CLIENT.shieldBarY.get();

        if (x == -1) {
            x = (screenWidth - width) / 2;
        }
        if (y == -1) {
            y = screenHeight - 49 - height - 10;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().setIdentity();

        drawShieldBar(guiGraphics, x, y, width, height, current, max);

        guiGraphics.pose().popPose();
    }

    private static float[] getTotalShield(Player player) {
        float totalCurrent = 0;
        float totalMax = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.getEnchantmentLevel(ModEnchantments.ADAPTATION.get()) > 0) {
                totalCurrent += armor.getOrCreateTag().getFloat("adapt_shield_current");
                totalMax += armor.getOrCreateTag().getFloat("adapt_shield_max");
            }
        }
        return new float[]{totalCurrent, totalMax};
    }

    private static void drawShieldBar(GuiGraphics guiGraphics, int x, int y,
                                      int width, int height, float current, float max) {
        boolean showText = ModConfig.CLIENT.showShieldBarText.get();
        if (showText) {
            String text = String.format("%.0f / %.0f", current, max);
            int textColor = 0xFFFFFF;
            int textWidth = Minecraft.getInstance().font.width(text);
            int textX = x + (width - textWidth) / 2;
            int textY = y - 8;
            guiGraphics.drawString(Minecraft.getInstance().font, text, textX, textY, textColor, true);
        }

        guiGraphics.fill(x, y, x + width, y + height, 0x66000000);

        float ratio = Math.min(1.0f, current / max);
        int fillWidth = (int) (width * ratio);
        if (fillWidth > 0) {
            int color = ratio < 0.2f ? 0xFFFF6633 : 0xFF33CCFF;
            guiGraphics.fill(x, y, x + fillWidth, y + height, color);
        }

        guiGraphics.fill(x, y, x + width, y + 1, 0xFFAAAAAA);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFFAAAAAA);
        guiGraphics.fill(x, y, x + 1, y + height, 0xFFAAAAAA);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFFAAAAAA);
    }
}