package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.network.SyncShieldDataPacket;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自适应附魔核心工具类
 * <p>
 * 管理层数、护盾、效果适应、火焰适应、夜间适应、迷雾适应等所有数据的读写和逻辑计算。
 * 所有数据存储在每件盔甲的 NBT 中，键名统一规范。
 * </p>
 * <p>
 * 数据存储结构：
 * <ul>
 *   <li>{@code adapt_layers} - float 层数</li>
 *   <li>{@code adapt_shield_current} - float 当前护盾值</li>
 *   <li>{@code adapt_shield_max} - float 护盾上限（缓存值）</li>
 *   <li>{@code adapt_data} - CompoundTag 包含以下子字段：
 *     <ul>
 *       <li>{@code adapted_effects} - List[String] 已适应的效果 ID</li>
 *       <li>{@code effect_time} - CompoundTag 效果累计时间 (effectId → tick)</li>
 *       <li>{@code fire_adapted} - boolean 是否已适应火焰</li>
 *       <li>{@code fire_exposure_time} - int 火焰累计暴露时间（tick）</li>
 *       <li>{@code night_adapted} - boolean 是否已适应夜间</li>
 *       <li>{@code night_exposure_time} - int 夜间累计暴露时间（tick）</li>
 *       <li>{@code fog_adapted} - boolean 是否已适应迷雾</li>
 *       <li>{@code fog_exposure_time} - int 迷雾累计暴露时间（tick）</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
public class AdaptationHelper {

    private static final float ROUND_THRESHOLD = 1e9f;
    private static final float MULTIPLY_SAFE_LIMIT = Float.MAX_VALUE / 1000.0f;

    /**
     * 安全四舍五入到 3 位小数
     * <p>
     * 数值过大时跳过四舍五入，防止溢出。
     * </p>
     *
     * @param value 原始数值
     * @return 四舍五入后的数值
     */
    private static float safeRound3Decimals(float value) {
        if (Math.abs(value) > MULTIPLY_SAFE_LIMIT) return value;
        if (Math.abs(value) < ROUND_THRESHOLD) {
            return (float) (Math.round(value * 1000.0) / 1000.0);
        }
        return value;
    }

    // ============================================================
    // 快速过滤
    // ============================================================

    /**
     * 快速判断该实体是否有可能携带自适应附魔的盔甲。
     * <p>
     * 直接检查物品的 {@code Enchantments} 组件，判断是否带有自适应附魔。
     * </p>
     *
     * @param entity 目标实体
     * @return 是否可能存在自适应盔甲
     */
    public static boolean mayHaveAdaptationArmor(LivingEntity entity) {
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()
                    && armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess())) > 0) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 盔甲 NBT 数据读写（底层）
    // ============================================================

    private static CompoundTag getAdaptData(ItemStack armor) {
        if (armor.isEmpty()) return new CompoundTag();
        CompoundTag tag = ItemNbt.getOrCreateTag(armor);
        if (!tag.contains("adapt_data")) return new CompoundTag();
        return tag.getCompound("adapt_data").copy();
    }

    /**
     * 将 {@code adapt_data} 写回物品栈，并通过 {@link ItemNbt#setTag} 强制触发客户端同步。
     * <p>
     * 1.21.1 中直接通过 {@code CustomData.getUnsafe()} 原地修改 NBT 不会触发装备重同步，
     * 客户端会一直读到旧数据（护盾条不更新、客户端看不到 fire_adapted 等）。
     * 因此必须用 {@code stack.set(...)} 换新组件，才能让客户端收到更新。
     * </p>
     * <p>
     * 注意：此方法会完整深拷贝标签并触发装备重同步，成本较高，仅适用于
     * 低频的“标记变化”（如 markFireAdapted）。每 tick 累加的暴露时间
     * 请使用 {@link #saveAdaptDataInPlace}（该数据无需同步到客户端）。
     * </p>
     */
    private static void saveAdaptData(ItemStack armor, CompoundTag data) {
        if (armor.isEmpty()) return;
        CompoundTag root = ItemNbt.getOrCreateTag(armor).copy();
        root.put("adapt_data", data);
        ItemNbt.setTag(armor, root);
    }

    /**
     * 原地写回 {@code adapt_data}，不触发客户端同步。
     * <p>
     * 用于每 tick 累加的累计暴露时间（效果/火焰/夜间/迷雾），这些数值客户端
     * 客户端无需实时查看；用 {@code getUnsafe()} 原地修改即可，避免每 tick 深拷贝 +
     * 组件替换 + 装备重同步的开销。
     * </p>
     */
    private static void saveAdaptDataInPlace(ItemStack armor, CompoundTag data) {
        if (armor.isEmpty()) return;
        ItemNbt.getOrCreateTag(armor).put("adapt_data", data);
    }

    // ----- 效果适应 -----

    public static Set<String> getAdaptedEffects(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        Set<String> result = new HashSet<>();
        if (data.contains("adapted_effects")) {
            ListTag list = data.getList("adapted_effects", 8);
            for (int i = 0; i < list.size(); i++) {
                result.add(list.getString(i));
            }
        }
        return result;
    }

    public static void markEffectAdapted(ItemStack armor, String effectId) {
        CompoundTag data = getAdaptData(armor);
        ListTag list = data.getList("adapted_effects", 8);
        list.add(StringTag.valueOf(effectId));
        data.put("adapted_effects", list);
        saveAdaptData(armor, data);
    }

    public static boolean isEffectAdapted(ItemStack armor, String effectId) {
        return getAdaptedEffects(armor).contains(effectId);
    }

    // ----- 效果累计时间 -----

    public static int getEffectTime(ItemStack armor, String effectId) {
        CompoundTag data = getAdaptData(armor);
        CompoundTag timeMap = data.getCompound("effect_time");
        return timeMap.getInt(effectId);
    }

    public static void addEffectTime(ItemStack armor, String effectId, int ticks) {
        CompoundTag data = getAdaptData(armor);
        CompoundTag timeMap = data.getCompound("effect_time");
        timeMap.putInt(effectId, timeMap.getInt(effectId) + ticks);
        data.put("effect_time", timeMap);
        saveAdaptDataInPlace(armor, data);
    }

    // ----- 火焰适应 -----

    public static boolean isFireAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("fire_adapted");
    }

    public static void markFireAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("fire_adapted", true);
        saveAdaptData(armor, data);
    }

    public static int getFireExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("fire_exposure_time");
    }

    public static void addFireExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("fire_exposure_time", data.getInt("fire_exposure_time") + ticks);
        saveAdaptDataInPlace(armor, data);
    }

    // ----- 夜间适应 -----

    public static boolean isNightAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("night_adapted");
    }

    public static void markNightAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("night_adapted", true);
        saveAdaptData(armor, data);
    }

    public static int getNightExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("night_exposure_time");
    }

    public static void addNightExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("night_exposure_time", data.getInt("night_exposure_time") + ticks);
        saveAdaptDataInPlace(armor, data);
    }

    // ----- 迷雾适应 -----

    public static boolean isFogAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("fog_adapted");
    }

    public static void markFogAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("fog_adapted", true);
        saveAdaptData(armor, data);
    }

    public static int getFogExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("fog_exposure_time");
    }

    public static void addFogExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("fog_exposure_time", data.getInt("fog_exposure_time") + ticks);
        saveAdaptDataInPlace(armor, data);
    }

    // ============================================================
    // 层数 & 护盾（高层 API）
    // ============================================================

    /**
     * 获取玩家所有穿戴的自适应盔甲
     *
     * @param entity 目标实体
     * @return 自适应盔甲列表（按穿戴顺序）
     */
    public static List<ItemStack> getAdaptationArmors(LivingEntity entity) {
        List<ItemStack> armors = new ArrayList<>(4);
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.isEmpty()) continue;
            if (armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess())) > 0) {
                armors.add(armor);
            }
        }
        return armors;
    }

    public static float getArmorLayers(ItemStack armor) {
        if (armor.isEmpty()) return 0;
        return ItemNbt.getOrCreateTag(armor).getFloat("adapt_layers");
    }

    public static void setArmorLayers(ItemStack armor, float layers) {
        if (armor.isEmpty()) return;
        CompoundTag root = ItemNbt.getOrCreateTag(armor).copy();
        root.putFloat("adapt_layers", safeRound3Decimals(layers));
        ItemNbt.setTag(armor, root);
    }

    public static void addArmorLayers(ItemStack armor, double amount) {
        float current = getArmorLayers(armor);
        double max = ModConfig.CLIENT.adaptMaxLayers.get();
        double newLayers = Math.min(current + amount, max);
        setArmorLayers(armor, (float) newLayers);
    }

    public static double getTotalLayers(LivingEntity entity) {
        double total = 0.0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess())) > 0) {
                total += getArmorLayers(armor);
            }
        }
        return total;
    }

    public static float getArmorShield(ItemStack armor) {
        if (armor.isEmpty()) return 0;
        return ItemNbt.getOrCreateTag(armor).getFloat("adapt_shield_current");
    }

    public static void setArmorShield(ItemStack armor, float value) {
        if (armor.isEmpty()) return;
        float max = getArmorMaxShield(armor);
        ItemNbt.getOrCreateTag(armor).putFloat("adapt_shield_current", Math.max(0, Math.min(value, max)));
    }

    /**
     * 将当前护盾/层数等自定义 NBT 通过 {@code stack.set} 写回（作为次要手段）。
     * <p>
     * 主要同步手段是 {@link #syncAdaptationDataToClient}，通过专用网络包推送，
     * 因为 1.21.1 里装备重同步并不总是可靠地到达客户端。
     * </p>
     */
    public static void syncArmorToClient(ItemStack armor) {
        if (armor.isEmpty()) return;
        ItemNbt.setTag(armor, ItemNbt.getOrCreateTag(armor).copy());
    }

    /**
     * 把每件自适应盔甲的护盾/层数/适应标记通过 {@link SyncShieldDataPacket} 推送给客户端，
     * 使客户端 HUD 与客户端 Mixin 能读到最新数据（1.21.1 装备重同步不可靠，必须走网络包）。
     *
     * @param player 目标玩家（服务端）
     */
    public static void syncAdaptationDataToClient(Player player) {
        if (player == null || player.level().isClientSide()) return;
        List<SyncShieldDataPacket.ArmorData> list = new ArrayList<>();
        int slot = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()
                    && armor.getEnchantments().getLevel(ModEnchantments.adaptation(player.level().registryAccess())) > 0) {
                list.add(new SyncShieldDataPacket.ArmorData(
                        slot,
                        getArmorLayers(armor),
                        getArmorShield(armor),
                        getArmorMaxShield(armor),
                        isFireAdapted(armor),
                        isNightAdapted(armor),
                        isFogAdapted(armor)
                ));
            }
            slot++;
        }
        if (!list.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, new SyncShieldDataPacket(list));
        }
    }

    public static float getArmorMaxShield(ItemStack armor) {
        if (armor.isEmpty()) return 0;
        float layers = getArmorLayers(armor);
        float shieldPerLayer = ModConfig.CLIENT.adaptShieldPerLayer.get().floatValue();
        float cap = ModConfig.CLIENT.adaptShieldCap.get().floatValue();
        float calculated = layers * shieldPerLayer;
        return cap > 0 ? Math.min(calculated, cap) : 0;
    }

    public static void updateArmorMaxShield(ItemStack armor) {
        if (armor.isEmpty()) return;
        float max = getArmorMaxShield(armor);
        float current = getArmorShield(armor);
        if (current > max) {
            setArmorShield(armor, max);
        }
        CompoundTag root = ItemNbt.getOrCreateTag(armor).copy();
        root.putFloat("adapt_shield_max", max);
        ItemNbt.setTag(armor, root);
    }

    // ============================================================
    // 核心逻辑：叠层
    // ============================================================

    /**
     * 受到伤害时触发层数增长
     * <p>
     * 每件盔甲独立计算，当原始伤害大于该盔甲当前层数时，
     * 获得 (伤害 - 层数) × 增益倍数 的层数增长。
     * 使用原始伤害（护盾吸收前）确保层数增长与伤害正相关。
     * </p>
     *
     * @param entity    目标实体
     * @param rawDamage 原始伤害值
     */
    public static void applyLayerGain(LivingEntity entity, float rawDamage) {
        List<ItemStack> armors = getAdaptationArmors(entity);
        if (armors.isEmpty()) return;

        double gainMultiplier = ModConfig.CLIENT.adaptLayerGainMultiplier.get();

        for (ItemStack armor : armors) {
            float currentLayers = getArmorLayers(armor);
            if (rawDamage > currentLayers) {
                double gain = (rawDamage - currentLayers) * gainMultiplier;
                if (gain > 0 && gain < 0.001f) gain = 0.001f;
                float safeGain = safeRound3Decimals((float) gain);
                float newLayers = currentLayers + safeGain;
                setArmorLayers(armor, newLayers);

                if (entity instanceof Player player) {
                    updateFlightAbility(entity);
                    if (ModConfig.CLIENT.enableAdaptationLayerMessage.get()) {
                        player.sendSystemMessage(Component.translatable(
                                "message.zzq_survival_toolbox.adaptation.damage_summary",
                                armor.getDisplayName().getString(),
                                rawDamage,
                                safeGain,
                                getArmorLayers(armor)
                        ));
                    }
                }
            }
        }
    }

    // ============================================================
    // 核心逻辑：恢复
    // ============================================================

    public static void applyRestore(LivingEntity entity) {
        applyRestore(entity, 1.0f);
    }

    /**
     * 触发恢复效果：回血 + 修复盔甲耐久
     * <p>
     * 每件盔甲独立计算，恢复量 = 层数 × 对应比例。
     * </p>
     *
     * @param entity           目标实体
     * @param privateMultiplier 私有倍率
     */
    public static void applyRestore(LivingEntity entity, float privateMultiplier) {
        List<ItemStack> armors = getAdaptationArmors(entity);
        if (armors.isEmpty()) return;

        double healMultiplier = ModConfig.CLIENT.adaptHealMultiplier.get();
        double repairPerLayer = ModConfig.CLIENT.adaptArmorRepairPerLayer.get();
        float currentHealth = entity.getHealth();
        float maxHealth = entity.getMaxHealth();

        for (ItemStack armor : armors) {
            float layers = getArmorLayers(armor);

            // 回血
            float healAmount = (float) (layers * healMultiplier * privateMultiplier);
            if (healAmount > 0 && currentHealth < maxHealth) {
                entity.heal(healAmount);
            }

            // 修复耐久
            int repairAmount = Math.max(1, (int) (layers * repairPerLayer * privateMultiplier));
            int repaired = 0;
            if (armor.isDamageableItem()) {
                int currentDamage = armor.getDamageValue();
                if (currentDamage > 0) {
                    repaired = Math.min(repairAmount, currentDamage);
                    armor.setDamageValue(currentDamage - repaired);
                }
            }

            if (entity instanceof Player player) {
                if (ModConfig.CLIENT.enableAdaptationRestoreMessage.get()) {
                    if ((healAmount > 0 && currentHealth < maxHealth) || repaired > 0) {
                        player.sendSystemMessage(Component.translatable(
                                "message.zzq_survival_toolbox.adaptation.armor_repair",
                                armor.getDisplayName().getString(),
                                repaired,
                                healAmount
                        ));
                    }
                }
            }
        }
    }

    // ============================================================
    // 飞行能力管理
    // ============================================================

    /**
     * 根据当前总层数动态开关创造飞行能力
     * <p>
     * 仅在层数或装备变化时调用，不干预创造模式玩家的飞行状态。
     * </p>
     *
     * @param entity 目标实体
     */
    public static void updateFlightAbility(LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        if (player.getAbilities().instabuild) return;

        List<ItemStack> armors = getAdaptationArmors(entity);
        double totalLayers = getTotalLayers(entity);
        int threshold = ModConfig.CLIENT.adaptFlightThreshold.get();

        boolean shouldFly = !armors.isEmpty() && totalLayers >= threshold;

        if (shouldFly && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (!shouldFly && player.getAbilities().mayfly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    /**
     * 更新夜间适应能力（夜视效果）
     * <p>
     * 根据盔甲是否仍然适应夜间环境，添加或移除夜视效果。
     * </p>
     *
     * @param entity 目标实体
     */
    public static void updateisNightAbility(LivingEntity entity) {
        if (!(entity instanceof Player player)) return;

        boolean stillAdapted = false;
        for (ItemStack armor : player.getArmorSlots()) {
            if (AdaptationHelper.isNightAdapted(armor)) {
                stillAdapted = true;
                break;
            }
        }

        if (stillAdapted) {
            if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        -1, 0, false, false
                ));
            }
        } else {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}