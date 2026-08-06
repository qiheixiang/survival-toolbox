package com.zzq.survival_toolbox.client;

import com.zzq.survival_toolbox.item.GuardianLanternBlockItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 动态光源管理器
 * <p>
 * 当玩家手持镇魂灯时，在其头顶生成一个不可见的光方块提供照明。
 * 每个玩家独立管理光源位置，登出或切换物品时自动清除。
 * </p>
 */
public class DynamicLightManager {

    private static final Map<UUID, BlockPos> lightSources = new HashMap<>();
    private static final BlockState LIGHT_BLOCK = Blocks.LIGHT.defaultBlockState();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Player player = mc.player;
        UUID playerId = player.getUUID();

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean hasLantern = mainHand.getItem() instanceof GuardianLanternBlockItem ||
                offHand.getItem() instanceof GuardianLanternBlockItem;

        BlockPos currentPos = player.blockPosition();

        if (hasLantern) {
            BlockPos lightPos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
            if (!lightSources.containsKey(playerId) || !lightSources.get(playerId).equals(lightPos)) {
                removeLight(player);
                placeLight(mc.level, lightPos);
                lightSources.put(playerId, lightPos);
            }
        } else {
            removeLight(player);
        }
    }

    private void placeLight(Level level, BlockPos pos) {
        if (level.getBlockState(pos).isAir()) {
            level.setBlock(pos, LIGHT_BLOCK, 3);
        }
    }

    private void removeLight(Player player) {
        UUID playerId = player.getUUID();
        BlockPos oldPos = lightSources.remove(playerId);
        if (oldPos != null && player.level().getBlockState(oldPos).getBlock() == Blocks.LIGHT) {
            player.level().removeBlock(oldPos, false);
        }
    }

    public static void clearAll() {
        for (BlockPos pos : lightSources.values()) {
            Minecraft.getInstance().level.removeBlock(pos, false);
        }
        lightSources.clear();
    }
}