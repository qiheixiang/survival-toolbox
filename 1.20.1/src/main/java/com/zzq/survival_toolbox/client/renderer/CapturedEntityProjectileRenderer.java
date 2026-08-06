package com.zzq.survival_toolbox.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zzq.survival_toolbox.entity.CapturedEntityProjectile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 被捕获实体投射物渲染器
 * <p>
 * 在游戏世界中渲染飞行中的被捕获实体投射物，
 * 直接渲染投射物中存储的实体模型。
 * </p>
 */
public class CapturedEntityProjectileRenderer extends EntityRenderer<CapturedEntityProjectile> {

    public CapturedEntityProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CapturedEntityProjectile entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        CompoundTag nbt = entity.getCapturedNBT();
        String typeId = entity.getEntityTypeId();
        if (nbt == null || typeId.isEmpty()) return;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(typeId));
        if (type == null) return;

        Entity captured = type.create(entity.level());
        if (captured == null) return;

        captured.load(nbt);

        Minecraft.getInstance().getEntityRenderDispatcher().render(
                captured, 0, 0, 0, 0, partialTicks, poseStack, buffer, packedLight
        );
    }

    @Override
    public ResourceLocation getTextureLocation(CapturedEntityProjectile entity) {
        return null;
    }
}