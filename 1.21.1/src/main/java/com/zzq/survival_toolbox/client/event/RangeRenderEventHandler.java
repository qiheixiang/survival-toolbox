package com.zzq.survival_toolbox.client.event;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzq.survival_toolbox.block.GuardianLanternBlock;
import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.block.entity.SmartFarmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 范围边框渲染器
 * <p>
 * 在客户端渲染镇魂灯（蓝色）和智慧农场（绿色）的作用范围边框。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class RangeRenderEventHandler {

    @SubscribeEvent
    public static void onRenderWorldLast(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        MultiBufferSource buffer = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();

        // ---- 镇魂灯 ----
        for (GuardianLanternBlockEntity lantern : GuardianLanternBlockEntity.getClientInstances()) {
            if (lantern.isRemoved() || lantern.getLevel() == null) continue;
            BlockState state = lantern.getLevel().getBlockState(lantern.getBlockPos());
            if (!(state.getBlock() instanceof GuardianLanternBlock)) continue;
            if (!lantern.isShowRange()) continue;

            BlockPos center = lantern.getBlockPos();
            if (mc.player.distanceToSqr(center.getX(), center.getY(), center.getZ()) > 128 * 128) continue;

            drawRangeOutline(lantern, poseStack, buffer);
        }

        // ---- 智慧农场 ----
        for (SmartFarmBlockEntity farm : SmartFarmBlockEntity.getClientInstances()) {
            if (farm.isRemoved() || farm.getLevel() == null) continue;
            if (!farm.isShowRange()) continue;

            BlockPos center = farm.getBlockPos();
            if (mc.player.distanceToSqr(center.getX(), center.getY(), center.getZ()) > 128 * 128) continue;

            drawRangeOutline(farm.getRangeX(), farm.getRangeY(), farm.getRangeZ(), center, poseStack, buffer);
        }
    }

    private static void drawRangeOutline(GuardianLanternBlockEntity lantern,
                                         PoseStack poseStack, MultiBufferSource buffer) {
        int rX = lantern.getRangeX();
        int rY = lantern.getRangeY();
        int rZ = lantern.getRangeZ();
        BlockPos center = lantern.getBlockPos();

        double minX = center.getX() - rX;
        double minY = center.getY() - rY;
        double minZ = center.getZ() - rZ;
        double maxX = center.getX() + rX + 1;
        double maxY = center.getY() + rY + 1;
        double maxZ = center.getZ() + rZ + 1;

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VertexConsumer consumer = buffer.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(poseStack, consumer,
                minX - cameraPos.x, minY - cameraPos.y, minZ - cameraPos.z,
                maxX - cameraPos.x, maxY - cameraPos.y, maxZ - cameraPos.z,
                0.2f, 0.8f, 1.0f, 0.8f);
    }

    private static void drawRangeOutline(int rX, int rY, int rZ, BlockPos center,
                                         PoseStack poseStack, MultiBufferSource buffer) {
        double minX = center.getX() - rX;
        double minY = center.getY() - rY;
        double minZ = center.getZ() - rZ;
        double maxX = center.getX() + rX + 1;
        double maxY = center.getY() + rY + 1;
        double maxZ = center.getZ() + rZ + 1;

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VertexConsumer consumer = buffer.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(poseStack, consumer,
                minX - cameraPos.x, minY - cameraPos.y, minZ - cameraPos.z,
                maxX - cameraPos.x, maxY - cameraPos.y, maxZ - cameraPos.z,
                0.2f, 0.8f, 0.2f, 0.6f);
    }
}