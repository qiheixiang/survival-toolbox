package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * flywheel（Create 内置的实例化渲染引擎）BlockEntity 视觉拦截（仅客户端）
 * <p>
 * Create 的齿轮/轴/传送带/表盘指针等"会动"的机械不走原版渲染链
 * （BlockStateBase.getRenderShape / BlockEntityRenderDispatcher），
 * 而是由 flywheel 以 GPU 实例化（Visual）渲染，因此矿透的
 * getRenderShape/BEWLR 拦截对它们无效。
 * <p>
 * 在 flywheel 创建 BlockEntity 视觉（queueAdd）时拦截：矿透激活且
 * 该方块不在白名单 → 不创建视觉，机械部分整体不渲染。
 * 实体/特效视觉（Entity/Effect）走同一个 VisualManagerImpl 的
 * queueAdd，但参数不是 BlockEntity，不会被误拦。
 * 切换矿透时需 reset flywheel 视觉（见 XrayClientHandler），
 * 否则已存在的视觉不会重建。
 * 必须使用 targets 字符串（而非 value 类引用）：
 * 类引用会让 mixin 在 prepare 阶段加载目标类，在带 Connector 的
 * 整合包中触发 MixinTargetAlreadyLoadedException；字符串仅在
 * 应用时才解析，目标缺失时仅警告不崩溃（无需打包 flywheel）。
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualManagerImpl", remap = false)
public class VisualManagerMixin {

    @Inject(method = "queueAdd", at = @At("HEAD"), cancellable = true, require = 0)
    private void zzq_survival_toolbox$xraySkipVisual(Object target, CallbackInfo ci) {
        if (!XrayOreHelper.isActive()) return;
        if (!(target instanceof BlockEntity be)) return;
        if (!XrayOreHelper.isEnabled(be.getBlockState())) {
            ci.cancel();
        }
    }
}
