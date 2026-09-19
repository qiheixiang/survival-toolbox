package com.zzq.survival_toolbox.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 光标上的流体（"和物品一样拖拽"用的载体）
 * <p>
 * 原版光标（{@code menu.getCarried()}）只能放 ItemStack，放不了 FluidStack。
 * 这里用一个**带标记 NBT 的桶**当载体：光标上显示该流体的桶（没有桶的流体退化成空桶），
 * 右下角由界面画出真实 mB 数字；标记本身随物品 NBT 一起被原版"光标物品"同步送到客户端，
 * 所以不需要额外的网络字段。
 * </p>
 * <p>
 * 标记只会出现在"从托盘/存储里拿起流体"到"放下"之间，所有点击在
 * {@code PocketDimensionMenu#clicked} 里被接管，不会把假桶放进任何格子；
 * 关界面时光标上残留的流体也会被存回袋子存储，既不复制也不吞掉。
 * </p>
 */
public final class PocketFluidCarry {

    private static final String TAG_ROOT = "PocketCarriedFluid";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_AMOUNT = "Amount";

    private PocketFluidCarry() {
    }

    /** 把一条流体做成"光标载体"物品（桶 + 标记） */
    public static ItemStack make(PocketStorageHelper.FluidEntry entry) {
        if (entry == null || entry.amount() <= 0 || entry.stackTag() == null) return ItemStack.EMPTY;
        ItemStack stack = entry.displayStack();
        stack.setCount(1);
        CompoundTag marker = new CompoundTag();
        marker.put(TAG_FLUID, entry.stackTag().copy());
        marker.putLong(TAG_AMOUNT, entry.amount());
        CompoundTag root = ItemNbt.copyForEdit(stack);
        root.put(TAG_ROOT, marker);
        stack.setTag(root);
        return stack;
    }

    /** 用给定数量重新做一份载体（放下了一部分后剩多少继续拿着） */
    public static ItemStack make(PocketStorageHelper.FluidEntry entry, long amount) {
        if (entry == null || amount <= 0) return ItemStack.EMPTY;
        return make(new PocketStorageHelper.FluidEntry(entry.stackTag(), amount));
    }

    /** 光标物品是不是"拿着流体"的载体；是则返回它代表的那条流体，否则 null */
    public static PocketStorageHelper.FluidEntry entryOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(TAG_ROOT, 10)) return null;
        CompoundTag marker = root.getCompound(TAG_ROOT);
        CompoundTag fluidTag = marker.getCompound(TAG_FLUID);
        long amount = marker.getLong(TAG_AMOUNT);
        if (fluidTag.isEmpty() || amount <= 0) return null;
        return new PocketStorageHelper.FluidEntry(fluidTag, amount);
    }
}
