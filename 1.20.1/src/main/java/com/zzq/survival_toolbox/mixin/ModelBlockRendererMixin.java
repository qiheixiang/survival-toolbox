package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 矿透亮度 Mixin（仅客户端）
 * <p>
 * 矿透激活时，所有方块的环境光遮蔽（AO）与打包光照强制全亮。
 * AO 光照为顶点插值模型（顶点亮度取周围邻居方块光照的加权），
 * 因此必须对"所有方块"生效：墙本身不渲染（INVISIBLE），只有矿石可见，无副作用。
 * </p>
 */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$Cache")
public abstract class ModelBlockRendererMixin {

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$xrayBrightness(BlockState state, BlockAndTintGetter level, BlockPos pos,
                                                     CallbackInfoReturnable<Float> cir) {
        if (XrayOreHelper.isActive()) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getLightColor", at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$xrayFullBright(BlockState state, BlockAndTintGetter level, BlockPos pos,
                                                     CallbackInfoReturnable<Integer> cir) {
        if (XrayOreHelper.isActive()) {
            cir.setReturnValue(15728880); // 全亮：天空光 15 + 方块光 15
        }
    }
}
