package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium/Sodium 系方块光照全亮（仅客户端）
 * <p>
 * 整合包（Embeddium/Oculus 渲染管线）不经过原版 ModelBlockRenderer/LevelRenderer 的
 * 光照注入，洞穴/矿洞无光所以透视画面是黑的。Sodium 系的光照数据在
 * LightDataAccess 子类（ArrayLightDataCache/HashLightDataCache）的 get(x,y,z) 中返回，
 * 这里在返回值上把"方块光 + 天空光"两个 4bit 字段（低 8 位）全部置 15，
 * 其余字段（AO/标志位）保持原值，矿透激活时整片画面全亮。
 * <p>
 * 包名兼容多个 Sodium 系版本（me.jellysquid = Embeddium 0.3.x / sodium 0.5，
 * net.caffeinemc = sodium 0.6+/Embeddium 1.x），目标缺失时仅警告不崩溃。
 */
@Mixin(targets = {
        "me.jellysquid.mods.sodium.client.model.light.data.ArrayLightDataCache",
        "me.jellysquid.mods.sodium.client.model.light.data.HashLightDataCache",
        "me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess"
}, remap = false)
public class EmbeddiumLightMixin {

    @Inject(method = "get(III)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void zzq_survival_toolbox$xrayFullBright(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (XrayOreHelper.isActive()) {
            // 全位 1：方块光/天空光/亮度/AO/自发光位全部最大（不依赖特定打包布局）
            cir.setReturnValue(-1);
        }
    }

    @Inject(method = "compute(III)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void zzq_survival_toolbox$xrayFullBrightCompute(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (XrayOreHelper.isActive()) {
            cir.setReturnValue(-1);
        }
    }
}
