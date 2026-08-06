package com.zzq.survival_toolbox.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzq.survival_toolbox.block.entity.FeastBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 混沌篝火范围渲染器
 * <p>
 * 在客户端渲染混沌篝火的作用范围边框（紫色）。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class FeastRangeRenderEventHandler {

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();

        for (FeastBlockEntity feast : FeastBlockEntity.getClientInstances()) {
            if (feast.isRemoved() || feast.getLevel() == null) continue;
            if (!feast.isShowRange()) continue;

            BlockPos center = feast.getBlockPos();
            if (mc.player.distanceToSqr(center.getX(), center.getY(), center.getZ()) > 128 * 128) continue;

            int rX = feast.getRangeX();
            int rY = feast.getRangeY();
            int rZ = feast.getRangeZ();

            double minX = center.getX() - rX;
            double minY = center.getY() - rY;
            double minZ = center.getZ() - rZ;
            double maxX = center.getX() + rX + 1;
            double maxY = center.getY() + rY + 1;
            double maxZ = center.getZ() + rZ + 1;

            Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
            double x1 = minX - cameraPos.x;
            double y1 = minY - cameraPos.y;
            double z1 = minZ - cameraPos.z;
            double x2 = maxX - cameraPos.x;
            double y2 = maxY - cameraPos.y;
            double z2 = maxZ - cameraPos.z;

            VertexConsumer consumer = buffer.getBuffer(RenderType.LINES);
            LevelRenderer.renderLineBox(poseStack, consumer,
                    x1, y1, z1, x2, y2, z2,
                    0.6f, 0.0f, 1.0f, 0.8f);
        }
    }
}