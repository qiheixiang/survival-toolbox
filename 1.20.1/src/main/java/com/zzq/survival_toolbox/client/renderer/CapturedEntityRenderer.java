package com.zzq.survival_toolbox.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzq.survival_toolbox.item.CapturedEntityItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 被捕获实体物品渲染器
 * <p>
 * 在物品栏中渲染捕获的实体模型。
 * 根据实体尺寸自动缩放，使其在物品栏中显示适中。
 * </p>
 */
public class CapturedEntityRenderer extends BlockEntityWithoutLevelRenderer {

    public static final CapturedEntityRenderer INSTANCE;

    static {
        INSTANCE = new CapturedEntityRenderer();
    }

    private CapturedEntityRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource buffer,
                             int combinedLight, int combinedOverlay) {

        String entityTypeId = CapturedEntityItem.getEntityTypeId(stack);
        if (entityTypeId.isEmpty()) return;

        CompoundTag entityNBT = CapturedEntityItem.getEntityNBT(stack);
        if (entityNBT == null || entityNBT.isEmpty()) return;

        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.parse(entityTypeId)
        );
        if (entityType == null) return;

        var level = Minecraft.getInstance().level;
        if (level == null) return;

        Entity entity = entityType.create(level);
        if (entity == null) return;
        entity.load(entityNBT);

        // ---- 动态计算基础缩放 ----
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        float maxDim = Math.max(dimensions.width, dimensions.height);
        if (maxDim <= 0) maxDim = 1.0F;

        float targetSize = 1.0F;
        float baseScale = targetSize / maxDim;

        // ---- 根据上下文设定倍率 ----
        float contextScale;
        float offsetY;
        float rotationY = 0F;

        switch (displayContext) {
            case GUI:
                contextScale = 0.8F;
                offsetY = -0.5F;
                break;
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                contextScale = 0.35F;
                offsetY = -0.5F;
                break;
            case GROUND:
            case FIXED:
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                contextScale = 0.3F;
                offsetY = -0.6F;
                break;
            default:
                contextScale = 0.5F;
                offsetY = -0.4F;
        }

        float finalScale = baseScale * contextScale;
        float heightOffset = (1.0F - dimensions.height * finalScale) / 2.0F;
        offsetY += heightOffset;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5 + offsetY, 0.5);
        poseStack.scale(finalScale, finalScale, finalScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));
        poseStack.mulPose(Axis.XP.rotationDegrees(15F));

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(entity, 0, 0, 0, 0, 0, poseStack, buffer, 15728880);
        poseStack.popPose();
    }
}