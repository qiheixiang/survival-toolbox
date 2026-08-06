package com.zzq.survival_toolbox.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzq.survival_toolbox.block.entity.MicroFarmBlockEntity;
import com.zzq.survival_toolbox.item.CapturedEntityItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 微型牧场方块实体渲染器
 * <p>
 * 在方块中渲染被捕获实体的 3D 模型。
 * 有食物时实体缓慢旋转并上下浮动，无食物时静止。
 * </p>
 */
public class MicroFarmRenderer implements BlockEntityRenderer<MicroFarmBlockEntity> {

    public MicroFarmRenderer(BlockEntityRendererProvider.Context context) {
        // 无额外初始化
    }

    @Override
    public void render(MicroFarmBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        ItemStack entityStack = blockEntity.getItem(MicroFarmBlockEntity.SLOT_ENTITY);
        if (entityStack.isEmpty()) return;

        String entityTypeId = CapturedEntityItem.getEntityTypeId(entityStack);
        CompoundTag entityNBT = CapturedEntityItem.getEntityNBT(entityStack);
        if (entityTypeId.isEmpty() || entityNBT == null) return;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityTypeId));
        if (type == null) return;

        // ---- 创建临时实体 ----
        Entity entity = type.create(blockEntity.getLevel());
        if (entity == null) return;
        entity.load(entityNBT);

        // ---- 强制重置动画和旋转 ----
        entity.setYRot(0);
        entity.setXRot(0);
        entity.setYBodyRot(0);
        entity.setYHeadRot(0);
        if (entity instanceof LivingEntity living) {
            living.setNoActionTime(1000);
            living.walkAnimation.setSpeed(0);
            if (living.getBrain() != null) {
                living.getBrain().clearMemories();
            }
        }

        // ---- 检查是否有食物 ----
        boolean hasFood = blockEntity.hasFood();

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        float baseYOffset = -0.30f;
        float floatOffset = 0f;
        float rotation = 0f;

        if (hasFood) {
            float time = blockEntity.getLevel().getGameTime() + partialTick;
            rotation = time * 0.5f % 360f;
            floatOffset = (float) Math.sin(time * 0.3f) * 0.05f;
        }

        poseStack.translate(0, baseYOffset + floatOffset, 0);

        // ---- 方向旋转 ----
        Direction facing = blockEntity.getFacing();
        float yaw = 0;
        switch (facing) {
            case NORTH:
                yaw = 180;
                break;
            case SOUTH:
                yaw = 0;
                break;
            case WEST:
                yaw = -90;
                break;
            case EAST:
                yaw = 90;
                break;
            default:
                yaw = 0;
        }

        if (hasFood) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw + rotation));
        } else {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        }

        // ---- 自适应缩放 ----
        float entitySize = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float scale = entitySize > 0 ? 0.6f / entitySize : 0.6f;
        scale = Math.min(scale, 1.2f);
        poseStack.scale(scale, scale, scale);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(entity, 0, 0, 0, 0, partialTick, poseStack, buffer, packedLight);

        poseStack.popPose();
    }
}