package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData;
import com.zzq.survival_toolbox.util.PocketSharedStorage;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
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
    /** 服务端：流体页数据（与物品页并存于同一份存储，共用同一页的 54 格） */
    private final List<PocketStorageHelper.FluidPage> fluidPages = new ArrayList<>();
    /** 服务端的开袋玩家：共享模式要按他的 UUID 读写服务器存档 */
    private final Player owner;
    /** true = 共享（末影箱式，数据在服务器存档）；false = 本地（数据存在袋子里） */
    private boolean shared;
    private boolean clientSide = false;

    // 服务端：完整页数据（每页 63 槽，null = 空）
    private final List<PocketStorageHelper.Page> pages = new ArrayList<>();

    // 客户端：服务端同步的页名列表 / 每页条目数（只读）
    private final List<String> pageNames = new ArrayList<>();
    private final List<Integer> pageCounts = new ArrayList<>();

    // 当前显示条目（非搜索 = 当前页 63 槽含 null；搜索 = 平铺结果）+ 真实位置（服务端维护）
    private final List<PocketStorageHelper.Entry> displayEntries = new ArrayList<>();
    private final List<EntryRef> displayRefs = new ArrayList<>();

    private int currentPage = 0;
    private int pageOffset = 0;
    private String filterKeyword = "";
    private boolean searching = false;
    /** 搜索结果超过一页时截断（不再翻页） */
    private boolean searchTruncated = false;

    /** 显示条目 → 真实页内槽位 */
    private record EntryRef(int page, int slot, boolean fluid) {
    }

    public PocketDimensionContainer(ItemStack bag, Player owner) {
        this.bag = bag;
        this.owner = owner;
        if (owner == null || owner.level().isClientSide) {
            // 客户端：页数据由同步包驱动，这里只放一页占位
            this.shared = PocketStorageHelper.isShared(bag);
            this.pages.add(new PocketStorageHelper.Page("1"));
        } else {
            // 兼容旧存档：老袋子没写模式标记，按"有没有本地数据"判定，然后立刻写死，避免以后模式跳变
            this.shared = PocketStorageHelper.isShared(bag);
            PocketStorageHelper.setShared(bag, this.shared);
            this.pages.addAll(loadPagesFromSource());
            if (pages.isEmpty()) pages.add(new PocketStorageHelper.Page("1"));
            this.fluidPages.addAll(loadFluidPagesFromSource());
            if (fluidPages.isEmpty()) fluidPages.add(new PocketStorageHelper.FluidPage("1"));
        }
        rebuildDisplay();
        repairSlotCollisions();
    }

    /**
     * 修老数据：同一格上既有物品又有流体。
     * <p>
     * 老版本入库不避让流体格，于是"物品压在水源上"这种数据被存进了存档：显示上物品优先，
     * 那片流体是**隐形**的，而且只要整理一次就会被清掉（实测曾丢失一格水源）。
     * 现在开袋时就把这种流体系目挪到第一个"物品页与流体页都空着"的格子上，同时把存档修干净。
     * </p>
     */
    private void repairSlotCollisions() {
        if (clientSide) return;
        boolean moved = false;
        for (int p = 0; p < fluidPages.size(); p++) {
            for (int slot = 0; slot < fluidPages.get(p).entries.size(); slot++) {
                PocketStorageHelper.FluidEntry f = fluidPages.get(p).entries.get(slot);
                if (f == null) continue;
                boolean blocked = p < pages.size() && slot < pages.get(p).entries.size()
                        && pages.get(p).entries.get(slot) != null;
                if (!blocked) continue;
                int limit = Math.max(pages.size(), fluidPages.size()) + 1;
                int tp = -1;
                int ts = -1;
                outer:
                for (int q = 0; q < limit; q++) {
                    while (pages.size() <= q) {
                        pages.add(new PocketStorageHelper.Page(String.valueOf(pages.size() + 1)));
                    }
                    while (fluidPages.size() <= q) {
                        fluidPages.add(new PocketStorageHelper.FluidPage(String.valueOf(fluidPages.size() + 1)));
                    }
                    for (int s = 0; s < PocketStorageHelper.Page.SLOTS; s++) {
                        if (pages.get(q).entries.get(s) != null) continue;
                        if (fluidPages.get(q).entries.get(s) != null) continue;
                        tp = q;
                        ts = s;
                        break outer;
                    }
                }
                if (tp < 0) continue;
                fluidPages.get(tp).entries.set(ts, f);
                fluidPages.get(p).entries.set(slot, null);
                moved = true;
            }
        }
        if (moved) {
            rebuildDisplay();
            save();
        }
    }

    /** 按当前模式从对应存储读取页数据（仅服务端） */
    private List<PocketStorageHelper.Page> loadPagesFromSource() {
        if (shared && owner != null && owner.getServer() != null) {
            return PocketSharedStorage.get(owner.getServer()).getPages(owner.getUUID());
        }
        return PocketStorageHelper.readPages(bag);
    }

    /** 按当前模式从对应存储读取流体页（仅服务端） */
    private List<PocketStorageHelper.FluidPage> loadFluidPagesFromSource() {
        if (shared && owner != null && owner.getServer() != null) {
            return PocketSharedStorage.get(owner.getServer()).getFluidPages(owner.getUUID());
        }
        return PocketStorageHelper.readFluidPages(bag);
    }

    /** 当前视图里哪些格是流体（54 位掩码） */
    public long buildFluidMask() {
        long mask = 0L;
        for (int i = 0; i < displayRefs.size() && i < 54; i++) {
            if (displayRefs.get(i).fluid()) mask |= 1L << i;
        }
        return mask;
    }

    /** 流体搜索匹配：显示名 + 注册名 + 拼音 */
    private static boolean fluidMatches(PocketStorageHelper.FluidEntry entry, String keyword) {
        ItemStack display = entry.displayStack();
        String name = display.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (name.contains(keyword)) return true;
        if (com.zzq.survival_toolbox.util.PinyinUtil.matches(name, keyword)) return true;
        net.minecraftforge.fluids.FluidStack stack = entry.toStack(1);
        if (stack.isEmpty()) return false;
        String id = net.minecraft.core.registries.BuiltInRegistries.FLUID
                .getKey(stack.getFluid()).toString().toLowerCase(Locale.ROOT);
        return id.contains(keyword);
    }

    /** 该显示格上是不是流体 */
    /**
     * 整格互换（"长按提起整格"之后点目标格时调它）：把两个显示格的**物品条目 + 流体系目整体对调**。
     * <p>
     * ⚠️ 整条搬运、数量按 long 原样搬，**不经过 ItemStack 的数量通道** ——
     * 1.20.1 给光标同步数量用的是 byte，>127 会被截断，所以"整格放到光标上"那条路走不通，才做成"提起 + 点目标格"。
     * 搜索视图（只是视图）不处理；越界返回 false。
     * </p>
     *
     * @return 是否真的换了
     */
    public boolean swapEntries(int slotA, int slotB) {
        if (clientSide || searching) return false;
        int ia = pageOffset + slotA;
        int ib = pageOffset + slotB;
        if (ia < 0 || ib < 0 || ia >= displayRefs.size() || ib >= displayRefs.size()) return false;
        EntryRef ra = displayRefs.get(ia);
        EntryRef rb = displayRefs.get(ib);
        if (ra
.page()
 < 0 || ra
.page()
 >= pages.size() || rb
.page()
 < 0 || rb
.page()
 >= pages.size()) return false;
        // 物品条目对调
        PocketStorageHelper.Entry ea = pages.get(ra
.page()
).entries.get(ra
.slot()
);
        PocketStorageHelper.Entry eb = pages.get(rb
.page()
).entries.get(rb
.slot()
);
        pages.get(ra
.page()
).entries.set(ra
.slot()
, eb);
        pages.get(rb
.page()
).entries.set(rb
.slot()
, ea);
        // 流体系目对调（页索引与物品页共用，缺页就一起补出来）
        while (fluidPages.size() <= Math.max(ra
.page()
, rb
.page()
)) {
            fluidPages.add(new PocketStorageHelper.FluidPage(String.valueOf(fluidPages.size() + 1)));
        }
        PocketStorageHelper.FluidEntry fa = fluidPages.get(ra
.page()
).entries.get(ra
.slot()
);
        PocketStorageHelper.FluidEntry fb = fluidPages.get(rb
.page()
).entries.get(rb
.slot()
);
        fluidPages.get(ra
.page()
).entries.set(ra
.slot()
, fb);
        fluidPages.get(rb
.page()
).entries.set(rb
.slot()
, fa);
        rebuildDisplay();
        save();
        return true;
    }

    public boolean isFluidSlot(int slot) {
        int idx = pageOffset + slot;
        return idx >= 0 && idx < displayRefs.size() && displayRefs.get(idx).fluid();
    }

    /** 看一眼某格的流体（不取出） */
    public net.minecraftforge.fluids.FluidStack peekFluidAt(int slot) {
        int idx = pageOffset + slot;
        if (clientSide || idx < 0 || idx >= displayRefs.size()) return net.minecraftforge.fluids.FluidStack.EMPTY;
        EntryRef ref = displayRefs.get(idx);
        if (!ref.fluid()) return net.minecraftforge.fluids.FluidStack.EMPTY;
        PocketStorageHelper.FluidEntry entry = fluidAt(ref.page(), ref.slot());
        return entry == null ? net.minecraftforge.fluids.FluidStack.EMPTY : entry.toStack(1);
    }

    /** 取走某格的流体 */
    public net.minecraftforge.fluids.FluidStack extractFluidAt(int slot, long maxAmount) {
        int idx = pageOffset + slot;
        if (clientSide || idx < 0 || idx >= displayRefs.size()) return net.minecraftforge.fluids.FluidStack.EMPTY;
        EntryRef ref = displayRefs.get(idx);
        if (!ref.fluid() || ref.page() >= fluidPages.size()) return net.minecraftforge.fluids.FluidStack.EMPTY;
        PocketStorageHelper.FluidEntry entry = fluidAt(ref.page(), ref.slot());
        if (entry == null || entry.amount() <= 0) return net.minecraftforge.fluids.FluidStack.EMPTY;
        long take = Math.min(maxAmount, entry.amount());
        net.minecraftforge.fluids.FluidStack out = entry.toStack(take);
        if (out.isEmpty()) return out;
        long left = entry.amount() - take;
        fluidPages.get(ref.page()).entries.set(ref.slot(),
                left <= 0 ? null : new PocketStorageHelper.FluidEntry(entry.stackTag(), left));
        rebuildDisplay();
        save();
        return out;
    }

    /** 取某页某格的流体条目（越界/无则 null） */
    private PocketStorageHelper.FluidEntry fluidAt(int page, int slot) {
        if (page < 0 || page >= fluidPages.size()) return null;
        List<PocketStorageHelper.FluidEntry> list = fluidPages.get(page).entries;
        return slot < 0 || slot >= list.size() ? null : list.get(slot);
    }

    /** 某个显示格上的完整流体条目（服务端；含真实 mB 数量，null = 该格不是流体） */
    public PocketStorageHelper.FluidEntry fluidEntryAt(int slot) {
        if (clientSide) return null;
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayRefs.size()) return null;
        EntryRef ref = displayRefs.get(idx);
        return fluidAt(ref.page(), ref.slot());
    }

    /**
     * 整条拿走某显示格的流体（"空手左键拿起"到光标上）：返回条目并清空该格。
     * <p>
     * 与 {@link #extractFluidAt(int, long)} 的区别：数量以 long 整条交给调用方，
     * 不受 FluidStack 的 int 上限影响，避免超大储量被截断丢失。
     * </p>
     */
    public PocketStorageHelper.FluidEntry takeFluidEntry(int slot) {
        if (clientSide) return null;
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayRefs.size()) return null;
        EntryRef ref = displayRefs.get(idx);
        PocketStorageHelper.FluidEntry entry = fluidAt(ref.page(), ref.slot());
        if (entry == null) return null;
        fluidPages.get(ref.page()).entries.set(ref.slot(), null);
        rebuildDisplay();
        save();
        return entry;
    }

    /**
     * 这一格能不能"整格换出去"：里面是物品条目、且数量不超过一组。
     * <p>
     * 用途：交换（物品↔流体、物品↔物品）时必须把这一格**整格腾空**，而光标最多只能拿一组 ——
     * 超过一组就换不了，此时**宁可什么都不做，也绝不把放不下的部分丢掉**。
     * （老写法只取一组却把整格覆盖掉，袋子里数以千计的大堆叠会被吃掉。）
     * </p>
     */
    public boolean canTakeWholeItem(int slot) {
        if (clientSide) return false;
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayRefs.size()) return false;
        EntryRef ref = displayRefs.get(idx);
        if (ref.page() < 0 || ref.page() >= pages.size()) return false;
        PocketStorageHelper.Entry e = pages.get(ref.page()).entries.get(ref.slot());
        return e != null && e.count() <= e.stack().getMaxStackSize();
    }

    /** 整格拿走物品（数量原样交给调用方；只应在 {@link #canTakeWholeItem} 为 true 时用） */
    public ItemStack takeWholeItem(int slot) {
        if (clientSide) return ItemStack.EMPTY;
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayRefs.size()) return ItemStack.EMPTY;
        EntryRef ref = displayRefs.get(idx);
        if (ref.page() < 0 || ref.page() >= pages.size()) return ItemStack.EMPTY;
        PocketStorageHelper.Entry e = pages.get(ref.page()).entries.get(ref.slot());
        if (e == null) return ItemStack.EMPTY;
        pages.get(ref.page()).entries.set(ref.slot(), null);
        ItemStack out = e.stack().copy();
        out.setCount((int) Math.min(Math.max(1L, e.count()), Integer.MAX_VALUE));
        rebuildDisplay();
        save();
        return out;
    }

    /**
     * 往指定显示格写流体（服务端）：空格放入 / 同种合并；目标格是物品或另一种流体则原样返回。
     * <p>
     * 用途：光标上拿着流体（{@link com.zzq.survival_toolbox.util.PocketFluidCarry}）放到存储格上。
     * 搜索模式（平铺结果）下不接受写入，避免破坏"搜索结果只是视图"的约定。
     * </p>
     *
     * @param slot  视图内槽位
     * @param stack 待写入的流体
     * @return 没放进去的量（0 = 全部放下）
     */
    public long insertFluidAt(int slot, net.minecraftforge.fluids.FluidStack stack) {
        long amount = stack.isEmpty() ? 0L : stack.getAmount();
        if (clientSide || searching || amount <= 0) return amount;
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayRefs.size()) return amount;
        EntryRef ref = displayRefs.get(idx);
        if (ref.page() < 0 || ref.page() >= pages.size()) return amount;
        PocketStorageHelper.Page itemPage = pages.get(ref.page());
        if (ref.slot() < 0 || ref.slot() >= itemPage.entries.size()) return amount;
        // 物品与流体共用格子：被物品占着就不能放流体
        if (itemPage.entries.get(ref.slot()) != null) return amount;
        while (fluidPages.size() <= ref.page()) {
            fluidPages.add(new PocketStorageHelper.FluidPage(String.valueOf(fluidPages.size() + 1)));
        }
        PocketStorageHelper.FluidPage fluidPage = fluidPages.get(ref.page());
        PocketStorageHelper.FluidEntry existing = fluidPage.entries.get(ref.slot());
        if (existing != null) {
            if (!existing.sameFluid(stack)) return amount;
            fluidPage.entries.set(ref.slot(),
                    new PocketStorageHelper.FluidEntry(existing.stackTag(), existing.amount() + amount));
        } else {
            fluidPage.entries.set(ref.slot(),
                    new PocketStorageHelper.FluidEntry(PocketStorageHelper.encodeFluid(stack), amount));
        }
        rebuildDisplay();
        save();
        return 0L;
    }

    /** 某页的流体条目数（页列表统计用） */
    private int fluidCountOn(int page) {
        if (page < 0 || page >= fluidPages.size()) return 0;
        int n = 0;
        for (PocketStorageHelper.FluidEntry e : fluidPages.get(page).entries) {
            if (e != null) n++;
        }
        return n;
    }

    /**
     * 往当前存储灌流体（服务端）。
     * <p>
     * 与物品收纳一致：先按流体种类合并、再找空位（避开被物品占用的格子）、所有页满就新建一页；
     * 数量 long 无上限，因此永远收得下，返回 0。
     * </p>
     *
     * @param stack 待灌入的流体
     * @return 剩余数量（恒为 0）
     */
    public long insertFluid(net.minecraftforge.fluids.FluidStack stack) {
        if (clientSide || stack.isEmpty()) return 0;
        if (fluidPages.isEmpty()) fluidPages.add(new PocketStorageHelper.FluidPage("1"));
        long remain = PocketStorageHelper.depositFluidInto(fluidPages, pages, stack);
        rebuildDisplay();
        save();
        return remain;
    }

    // ============================================================
    // 整理（排序 + 同种合并）
    // ============================================================

    /** 整理范围：只整理当前页 */
    public static final int SORT_CURRENT_PAGE = 0;
    /** 整理范围：所有页当成一个整体整理 */
    public static final int SORT_ALL_PAGES = 1;

    /** 排序方式：不分 mod，整体按注册名（用注册名的路径部分排，同名的不同 mod 会挨在一起） */
    public static final int SORT_BY_NAME = 0;
    /** 排序方式：同一个 mod 的聚在一起，各自按注册名排（用完整注册名 namespace:path 排） */
    public static final int SORT_BY_MOD = 1;
    /** 排序方式：按数量从多到少，数量相同按注册名排 */
    public static final int SORT_BY_COUNT = 2;
    /** 排序方式：按**标签**（同类的放一起：同种木头/同种锭/同种矿石…） */
    public static final int SORT_BY_TAG = 3;

    /** 整理时用的条目引用：物品或流体二选一 + 三种排序各自要用的键 */
    private record SortItem(String idKey, String pathKey, String tagKey, long count,
                            PocketStorageHelper.Entry item, PocketStorageHelper.FluidEntry fluid) {
    }

    /** 完整注册名（namespace:path）：语言无关。服务端拿不到客户端译名，所以用不了"显示名" */
    private static String idKeyOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** 只取注册名的路径部分（去掉 mod 前缀），用于"不分 mod 整体排" */
    private static String pathKeyOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    /**
     * 整理（仅服务端）：同种合并 + 按所选方式排序 + 把条目紧凑地排到前面。
     * <p>
     * 范围（scope）：
     * <ul>
     *   <li>{@link #SORT_CURRENT_PAGE}（默认）：只重排当前页的 54 格；</li>
     *   <li>{@link #SORT_ALL_PAGES}：把所有页当成<b>一个整体</b>排序再顺序摆回去（不是每页各排各的），
     *       跨页的同种条目会合并成一条，条目整体压实到前几页。</li>
     * </ul>
     * 排序方式（sortBy，都是语言无关的键，见下）：
     * <ul>
     *   <li>{@link #SORT_BY_NAME}：不分 mod，整体按注册名的路径名排（不同 mod 的同名物会挨在一起）；</li>
     *   <li>{@link #SORT_BY_MOD}：同一 mod 的聚在一起，各自按注册名排；</li>
     *   <li>{@link #SORT_BY_COUNT}：数量从多到少，数量相同按注册名排（流体按 mB 与物品件数一起比）。</li>
     * </ul>
     * 物品与流体混在一起排（"不分家"）：流体用它的桶物品的注册名/数量参与排序。
     * 页名与页数不变，整理不删页（空的页留在那里，要删可以自己按 ✕）。
     * </p>
     *
     * @param scope  {@link #SORT_CURRENT_PAGE} 或 {@link #SORT_ALL_PAGES}
     * @param sortBy {@link #SORT_BY_NAME} / {@link #SORT_BY_MOD} / {@link #SORT_BY_COUNT}
     * @return 是否真的整理了（范围内没有东西时返回 false）
     */
    public boolean sort(int scope, int sortBy) {
        if (clientSide) return false;
        boolean all = scope == SORT_ALL_PAGES;
        int pageCount = Math.max(pages.size(), fluidPages.size());
        int from = all ? 0 : Math.min(currentPage, Math.max(0, pageCount - 1));
        int to = all ? pageCount : from + 1;
        if (pageCount <= 0) return false;

        // 1) 收集范围内的条目
        // ⚠️⚠️ 同一格上"既有物品又有流体"时（老版本入库不避让留下的脏数据）**两样都要收集**：
        //     以前写成 if/else（物品优先），于是那片流体没进列表，而第 4 步会把整页清空再摆回 ——
        //     结果就是"整理一次，被物品压着的那格水源凭空消失"（实测曾丢失一格水源）。
        //     两个都收集之后，排序会把它们摆到不同的格子上，同时也把这种脏数据修好了。
        List<SortItem> items = new ArrayList<>();
        for (int p = from; p < to; p++) {
            for (int slot = 0; slot < PocketStorageHelper.Page.SLOTS; slot++) {
                PocketStorageHelper.Entry e = p < pages.size() ? pages.get(p).entries.get(slot) : null;
                PocketStorageHelper.FluidEntry f = fluidAt(p, slot);
                if (e != null) {
                    items.add(new SortItem(idKeyOf(e.stack()), pathKeyOf(e.stack()), tagKeyOf(e.stack()), e.count(), e, null));
                }
                if (f != null) {
                    ItemStack icon = f.displayStack();
                    items.add(new SortItem(idKeyOf(icon), pathKeyOf(icon), tagKeyOf(icon), f.amount(), null, f));
                }
            }
        }
        if (items.size() <= 1) return false;

        // 2) 先按"种类"合并（跨页同种并成一条，数量相加）——必须在排序前做：
        //    按数量排时同种但数量不同的两条不会相邻，靠"排完再合并相邻"会漏掉。
        //    分桶用注册名（廉价字符串键），桶内再用 sameItem/sameEntry 精确比（同 id 的条目很少，代价可控）。
        java.util.Map<String, List<SortItem>> buckets = new java.util.LinkedHashMap<>();
        for (SortItem it : items) {
            buckets.computeIfAbsent(it.idKey(), k -> new ArrayList<>()).add(it);
        }
        List<SortItem> merged = new ArrayList<>(items.size());
        for (List<SortItem> bucket : buckets.values()) {
            List<SortItem> group = new ArrayList<>();
            for (SortItem it : bucket) {
                int hit = -1;
                for (int i = 0; i < group.size(); i++) {
                    if (sameSortType(group.get(i), it)) {
                        hit = i;
                        break;
                    }
                }
                if (hit < 0) {
                    group.add(it);
                } else {
                    group.set(hit, combine(group.get(hit), it));
                }
            }
            merged.addAll(group);
        }

        // 3) 按所选方式排序
        java.util.Comparator<SortItem> comparator = switch (sortBy) {
            case SORT_BY_COUNT -> java.util.Comparator
                    .comparingLong((SortItem s) -> -s.count())   // 数量从多到少
                    .thenComparing(SortItem::idKey);
            case SORT_BY_MOD -> java.util.Comparator.comparing(SortItem::idKey);
            case SORT_BY_TAG -> java.util.Comparator.comparing(SortItem::tagKey).thenComparing(SortItem::idKey);
            default -> java.util.Comparator.comparing(SortItem::pathKey).thenComparing(SortItem::idKey);
        };
        merged.sort(comparator);

        // 4) 清空范围内的格子，再按顺序摆回去（空格自然都留在后面）
        for (int p = from; p < to; p++) {
            if (p < pages.size()) java.util.Collections.fill(pages.get(p).entries, null);
            if (p < fluidPages.size()) java.util.Collections.fill(fluidPages.get(p).entries, null);
        }
        int page = from;
        int slot = 0;
        for (SortItem it : merged) {
            if (slot >= PocketStorageHelper.Page.SLOTS) {
                slot = 0;
                page++;
            }
            // 铁律：物品页与流体页共用页索引，需要新页时两边一起建
            while (pages.size() <= page) {
                pages.add(new PocketStorageHelper.Page(String.valueOf(pages.size() + 1)));
            }
            while (fluidPages.size() <= page) {
                fluidPages.add(new PocketStorageHelper.FluidPage(String.valueOf(fluidPages.size() + 1)));
            }
            if (it.item() != null) {
                pages.get(page).entries.set(slot, it.item());
            } else {
                fluidPages.get(page).entries.set(slot, it.fluid());
            }
            slot++;
        }
        rebuildDisplay();
        save();
        return true;
    }

    /**
     * 这件东西的"标签键"（按标签排序用）。
     * <p>
     * 一个物品常常带有多条标签，直接取第一个会把木头排到 `minecraft:mineable/axe` 里去 ——
     * 所以优先取**路径里不带斜杠**的"种类标签"（`minecraft:logs`、`c:ingots` 这种），
     * 有多个就按名字取第一个（稳定、可复现）；没有种类标签就退回第一个标签；
     * 连标签都没有（模组乱写的物品）就用注册名兜底，保证排序结果永远确定。
     * </p>
     */
    private static String tagKeyOf(ItemStack stack) {
        java.util.List<String> kind = new java.util.ArrayList<>();
        java.util.List<String> all = new java.util.ArrayList<>();
        stack.getTags().forEach(t -> {
            String s = t.location().toString();
            all.add(s);
            if (!t.location().getPath().contains("/")) kind.add(s);
        });
        if (!kind.isEmpty()) {
            java.util.Collections.sort(kind);
            return kind.get(0);
        }
        if (!all.isEmpty()) {
            java.util.Collections.sort(all);
            return all.get(0);
        }
        return idKeyOf(stack);
    }

    /** 两条整理项是不是同一种（同物品 / 同流体） */
    private static boolean sameSortType(SortItem a, SortItem b) {
        if (a.item() != null && b.item() != null) {
            return PocketStorageHelper.sameItem(a.item().stack(), b.item().stack());
        }
        if (a.fluid() != null && b.fluid() != null) {
            return a.fluid().sameEntry(b.fluid());
        }
        return false;
    }

    /** 合并两条同种整理项（数量相加） */
    private static SortItem combine(SortItem a, SortItem b) {
        if (a.item() != null) {
            return new SortItem(a.idKey(), a.pathKey(), a.tagKey(), a.count() + b.count(),
                    new PocketStorageHelper.Entry(a.item().stack(), a.item().count() + b.item().count()), null);
        }
        return new SortItem(a.idKey(), a.pathKey(), a.tagKey(), a.count() + b.count(), null,
                new PocketStorageHelper.FluidEntry(a.fluid().stackTag(),
                        a.fluid().amount() + b.fluid().amount()));
    }

    /** 当前是否共享（末影箱式）存储 */
    public boolean isShared() {
        return shared;
    }

    /** 客户端：用同步包里的模式刷新界面显示（不写存档） */
    public void applySyncedShared(boolean shared) {
        this.shared = shared;
    }

    /**
     * 切换存储模式（仅服务端）。
     * <p>
     * 两边各自独立保留：切换只是换看哪一份数据，不会搬动物品。
     * 共享→本地时调用方应先给玩家确认提示（本地数据存在袋子里，袋子遗失就找不回来）。
     * </p>
     *
     * @param toShared true = 切到共享，false = 切到本地
     */
    public void switchMode(boolean toShared) {
        if (clientSide) return;
        this.shared = toShared;
        PocketStorageHelper.setShared(bag, toShared);
        pages.clear();
        pages.addAll(loadPagesFromSource());
        if (pages.isEmpty()) pages.add(new PocketStorageHelper.Page("1"));
        // 流体页必须一起重新加载：少了这步，save() 会把上一个模式的流体页写到新模式里
        // （表现为"共享的流体跑到本地"或反过来，等于跨模式串数据）
        fluidPages.clear();
        fluidPages.addAll(loadFluidPagesFromSource());
        if (fluidPages.isEmpty()) fluidPages.add(new PocketStorageHelper.FluidPage("1"));
        currentPage = 0;
        pageOffset = 0;
        filterKeyword = "";
        searching = false;
        rebuildDisplay();
        save();
    }

    /** 本地并入共享的结果：done = 是否真的执行了转移；items = 物品总件数；fluid = 流体总 mB */
    public record MergeResult(boolean done, long items, long fluid) {
        public static final MergeResult NOTHING = new MergeResult(false, 0L, 0L);
    }

    /**
     * 把"本地存储"里的全部物品与流体<b>并入</b>"共享空间"（仅服务端）。
     * <p>
     * 需求：老存档可能把一大堆东西存在本地，需要一个一键转移入口；<b>只做本地 → 共享</b>；
     * 而且<b>不能覆盖</b>共享里已有的东西——本地的每一条都"加"到共享上（同种数量累加、
     * 优先放进空位、装不下就新建页），完成后清空本地并把袋子切到共享。
     * </p>
     * <p>
     * 共享原本是空的时候整份搬过去（连页名一起保留）；共享已有内容时按条并入，
     * 期间新建的页用默认数字名（共享原有的页名不动）。
     * 数量走 long 版入口（{@code depositEntryInto} / {@code depositFluidEntryInto}），
     * 避免超过 int 上限的堆叠数 / mB 被截断。
     * </p>
     *
     * @return 转移结果（本地本来就是空的时候返回 {@link MergeResult#NOTHING}）
     */
    public MergeResult mergeLocalToShared() {
        // 只允许"本地模式"下执行：界面上的按钮也只在本地模式出现，
        // 这里再挡一道，避免改造过的客户端在共享模式下重复把本地内容加一遍
        if (clientSide || shared || owner == null || owner.getServer() == null) return MergeResult.NOTHING;
        List<PocketStorageHelper.Page> localPages = PocketStorageHelper.readPages(bag);
        List<PocketStorageHelper.FluidPage> localFluids = PocketStorageHelper.readFluidPages(bag);
        long itemCount = 0L;
        long fluidAmount = 0L;
        for (PocketStorageHelper.Page p : localPages) {
            for (PocketStorageHelper.Entry e : p.entries) {
                if (e != null) itemCount += e.count();
            }
        }
        for (PocketStorageHelper.FluidPage p : localFluids) {
            for (PocketStorageHelper.FluidEntry f : p.entries) {
                if (f != null) fluidAmount += f.amount();
            }
        }
        if (itemCount <= 0 && fluidAmount <= 0) return MergeResult.NOTHING;

        PocketSharedStorage store = PocketSharedStorage.get(owner.getServer());
        List<PocketStorageHelper.Page> sharedPages = store.getPages(owner.getUUID());
        List<PocketStorageHelper.FluidPage> sharedFluids = store.getFluidPages(owner.getUUID());
        if (isStorageEmpty(sharedPages, sharedFluids)) {
            // 共享还是空的：整份搬过去，页名原样保留
            store.setPages(owner.getUUID(), localPages.isEmpty() ? sharedPages : localPages);
            store.setFluidPages(owner.getUUID(), localFluids.isEmpty() ? sharedFluids : localFluids);
        } else {
            // 共享已有内容：逐条"加"上去，绝不覆盖
            for (PocketStorageHelper.Page p : localPages) {
                for (PocketStorageHelper.Entry e : p.entries) {
                    if (e == null) continue;
                    PocketStorageHelper.depositEntryInto(sharedPages, sharedFluids, e.stack(), e.count());
                }
            }
            for (PocketStorageHelper.FluidPage p : localFluids) {
                for (PocketStorageHelper.FluidEntry f : p.entries) {
                    if (f == null) continue;
                    PocketStorageHelper.depositFluidEntryInto(sharedFluids, sharedPages, f);
                }
            }
            store.setPages(owner.getUUID(), sharedPages);
            store.setFluidPages(owner.getUUID(), sharedFluids);
        }
        // 清空本地（只删 Pages / FluidPages，托盘数据与模式标记保留），然后切到共享
        PocketStorageHelper.clearLocalPages(bag);
        switchMode(true);
        return new MergeResult(true, itemCount, fluidAmount);
    }

    /** 一份存储是不是空的（只看条目，页名不算） */
    private static boolean isStorageEmpty(List<PocketStorageHelper.Page> pages,
                                          List<PocketStorageHelper.FluidPage> fluids) {
        for (PocketStorageHelper.Page p : pages) {
            for (PocketStorageHelper.Entry e : p.entries) {
                if (e != null) return false;
            }
        }
        for (PocketStorageHelper.FluidPage p : fluids) {
            for (PocketStorageHelper.FluidEntry f : p.entries) {
                if (f != null) return false;
            }
        }
        return true;
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
        // 物品页与流体页共用同一套页索引：删页时两边都要删，否则流体页会整体错位
        if (index < fluidPages.size()) fluidPages.remove(index);
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
        // 流体页里还有流体的页同样不能算"空页"（界面的 ✕ 也会因此不显示，双保险）
        if (index < fluidPages.size()) {
            for (PocketStorageHelper.FluidEntry f : fluidPages.get(index).entries) {
                if (f != null) return false;
            }
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
                    PocketStorageHelper.FluidEntry f = fluidAt(p, slot);
                    boolean hit = e != null ? matches(e.stack(), filterKeyword)
                            : (f != null && fluidMatches(f, filterKeyword));
                    if (!hit) continue;
                    if (displayEntries.size() >= PAGE_SIZE) {
                        searchTruncated = true;
                        break outer;
                    }
                    displayEntries.add(e != null ? e
                            : new PocketStorageHelper.Entry(PocketStorageHelper.fluidDisplayStack(f), f.amount()));
                    displayRefs.add(new EntryRef(p, slot, e == null));
                }
            }
        } else {
            PocketStorageHelper.Page page = pages.get(currentPage);
            for (int i = 0; i < page.entries.size(); i++) {
                PocketStorageHelper.Entry e = page.entries.get(i);
                PocketStorageHelper.FluidEntry f = e == null ? fluidAt(currentPage, i) : null;
                displayEntries.add(e != null ? e
                        : (f == null ? null
                        : new PocketStorageHelper.Entry(PocketStorageHelper.fluidDisplayStack(f), f.amount())));
                displayRefs.add(new EntryRef(currentPage, i, e == null && f != null));
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
            for (SlotData sd : slots) max = Math.max(max, sd.slot);
            for (int i = 0; i <= max; i++) displayEntries.add(null);
            for (SlotData sd : slots) displayEntries.set(sd.slot, sd.entry);
        } else {
            // 当前页 54 槽（含空）
            for (int i = 0; i < PAGE_SIZE; i++) displayEntries.add(null);
            for (SlotData sd : slots) {
                if (sd.slot >= 0 && sd.slot < PAGE_SIZE) {
                    displayEntries.set(sd.slot, sd.entry);
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
        for (int i = 0; i < pages.size(); i++) {
            // 物品与流体都算（与 1.21.1 对齐）：只算物品会让"只有流体的页"显示 0 并被 ✕ 删掉
            int n = countNonEmpty(pages.get(i));
            if (i < fluidPages.size()) {
                for (PocketStorageHelper.FluidEntry e : fluidPages.get(i).entries) {
                    if (e != null) n++;
                }
            }
            counts.add(n);
        }
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
                // 流体格：显示栈自带标记（数量已是 1，存量与流体本身都在它的自定义数据里）。
                // 不能用 displayRefs 判断 —— 客户端那份是空的（见 PocketStorageHelper#isFluidDisplayStack）
                if (PocketStorageHelper.isFluidDisplayStack(e.stack())) {
                    ItemStack icon = e.stack().copy();
                    if (icon.getCount() != 1) icon.setCount(1);
                    return icon;
                }
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
        // "取走这一格"按原版堆叠上限来，请勿取出超过一组的数量
        return removeItem(slot, stackSizeAt(slot));
    }

    /**
     * 该显示格上"一组"的数量（原版堆叠上限，通常 64；物品自己另有上限时用它自己的）。
     * <p>
     * 用途：从无限堆叠里取出时不能超过一组（左键取一组、右键取半组），
     * 否则光标上会出现 64 以上的堆叠，看着不原版、也容易误操作。
     * </p>
     */
    public int stackSizeAt(int slot) {
        int idx = pageOffset + slot;
        if (idx < 0 || idx >= displayEntries.size()) return 64;
        PocketStorageHelper.Entry e = displayEntries.get(idx);
        if (e == null) return 64;
        return Math.max(1, e.stack().getMaxStackSize());
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

    /** 把全部页写回对应存储（本地 = 袋子 NBT；共享 = 服务器存档里该玩家的数据）并通知菜单刷新 */
    public void save() {
        if (clientSide) return;
        if (shared && owner != null && owner.getServer() != null) {
            PocketSharedStorage store = PocketSharedStorage.get(owner.getServer());
            store.setPages(owner.getUUID(), pages);
            store.setFluidPages(owner.getUUID(), fluidPages);
        } else {
            PocketStorageHelper.writePages(bag, pages);
            PocketStorageHelper.writeFluidPages(bag, fluidPages);
        }
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
            long take = Math.min(e.stack().getMaxStackSize(), e.count());
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

    /**
     * 快捷收纳（走<b>本容器缓存的页</b>）：所有页找同种合并 → 找空位 → 都没有就新建一页；数量 long 无上限。
     * <p>
     * ⚠️ 为什么要专门有这个方法：界面开着的时候，页数据在本对象里有一份缓存，
     * 任何"绕过缓存直接写袋子 NBT"的入库（例如 {@code PocketStorageHelper#quickDeposit}）都会被
     * 下一次 {@link #save()} 用旧缓存整份覆盖掉 —— 表现就是"东西放进去就没了"（实测曾多次出现）。
     * 所以只要次元袋界面开着，所有入库都必须走这里（或 {@link #insertFluid}）。
     * </p>
     *
     * @return 剩余数量（恒为 0：袋子无上限）
     */
    public long quickDepositCached(ItemStack stack) {
        if (clientSide) return stack.isEmpty() ? 0 : stack.getCount();
        if (stack.isEmpty()) return 0;
        if (stack.getItem() instanceof com.zzq.survival_toolbox.item.PocketDimensionItem) {
            return stack.getCount(); // 袋子这类被拉黑的东西不收
        }
        // 物品要避开流体格（否则那片流体会隐形，整理时还会被清掉）
        long need = PocketStorageHelper.depositInto(pages, fluidPages, stack);
        rebuildDisplay();
        save();
        return need;
    }

    /**
     * 从储物空间<b>扣库存</b>（走本容器缓存的页，服务端专用）。
     * <p>
     * ⚠️ 与 {@link #quickDepositCached} 是同一个成因的两面：界面开着的时候，页数据在本对象里有一份缓存，
     * 任何"绕过缓存直接改袋子 NBT / 共享存档"的扣减都会被下一次 {@link #save()} 用旧缓存<b>整份盖回来</b>
     * ——表现就是"料取出来了、袋子里一个没少"，也就是 JEI 的 + <b>凭空复制材料</b>（实测）。
     * 所以取料也必须走这里。
     * </p>
     * <p>
     * 规则：<b>打开哪页就只从哪页取</b>（玩家看得见的东西才算数）。
     * 搜索视图是跨页的（那是"把各页的结果摊开看"），那种情况下按"所有页"处理才符合玩家看到的画面。
     * </p>
     *
     * @param template  要取的物品（数量会被忽略）
     * @param count     想取多少个
     * @param pageIndex 页下标（0 起）；{@code PocketStorageHelper.PAGE_ALL} = 所有页
     * @return 实际取到的物品（空 = 够不着）
     */
    public ItemStack withdrawCached(ItemStack template, int count, int pageIndex) {
        if (clientSide || template == null || template.isEmpty() || count <= 0) return ItemStack.EMPTY;
        int from = pageIndex == PocketStorageHelper.PAGE_ALL ? 0 : pageIndex;
        int to = pageIndex == PocketStorageHelper.PAGE_ALL ? pages.size() : Math.min(pages.size(), pageIndex + 1);
        long need = count;
        for (int p = from; p < to && need > 0; p++) {
            PocketStorageHelper.Page page = pages.get(p);
            for (int slot = 0; slot < PocketStorageHelper.Page.SLOTS && need > 0; slot++) {
                PocketStorageHelper.Entry e = page.entries.get(slot);
                if (e == null || !PocketStorageHelper.sameItem(e.stack(), template)) continue;
                long take = Math.min(need, e.count());
                page.entries.set(slot, e.count() - take <= 0
                        ? null : new PocketStorageHelper.Entry(e.stack(), e.count() - take));
                need -= take;
            }
        }
        long taken = count - need;
        if (taken <= 0) return ItemStack.EMPTY;
        rebuildDisplay();
        save();
        ItemStack out = template.copy();
        out.setCount((int) taken);
        return out;
    }

    /** 放入物品（只作用于当前页）：同类合并；新种类第一个空槽；页满返回剩余。 */
    public ItemStack insert(ItemStack stack) {        if (clientSide) return stack;
        if (stack.isEmpty()) return stack;
        if (stack.getItem() instanceof com.zzq.survival_toolbox.item.PocketDimensionItem) return stack;
        PocketStorageHelper.Page cur = pages.get(currentPage);
        // ⚠️ 空位必须避开"被流体占着"的格子：物品页与流体页是**同一套 54 格**，
        //    物品压上去之后那片流体在界面上直接隐形（显示上物品优先），整理时还会被当成
        //    "没收集到的残留"清掉。实测："Shift+左键快捷存入会把物品塞进流体格"。
        //    （其它入库路径（depositInto / quickDepositCached）一直传了 fluidPages，
        //      只有这个"往当前页插一个空位"漏了避让 —— 根因就在这一处。）
        java.util.List<PocketStorageHelper.FluidEntry> fluidCells = currentPage < fluidPages.size()
                ? fluidPages.get(currentPage).entries : java.util.Collections.emptyList();
        int emptySlot = -1;
        for (int i = 0; i < cur.entries.size(); i++) {
            PocketStorageHelper.Entry e = cur.entries.get(i);
            if (e == null) {
                if (emptySlot < 0 && (i >= fluidCells.size() || fluidCells.get(i) == null)) emptySlot = i;
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
