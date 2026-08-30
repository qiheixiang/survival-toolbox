package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 矿透面剔除/光照 Mixin（BlockState 基类）
 * <p>
 * 矿透激活时：
 * <ul>
 *   <li>{@code canOcclude} 返回 false：面剔除（cull）不会生效，矿石被石头包围时
 *       六个面全部渲染，埋在墙里的矿石才能完整看见。</li>
 *   <li>{@code isSolidRender} 返回 false：光照/区块可见性不受遮挡方块影响。</li>
 * </ul>
 * </p>
 */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$xrayNoCull(CallbackInfoReturnable<Boolean> cir) {
        if (XrayOreHelper.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isSolidRender", at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$xrayNotSolid(BlockGetter level, BlockPos pos,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (XrayOreHelper.isActive()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 直接拦截区块编译实际调用的无参 getRenderShape()：
     * 白名单之外的方块（含熔炉/发射器等完整方块）一律 INVISIBLE。
     */
    @Inject(method = "getRenderShape()Lnet/minecraft/world/level/block/RenderShape;",
            at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$xrayRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        if (XrayOreHelper.isActive()
                && !XrayOreHelper.isEnabled((net.minecraft.world.level.block.state.BlockState) (Object) this)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}
