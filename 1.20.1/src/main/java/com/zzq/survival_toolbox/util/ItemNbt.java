package com.zzq.survival_toolbox.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * ItemStack NBT 辅助类（1.20.1 直接用物品自己的 NBT 标签，没有 1.21.1 那套数据组件）
 * <p>
 * ⚠️ <b>线程安全（实测曾多次出现的掉线问题）</b>：服务端每 tick 都会把打开的容器整包发给客户端
 * （{@code ClientboundContainerSetContentPacket}），<b>编码是在 netty 线程上做的</b>，
 * 它会把物品的 NBT 标签整份 {@code CompoundTag#copy()} 一遍。
 * 这时服务端线程若正在<b>就地修改同一份标签</b>（{@code ItemStack#getOrCreateTag()} 拿到的就是实时那份），
 * netty 那边就会 {@code ConcurrentModificationException} →
 * {@code Failed to encode packet 'clientbound/minecraft:container_set_content'} → <b>玩家直接掉线</b>。
 * 袋子（玩家物品栏里那个）每 tick 都在这张包里，所以凡是写袋子 NBT 的地方
 * <b>一律用 {@link #copyForEdit} 复制出来改，再用 {@link #setTag} 写回</b>，绝不要就地改。
 * </p>
 */
public final class ItemNbt {

    private ItemNbt() {
    }

    /** 取一份标签副本（没有标签时返回 null） */
    public static CompoundTag getTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.copy();
    }

    /** 取一份"可自由修改"的标签副本；改完必须用 {@link #setTag} 写回 */
    public static CompoundTag copyForEdit(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
    }

    /** 写回标签（内部再复制一份：调用方之后继续改自己那份也不会动到物品里的） */
    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag.copy());
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
    /** 物品栈有没有自定义 NBT 标签 */
    public static boolean hasTag(ItemStack stack) {
        return stack.hasTag();
    }
}
