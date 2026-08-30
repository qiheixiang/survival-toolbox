package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 矿透渲染 Mixin（方块主体）
 * <p>
 * 手持透视眼镜时，将非矿石方块的渲染形状改为 {@link RenderShape#INVISIBLE}：
 * 区块编译时这些方块直接不渲染（墙变透明），矿石正常显示，实现透视效果。
 * 面剔除（canOcclude）与光照（isSolidRender）处理见 {@link BlockStateBaseMixin}。
 * </p>
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {

    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$xrayGoggles(BlockState state, CallbackInfoReturnable<RenderShape> cir) {
        if (XrayOreHelper.isActive() && !XrayOreHelper.isEnabled(state)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}
