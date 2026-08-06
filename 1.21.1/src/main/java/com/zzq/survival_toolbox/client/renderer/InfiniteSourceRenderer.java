package com.zzq.survival_toolbox.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzq.survival_toolbox.block.entity.InfiniteSourceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 无限之源方块实体渲染器
 * <p>
 * 在方块中渲染标记物物品的 3D 模型，使其缓慢旋转展示。
 * </p>
 */
public class InfiniteSourceRenderer implements BlockEntityRenderer<InfiniteSourceBlockEntity> {

    public InfiniteSourceRenderer(BlockEntityRendererProvider.Context context) {
        // 无额外初始化
    }

    @Override
    public void render(InfiniteSourceBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        ItemStack stack = be.getItem(InfiniteSourceBlockEntity.SLOT_MARKER);
        if (stack.isEmpty()) return;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.25, 0.5);
        poseStack.scale(0.6f, 0.6f, 0.6f);

        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
        poseStack.mulPose(Axis.YP.rotationDegrees(gameTime * 0.5f % 360));

        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                packedOverlay,
                poseStack,
                buffer,
                be.getLevel(),
                0
        );

        poseStack.popPose();
    }
}