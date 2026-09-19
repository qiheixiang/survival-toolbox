package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Map;
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
     * 仅检查 NBT 中是否包含 Enchantments 标签，避免完整解析 NBT 的开销。
     * </p>
     *
     * @param entity 目标实体
     * @return 是否可能存在自适应盔甲
     */
    public static boolean mayHaveAdaptationArmor(LivingEntity entity) {
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty() && armor.hasTag() && armor.getTag().contains("Enchantments")) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 盔甲 NBT 数据读写（底层）
    // ============================================================

    // ============================================================
    // ② 护盾当前值的内存缓存：先攒着，低频/关键时机才写物品 NBT
    // ============================================================
    //
    // 为什么 1.20.1 只做 ②（护盾）而没做 ④（适应计时）：
    //   1.21.1 的暴露计时走 ItemNbt.edit（整份 CUSTOM_DATA 深拷贝 + 换组件）才需要攒着写；
    //   1.20.1 的 getAdaptData 返回的就是物品里那份**实时标签**，累加是原地改，本身开销就很低，
    //   再套一层缓存只会白白增加复杂度（这里刻意和 1.21.1 不一致）。
    // 也没有 ③：1.20.1 没有专用的护盾同步包，护盾值是靠"写 NBT → 装备重同步"到客户端的，
    //   所以落盘节奏必须保持"恢复中 2 tick 一次"，不然护盾条会变卡（见 AdaptationEventHandler）。
    // ============================================================

    /** 落盘间隔（tick）：静止时护盾值最多每秒写一次物品 NBT */
    public static final int FLUSH_INTERVAL_TICKS = 20;
    /** 待落盘条目数超过它时顺手清理很久没动过的 */
    private static final int PENDING_SWEEP_SIZE = 64;
    /** 多久没动过就算垃圾（毫秒）；清理时**先落盘再丢** */
    private static final long PENDING_STALE_MS = 5000L;

    /**
     * 待落盘缓存：键是**物品栈本身**（IdentityHashMap，一件盔甲一个条目）。
     * <p>
     * ⚠️ 只由"写"创建（{@link #setArmorShield}），落盘后立刻清掉 —— 客户端那一边（只读）永远不会有条目，
     * 所以它读到的始终是物品 NBT 里的真值，不会读到陈旧值。
     * </p>
     */
    private static final Map<ItemStack, Pending> PENDING =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private static final class Pending {
        private float shieldCurrent;
        private boolean hasShield;
        private long touchedAt;
    }

    private static Pending pendingOf(ItemStack armor, boolean create) {
        if (!create) return PENDING.get(armor);
        Pending p = PENDING.get(armor);
        if (p == null) {
            if (PENDING.size() >= PENDING_SWEEP_SIZE) sweepPending();
            p = new Pending();
            PENDING.put(armor, p);
        }
        p.touchedAt = System.currentTimeMillis();
        return p;
    }

    /** 清掉很久没动过的待落盘条目（先落盘，再丢） */
    private static void sweepPending() {
        long now = System.currentTimeMillis();
        List<ItemStack> stale = new ArrayList<>();
        synchronized (PENDING) {
            for (Map.Entry<ItemStack, Pending> e : PENDING.entrySet()) {
                if (now - e.getValue().touchedAt > PENDING_STALE_MS) stale.add(e.getKey());
            }
        }
        for (ItemStack stack : stale) {
            flushPendingAdapt(stack);
        }
    }

    /**
     * 把一件盔甲上攒着的护盾值写进物品 NBT。
     * <p>
     * 关键时机一定要调：受伤、换甲、丢甲、死亡复活、切维度、退出、服务器停。调完缓存就空了。
     * </p>
     */
    public static void flushPendingAdapt(ItemStack armor) {
        if (armor == null || armor.isEmpty()) return;
        Pending p = PENDING.remove(armor);
        if (p == null || !p.hasShield) return;
        ItemNbt.edit(armor, t -> t.putFloat("adapt_shield_current", p.shieldCurrent));
    }

    /** 每 tick 调一次：到了落盘间隔才写 */
    public static void flushPendingAdaptIfDue(ItemStack armor, int tickCount) {
        if (tickCount % FLUSH_INTERVAL_TICKS == 0) flushPendingAdapt(armor);
    }

    /** 把某个实体身上所有自适应盔甲的护盾落盘 */
    public static void flushPendingAdapt(LivingEntity entity) {
        if (entity == null) return;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) flushPendingAdapt(armor);
        }
    }

    /** 把所有待落盘数据落盘（服务器停 / 关卡卸载） */
    public static void flushAllPendingAdapt() {
        List<ItemStack> keys;
        synchronized (PENDING) {
            keys = new ArrayList<>(PENDING.keySet());
        }
        for (ItemStack stack : keys) {
            flushPendingAdapt(stack);
        }
    }

    private static CompoundTag getAdaptData(ItemStack armor) {
        if (armor.isEmpty()) return new CompoundTag();
        CompoundTag tag = armor.getOrCreateTag();
        if (!tag.contains("adapt_data")) {
            tag.put("adapt_data", new CompoundTag());
        }
        return tag.getCompound("adapt_data");
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
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
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
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    // ----- 火焰适应 -----

    public static boolean isFireAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("fire_adapted");
    }

    public static void markFireAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("fire_adapted", true);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    public static int getFireExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("fire_exposure_time");
    }

    public static void addFireExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("fire_exposure_time", data.getInt("fire_exposure_time") + ticks);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    // ----- 夜间适应 -----

    public static boolean isNightAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("night_adapted");
    }

    public static void markNightAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("night_adapted", true);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    public static int getNightExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("night_exposure_time");
    }

    public static void addNightExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("night_exposure_time", data.getInt("night_exposure_time") + ticks);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    // ----- 迷雾适应 -----

    public static boolean isFogAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("fog_adapted");
    }

    public static void markFogAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("fog_adapted", true);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    public static int getFogExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("fog_exposure_time");
    }

    public static void addFogExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("fog_exposure_time", data.getInt("fog_exposure_time") + ticks);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    // ----- 口渴模糊适应（LSO 低水分时糊屏的那个后处理，见 LsoThirstBlurMixin）-----

    /**
     * 统一适应系统的阈值（tick）：{@code max(1, adaptTime - 层数 × adaptTimeReduction) × 20}。
     * <p>
     * ⚠️ 服务端拿它累计暴露时间、客户端拿它算"适应进度"，**两边必须是同一个公式**，
     * 否则玩家看到的"逐步变淡"和服务端认定的"适应完成"对不上。
     * </p>
     */
    public static int adaptThresholdTicks(LivingEntity entity) {
        int baseSeconds = ModConfig.CLIENT.adaptTime.get();
        double reduction = ModConfig.CLIENT.adaptTimeReduction.get();
        double thresholdSeconds = Math.max(1.0D, baseSeconds - getTotalLayers(entity) * reduction);
        return (int) (thresholdSeconds * 20.0D);
    }

    public static boolean isThirstBlurAdapted(ItemStack armor) {
        return getAdaptData(armor).getBoolean("thirst_blur_adapted");
    }

    public static void markThirstBlurAdapted(ItemStack armor) {
        CompoundTag data = getAdaptData(armor);
        data.putBoolean("thirst_blur_adapted", true);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    public static int getThirstBlurExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("thirst_blur_exposure_time");
    }

    public static void addThirstBlurExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("thirst_blur_exposure_time", data.getInt("thirst_blur_exposure_time") + ticks);
        ItemNbt.edit(armor, t -> t.put("adapt_data", data));
    }

    /**
     * 口渴模糊的<b>适应进度</b>（0 = 完全没适应，1 = 已完成适应）。
     * <p>
     * 设计规则：<b>不做"有盔甲就直接抵消"，而是逐步适应</b> ——
     * 所以客户端的 {@code LsoThirstBlurMixin} 不做"有盔甲就归零"，而是按这个进度
     * 把 LSO 算出来的模糊强度**线性压下去**：刚穿上时照糊，适应到一半时糊一半，
     * 攒满适应时间（和火焰/夜视/迷雾一个阈值）才完全不糊。
     * </p>
     *
     * @param thresholdTicks 服务端累计用的那个阈值（见 {@link #adaptThresholdTicks}）
     */
    public static float getThirstBlurAdaptProgress(LivingEntity entity, int thresholdTicks) {
        List<ItemStack> armors = getAdaptationArmors(entity);
        if (armors.isEmpty()) return 0.0F;
        for (ItemStack armor : armors) {
            if (isThirstBlurAdapted(armor)) return 1.0F;      // 已经适应完了：完全不糊
        }
        if (thresholdTicks <= 0) return 0.0F;
        int exposure = getThirstBlurExposureTime(armors.get(0));
        if (exposure <= 0) return 0.0F;                        // 刚穿上、还没开始适应：照糊
        return Math.min(1.0F, (float) exposure / (float) thresholdTicks);
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
            if (!armor.hasTag() || !armor.getTag().contains("Enchantments")) continue;
            if (armor.getEnchantmentLevel(ModEnchantments.ADAPTATION.get()) > 0) {
                armors.add(armor);
            }
        }
        return armors;
    }

    public static float getArmorLayers(ItemStack armor) {
        if (armor.isEmpty()) return 0;
        return armor.getOrCreateTag().getFloat("adapt_layers");
    }

    public static void setArmorLayers(ItemStack armor, float layers) {
        if (armor.isEmpty()) return;
        ItemNbt.edit(armor, t -> t.putFloat("adapt_layers", safeRound3Decimals(layers)));
    }

    public static void addArmorLayers(ItemStack armor, double amount) {
        float current = getArmorLayers(armor);
        // 防御：损坏数据（NaN/Infinity）归零，避免 float 溢出后无法恢复
        if (Float.isNaN(current) || Float.isInfinite(current)) current = 0;
        double max = ModConfig.CLIENT.adaptMaxLayers.get();
        double newLayers = Math.min(current + amount, max);
        if (newLayers < 0) newLayers = 0;
        setArmorLayers(armor, (float) newLayers);
    }

    public static double getTotalLayers(LivingEntity entity) {
        double total = 0.0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor.getEnchantmentLevel(ModEnchantments.ADAPTATION.get()) > 0) {
                total += getArmorLayers(armor);
            }
        }
        return total;
    }

    public static float getArmorShield(ItemStack armor) {
        if (armor.isEmpty()) return 0;
        Pending p = pendingOf(armor, false);
        if (p != null && p.hasShield) return p.shieldCurrent;
        return armor.getOrCreateTag().getFloat("adapt_shield_current");
    }

    /**
     * ② 护盾当前值只写内存。
     * <p>
     * 以前每 tick 一次 {@code ItemNbt.edit}（整份标签深拷贝 + 换组件 → 触发装备重同步），
     * 自适应甲多的时候是最大的一笔开销。现在攒在 {@link #PENDING} 里，
     * 由 {@link AdaptationEventHandler} 按"恢复中 2 tick / 静止 20 tick"的节奏落盘，
     * 关键时机（受伤、换甲、复活、切维度、停机）立刻落盘 —— 客户端刷新率与以前一致。
     * </p>
     */
    public static void setArmorShield(ItemStack armor, float value) {
        if (armor.isEmpty()) return;
        float max = getArmorMaxShield(armor);
        float clamped = Math.max(0, Math.min(value, max));
        Pending p = pendingOf(armor, true);
        if (p.hasShield && p.shieldCurrent == clamped) return;
        p.shieldCurrent = clamped;
        p.hasShield = true;
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
        ItemNbt.edit(armor, t -> t.putFloat("adapt_shield_max", max));
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
        double maxLayers = ModConfig.CLIENT.adaptMaxLayers.get();

        for (ItemStack armor : armors) {
            float currentLayers = getArmorLayers(armor);
            if (rawDamage > currentLayers) {
                // 线性叠层：以本次"未减伤的原始伤害"为基准，固定获得 伤害 × 获取比例 层。
                // 不再按（伤害 − 层数）收敛，因此任意量级（含科学计数法）都能稳定增长：
                // 增益恒为伤害的固定比例（默认 1%），远大于 float 在该量级下的最小步进，
                // 约 100 击即可追平并超过该档伤害而达成免疫。
                double gain = rawDamage * gainMultiplier;
                if (gain > 0 && gain < 0.001) gain = 0.001;
                float safeGain = safeRound3Decimals((float) gain);
                // 与 addArmorLayers 保持一致：按配置的「最大层数」截断，
                // 并防护 NaN/Infinity/负数，避免异常数值导致数据损坏后无法恢复。
                double newLayers = Math.min(currentLayers + (double) safeGain, maxLayers);
                if (Double.isNaN(newLayers) || Double.isInfinite(newLayers) || newLayers < 0) {
                    newLayers = 0;
                }
                setArmorLayers(armor, (float) newLayers);

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