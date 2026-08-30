package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.XrayOreHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlockEntityRenderDispatcher Mixin（仅客户端）
 * <p>
 * 透视激活时，白名单之外的方块实体（箱子/熔炉/酿造台及各种 mod 工作台、
 * 装饰方块等）不渲染。这些方块走 BEWLR 渲染，不受 getRenderShape 控制，
 * 需要在这里单独拦截才能随列表开关显隐。
 * </p>
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void zzq_hideBlockEntity(BlockEntity be, float partialTick,
                                     PoseStack pose, MultiBufferSource buffer, CallbackInfo ci) {
        if (XrayOreHelper.isActive() && !XrayOreHelper.isEnabled(be.getBlockState())) {
            ci.cancel();
        }
    }

    /**
     * 最终渲染入口拦截：所有方块实体渲染（含 Create 齿轮等自定义渲染器）都经过
     * setupAndRender，在这里拦截确保任何 BEWLR 都随列表开关显隐。
     */
    @Inject(method = "setupAndRender(Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true)
    private static void zzq_hideBlockEntityFinal(net.minecraft.client.renderer.blockentity.BlockEntityRenderer<?> renderer,
                                                 BlockEntity be, float partialTick,
                                                 PoseStack pose, MultiBufferSource buffer, CallbackInfo ci) {
        if (XrayOreHelper.isActive() && !XrayOreHelper.isEnabled(be.getBlockState())) {
            ci.cancel();
        }
    }
}
