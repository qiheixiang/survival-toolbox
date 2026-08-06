package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.item.StaticLeashItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 静态拴绳事件处理器
 * <p>
 * 管理静态拴绳的生物绑定、AI 冻结、拴绳恢复等逻辑。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class StaticLeashEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof StaticLeashItem)) return;

        event.setCanceled(true);
        if (player.level().isClientSide) return;
        if (!(event.getTarget() instanceof Mob mob)) return;

        StaticLeashItem.handleStaticLeash(player, mob, player.isCrouching());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;

        CompoundTag data = mob.getPersistentData();
        if (!data.contains(StaticLeashItem.TAG_LEASHED_BY)) return;

        // 强制冻结 AI
        if (!mob.isNoAi()) mob.setNoAi(true);
        if (!mob.isPersistenceRequired()) mob.setPersistenceRequired();
        if (mob.isUsingItem()) mob.stopUsingItem();
        mob.removeAllEffects();
        mob.clearFire();

        // 清除仇恨目标
        if (mob.getTarget() != null) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
        }

        // 根据当前拴绳状态维护坐标
        if (mob.isLeashed() && mob.getLeashHolder() instanceof LeashFenceKnotEntity knot) {
            if (!data.contains(StaticLeashItem.TAG_LEASHED_POS)) {
                BlockPos knotPos = knot.blockPosition();
                data.putIntArray(StaticLeashItem.TAG_LEASHED_POS,
                        new int[]{knotPos.getX(), knotPos.getY(), knotPos.getZ()});
            }
        } else if (mob.isLeashed() && mob.getLeashHolder() instanceof Player) {
            data.remove(StaticLeashItem.TAG_LEASHED_POS);
        } else if (!mob.isLeashed()) {
            tryRestoreLeashPoint(mob, data);
        }

        // 拉拽（只有拴在玩家身上时触发）
        if (mob.getLeashHolder() instanceof Player holder) {
            double distSq = mob.distanceToSqr(holder);
            if (distSq > 100.0) {
                Vec3 dir = holder.position().subtract(mob.position()).normalize();
                Vec3 target = holder.position().add(dir.scale(-2.0));
                mob.teleportTo(target.x, target.y, target.z);
            }
            if (distSq > 400) {
                mob.teleportTo(holder.getX(), holder.getY(), holder.getZ());
            }
        }
    }

    /**
     * 取消被拴住的生物造成的任何伤害
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Mob attacker) {
            CompoundTag data = attacker.getPersistentData();
            if (data.contains(StaticLeashItem.TAG_LEASHED_BY)) {
                event.setCanceled(true);
            }
        }
    }

    private static void tryRestoreLeashPoint(Mob mob, CompoundTag data) {
        if (!data.contains(StaticLeashItem.TAG_LEASHED_POS)) {
            UUID ownerUUID = StaticLeashItem.safeGetUUID(data, StaticLeashItem.TAG_LEASHED_BY);
            if (ownerUUID != null) {
                Player owner = mob.level().getPlayerByUUID(ownerUUID);
                if (owner != null) {
                    mob.setLeashedTo(owner, true);
                    StaticLeashItem.broadcastLeash(mob, owner);
                }
            }
            return;
        }

        int[] posArr = data.getIntArray(StaticLeashItem.TAG_LEASHED_POS);
        if (posArr.length != 3) return;

        BlockPos pos = new BlockPos(posArr[0], posArr[1], posArr[2]);

        if (!mob.level().hasChunkAt(pos)) {
            mob.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }

        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(mob.level(), pos);
        if (knot != null) {
            mob.setLeashedTo(knot, true);
            StaticLeashItem.broadcastLeash(mob, knot);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        StaticLeashItem.cleanupOnDeath(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(100)).stream()
                .filter(mob -> mob.isLeashed() && mob.getLeashHolder() == player)
                .forEach(mob -> {
                    CompoundTag data = mob.getPersistentData();
                    if (data.contains(StaticLeashItem.TAG_LEASHED_BY)) {
                        mob.setLeashedTo(null, true);
                        data.remove(StaticLeashItem.TAG_AI_FROZEN);
                    }
                });
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide) return;

        CompoundTag data = mob.getPersistentData();
        if (data.contains(StaticLeashItem.TAG_LEASHED_BY)) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();

            if (!mob.isLeashed()) {
                tryRestoreLeashPoint(mob, data);
            }
        }
    }
}