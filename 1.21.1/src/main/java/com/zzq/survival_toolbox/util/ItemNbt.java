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
 * ⚠️ <b>线程安全（实测曾导致掉线）</b>：服务端每 tick 都会把打开的容器整包发给客户端
 * （{@code ClientboundContainerSetContentPacket}），<b>编码是在 netty 线程上做的</b>，
 * 它会把物品 CUSTOM_DATA 里那份标签整份 {@code CompoundTag#copy()} 一遍。
 * 这时服务端线程若正在<b>就地修改同一份标签</b>（put/remove），netty 那边就会
 * {@code ConcurrentModificationException: Failed to encode packet 'clientbound/minecraft:container_set_content'}
 * → <b>玩家直接掉线</b>。袋子（玩家物品栏里那个）每 tick 都在这张包里，
 * 所以凡是写袋子 NBT 的地方一律用 {@link #copyForEdit} 复制出来改、再用 {@link #setTag} 写回。
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
    /**
     * 取一份<b>脱离物品</b>的标签副本：随便改，改完用 {@link #setTag} 写回。
     * <p>
     * 写袋子 / 打开着的页面数据一律用这个（原因见类注释的"线程安全"一段）：
     * {@link #getOrCreateTag} 给的是物品里那份<b>实时标签</b>，就地改它会和网络编码线程抢同一个 HashMap。
     * </p>
     */
    public static CompoundTag copyForEdit(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return tag == null ? new CompoundTag() : tag;
    }

    /**
     * 就地"改一份副本再写回"：绝不动物品里那份标签（原因见类注释的线程安全说明）。
     * <p>
     * 单条修改用它最省事：{@code ItemNbt.edit(stack, tag -> tag.putInt("x", v));}
     * </p>
     */
    public static void edit(ItemStack stack, java.util.function.Consumer<CompoundTag> action) {
        CompoundTag tag = copyForEdit(stack);
        action.accept(tag);
        setTag(stack, tag);
    }
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
