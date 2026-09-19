package com.zzq.survival_toolbox.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 战利品表解析工具
 * <p>
 * 模组实体（尤其是经 Sinytra Connector 加载的 Fabric 模组实体）可能声明一个
 * 数据包中并不存在的战利品表键；原版 {@code HolderGetter#getOrThrow} 遇到缺失元素会
 * 抛出 {@link IllegalStateException}，若发生在方块实体 tick 或弹射物命中流程中会直接
 * 崩掉服务器线程。本工具改为返回 {@code null}，由调用方决定跳过掉落产出。
 * </p>
 */
public final class LootTableHelper {

    private LootTableHelper() {
    }

    /**
     * 安全解析战利品表。
     *
     * @param context     战利品上下文（携带注册表解析器）
     * @param lootTableId 键；为 {@code null} 时直接返回 {@code null}
     * @return 对应战利品表；键为 {@code null} 或表中不存在该项时返回 {@code null}
     */
    public static LootTable resolveOrNull(LootContext context, ResourceKey<LootTable> lootTableId) {
        if (context == null || lootTableId == null) return null;
        Holder<LootTable> holder = context.getResolver()
                .lookupOrThrow(Registries.LOOT_TABLE)
                .get(lootTableId)
                .orElse(null);
        return holder == null ? null : holder.value();
    }

    /**
     * 安全生成战利品掉落。
     * <p>
     * 战利品表缺失或掉落生成过程中抛异常（第三方数据包的掉落函数异常等）时均返回空列表，
     * 保证调用方的 tick / 命中流程不会因掉落问题中断。
     * </p>
     *
     * @param context     战利品上下文
     * @param lootTableId 实体声明的战利品表键
     * @return 掉落物列表；无法产出时为空列表
     */
    public static java.util.List<net.minecraft.world.item.ItemStack> rollDropsOrEmpty(
            LootContext context, ResourceKey<LootTable> lootTableId,
            net.minecraft.world.level.storage.loot.LootParams params) {
        LootTable lootTable = resolveOrNull(context, lootTableId);
        if (lootTable == null) return java.util.Collections.emptyList();
        try {
            return lootTable.getRandomItems(params);
        } catch (Exception ignored) {
            // 第三方战利品表生成异常：吞掉异常防止中断服务器 tick
            return java.util.Collections.emptyList();
        }
    }
}
