package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 随身次元袋容器（多页 + 固定槽位版）
 * <p>
 * 每页一个"箱子"：54 个固定槽位（9 列 × 6 行，空槽 = null），点击哪个格子就放哪个格子。
 * 渲染/交互统一走"当前显示条目"（非搜索 = 当前页 54 槽含空，搜索 = 平铺结果），
 * 客户端显示完全由同步包驱动，服务端数据权威。
 * </p>
 */
public class PocketDimensionContainer implements Container {

    public static final int PAGE_SIZE = 54;

    private final ItemStack bag;
    private final HolderLookup.Provider registries;
    private boolean clientSide = false;

    // 服务端：完整页数据（每页 54 槽，null = 空）
    private final List<PocketStorageHelper.Page> pages = new ArrayList<>();

    // 客户端：服务端同步的页名列表 / 每页条目数（只读）
    private final List<String> pageNames = new ArrayList<>();
    private final List<Integer> pageCounts = new ArrayList<>();

    // 当前显示条目（非搜索 = 当前页 54 槽含 null；搜索 = 平铺结果）+ 真实位置（服务端维护）
    private final List<PocketStorageHelper.Entry> displayEntries = new ArrayList<>();
    private final List<EntryRef> displayRefs = new ArrayList<>();

    private int currentPage = 0;
    private int pageOffset = 0;
    private String filterKeyword = "";
    private boolean searching = false;
    /** 搜索结果超过一页时截断（不再翻页） */
    private boolean searchTruncated = false;

    /** 显示条目 → 真实页内槽位 */
    private record EntryRef(int page, int slot) {
    }

    public PocketDimensionContainer(ItemStack bag, HolderLookup.Provider registries) {
        this.bag = bag;
        this.registries = registries;
        this.pages.addAll(PocketStorageHelper.readPages(bag, registries));
        if (pages.isEmpty()) pages.add(new PocketStorageHelper.Page("1"));
        rebuildDisplay();
    }

    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    // ============================================================
    // 页操作（服务端）
    // ============================================================

    public List<PocketStorageHelper.Page> getPages() {
        return pages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /** 添加新页（默认名 = 当前最大数字页名 + 1） */
    public void addPage() {
        if (clientSide) return;
        int maxNum = 0;
        for (PocketStorageHelper.Page p : pages) {
            try {
                maxNum = Math.max(maxNum, Integer.parseInt(p.name.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        pages.add(new PocketStorageHelper.Page(String.valueOf(maxNum + 1)));
        currentPage = pages.size() - 1;
        rebuildDisplay();
        save();
    }

    /** 删除页（仅空页可删）；返回是否成功 */
    public boolean removePage(int index) {
        if (clientSide) return false;
        if (index < 0 || index >= pages.size()) return false;
        if (!isPageEmptyInternal(index)) return false;
        pages.remove(index);
        if (pages.isEmpty()) {
            pages.add(new PocketStorageHelper.Page("1"));
            currentPage = 0;
        } else if (currentPage >= pages.size()) {
            currentPage = Math.max(0, pages.size() - 1);
        }
        rebuildDisplay();
        save();
        return true;
    }

    private boolean isPageEmptyInternal(int index) {
        for (PocketStorageHelper.Entry e : pages.get(index).entries) {
            if (e != null) return false;
        }
        return true;
    }

    /** 页重命名 */
    public void renamePage(int index, String name) {
        if (clientSide) return;
        if (index < 0 || index >= pages.size()) return;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return;
        pages.get(index).name = n;
        save();
    }

    /** 切换当前页（清除搜索） */
    public void setCurrentPage(int index) {
        if (clientSide) return;
        if (index < 0 || index >= pages.size()) return;
        currentPage = index;
        setSearch("");
    }

    /** 搜索关键词：跨页匹配，结果平铺显示（空串恢复分页） */
    public void setSearch(String keyword) {
        this.filterKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        this.searching = !this.filterKeyword.isEmpty();
        this.pageOffset = 0;
        rebuildDisplay();
        setChanged();
    }

    public boolean isSearching() {
        return searching;
    }

    /** 搜索结果是否因超过一页而被截断 */
    public boolean isSearchTruncated() {
        return searchTruncated;
    }

    private static boolean matches(ItemStack stack, String keyword) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (name.contains(keyword)) return true;
        // 拼音匹配（联动 JEC/PinIn，未安装自动跳过）
        if (com.zzq.survival_toolbox.util.PinyinUtil.matches(name, keyword)) return true;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        return id.contains(keyword);
    }

    /** 重建当前显示（非搜索 = 当前页 54 槽含 null；搜索 = 平铺结果，超过一页截断） */
    private void rebuildDisplay() {
        displayEntries.clear();
        displayRefs.clear();
        searchTruncated = false;
        if (searching) {
            outer:
            for (int p = 0; p < pages.size(); p++) {
                List<PocketStorageHelper.Entry> entries = pages.get(p).entries;
                for (int slot = 0; slot < entries.size(); slot++) {
                    PocketStorageHelper.Entry e = entries.get(slot);
                    if (e != null && matches(e.stack(), filterKeyword)) {
                        if (displayEntries.size() >= PAGE_SIZE) {
                            searchTruncated = true;
                            break outer;
                        }
                        displayEntries.add(e);
                        displayRefs.add(new EntryRef(p, slot));
                    }
                }
            }
        } else {
            PocketStorageHelper.Page page = pages.get(currentPage);
            for (int i = 0; i < page.entries.size(); i++) {
                displayEntries.add(page.entries.get(i));
                displayRefs.add(new EntryRef(currentPage, i));
            }
        }
        if (pageOffset > maxPageOffset()) pageOffset = maxPageOffset();
    }

    /** 当前显示的非空条目总数 */
    public int getDisplayCount() {
        int n = 0;
        for (PocketStorageHelper.Entry e : displayEntries) {
            if (e != null) n++;
        }
        return n;
    }

    private static int countNonEmpty(PocketStorageHelper.Page page) {
        int n = 0;
        for (PocketStorageHelper.Entry e : page.entries) {
            if (e != null) n++;
        }
        return n;
    }

    /** 最大翻页偏移（搜索结果超过一页时可翻） */
    public int maxPageOffset() {
        return Math.max(0, getDisplayCount() - PAGE_SIZE);
    }

    public int getPageOffset() {
        return pageOffset;
    }

    public void setPageOffset(int offset) {
        this.pageOffset = Math.max(0, Math.min(offset, maxPageOffset()));
        setChanged();
    }

    /** 服务端：当前显示的非空条目（槽位索引 + 条目），供同步包使用 */
    public List<SlotData> getSyncSlots() {
        List<SlotData> out = new ArrayList<>();
        for (int i = 0; i < displayEntries.size(); i++) {
            PocketStorageHelper.Entry e = displayEntries.get(i);
            if (e != null) out.add(new SlotData(i, e));
        }
        return out;
    }

    /** 客户端：接收服务端同步的显示数据（槽位条目 + 页名 + 每页条目数 + 当前页） */
    public void replaceDisplay(List<SlotData> slots, int pageOffset,
                               List<String> pageNames, List<Integer> pageCounts, int currentPage) {
        this.pageOffset = Math.max(0, pageOffset);
        this.pageNames.clear();
        this.pageNames.addAll(pageNames);
        this.pageCounts.clear();
        this.pageCounts.addAll(pageCounts);
        this.currentPage = currentPage;
        this.searching = !this.filterKeyword.isEmpty();
        this.displayEntries.clear();
        this.displayRefs.clear();
        if (searching) {
            // 平铺结果：slot 即平铺索引
            int max = 0;
            for (SlotData sd : slots) max = Math.max(max, sd.slot());
            for (int i = 0; i <= max; i++) displayEntries.add(null);
            for (SlotData sd : slots) displayEntries.set(sd.slot(), sd.entry());
        } else {
            // 当前页 54 槽（含空）
            for (int i = 0; i < PAGE_SIZE; i++) displayEntries.add(null);
            for (SlotData sd : slots) {
                if (sd.slot() >= 0 && sd.slot() < PAGE_SIZE) {
                    displayEntries.set(sd.slot(), sd.entry());
                }
            }
        }
    }

    /** 页名列表（服务端 = 真实页名；客户端 = 同步的页名） */
    public List<String> getPageNames() {
        if (clientSide) return pageNames;
        List<String> names = new ArrayList<>(pages.size());
        for (PocketStorageHelper.Page p : pages) names.add(p.name);
        return names;
    }

    /** 每页条目数（服务端 = 真实计数；客户端 = 同步的计数） */
    public List<Integer> getPageCounts() {
        if (clientSide) return pageCounts;
        List<Integer> counts = new ArrayList<>(pages.size());
        for (PocketStorageHelper.Page p : pages) counts.add(countNonEmpty(p));
        return counts;
    }

    // ============================================================
    // Container 接口（54 格窗口）
    // ============================================================

    @Override
    public int getContainerSize() {
        return PAGE_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return getDisplayCount() == 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        int idx = pageOffset + slot;
        if (idx >= 0 && idx < displayEntries.size()) {
            PocketStorageHelper.Entry e = displayEntries.get(idx);
            if (e != null) {
                ItemStack s = e.stack().copy();
                s.setCount((int) Math.max(1, Math.min(e.count(), Integer.MAX_VALUE)));
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (clientSide) return ItemStack.EMPTY;
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayRefs.size()) return ItemStack.EMPTY;
        EntryRef ref = displayRefs.get(idx);
        PocketStorageHelper.Page pg = pages.get(ref.page);
        PocketStorageHelper.Entry e = pg.entries.get(ref.slot);
        if (e == null) return ItemStack.EMPTY;
        long take = Math.min(amount, e.count());
        ItemStack s = e.stack().copy();
        s.setCount((int) take);
        if (e.count() - take <= 0) {
            pg.entries.set(ref.slot, null);
        } else {
            pg.entries.set(ref.slot, new PocketStorageHelper.Entry(e.stack(), e.count() - take));
        }
        rebuildDisplay();
        save();
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setBySlot(slot, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void setChanged() {
        // 菜单通过 broadcastChanges 轮询比较检测变化
    }

    @Override
    public void clearContent() {
        if (clientSide) return;
        for (PocketStorageHelper.Page p : pages) {
            java.util.Collections.fill(p.entries, null);
        }
        rebuildDisplay();
        save();
    }

    /** 把全部页写回物品 NBT 并通知菜单刷新 */
    public void save() {
        if (clientSide) return;
        PocketStorageHelper.writePages(bag, pages, registries);
        setChanged();
    }

    // ============================================================
    // 槽位交互（服务端）
    // ============================================================

    /** Slot.set：空 = 取走整格；非空 = 该槽条目显示值设为 stack.count。客户端广播忽略。 */
    public void setBySlot(int slot, ItemStack stack) {
        if (clientSide || searching) return;
        int targetSlot = pageOffset + slot;
        if (targetSlot < 0 || targetSlot >= PAGE_SIZE) return;
        PocketStorageHelper.Page pg = pages.get(currentPage);
        if (stack.isEmpty()) {
            PocketStorageHelper.Entry e = pg.entries.get(targetSlot);
            if (e == null) return;
            long take = Math.min(64, e.count());
            if (e.count() - take <= 0) {
                pg.entries.set(targetSlot, null);
            } else {
                pg.entries.set(targetSlot, new PocketStorageHelper.Entry(e.stack(), e.count() - take));
            }
        } else {
            PocketStorageHelper.Entry e = pg.entries.get(targetSlot);
            if (e == null) {
                // 空槽放入（外部容器操作如整理 mod 的一键放入调用 setItem）
                pg.entries.set(targetSlot, new PocketStorageHelper.Entry(stack.copy(), stack.getCount()));
            } else if (e.count() != stack.getCount()) {
                pg.entries.set(targetSlot, new PocketStorageHelper.Entry(e.stack(), stack.getCount()));
            }
        }
        rebuildDisplay();
        save();
    }

    /**
     * 指定位置放入（点击格子）：同类 → 合并到该槽；空槽 → 放入该槽；异类 → 替换（交换用）。
     */
    public void setItemAt(int slot, ItemStack stack) {
        if (clientSide || searching) return;
        if (stack.isEmpty()) return;
        int targetSlot = pageOffset + slot;
        if (targetSlot < 0 || targetSlot >= PAGE_SIZE) return;
        PocketStorageHelper.Page cur = pages.get(currentPage);
        PocketStorageHelper.Entry e = cur.entries.get(targetSlot);
        if (e != null && PocketStorageHelper.sameItem(e.stack(), stack)) {
            cur.entries.set(targetSlot, new PocketStorageHelper.Entry(e.stack(), e.count() + stack.getCount()));
        } else {
            cur.entries.set(targetSlot, new PocketStorageHelper.Entry(stack.copy(), stack.getCount()));
        }
        rebuildDisplay();
        save();
    }

    /** 放入物品（只作用于当前页）：同类合并；新种类第一个空槽；页满返回剩余。 */
    public ItemStack insert(ItemStack stack) {
        if (clientSide) return stack;
        if (stack.isEmpty()) return stack;
        if (stack.getItem() instanceof com.zzq.survival_toolbox.item.PocketDimensionItem) return stack;
        PocketStorageHelper.Page cur = pages.get(currentPage);
        int emptySlot = -1;
        for (int i = 0; i < cur.entries.size(); i++) {
            PocketStorageHelper.Entry e = cur.entries.get(i);
            if (e == null) {
                if (emptySlot < 0) emptySlot = i;
                continue;
            }
            if (PocketStorageHelper.sameItem(e.stack(), stack)) {
                cur.entries.set(i, new PocketStorageHelper.Entry(e.stack(), e.count() + stack.getCount()));
                rebuildDisplay();
                save();
                return ItemStack.EMPTY;
            }
        }
        if (emptySlot >= 0) {
            cur.entries.set(emptySlot, new PocketStorageHelper.Entry(stack.copy(), stack.getCount()));
            rebuildDisplay();
            save();
            return ItemStack.EMPTY;
        }
        return stack;
    }
}
