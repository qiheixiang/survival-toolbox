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
 * 矿透亮度 Mixin（无 AO 渲染路径，仅客户端）
 * <p>
 * 部分方块渲染（无 AO 路径）直接调用静态 {@code LevelRenderer.getLightColor}，
 * 矿透激活时矿石返回全亮打包光照（15728880），与 {@link ModelBlockRendererMixin} 互补。
 * </p>
 */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class LevelRendererLightMixin {

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    private static void zzq_survival_toolbox$xrayFullBright(BlockAndTintGetter level, BlockState state, BlockPos pos,
                                                            CallbackInfoReturnable<Integer> cir) {
        if (XrayOreHelper.isActive()) {
            cir.setReturnValue(15728880); // 全亮：天空光 15 + 方块光 15
        }
    }
}
