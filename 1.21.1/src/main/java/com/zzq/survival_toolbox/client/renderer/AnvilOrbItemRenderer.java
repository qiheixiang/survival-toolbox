package com.zzq.survival_toolbox.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 铁砧球物品渲染器
 * <p>
 * 在物品栏中渲染铁砧球，由内部的铁砧和外部的玻璃罩组成。
 * 根据显示上下文自动调整缩放比例。
 * </p>
 */
public class AnvilOrbItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final AnvilOrbItemRenderer INSTANCE;

    static {
        INSTANCE = new AnvilOrbItemRenderer();
    }

    private AnvilOrbItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer,
                             int combinedLight, int combinedOverlay) {
        var itemRenderer = Minecraft.getInstance().getItemRenderer();

        // ---- 根据上下文设置缩放 ----
        float scale;
        switch (displayContext) {
            case GUI:
                scale = 0.8f;
                break;
            case GROUND:
                scale = 0.6f;
                break;
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                scale = 0.5f;
                break;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                scale = 0.4f;
                break;
            default:
                scale = 0.6f;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(scale, scale, scale);

        // ---- 渲染铁砧（内部） ----
        poseStack.pushPose();
        poseStack.scale(0.7f, 0.7f, 0.7f);
        itemRenderer.renderStatic(
                new ItemStack(Items.ANVIL),
                displayContext,
                combinedLight,
                combinedOverlay,
                poseStack,
                buffer,
                Minecraft.getInstance().level,
                0
        );
        poseStack.popPose();

        // ---- 渲染玻璃罩（外部） ----
        poseStack.pushPose();
        poseStack.scale(1.1f, 1.1f, 1.1f);
        itemRenderer.renderStatic(
                new ItemStack(Items.GLASS),
                displayContext,
                combinedLight,
                combinedOverlay,
                poseStack,
                buffer,
                Minecraft.getInstance().level,
                0
        );
        poseStack.popPose();

        poseStack.popPose();
    }
}