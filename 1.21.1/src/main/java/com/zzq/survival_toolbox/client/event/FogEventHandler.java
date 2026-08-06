package com.zzq.survival_toolbox.client.event;

import net.neoforged.fml.common.EventBusSubscriber;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.List;

/**
 * 迷雾适应渲染处理器
 * <p>
 * 当玩家穿戴已适应迷雾环境的自适应盔甲时，取消水下迷雾效果。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class FogEventHandler {

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        var eyeFluidType = player.getEyeInFluidType();
        if (eyeFluidType == null || eyeFluidType.isAir()) return;

        if (!AdaptationHelper.mayHaveAdaptationArmor(player)) return;

        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(player);
        if (armors.isEmpty()) return;

        boolean anyFogAdapted = false;
        for (ItemStack armor : armors) {
            if (AdaptationHelper.isFogAdapted(armor)) {
                anyFogAdapted = true;
                break;
            }
        }

        if (anyFogAdapted) {
            int renderDistance = Minecraft.getInstance().options.renderDistance().get();
            float farDistance = renderDistance * 16f;

            event.setFarPlaneDistance(farDistance);
            event.setNearPlaneDistance(0f);
            event.setCanceled(true);
        }
    }
}