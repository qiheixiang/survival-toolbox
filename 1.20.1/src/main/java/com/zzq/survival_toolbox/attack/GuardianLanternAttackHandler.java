package com.zzq.survival_toolbox.attack;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.data.BlacklistEntry;
import com.zzq.survival_toolbox.item.BlacklistItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 镇魂灯攻击处理器
 * <p>
 * 负责执行镇魂灯的自动攻击逻辑。
 * 使用复用的假玩家对象执行攻击，以模拟实体伤害来源。
 * </p>
 */
public class GuardianLanternAttackHandler {

    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final ConcurrentHashMap<BlockPos, Boolean> ATTACKING = new ConcurrentHashMap<>();

    /** 复用的假玩家（延迟初始化） */
    private static FakePlayer reusableFakePlayer = null;

    /**
     * 执行一次攻击
     *
     * @param lantern 镇魂灯方块实体
     * @param level   服务端世界
     */
    public static void performAttack(GuardianLanternBlockEntity lantern, ServerLevel level) {
        BlockPos pos = lantern.getBlockPos();
        if (ATTACKING.putIfAbsent(pos, Boolean.TRUE) != null) return;

        try {
            BlockPos center = lantern.getBlockPos();
            int rX = lantern.getRangeX();
            int rY = lantern.getRangeY();
            int rZ = lantern.getRangeZ();

            ItemStack weapon = lantern.getWeapon();
            if (weapon.isEmpty()) return;

            ItemStack blacklist = lantern.getBlacklist();
            boolean hasBlacklist = !blacklist.isEmpty() && blacklist.getItem() instanceof BlacklistItem;

            List<LivingEntity> originalTargets = getOriginalTargets(lantern, level, center, rX, rY, rZ);
            List<LivingEntity> finalTargets = hasBlacklist ?
                    applyBlacklistModifier(originalTargets, level, center, rX, rY, rZ, blacklist) :
                    originalTargets;

            if (finalTargets.isEmpty()) return;

            // 复用假玩家
            if (reusableFakePlayer == null || reusableFakePlayer.level() != level) {
                reusableFakePlayer = FakePlayerFactory.getMinecraft(level);
            }
            FakePlayer attacker = reusableFakePlayer;
            attacker.setPos(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
            attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, weapon);

            // 计算武器攻击力
            float attackDamage = 0.0f;
            var modifiers = weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            if (modifiers.containsKey(Attributes.ATTACK_DAMAGE)) {
                for (AttributeModifier mod : modifiers.get(Attributes.ATTACK_DAMAGE)) {
                    attackDamage += (float) mod.getAmount();
                }
            }
            if (attackDamage < 1.0f) attackDamage = 1.0f;

            // 设置假玩家的攻击力
            var attrInstance = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attrInstance != null) {
                attrInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_UUID);
                attrInstance.addTransientModifier(new AttributeModifier(
                        ATTACK_DAMAGE_MODIFIER_UUID,
                        "Weapon attack damage",
                        attackDamage - 1.0,
                        AttributeModifier.Operation.ADDITION
                ));
            }

            // 执行攻击
            for (LivingEntity target : finalTargets) {
                attacker.attack(target);
            }

            // 清理
            if (attrInstance != null) {
                attrInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_UUID);
            }

            // 更新武器状态（可能因耐久消耗而变化）
            ItemStack updatedWeapon = attacker.getMainHandItem();
            lantern.setWeapon(updatedWeapon.isEmpty() ? ItemStack.EMPTY : updatedWeapon);
            attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        } finally {
            ATTACKING.remove(pos);
        }
    }

    // ============================================================
    // 获取原始目标列表（按类别过滤）
    // ============================================================

    private static List<LivingEntity> getOriginalTargets(GuardianLanternBlockEntity lantern,
                                                         ServerLevel level, BlockPos center,
                                                         int rX, int rY, int rZ) {
        AABB bounds = new AABB(
                center.getX() - rX, center.getY() - rY, center.getZ() - rZ,
                center.getX() + rX + 1, center.getY() + rY + 1, center.getZ() + rZ + 1
        );

        boolean attackHostile = lantern.isAttackHostile();
        boolean attackNeutral = lantern.isAttackNeutral();
        boolean attackPassive = lantern.isAttackPassive();

        if (!attackHostile && !attackNeutral && !attackPassive) {
            return new ArrayList<>();
        }

        return level.getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> {
                    if (entity == null || !entity.isAlive()) return false;
                    if (entity instanceof Player && ((Player) entity).isCreative()) return false;

                    MobCategory category = entity.getType().getCategory();
                    if (category == MobCategory.MONSTER) return attackHostile;
                    if (category == MobCategory.CREATURE) return attackPassive;
                    if (category == MobCategory.AMBIENT) return attackPassive;
                    if (category == MobCategory.WATER_CREATURE) return attackPassive;
                    if (category == MobCategory.WATER_AMBIENT) return attackPassive;
                    if (category == MobCategory.MISC) return attackNeutral;
                    return false;
                });
    }

    // ============================================================
    // 黑白名单修正逻辑
    // ============================================================

    private static List<LivingEntity> applyBlacklistModifier(List<LivingEntity> originalTargets,
                                                             ServerLevel level, BlockPos center,
                                                             int rX, int rY, int rZ,
                                                             ItemStack blacklist) {
        List<BlacklistEntry> entries = BlacklistItem.getEntries(blacklist);
        if (entries.isEmpty()) return originalTargets;

        // 分离黑白名单
        List<BlacklistEntry> whitelist = new ArrayList<>();
        List<BlacklistEntry> blacklistEntries = new ArrayList<>();

        for (BlacklistEntry entry : entries) {
            if (entry.action == BlacklistEntry.Action.WHITELIST) {
                whitelist.add(entry);
            } else if (entry.action == BlacklistEntry.Action.BLACKLIST) {
                blacklistEntries.add(entry);
            }
        }

        if (whitelist.isEmpty() && blacklistEntries.isEmpty()) return originalTargets;

        // 获取范围内所有生物
        AABB bounds = new AABB(
                center.getX() - rX, center.getY() - rY, center.getZ() - rZ,
                center.getX() + rX + 1, center.getY() + rY + 1, center.getZ() + rZ + 1
        );
        List<LivingEntity> allTargets = level.getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity != null && entity.isAlive() &&
                        !(entity instanceof Player && ((Player) entity).isCreative()));

        List<LivingEntity> result = new ArrayList<>(originalTargets);

        // 白名单：从原始列表中移除匹配的
        if (!whitelist.isEmpty()) {
            result.removeIf(target -> matchesAny(target, whitelist));
        }

        // 黑名单：添加黑名单匹配的（且不在白名单中）
        if (!blacklistEntries.isEmpty()) {
            for (LivingEntity target : allTargets) {
                if (matchesAny(target, blacklistEntries)) {
                    if (matchesAny(target, whitelist)) continue;
                    if (!result.contains(target)) result.add(target);
                }
            }
        }

        return result;
    }

    private static boolean matchesAny(LivingEntity target, List<BlacklistEntry> entries) {
        for (BlacklistEntry entry : entries) {
            if (entry.matches(target)) return true;
        }
        return false;
    }
}