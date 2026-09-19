package com.zzq.survival_toolbox.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 次元袋托盘（出货工作台）数据层
 * <p>
 * 托盘是"装着东西去和容器打交道"的中转台，不是第二套存储：
 * 27 格，每格一叠物品（原版堆叠上限）或一种流体（数量 mB，long）。
 * </p>
 * <p>
 * <b>只存在袋子自己的 NBT 里</b>（与 {@code Pages}/{@code FluidPages} 同一个自定义数据根标签，
 * 键不同）：不随"共享/本地"模式切换，也不写进服务器存档——托盘跟着袋子走，袋子丢了托盘也丢。
 * </p>
 * <p>
 * 存法（与页格式保持一致，便于复用读写代码）：
 * {@code TrayItems: [{Item: <物品NBT，数量恒为1>, Count: <long>, Slot: 0..26}]}、
 * {@code TrayFluids: [{Fluid: <流体NBT>, Amount: <long mB>, Slot: 0..26}]}、
 * {@code TrayOut: <boolean>}（true = 送出托盘→容器，false = 纳入容器→存储，缺省送出）。
 * 同一格物品与流体互斥，读到时以物品为准。
 * </p>
 */
public final class PocketTrayStorage {

    /** 托盘格数：9×3，与界面里的排布一致 */
    public static final int SLOTS = 27;

    private static final String TAG_ITEMS = "TrayItems";
    private static final String TAG_FLUIDS = "TrayFluids";
    private static final String TAG_OUT = "TrayOut";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_SLOT = "Slot";

    private PocketTrayStorage() {
    }

    /** 托盘内容：27 格物品 + 27 格流体（同格互斥）+ 方向 */
    public static class Tray {
        /** 物品格（恒为 27 项，空 = ItemStack.EMPTY；不是最后修改的实时引用，改完要写回） */
        public final List<ItemStack> items = new ArrayList<>();
        /** 流体格（恒为 27 项，空 = null） */
        public final List<PocketStorageHelper.FluidEntry> fluids = new ArrayList<>();
        /** 交互方向：true = 送出（托盘 → 容器），false = 纳入（容器 → 存储） */
        public boolean out = true;

        public Tray() {
            for (int i = 0; i < SLOTS; i++) {
                items.add(ItemStack.EMPTY);
                fluids.add(null);
            }
        }

        public boolean isEmpty() {
            for (int i = 0; i < SLOTS; i++) {
                if (!items.get(i).isEmpty() || fluids.get(i) != null) return false;
            }
            return true;
        }
    }

    /** 从次元袋物品读取托盘（没有托盘数据就是空托盘） */
    public static Tray read(ItemStack bag, HolderLookup.Provider registries) {
        CompoundTag root = ItemNbt.getTag(bag);
        return root == null ? new Tray() : read(root, registries);
    }

    /** 从一段自定义数据根标签读取托盘 */
    public static Tray read(CompoundTag root, HolderLookup.Provider registries) {
        Tray tray = new Tray();
        if (root.contains(TAG_OUT, 1)) {
            tray.out = root.getBoolean(TAG_OUT);
        }
        if (root.contains(TAG_ITEMS, 9)) {
            ListTag list = root.getList(TAG_ITEMS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                ItemStack stack = ItemStack.parseOptional(registries, entryTag.getCompound(TAG_ITEM));
                if (stack.isEmpty()) continue;
                int slot = entryTag.getInt(TAG_SLOT);
                if (slot < 0 || slot >= SLOTS) continue;
                stack.setCount((int) Math.max(1, Math.min(entryTag.getLong(TAG_COUNT), 64)));
                tray.items.set(slot, stack);
            }
        }
        if (root.contains(TAG_FLUIDS, 9)) {
            ListTag list = root.getList(TAG_FLUIDS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                CompoundTag fluidTag = entryTag.getCompound(TAG_FLUID);
                if (fluidTag.isEmpty()) continue;
                long amount = entryTag.getLong(TAG_AMOUNT);
                if (amount <= 0) continue;
                int slot = entryTag.getInt(TAG_SLOT);
                if (slot < 0 || slot >= SLOTS) continue;
                // 同格互斥：物品优先（物品页与流体页共用格子的老规矩）
                if (tray.items.get(slot).isEmpty()) {
                    tray.fluids.set(slot, new PocketStorageHelper.FluidEntry(fluidTag, amount));
                }
            }
        }
        return tray;
    }

    /** 把托盘写回次元袋物品（只替换 TrayItems / TrayFluids / TrayOut 三个键，保留 Pages 等其他键） */
    public static void write(ItemStack bag, Tray tray, HolderLookup.Provider registries) {
        CompoundTag root = ItemNbt.copyForEdit(bag);
        ListTag items = new ListTag();
        ListTag fluids = new ListTag();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = tray.items.get(slot);
            if (!stack.isEmpty()) {
                CompoundTag entryTag = new CompoundTag();
                CompoundTag itemTag = (CompoundTag) stack.copyWithCount(1).saveOptional(registries);
                entryTag.put(TAG_ITEM, itemTag);
                entryTag.putLong(TAG_COUNT, stack.getCount());
                entryTag.putInt(TAG_SLOT, slot);
                items.add(entryTag);
            }
            PocketStorageHelper.FluidEntry fluid = tray.fluids.get(slot);
            if (fluid != null && fluid.amount() > 0 && fluid.stackTag() != null) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(TAG_FLUID, fluid.stackTag());
                entryTag.putLong(TAG_AMOUNT, fluid.amount());
                entryTag.putInt(TAG_SLOT, slot);
                fluids.add(entryTag);
            }
        }
        root.put(TAG_ITEMS, items);
        root.put(TAG_FLUIDS, fluids);
        root.putBoolean(TAG_OUT, tray.out);
        ItemNbt.setTag(bag, root);
    }

    /** 袋子当前的托板方向（没有标记时默认"送出"） */
    public static boolean isOut(ItemStack bag) {
        CompoundTag root = ItemNbt.getTag(bag);
        if (root == null || !root.contains(TAG_OUT, 1)) return true;
        return root.getBoolean(TAG_OUT);
    }

    /** 只写方向标记（面板上切"送出/纳入"时用；不碰托盘内容） */
    public static void setOut(ItemStack bag, boolean out) {
        CompoundTag root = ItemNbt.copyForEdit(bag);
        root.putBoolean(TAG_OUT, out);
        ItemNbt.setTag(bag, root);
    }
}
