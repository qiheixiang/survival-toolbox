package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 镇魂灯事件处理器
 * <p>
 * 功能：
 * <ul>
 *   <li>实体生成镇压：阻止敌对/中立/被动生物在镇魂灯范围内生成</li>
 *   <li>幻翼生成镇压：阻止玩家在镇魂灯范围内触发幻翼生成</li>
 * </ul>
 * </p>
 */
public class GuardianLanternEventHandler {

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        BlockPos pos = event.getEntity().blockPosition();
        EntityType<?> entityType = event.getEntity().getType();

        if (shouldSuppressSpawn(level, pos, entityType)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerSpawnPhantoms(PlayerSpawnPhantomsEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;

        BlockPos pos = player.blockPosition();

        for (GuardianLanternBlockEntity lantern : GuardianLanternBlockEntity.getServerInstances()) {
            if (lantern.isInSuppressionRange(pos) && lantern.isSuppressEnabled()) {
                if (lantern.isSuppressHostile()) {
                    event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
                    return;
                }
            }
        }
    }

    /**
     * 检查指定位置和实体类型是否应被镇压
     *
     * @param level      世界
     * @param pos        位置
     * @param entityType 实体类型
     * @return 是否应被镇压
     */
    private boolean shouldSuppressSpawn(Level level, BlockPos pos, EntityType<?> entityType) {
        MobCategory category = entityType.getCategory();
        if (category != MobCategory.MONSTER &&
                category != MobCategory.CREATURE &&
                category != MobCategory.AMBIENT &&
                category != MobCategory.WATER_CREATURE &&
                category != MobCategory.WATER_AMBIENT &&
                category != MobCategory.MISC) {
            return false;
        }

        for (GuardianLanternBlockEntity lantern : GuardianLanternBlockEntity.getServerInstances()) {
            if (lantern.isInSuppressionRange(pos) && lantern.shouldSuppressEntity(entityType)) {
                return true;
            }
        }
        return false;
    }
}