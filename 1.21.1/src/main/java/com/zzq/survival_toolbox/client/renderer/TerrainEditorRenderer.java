package com.zzq.survival_toolbox.client.renderer;

import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zzq.survival_toolbox.item.TerrainEditorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 地形编辑器范围渲染器
 * <p>
 * 在客户端渲染地形编辑器的操作范围边框，帮助玩家预览操作区域。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class TerrainEditorRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Player player = mc.player;
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof TerrainEditorItem)) return;
        if (!TerrainEditorItem.getShowRange(mainHand)) return;

        // ---- 计算目标坐标（与操作逻辑完全一致） ----
        BlockPos target;
        // 注意：MISS 类型的 HitResult 也是 BlockHitResult，必须用 getType() 判断
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            // 准星命中方块（近处交互范围）：直接取被击中的方块本身
            target = ((BlockHitResult) mc.hitResult).getBlockPos();
        } else {
            // 准星未命中方块（空气或远处）：使用与操作完全相同的射线计算
            target = TerrainEditorItem.getTargetPosClient(player);
        }

        AABB box = TerrainEditorItem.getBoundingBox(mainHand, target);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = mc.renderBuffers().bufferSource();

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double x1 = box.minX - cameraPos.x;
        double y1 = box.minY - cameraPos.y;
        double z1 = box.minZ - cameraPos.z;
        double x2 = box.maxX - cameraPos.x;
        double y2 = box.maxY - cameraPos.y;
        double z2 = box.maxZ - cameraPos.z;

        VertexConsumer consumer = buffer.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(poseStack, consumer,
                x1, y1, z1, x2, y2, z2,
                0.2f, 0.8f, 1.0f, 0.8f);
    }
}