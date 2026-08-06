package com.zzq.survival_toolbox.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * ItemStack NBT 辅助类
 * <p>
 * 在 1.21.1 中 {@link ItemStack#getOrCreateTag()} 已移除，改为使用
 * {@link DataComponents#CUSTOM_DATA} 组件。本类提供兼容方法。
 * </p>
 * <p>
 * 注意：{@link CustomData#of(CompoundTag)} 会复制标签，因此必须通过
 * {@link CustomData#getUnsafe()} 获取存储标签的实时引用，才能保证
 * 对返回标签的修改写回物品栈。
 * </p>
 */
public final class ItemNbt {

    private ItemNbt() {
    }

    /**
     * 获取（必要时创建）物品栈的自定义 NBT 标签。
     * <p>
     * 返回的是存储标签的实时引用，直接修改该标签即可持久化。
     * </p>
     */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            return data.getUnsafe();
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        return stack.get(DataComponents.CUSTOM_DATA).getUnsafe();
    }

    /**
     * 获取物品栈的自定义 NBT 标签（不存在时返回 null）。
     */
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return (data == null || data.isEmpty()) ? null : data.copyTag();
    }

    /**
     * 判断物品栈是否包含自定义 NBT 标签。
     */
    public static boolean hasTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    /**
     * 将标签写回物品栈。
     */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
