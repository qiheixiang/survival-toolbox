package com.zzq.survival_toolbox.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 随身次元袋存储辅助（多页版）
 * <p>
 * 存储结构：每页一个"箱子"（63 格，每格一种物品、数量 long 无上限自动合并），页数无限。
 * 数据保存在物品 NBT 中：{@code PocketData: {Pages: [{Name: <页名>, Items: [{Item: <物品NBT>, Count: <long>}, ...]}, ...]}}
 * 旧版单页格式（PocketItems 键）读取时自动迁移为第 1 页。
 * </p>
 */
public class PocketStorageHelper {

    private static final String TAG_DATA = "PocketData";
    private static final String TAG_PAGES = "Pages";
    private static final String TAG_NAME = "Name";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_OLD_ITEMS = "PocketItems";

    /** 存储条目：物品（不含数量）+ 合并后的数量（long 无上限） */
    public record Entry(ItemStack stack, long count) {
    }

    /** 一页：页名 + 54 个固定槽位（null = 空槽） */
    public static class Page {
        public static final int SLOTS = 54;
        public String name;
        public final List<Entry> entries = new ArrayList<>();

        public Page(String name) {
            this.name = name;
            while (entries.size() < SLOTS) entries.add(null);
        }
    }

    /** 从次元袋物品读取全部页 */
    public static List<Page> readPages(ItemStack bag) {
        List<Page> pages = new ArrayList<>();
        if (!bag.hasTag()) return pages;
        CompoundTag root = bag.getTag();
        if (root == null) return pages;
        // contains 的第二参数为值类型：Pages/Items 均为列表（TAG_LIST=9），若写成 10 将永远判定为 false。
        if (root.contains(TAG_PAGES, 9)) {
            ListTag pagesTag = root.getList(TAG_PAGES, 10);
            for (int p = 0; p < pagesTag.size(); p++) {
                CompoundTag pageTag = pagesTag.getCompound(p);
                Page page = new Page(pageTag.getString(TAG_NAME));
                if (page.name == null || page.name.isEmpty()) page.name = String.valueOf(p + 1);
                ListTag list = pageTag.getList(TAG_ITEMS, 10);
                // 新格式带 Slot 键按原槽位放回；旧格式（无 Slot）为紧凑列表按顺序填入
                int nextSlot = 0;
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entryTag = list.getCompound(i);
                    ItemStack stack = ItemStack.of(entryTag.getCompound("Item"));
                    if (stack.isEmpty()) continue;
                    int slot = entryTag.contains("Slot", 3) ? entryTag.getInt("Slot") : nextSlot;
                    if (slot >= 0 && slot < Page.SLOTS) {
                        page.entries.set(slot, new Entry(stack, Math.max(1, entryTag.getLong("Count"))));
                    }
                    nextSlot++;
                }
                pages.add(page);
            }
        } else if (root.contains(TAG_OLD_ITEMS, 9)) {
            // 旧版单页格式兼容：作为第 1 页
            Page page = new Page("1");
            ListTag list = root.getList(TAG_OLD_ITEMS, 10);
            int nextSlot = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                ItemStack stack = ItemStack.of(entryTag.getCompound("Item"));
                if (stack.isEmpty()) continue;
                if (nextSlot < Page.SLOTS) {
                    page.entries.set(nextSlot, new Entry(stack, Math.max(1, entryTag.getLong("Count"))));
                }
                nextSlot++;
            }
            pages.add(page);
        }
        return pages;
    }

    /** 把全部页写回次元袋物品（清空并重写） */
    public static void writePages(ItemStack bag, List<Page> pages) {
        ListTag pagesTag = new ListTag();
        for (int p = 0; p < pages.size(); p++) {
            Page page = pages.get(p);
            CompoundTag pageTag = new CompoundTag();
            pageTag.putString(TAG_NAME, page.name == null || page.name.isEmpty() ? String.valueOf(p + 1) : page.name);
            ListTag list = new ListTag();
            for (int slot = 0; slot < page.entries.size(); slot++) {
                PocketStorageHelper.Entry e = page.entries.get(slot);
                if (e == null || e.stack().isEmpty() || e.count() <= 0) continue;
                CompoundTag entryTag = new CompoundTag();
                CompoundTag itemTag = new CompoundTag();
                e.stack().save(itemTag);
                // 关键：1.20.1 的 ItemStack 构造要求 Count > 0，否则判空读不出来
                itemTag.putByte("Count", (byte) 1);
                entryTag.put("Item", itemTag);
                entryTag.putLong("Count", e.count());
                entryTag.putInt("Slot", slot);
                list.add(entryTag);
            }
            pageTag.put(TAG_ITEMS, list);
            pagesTag.add(pageTag);
        }
        CompoundTag root = bag.getOrCreateTag();
        root.put(TAG_PAGES, pagesTag);
        bag.setTag(root);
    }

    /** 判断两条物品是否可合并（物品与 NBT 完全相同，忽略数量）。
     * 不可使用 ItemStack.isSameItemSameTags：1.20.1 中该方法会比较 count，
     * 而模板 stack 数量固定为 1，会把同种物品误判为不同而分成多条。
     * 药水等物品在 NBT 解析时会被 verifyTagAfterLoad 规范化（如补写 Potion 键），
     * 与手持/新放入物品的 NBT 可能不一致导致不合并，因此两边规范化后再比较。 */
    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (!a.is(b.getItem())) return false;
        net.minecraft.nbt.CompoundTag ta = a.getTag() == null ? new net.minecraft.nbt.CompoundTag() : a.getTag().copy();
        net.minecraft.nbt.CompoundTag tb = b.getTag() == null ? new net.minecraft.nbt.CompoundTag() : b.getTag().copy();
        a.getItem().verifyTagAfterLoad(ta);
        b.getItem().verifyTagAfterLoad(tb);
        return ta.equals(tb);
    }

    /**
     * 快捷收纳：把物品收进次元袋（手持右键背包物品时调用）。
     * <p>
     * 从第一页开始：有一样的（同物品同 NBT）就合并进去；没有就找本页空位放入；
     * 本页没有空位就翻下一页；所有页都满且没有一样的 → 新建一页放入。
     * 每格数量无上限（long），合并/放置不受堆叠上限限制。
     * </p>
     *
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long quickDeposit(ItemStack bag, ItemStack target) {
        List<Page> pages = readPages(bag);
        long need = target.getCount();
        if (need <= 0) return 0;
        for (Page page : pages) {
            // 1) 同物品合并（同页任意已有堆）
            for (int slot = 0; slot < Page.SLOTS && need > 0; slot++) {
                Entry e = page.entries.get(slot);
                if (e != null && sameItem(e.stack(), target)) {
                    page.entries.set(slot, new Entry(e.stack(), e.count() + need));
                    need = 0;
                    break;
                }
            }
            // 2) 空位放入
            if (need > 0) {
                for (int slot = 0; slot < Page.SLOTS; slot++) {
                    if (page.entries.get(slot) == null) {
                        ItemStack s = target.copy();
                        s.setCount(1);
                        page.entries.set(slot, new Entry(s, need));
                        need = 0;
                        break;
                    }
                }
            }
        }
        // 3) 所有页都满：新建一页放入
        if (need > 0) {
            Page p = new Page(String.valueOf(pages.size() + 1));
            ItemStack s = target.copy();
            s.setCount(1);
            p.entries.set(0, new Entry(s, need));
            pages.add(p);
            need = 0;
        }
        writePages(bag, pages);
        return need;
    }
}
