package com.zzq.survival_toolbox.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzq.survival_toolbox.entity.AnvilOrbProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 铁砧球投射物实体渲染器
 * <p>
 * 在游戏世界中渲染飞行中的铁砧球投射物。
 * </p>
 */
public class AnvilOrbRenderer extends EntityRenderer<AnvilOrbProjectile> {

    public AnvilOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AnvilOrbProjectile entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        poseStack.pushPose();

        poseStack.scale(0.6f, 0.6f, 0.6f);
        poseStack.translate(0.0D, 0.15D, 0.0D);

        var itemRenderer = Minecraft.getInstance().getItemRenderer();

        // 渲染铁砧（内部）
        poseStack.pushPose();
        poseStack.scale(0.7f, 0.7f, 0.7f);
        itemRenderer.renderStatic(
                new ItemStack(Items.ANVIL),
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                0
        );
        poseStack.popPose();

        // 渲染玻璃罩（外部）
        poseStack.pushPose();
        poseStack.scale(1.1f, 1.1f, 1.1f);
        itemRenderer.renderStatic(
                new ItemStack(Items.GLASS),
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                0
        );
        poseStack.popPose();

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AnvilOrbProjectile entity) {
        return new ResourceLocation("minecraft", "textures/block/glass.png");
    }
}