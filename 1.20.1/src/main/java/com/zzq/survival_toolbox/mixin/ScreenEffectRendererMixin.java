package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ScreenEffectRenderer Mixin（仅客户端）
 * <p>
 * 只有当任意一件自适应装备已经"免疫燃烧"且"免疫液体迷雾"时
 * （且配置 adaptBlockScreenEffects 开启），才屏蔽所有屏幕视觉效果：
 * 失明黑雾、火焰红屏、水雾、冰冻、传送门等。
 * 对使用原版效果/覆盖层渲染的"视野模糊"类效果（如口渴的模糊）同样有效；
 * 模组自绘的覆盖层不在本方法内，无法拦截。
 * </p>
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(method = "renderScreenEffect(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void zzq_blockScreenEffects(Minecraft mc, PoseStack poseStack, CallbackInfo ci) {
        if (mc.player == null) return;
        if (!ModConfig.CLIENT.adaptBlockScreenEffects.get()) return;
        // 需先免疫燃烧（fire_adapted）和液体迷雾（fog_adapted）才屏蔽所有视觉效果
        for (ItemStack armor : AdaptationHelper.getAdaptationArmors(mc.player)) {
            if (AdaptationHelper.isFireAdapted(armor) && AdaptationHelper.isFogAdapted(armor)) {
                ci.cancel();
                return;
            }
        }
    }
}
