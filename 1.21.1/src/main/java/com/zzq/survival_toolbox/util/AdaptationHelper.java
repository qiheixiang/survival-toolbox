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
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
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
    /**
     * "这个实体可能穿着自适应甲吗"——**带负缓存**（针对性能问题，优先做这条最划算的）：
     * <p>
     * 原来每 tick 对**每个生物**都要扫 4 格护甲、而且每件都调一次
     * {@link ModEnchantments#adaptation(HolderLookup.Provider)}（= 查一次附魔注册表 + getOrThrow），
     * 刷怪塔那种上百只怪的场面就是每 tick 上千次注册表查询。
     * 绝大多数怪压根没有自适应甲，所以这里给"没查到"的结果做缓存：
     * **查到过的实体每 tick 正常检查**（它们本来就少），**没查到过的每 40 tick（2 秒）才重查一次**。
     * </p>
     * <p>
     * 行为差异只有一个：给一只本来没有自适应甲的怪**刚穿上**自适应甲时，最多晚 40 tick（2 秒）被识别；
     * 识别之后的层数累积、适应速度、阈值判定**完全不变**。缓存用 {@code WeakHashMap}，实体卸载后不会留引用。
     * </p>
     */
    public static boolean mayHaveAdaptationArmor(LivingEntity entity) {
        int now = entity.tickCount;
        Integer lastNegative = NEGATIVE_ARMOR_CACHE.get(entity);
        if (lastNegative != null && now - lastNegative < NEGATIVE_RECHECK_TICKS) {
            return false;
        }
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()
                    && armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess())) > 0) {
                NEGATIVE_ARMOR_CACHE.remove(entity);   // 有甲了：退出负缓存，之后每 tick 正常检查
                return true;
            }
        }
        NEGATIVE_ARMOR_CACHE.put(entity, now);
        return false;
    }

    /** 负缓存：实体 → "上次确认没有自适应甲"的 tickCount（WeakHashMap，实体没了自动回收） */
    private static final java.util.Map<LivingEntity, Integer> NEGATIVE_ARMOR_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** 负缓存多久重查一次（2 秒）：够快，能省掉绝大部分注册表查询 */
    private static final int NEGATIVE_RECHECK_TICKS = 40;

    // ============================================================
    // 盔甲 NBT 数据读写（底层）
    // ============================================================

    // ============================================================
    // ②③④ 运行时缓存：护盾当前值 / 适应计时先攒在内存，低频落盘
    // ============================================================

    /** 落盘间隔（tick）：护盾/适应计时最多每秒写一次物品 NBT */
    public static final int FLUSH_INTERVAL_TICKS = 20;
    /** 待落盘条目数超过它时一并清理很久没动过的（换下来的盔甲不留垃圾） */
    private static final int PENDING_SWEEP_SIZE = 64;
    /** 多久没动过就算垃圾（毫秒）；清理时**先落盘再丢**，不白攒 */
    private static final long PENDING_STALE_MS = 5000L;

    /**
     * 待落盘缓存：键是**物品栈本身**（IdentityHashMap，一件盔甲一个条目）。
     * <p>
     * ⚠️ 只由“写”创建（{@link #setArmorShield}、{@link #saveAdaptDataInPlace}），落盘后立刻清掉
     * —— 所以客户端（只读那一边）永远不会有条目，读永远走物品 NBT，不会读到陈旧值。
     * </p>
     */
    private static final Map<ItemStack, Pending> PENDING =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** 上一次推给客户端的自适应数据（③：内容一模一样就不再发包） */
    private static final Map<Player, List<SyncShieldDataPacket.ArmorData>> LAST_SYNCED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 一件盔甲身上攒着、还没写进 NBT 的自适应数据 */
    private static final class Pending {
        private CompoundTag adaptData;
        private boolean hasAdaptData;
        private float shieldCurrent;
        private boolean hasShield;
        private long touchedAt;

        private boolean isEmpty() {
            return !hasAdaptData && !hasShield;
        }
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
     * 把一件盔甲上攒着的自适应数据写进物品 NBT。
     * <p>
     * 关键时机一定要调：受伤、换甲、丢甲、死亡复活、退出、切维度、服务器停。
     * 调完缓存就空了，所以不存在“写回旧值”的问题。
     * </p>
     */
    public static void flushPendingAdapt(ItemStack armor) {
        if (armor == null || armor.isEmpty()) return;
        Pending p = PENDING.remove(armor);
        if (p == null || p.isEmpty()) return;
        ItemNbt.edit(armor, t -> {
            if (p.hasAdaptData) t.put("adapt_data", p.adaptData.copy());
            if (p.hasShield) t.putFloat("adapt_shield_current", p.shieldCurrent);
        });
    }

    /** 每 tick 调一次：到了落盘间隔才写（默认每秒一次） */
    public static void flushPendingAdaptIfDue(ItemStack armor, int tickCount) {
        if (tickCount % FLUSH_INTERVAL_TICKS == 0) flushPendingAdapt(armor);
    }

    /** 把某个实体身上所有自适应盔甲的数据落盘 */
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

    /** 外部直接改过 NBT（命令、附魔转移）之后调：丢掉内存缓存，别让它把旧值写回去 */
    public static void invalidatePendingAdapt(ItemStack armor) {
        if (armor == null) return;
        PENDING.remove(armor);
    }

    private static CompoundTag getAdaptData(ItemStack armor) {
        if (armor.isEmpty()) return new CompoundTag();
        // 有攒着没落盘的，就用内存里那份（它是这份数据的最新值）
        Pending p = pendingOf(armor, false);
        if (p != null && p.hasAdaptData) return p.adaptData.copy();
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
        root.put("adapt_data", data.copy());
        // 这条是"客户端必须马上看到"的路（markXxxAdapted）：同时把攒着的护盾值一起写掉，省一次写
        Pending p = pendingOf(armor, false);
        if (p != null && p.hasShield) {
            root.putFloat("adapt_shield_current", p.shieldCurrent);
            p.hasShield = false;
        }
        ItemNbt.setTag(armor, root);
        if (p != null) {
            p.hasAdaptData = false;
            p.adaptData = null;
        }
    }

    /**
     * 把 {@code adapt_data} 攒进内存缓存（不写物品 NBT）。
     * <p>
     * 用于每 tick 累加的累计暴露时间（效果/火焰/夜间/迷雾）：这些数值客户端不需要实时看到。
     * ⚠️ 以前这里写的是 {@code ItemNbt.edit}，注释写着"原地修改即可" —— **那是错的**：
     * {@code ItemNbt.edit} 是"整份 CUSTOM_DATA 深拷贝 + {@code stack.set} 换组件"，
     * 每 tick 每件甲都来一次，既深拷贝又触发装备重同步（④ 的开销就是它）。
     * 现在只改内存，由 {@link #flushPendingAdaptIfDue}（每秒一次）或关键时机统一落盘。
     * </p>
     */
    private static void saveAdaptDataInPlace(ItemStack armor, CompoundTag data) {
        if (armor.isEmpty()) return;
        Pending p = pendingOf(armor, true);
        p.adaptData = data.copy();
        p.hasAdaptData = true;
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
        saveAdaptDataInPlace(armor, data);
    }

    public static int getThirstBlurExposureTime(ItemStack armor) {
        return getAdaptData(armor).getInt("thirst_blur_exposure_time");
    }

    public static void addThirstBlurExposureTime(ItemStack armor, int ticks) {
        CompoundTag data = getAdaptData(armor);
        data.putInt("thirst_blur_exposure_time", data.getInt("thirst_blur_exposure_time") + ticks);
        saveAdaptDataInPlace(armor, data);
    }

    /**
     * 口渴模糊的<b>适应进度</b>（0 = 完全没适应，1 = 已完成适应）。
     * <p>
     * 设计约定：<b>"不做直接抵消，而是逐步适应"</b> ——
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
            if (armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess())) > 0) {
                total += getArmorLayers(armor);
            }
        }
        return total;
    }

    public static float getArmorShield(ItemStack armor) {
        if (armor.isEmpty()) return 0;
        Pending p = pendingOf(armor, false);
        if (p != null && p.hasShield) return p.shieldCurrent;
        return ItemNbt.getOrCreateTag(armor).getFloat("adapt_shield_current");
    }

    /**
     * ② 护盾当前值只写内存。
     * <p>
     * 以前每 tick 一次 {@code ItemNbt.edit}（整份 CUSTOM_DATA 深拷贝 + 换组件 → 触发装备重同步），
     * 自适应甲多的时候这是最大的一笔开销。现在攒在 {@link #PENDING} 里，
     * 由 {@link #flushPendingAdaptIfDue} 每秒落盘 + 关键时机立刻落盘；
     * 客户端看到的数值仍由 {@link #syncAdaptationDataToClient} 按原来的频率推送，HUD 不会变卡。
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
        syncAdaptationDataToClient(player, false);
    }

    /**
     * ③ 同步节流：内容和上次一模一样就不再发包（护盾满、层数没变时不再每秒空发一包）。
     * <p>
     * ⚠️ "重新入场"类时机必须传 {@code force = true}（登录、复活、切维度）：
     * 那时客户端身上的物品是新的，得无条件全量推一次，否则它会一直显示默认值。
     * 换甲之后用 {@link #invalidateSyncCache} 丢掉记录即可（装备重同步已经把 NBT 带过去了）。
     * </p>
     */
    public static void syncAdaptationDataToClient(Player player, boolean force) {
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
            List<SyncShieldDataPacket.ArmorData> last = LAST_SYNCED.get(player);
            if (!force && list.equals(last)) return;
            LAST_SYNCED.put(player, list);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, new SyncShieldDataPacket(list));
        }
    }

    /** 换甲之后调：丢掉"上次推过什么"的记录，让下一次同步一定发出去 */
    public static void invalidateSyncCache(Player player) {
        if (player != null) LAST_SYNCED.remove(player);
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