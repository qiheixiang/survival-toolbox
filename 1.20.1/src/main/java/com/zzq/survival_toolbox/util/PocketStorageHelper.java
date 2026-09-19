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

    /**
     * ⚠️ <b>排查历史（留档，供后续排查同类问题时参考）</b>：此处曾挂过一套临时诊断（{@code DIAG} + {@code diag()} +
     * {@code totalEntries()}：把"开袋 / 保存 / 入库 / 出库"的关键计数打日志），用来定位
     * 「1.20.1 本地模式：放进去什么、重新打开袋子就什么都没了」这一现象。
     * 结论已经写进 {@link #writePages} 的注释（Pages 被写成了 CompoundTag，读取端按列表判定永远读不到），
     * 诊断代码随后全部删除 —— 排查同类问题请用 {@code runServer} + FakePlayer 自检那一套，
     * 请勿把日志开关留在发布版里。
     */

    /** 页列表里一共有多少条非空条目 */
    public static long totalEntries(List<Page> pages) {
        long n = 0L;
        for (Page p : pages) {
            for (Entry e : p.entries) {
                if (e != null && e.count() > 0) n++;
            }
        }
        return n;
    }

    /**
     * "不限页"：把储物空间的所有页当成一个整体看。
     * <p>
     * ⚠️ <b>只有"没有次元袋界面开着"的场合才允许用它</b>（原版工作台 / 锻造台那种）。
     * 次元袋界面开着的时候一律要换成"当前显示的那一页"
     * （<b>打开哪页就匹配哪页、取料也只从那页取</b>）——
     * 否则玩家看着第 1 页却在用第 7 页的材料，而玩家并不知道材料取自哪一页。
     * 页号怎么取：{@code PocketDimensionMenu#getDisplayedPageIndex()}。
     * </p>
     */
    public static final int PAGE_ALL = -1;

    private static final String TAG_DATA = "PocketData";
    private static final String TAG_PAGES = "Pages";
    private static final String TAG_NAME = "Name";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_OLD_ITEMS = "PocketItems";
    /** 存储模式标记：true = 共享（末影箱式，数据在服务器存档里按玩家存），false = 本地（数据存在袋子里）。缺省按内容判定。 */
    private static final String TAG_SHARED = "PocketShared";
    /** 流体页（与物品页同一个根标签下的另一个键） */
    private static final String TAG_FLUID_PAGES = "FluidPages";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_AMOUNT = "Amount";

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
        if (!bag.hasTag()) return new ArrayList<>();
        CompoundTag root = bag.getTag();
        if (root == null) return new ArrayList<>();
        return readPages(root);
    }

    /**
     * 从一段 NBT 读取全部页（本地模式 = 袋子 NBT；共享模式 = 服务器存档里该玩家的数据，格式相同）。
     *
     * @param root 含 {@code Pages} 的根标签
     * @return 页列表（可能为空）
     */
    public static List<Page> readPages(CompoundTag root) {
        List<Page> pages = new ArrayList<>();
        // 老袋子可能带着"Pages 被写成 CompoundTag"的历史坏格式，这里解开（详见 pageRootOf）
        CompoundTag src = pageRootOf(root);
        // contains 的第二参数为值类型：Pages/Items 均为列表（TAG_LIST=9），若写成 10 将永远判定为 false。
        if (src != null && src.contains(TAG_PAGES, 9)) {
            ListTag pagesTag = src.getList(TAG_PAGES, 10);
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
        } else if (root != null && root.contains(TAG_OLD_ITEMS, 9)) {
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
        CompoundTag root = ItemNbt.copyForEdit(bag);
        // ⚠️⚠️ 这里必须取 writePagesTag 结果里的**列表**再放进 Pages 键。
        //        writePagesTag 返回的是"含 Pages 键的根标签"（给共享存储整份存盘用），
        //        早期版本直接把整份根标签塞进 Pages 键，于是袋子 NBT 里 Pages 存成了 CompoundTag，
        //        而所有读取端一律按列表判定（contains(Pages, 9)）→ 永远读不到：
        //        表现就是「本地模式放进去什么，重开袋子全没了」（实测丢东西），
        //        而共享模式走 PocketSharedStorage（那边本来就 .getList 取列表）所以完全正常。
        //        这也是为什么只有 1.20.1 坏、1.21.1 一直是好的。
        root.put(TAG_PAGES, writePagesTag(pages).getList(TAG_PAGES, 10));
        bag.setTag(root);
    }

    /**
     * 取出"真正挂着 Pages 列表"的那层根标签。
     * <p>
     * <b>兼容历史坏格式</b>：早期版本把 Pages 写成了 CompoundTag（形如 {@code {Pages:{Pages:[...]}} }），
     * 存进去的东西其实一直都在，只是读取端按列表判定读不出来，看起来像"东西丢了"。
     * 这里把那一层解开，老袋子里的东西就能原样回来。
     * </p>
     */
    private static CompoundTag pageRootOf(CompoundTag root) {
        if (root == null || root.contains(TAG_PAGES, 9)) return root;
        if (root.contains(TAG_PAGES, 10)) {
            CompoundTag nested = root.getCompound(TAG_PAGES);
            if (nested.contains(TAG_PAGES, 9)) return nested;
        }
        return root;
    }

    /**
     * 把全部页序列化成一段 NBT（本地/共享两种存储共用同一格式）。
     *
     * @param pages 页列表
     * @return 含 {@code Pages} 的根标签
     */
    public static CompoundTag writePagesTag(List<Page> pages) {
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
        CompoundTag root = new CompoundTag();
        root.put(TAG_PAGES, pagesTag);
        return root;
    }

    // ============================================================
    // 存储模式（共享 = 末影箱式，本地 = 存在袋子里）
    // ============================================================

    /**
     * 袋子是否使用共享存储。
     * <p>
     * <b>旧存档兼容规则</b>：老袋子没有模式标记，此时看它里面有没有本地数据——
     * 有的按"本地"处理（否则老玩家开袋会看到空的共享空间，以为物品丢了），
     * 从没存过东西的袋子（新做的、空的）才默认"共享"。
     * 调用方在开袋时应把判定结果用 {@link #setShared} 写死，之后模式就不再随内容变化。
     * </p>
     *
     * @param bag 次元袋
     * @return true = 共享，false = 本地
     */
    public static boolean isShared(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        if (tag == null) return true;
        if (tag.contains(TAG_SHARED, 1)) return tag.getBoolean(TAG_SHARED);
        return !hasLocalData(tag);
    }

    /** 写入存储模式标记（写死后再也不会因内容变化而改变判定） */
    public static void setShared(ItemStack bag, boolean shared) {
        CompoundTag root = ItemNbt.copyForEdit(bag);
        root.putBoolean(TAG_SHARED, shared);
        bag.setTag(root);
    }

    /** 袋子里是否已经存着本地数据（含旧版单页格式） */
    private static boolean hasLocalData(CompoundTag tag) {
        if (tag == null) return false;
        if (tag.contains(TAG_OLD_ITEMS, 9) && !tag.getList(TAG_OLD_ITEMS, 10).isEmpty()) return true;
        // 历史坏格式（Pages 存成 CompoundTag）也要算"有本地数据"，否则老袋子会被误判成共享袋
        tag = pageRootOf(tag);
        if (!tag.contains(TAG_PAGES, 9)) return false;
        ListTag pages = tag.getList(TAG_PAGES, 10);
        for (int p = 0; p < pages.size(); p++) {
            if (!pages.getCompound(p).getList(TAG_ITEMS, 10).isEmpty()) return true;
        }
        return false;
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
        // 物品要避开流体格（物品页与流体页同索引共用格子）
        long need = depositInto(pages, readFluidPages(bag), target);
        writePages(bag, pages);
        return need;
    }

    /**
     * 按袋子<b>当前模式</b>读物品页（只读）。
     * <p>
     * ⚠️ 请勿在别处直接 {@code readPages(bag)}：那是"袋子自己的本地页"，
     * 共享模式的袋子读它会永远读到空的（磁铁"只吸袋里有的"就是这样失效的）。
     * </p>
     */
    public static List<Page> readPagesForMode(net.minecraft.world.entity.player.Player player, ItemStack bag) {
        if (isShared(bag) && player != null && !player.level().isClientSide && player.getServer() != null) {
            return PocketSharedStorage.get(player.getServer()).getPages(player.getUUID());
        }
        return readPages(bag);
    }

    /**
     * 按袋子<b>当前模式</b>读物品页（只读），并且<b>只读指定的那一页</b>。
     * <p>
     * 用途：次元袋界面开着时的"匹配/取料"。
     * 规则是"<b>打开哪个就匹配哪个</b>"——袋子里第 1 页是木头、第 7 页是木板时，
     * 玩家盯着第 1 页点 JEI 的 +，就不该把第 7 页的木板也算进来（那些物品玩家看不到）。
     * 所以这里按页取；{@value #PAGE_ALL} 才表示"所有页"（原版工作台 / 锻造台那种没有袋子页开着的场合）。
     * </p>
     *
     * @param pageIndex 页下标（0 起）；{@value #PAGE_ALL} = 所有页；越界 = 空列表
     */
    public static List<Page> readPagesForMode(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                              int pageIndex) {
        List<Page> all = readPagesForMode(player, bag);
        if (pageIndex == PAGE_ALL) return all;
        // ⚠️ 这里返回的是"新列表里放着那一页"，不是原列表的 subList：
        //    subList 是视图，调用方一旦 add/remove 就会动到原列表（下面的读取都是只读，但请勿留下这个隐患）
        List<Page> one = new ArrayList<>(1);
        if (pageIndex >= 0 && pageIndex < all.size()) one.add(all.get(pageIndex));
        return one;
    }

    /**
     * 按袋子<b>当前模式</b>读流体页（只读）。与 {@link #readPagesForMode} 同理：
     * 共享袋子直接 {@code readFluidPages(bag)} 读的是袋子自己的本地流体页，永远读空。
     */
    public static List<FluidPage> readFluidPagesForMode(net.minecraft.world.entity.player.Player player, ItemStack bag) {
        if (isShared(bag) && player != null && !player.level().isClientSide && player.getServer() != null) {
            return PocketSharedStorage.get(player.getServer()).getFluidPages(player.getUUID());
        }
        return readFluidPages(bag);
    }

    /**
     * 按当前模式数一数"这种物品在存储里一共有多少个"（跨所有页，只读）。
     * <p>
     * 用途：入库之后<b>回读确认</b>——入库前先记个数，写完再数一次，差值才是"真的进袋子了"的数量。
     * 托盘"纳入"这类"先从别的容器拿走、再写进袋子"的流程必须这么确认：
     * {@code quickDeposit} 的返回值恒为 0（装不下会自动新建页），拿它判断等于完全信任写入，
     * 一旦哪条路径没写进去，表现就是"箱子里的东西取走了、袋子里两边都看不到"（实测丢东西）。
     * </p>
     *
     * @return 该物品在存储里的总数量（同物品同 NBT，跨页累加）
     */
    public static long countInStorage(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                      ItemStack template) {
        if (template == null || template.isEmpty()) return 0L;
        long total = 0L;
        for (Page page : readPagesForMode(player, bag)) {
            for (Entry e : page.entries) {
                if (e != null && sameItem(e.stack(), template)) total += e.count();
            }
        }
        return total;
    }

    /**
     * 按当前模式数一数"这种物品在<b>某一页</b>上一共有多少个"（只读）。用途同 {@link #countInStorage}。
     * <p>
     * JEI 取料要用它做<b>回读确认</b>：取之前数一次、取之后数一次，只有真的少了那么多才认，
     * 否则就是"取出来了但其实没落盘"——那意味着凭空多出来一份（实测"料是复制出来的"）。
     * </p>
     *
     * @param pageIndex 页下标（0 起）；{@value #PAGE_ALL} = 所有页
     */
    public static long countInStorage(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                      ItemStack template, int pageIndex) {
        if (template == null || template.isEmpty()) return 0L;
        long total = 0L;
        for (Page page : readPagesForMode(player, bag, pageIndex)) {
            for (Entry e : page.entries) {
                if (e != null && sameItem(e.stack(), template)) total += e.count();
            }
        }
        return total;
    }

    /**
     * 按当前模式数一数"这种流体在存储里一共有多少 mB"（跨所有页，只读）。用途同 {@link #countInStorage}。
     */
    public static long fluidAmountInStorage(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                            net.minecraftforge.fluids.FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return 0L;
        long total = 0L;
        for (FluidPage page : readFluidPagesForMode(player, bag)) {
            for (FluidEntry e : page.entries) {
                if (e != null && e.sameFluid(fluid)) total += e.amount();
            }
        }
        return total;
    }

    /**
     * 快捷收纳（按袋子当前模式写入对应存储）。
     * <p>
     * 共享模式的袋子必须写进服务器存档（按玩家），否则会错写进袋子自己的本地存储。
     * </p>
     *
     * @param player 操作者（判定玩家与服务器；仅服务端有效）
     * @param bag    次元袋
     * @param target 待收纳物品
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long quickDeposit(net.minecraft.world.entity.player.Player player, ItemStack bag, ItemStack target) {
        // ⚠️⚠️ 界面开着时**绝不能**直接写存储：界面里的 {@code PocketDimensionContainer} 有一份页缓存，
        //       它每次 save() 都会把缓存整份写回去 —— 绕过缓存写入的东西**当场看不出来、关界面就没了**
        //       （"磁铁吸了东西，开袋子什么都没有"就是这一类）。
        //       以前靠"调用方记得走 depositToOpenMenu"这条约定，太容易漏（磁铁/托盘/快速收纳各写各的），
        //       现在直接在入口这里兜住：只要开着的是"同一份存储"的袋子界面，就交给它的缓存去写。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu
                && menu.shouldRouteDeposit(bag)) {
            return menu.depositToStorage(target);
        }
        if (isShared(bag) && player != null && !player.level().isClientSide && player.getServer() != null) {
            PocketSharedStorage shared = PocketSharedStorage.get(player.getServer());
            List<Page> pages = shared.getPages(player.getUUID());
            // 物品要避开流体格
            long need = depositInto(pages, shared.getFluidPages(player.getUUID()), target);
            shared.setPages(player.getUUID(), pages);
            return need;
        }
        return quickDeposit(bag, target);
    }

    /**
     * 快捷收纳流体（按袋子当前模式写入对应存储）。
     * <p>
     * 与 {@link #quickDeposit(net.minecraft.world.entity.player.Player, ItemStack, ItemStack)}
     * 完全同构：共享模式的袋子写服务器存档（按玩家），本地模式写袋子自己的 NBT。
     * 托盘"纳入"、光标流体放回等入口都必须走这里，才能严格跟随当前的共享/本地模式。
     * </p>
     * <p>
     * 注意：往共享存档写流体页时只替换 FluidPages 键（物品页要一起读出来判空位，但不要写回去），
     * 否则会把物品页抹掉。
     * </p>
     *
     * @param player 操作者（判定玩家与服务器；仅服务端有效）
     * @param bag    次元袋
     * @param stack  待收纳流体
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long quickDepositFluid(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                         net.minecraftforge.fluids.FluidStack stack) {
        if (stack.isEmpty() || stack.getAmount() <= 0) return 0;
        // ⚠️ 与 quickDeposit 同理：界面开着时必须走界面那份页缓存，否则关界面就被缓存盖掉
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu
                && menu.shouldRouteDeposit(bag)) {
            menu.depositFluidToStorage(stack);
            return 0;
        }
        if (isShared(bag) && player != null && !player.level().isClientSide && player.getServer() != null) {
            PocketSharedStorage shared = PocketSharedStorage.get(player.getServer());
            List<Page> pages = shared.getPages(player.getUUID());
            List<FluidPage> fluidPages = shared.getFluidPages(player.getUUID());
            long need = depositFluidInto(fluidPages, pages, stack);
            // 两个键都要写（新页是"物品页 + 流体页"一起扩出来的）
            shared.setPages(player.getUUID(), pages);
            shared.setFluidPages(player.getUUID(), fluidPages);
            return need;
        }
        // 本地：物品页与流体页都在袋子 NBT 里（物品页用来避开被物品占用的格子，也可能被扩出新页）
        CompoundTag root = bag.getTag();
        List<Page> pages = root == null ? new ArrayList<>() : readPages(root);
        List<FluidPage> fluidPages = root == null ? new ArrayList<>() : readFluidPages(root);
        if (fluidPages.isEmpty()) fluidPages.add(new FluidPage("1"));
        long need = depositFluidInto(fluidPages, pages, stack);
        // 物品页也要写回：需要新页时是"物品页 + 流体页"一起扩的（界面按物品页列页）
        writePages(bag, pages);
        writeFluidPages(bag, fluidPages);
        return need;
    }

    /**
     * 收纳纯逻辑：往给定页列表里塞物品。
     * <p>
     * 从第一页开始：有一样的（同物品同 NBT）就合并进去；没有就找本页空位放入；
     * 本页没有空位就翻下一页；所有页都满且没有一样的 → 新建一页放入。
     * 每格数量无上限（long），合并/放置不受堆叠上限限制。
     * </p>
     *
     * @param pages  页列表（会被就地修改）
     * @param target 待收纳物品
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long depositInto(List<Page> pages, ItemStack target) {
        if (target.isEmpty()) return 0;
        return depositEntryInto(pages, null, target, target.getCount());
    }

    /** 与 {@link #depositInto(List, ItemStack)} 同，但会**避开被流体占着的格子**（推荐用这个） */
    public static long depositInto(List<Page> pages, List<FluidPage> fluidPages, ItemStack target) {
        if (target.isEmpty()) return 0;
        return depositEntryInto(pages, fluidPages, target, target.getCount());
    }

    /**
     * 收纳纯逻辑（long 数量版）：把 count 个 template 塞进页列表。
     * <p>
     * 与 {@link #depositInto(List, ItemStack)} 同一套规则，区别是数量用 long——
     * 页里的条目数量本来就没有上限，需要"整条搬运"的场景（例如把本地存储整体并进共享空间）
     * 必须走这个入口，否则超过 int 上限的数量会被截断。
     * </p>
     * <p>
     * ⚠️ {@code fluidPages} 一定要传：物品页与流体页**同索引共用格子**，
     * 若把物品放进"其实被流体占着"的格子，显示上物品优先 → 那片流体就**隐形**了；
     * 更糟的是"整理"会把它当成没被收集到而**直接清掉**（实测丢了一格水源）。
     * 传 null = 不做避让（只建议在没有流体页的场合用）。
     * </p>
     *
     * @param pages      页列表（会被就地修改）
     * @param fluidPages 流体页（可为 null；非 null 时物品会避开流体格）
     * @param template   物品模板（数量会被忽略）
     * @param count      数量
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long depositEntryInto(List<Page> pages, List<FluidPage> fluidPages,
                                        ItemStack template, long count) {
        long need = count;
        if (need <= 0 || template.isEmpty()) return 0;
        for (int p = 0; p < pages.size(); p++) {
            Page page = pages.get(p);
            // 1) 同物品合并（同页任意已有堆）
            for (int slot = 0; slot < Page.SLOTS && need > 0; slot++) {
                Entry e = page.entries.get(slot);
                if (e != null && sameItem(e.stack(), template)) {
                    page.entries.set(slot, new Entry(e.stack(), e.count() + need));
                    need = 0;
                    break;
                }
            }
            // 2) 空位放入：跳过"被流体占着"的格子（否则那片流体会被隐形，整理时还会被清掉）
            if (need > 0) {
                for (int slot = 0; slot < Page.SLOTS; slot++) {
                    if (page.entries.get(slot) != null) continue;
                    if (fluidAt(fluidPages, p, slot) != null) continue;
                    ItemStack s = template.copy();
                    s.setCount(1);
                    page.entries.set(slot, new Entry(s, need));
                    need = 0;
                    break;
                }
            }
        }
        // 3) 所有页都满：新建一页放入
        if (need > 0) {
            Page p = new Page(String.valueOf(pages.size() + 1));
            ItemStack s = template.copy();
            s.setCount(1);
            p.entries.set(0, new Entry(s, need));
            pages.add(p);
            need = 0;
        }
        return need;
    }

    /** 取某页某格的流体条目（越界 / fluidPages 为 null → null） */
    private static FluidEntry fluidAt(List<FluidPage> fluidPages, int page, int slot) {
        if (fluidPages == null || page < 0 || page >= fluidPages.size()) return null;
        List<FluidEntry> list = fluidPages.get(page).entries;
        return slot < 0 || slot >= list.size() ? null : list.get(slot);
    }

    // ============================================================
    // 流体存储（与物品同构：每种流体一"格"，数量 mB 用 long 无上限）
    // ============================================================

    /** 流体条目：FluidStack 的 NBT（数量恒为 1，仅用于标识"哪种流体"）+ 合并后的数量（mB，long 无上限） */
    public record FluidEntry(CompoundTag stackTag, long amount) {

        /** 还原成 FluidStack（数量用给定值） */
        public net.minecraftforge.fluids.FluidStack toStack(long withAmount) {
            if (stackTag == null || amount <= 0 || withAmount <= 0) {
                return net.minecraftforge.fluids.FluidStack.EMPTY;
            }
            net.minecraftforge.fluids.FluidStack stack = decodeFluid(stackTag);
            if (stack.isEmpty()) return stack;
            stack.setAmount((int) Math.min(withAmount, Integer.MAX_VALUE));
            return stack;
        }

        /** 展示用物品（该流体的桶；没有桶则退化成空桶） */
        public ItemStack displayStack() {
            net.minecraftforge.fluids.FluidStack stack = decodeFluid(stackTag);
            if (stack.isEmpty()) return new ItemStack(net.minecraft.world.item.Items.BUCKET);
            ItemStack bucket = stack.getFluid().getBucket().getDefaultInstance();
            return bucket.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.BUCKET) : bucket;
        }

        /** 是否与给定流体同种（流体 + NBT 一致，忽略数量） */
        public boolean sameFluid(net.minecraftforge.fluids.FluidStack other) {
            if (other.isEmpty()) return false;
            net.minecraftforge.fluids.FluidStack mine = toStack(1);
            return !mine.isEmpty() && mine.isFluidEqual(other);
        }

        /** 是否与另一条流体条目同种（流体 + NBT 一致，忽略数量）：整条搬运合并时用 */
        public boolean sameEntry(FluidEntry other) {
            if (other == null) return false;
            net.minecraftforge.fluids.FluidStack mine = toStack(1);
            net.minecraftforge.fluids.FluidStack theirs = other.toStack(1);
            return !mine.isEmpty() && !theirs.isEmpty() && mine.isFluidEqual(theirs);
        }
    }

    /** FluidStack → NBT（数量固定写 1，数量单独记在条目里）；托盘数据层也要用，故为 public */
    public static CompoundTag encodeFluid(net.minecraftforge.fluids.FluidStack stack) {
        CompoundTag tag = new CompoundTag();
        net.minecraftforge.fluids.FluidStack copy = stack.copy();
        copy.setAmount(1);
        copy.writeToNBT(tag);
        return tag;
    }

    /** NBT → FluidStack（读不出来时返回空） */
    public static net.minecraftforge.fluids.FluidStack decodeFluid(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return net.minecraftforge.fluids.FluidStack.EMPTY;
        return net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(tag);
    }

    /** 流体页：页名 + 54 个固定流体槽位（null = 空槽） */
    public static class FluidPage {
        public static final int SLOTS = 54;
        public String name;
        public final List<FluidEntry> entries = new ArrayList<>();

        public FluidPage(String name) {
            this.name = name;
            while (entries.size() < SLOTS) entries.add(null);
        }
    }

    /** 从次元袋物品读取全部流体页 */
    public static List<FluidPage> readFluidPages(ItemStack bag) {
        CompoundTag tag = bag.getTag();
        return tag == null ? new ArrayList<>() : readFluidPages(tag);
    }

    /** 从一段 NBT 读取全部流体页（本地/共享共用） */
    public static List<FluidPage> readFluidPages(CompoundTag root) {
        List<FluidPage> pages = new ArrayList<>();
        if (!root.contains(TAG_FLUID_PAGES, 9)) return pages;
        ListTag pagesTag = root.getList(TAG_FLUID_PAGES, 10);
        for (int p = 0; p < pagesTag.size(); p++) {
            CompoundTag pageTag = pagesTag.getCompound(p);
            FluidPage page = new FluidPage(pageTag.getString(TAG_NAME));
            if (page.name == null || page.name.isEmpty()) page.name = String.valueOf(p + 1);
            ListTag list = pageTag.getList(TAG_ITEMS, 10);
            int nextSlot = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                CompoundTag fluidTag = entryTag.getCompound(TAG_FLUID);
                if (fluidTag.isEmpty()) continue;
                long amount = entryTag.getLong(TAG_AMOUNT);
                if (amount <= 0) continue;
                int slot = entryTag.contains("Slot", 3) ? entryTag.getInt("Slot") : nextSlot;
                if (slot >= 0 && slot < FluidPage.SLOTS) {
                    page.entries.set(slot, new FluidEntry(fluidTag, amount));
                }
                nextSlot++;
            }
            pages.add(page);
        }
        return pages;
    }

    /** 把流体页写回次元袋物品（只替换 FluidPages 键，保留同一份 NBT 里的其他键） */
    public static void writeFluidPages(ItemStack bag, List<FluidPage> pages) {
        CompoundTag root = ItemNbt.copyForEdit(bag);
        root.put(TAG_FLUID_PAGES, writeFluidPagesTag(pages).getList(TAG_FLUID_PAGES, 10));
        bag.setTag(root);
    }

    /** 把流体页序列化成一段 NBT（本地/共享共用同一格式） */
    public static CompoundTag writeFluidPagesTag(List<FluidPage> pages) {
        ListTag pagesTag = new ListTag();
        for (int p = 0; p < pages.size(); p++) {
            FluidPage page = pages.get(p);
            CompoundTag pageTag = new CompoundTag();
            pageTag.putString(TAG_NAME, page.name == null || page.name.isEmpty() ? String.valueOf(p + 1) : page.name);
            ListTag list = new ListTag();
            for (int slot = 0; slot < page.entries.size(); slot++) {
                FluidEntry e = page.entries.get(slot);
                if (e == null || e.amount() <= 0 || e.stackTag() == null) continue;
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(TAG_FLUID, e.stackTag());
                entryTag.putLong(TAG_AMOUNT, e.amount());
                entryTag.putInt("Slot", slot);
                list.add(entryTag);
            }
            pageTag.put(TAG_ITEMS, list);
            pagesTag.add(pageTag);
        }
        CompoundTag root = new CompoundTag();
        root.put(TAG_FLUID_PAGES, pagesTag);
        return root;
    }

    /**
     * 收纳纯逻辑：把流体灌进给定流体页列表。
     * <p>
     * 物品与流体共用同一页的 54 格，因此会避开被物品占用的格子；同种流体先合并、
     * 再找空位、所有页都满就新建一页。数量 long 无上限。
     * </p>
     *
     * @param pages     流体页（会被就地修改）
     * @param itemPages 同索引的物品页（用于避开被物品占的格子）
     * @param stack     待收纳流体
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long depositFluidInto(List<FluidPage> pages, List<Page> itemPages,
                                        net.minecraftforge.fluids.FluidStack stack) {
        if (stack.isEmpty()) return 0;
        return depositFluidEntryInto(pages, itemPages, new FluidEntry(encodeFluid(stack), stack.getAmount()));
    }

    /**
     * 收纳纯逻辑（long 数量版）：把一条流体条目并进流体页列表。
     * <p>
     * 与 {@link #depositFluidInto(List, List, net.minecraftforge.fluids.FluidStack)} 同一套规则，
     * 但数量用条目的 long——把本地存储整体并进共享空间这类"整条搬运"必须走这里，
     * 否则超过 int 上限的 mB 会被截断。
     * </p>
     *
     * @param pages     流体页（会被就地修改）
     * @param itemPages 同索引的物品页（只读：用于避开被物品占的格子）
     * @param entry     待收纳的流体条目
     * @return 剩余数量（成功收纳后恒为 0）
     */
    public static long depositFluidEntryInto(List<FluidPage> pages, List<Page> itemPages, FluidEntry entry) {
        if (entry == null || entry.amount() <= 0 || entry.stackTag() == null) return 0;
        long need = entry.amount();
        for (int p = 0; p < pages.size(); p++) {
            FluidPage page = pages.get(p);
            List<Entry> occupied = p < itemPages.size() ? itemPages.get(p).entries : java.util.Collections.emptyList();
            for (int slot = 0; slot < FluidPage.SLOTS && need > 0; slot++) {
                FluidEntry e = page.entries.get(slot);
                if (e != null && e.sameEntry(entry)) {
                    page.entries.set(slot, new FluidEntry(e.stackTag(), e.amount() + need));
                    need = 0;
                }
            }
            if (need > 0) {
                for (int slot = 0; slot < FluidPage.SLOTS; slot++) {
                    if (page.entries.get(slot) == null && (slot >= occupied.size() || occupied.get(slot) == null)) {
                        page.entries.set(slot, new FluidEntry(entry.stackTag().copy(), need));
                        need = 0;
                        break;
                    }
                }
            }
        }
        if (need > 0) {
            // 页索引是物品页与流体页共用的：新页必须两边一起建，
            // 否则这一页在界面上根本不存在（页列表/网格都按物品页走），流体就成了"看不见的库存"
            itemPages.add(new Page(String.valueOf(itemPages.size() + 1)));
            FluidPage p = new FluidPage(String.valueOf(pages.size() + 1));
            p.entries.set(0, new FluidEntry(entry.stackTag().copy(), need));
            pages.add(p);
            need = 0;
        }
        return need;
    }

    /**
     * 从袋子里取出指定物品（<b>跨所有页</b>；严格按当前共享/本地模式，仅服务端）。
     * <p>
     * 用途：JEI 的"+"号配方转移 —— 玩家背包里没有的材料，先从袋子里（不限当前页）取到背包，
     * 再交给 JEI 自己放进输入槽。
     * </p>
     * <p>
     * 与收纳（{@link #quickDeposit}）相反：这里是"扣库存"。同种的条目可能分布在多页多格，
     * 所以会从第 1 页扫到最后一页、跨格累计；数量用 long 扣减，返回时按 int 截断（超出部分留在袋子里）。
     * </p>
     *
     * @param player   玩家（判定服务器与模式；仅服务端有效）
     * @param bag      次元袋
     * @param template 要取的物品（数量会被忽略）
     * @param count    想取多少个
     * @return 实际取到的物品（空 = 袋子里没有）
     */
    public static ItemStack withdrawFromStorage(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                                ItemStack template, int count) {
        return withdrawFromStorage(player, bag, template, count, PAGE_ALL);
    }

    /**
     * 从袋子里取出指定物品（<b>跨页或只取某一页</b>；严格按当前共享/本地模式，仅服务端）。
     * <p>
     * ⚠️⚠️ <b>界面开着时必须走界面那份页缓存</b>（与 {@link #quickDeposit} 同因）：
     * 界面里的 {@code PocketDimensionContainer} 缓存着整份页数据，它每次 {@code save()}
     * 都会把缓存<b>整份写回去</b>。以前这里直接从袋子 NBT / 共享存档里扣数、再整份写回：
     * 只要界面里那次 {@code save()} 发生在后面（点一下格子就会 save），
     * 刚扣掉的数量就会被旧缓存<b>原样盖回来</b>——也就是"材料取出来了、袋子里一个没少"，
     * 表现就是 JEI 的 + <b>凭空复制材料</b>（实测正是这一现象）。
     * 所以这里兜住：只要开着的是"同一份存储"的袋子界面，取料一律交给它的缓存去扣。
     * </p>
     *
     * @param player    玩家（判定服务器与模式；仅服务端有效）
     * @param bag       次元袋
     * @param template  要取的物品（数量会被忽略）
     * @param count     想取多少个
     * @param pageIndex 页下标（0 起）；{@value #PAGE_ALL} = 所有页；>= 0 = 只从这一页取
     * @return 实际取到的物品（空 = 袋子里没有）
     */
    public static ItemStack withdrawFromStorage(net.minecraft.world.entity.player.Player player, ItemStack bag,
                                                ItemStack template, int count, int pageIndex) {
        if (player == null || player.level().isClientSide || count <= 0 || template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // 界面开着 → 必须走它的页缓存（理由见方法注释，这是"取料变复制"的根因）
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu
                && menu.shouldRouteDeposit(bag)) {
            return menu.withdrawFromStorage(template, count, pageIndex);
        }
        boolean shared = isShared(bag) && player.getServer() != null;
        PocketSharedStorage store = shared ? PocketSharedStorage.get(player.getServer()) : null;
        List<Page> pages;
        if (shared) {
            pages = store.getPages(player.getUUID());
        } else {
            CompoundTag root = bag.getTag();
            pages = root == null ? new ArrayList<>() : readPages(root);
        }
        // 只扫需要的那些页（PAGE_ALL = 全扫；否则只扫那一位，越界就什么都不取）
        int from = pageIndex == PAGE_ALL ? 0 : pageIndex;
        int to = pageIndex == PAGE_ALL ? pages.size() : Math.min(pages.size(), pageIndex + 1);
        long need = count;
        for (int p = from; p < to && need > 0; p++) {
            Page page = pages.get(p);
            for (int slot = 0; slot < Page.SLOTS && need > 0; slot++) {
                Entry e = page.entries.get(slot);
                if (e == null || !sameItem(e.stack(), template)) continue;
                long take = Math.min(need, e.count());
                page.entries.set(slot, e.count() - take <= 0 ? null : new Entry(e.stack(), e.count() - take));
                need -= take;
            }
        }
        long taken = count - need;
        if (taken <= 0) return ItemStack.EMPTY;
        if (shared) {
            // 只写物品页（流体页没动）
            store.setPages(player.getUUID(), pages);
        } else {
            writePages(bag, pages);
        }
        ItemStack out = template.copy();
        out.setCount((int) taken);
        return out;
    }

    /**
     * 清空袋子 NBT 里的<b>本地</b>页数据（只删 {@code Pages} / {@code FluidPages} 两个键）。
     * <p>
     * 用途：把本地存储整体并入共享空间之后清空本地。托盘数据（{@code Tray*}）与模式标记
     * （{@code PocketShared}）必须原样保留，所以不能整份清掉自定义数据。
     * </p>
     */
    public static void clearLocalPages(ItemStack bag) {
        CompoundTag root = ItemNbt.copyForEdit(bag);
        root.remove(TAG_PAGES);
        root.remove(TAG_FLUID_PAGES);
        ItemNbt.setTag(bag, root);
    }

    // ============================================================
    // 显示辅助
    // ============================================================

    /**
     * 流体数量文案（约定格式，恒定带 mB 后缀）：{@code <100} → {@code 87 mB}；
     * 之后按 百/万/亿 进位（{@code 124百 mB}）。界面绘制与提示消息共用同一份实现。
     */
    public static String formatFluidAmount(long amount) {
        // 与机械动力保持一致的显示方式：
        // Create 的 com.simibubi.create.foundation.utility.FluidFormatter 就是
        // "≥1000 就除以 1000、配 lang 里的 create.generic.unit.buckets = B"，不到 1000 才写 mB。
        // （原来那套"≥100 写成 124百 mB"是早期为了在格子角上省地方定的，现在改成桶更直观：
        //  6000 mB → 6 B、6500 → 6.5 B，一眼就能和储罐/管道对上。）
        if (amount < 1000L) return amount + " mB";
        long buckets = amount / 1000L;
        long tenth = amount % 1000L / 100L;          // 第一位小数（截断，不四舍五入）
        if (buckets >= 10_000L) return formatItemCount(buckets) + " B";   // 桶数上万就缩写：1.2万 B
        return (tenth > 0 ? buckets + "." + tenth : Long.toString(buckets)) + " B";
    }

    /**
     * 储物格右下角那颗数量文字的文案（界面与 mixin 共用这一份）。
     * <p>
     * 规则：**上千就开始缩写，不等到一万** ——
     * {@code 999} 以内精确显示，{@code 1000} → {@code 1千}、{@code 1304} → {@code 1.3千}、{@code 19456} → {@code 1.9万}。
     * 单位阶梯按万进排：**千 → 万 → 亿 → 万亿 → 亿亿**，所以十亿 / 百亿 / 千亿 会自然写成
     * {@code 10亿} / {@code 100亿} / {@code 1000亿}，再往上还有 {@code 1万亿}、{@code 922亿亿}（long 上限）——
     * **任何量级都只占几个字**，不会退化成一大串数字。
     * </p>
     * <p>
     * 系数一律**向下取整、不四舍五入**：否则 9999 会写成 {@code 10千}、999999 变成 {@code 100万}，
     * 等于把"还没到的那一档"说大了。系数 ≥10 时不再带小数（{@code 19万}），进一步压长度。
     * </p>
     * <p>
     * ⚠️ 这份文案必须**替换掉原版自己写的那串数字**（见 {@code mixin/AbstractContainerScreenMixin}）：
     * 原版的数量是它自己画的，若另外叠加一个缩写，只会得到"两个数字"或者"缩写被盖住" ——
     * 实测现象即为此（19456 原样显示，缩写完全没有露出来）。
     * </p>
     */
    public static String formatItemCount(long count) {
        if (count < 1000L) return Long.toString(count);
        final long[] steps = {1000L, 1_0000L, 1_0000_0000L, 1_0000_0000_0000L, 1_0000_0000_0000_0000L};
        final String[] units = {"千", "万", "亿", "万亿", "亿亿"};
        int idx = steps.length - 1;
        while (idx > 0 && count < steps[idx]) idx--;
        long unit = steps[idx];
        long whole = count / unit;                       // 向下取整
        long tenth = count % unit * 10L / unit;          // 第一位小数（同样截断，不会溢出：<10*unit）
        String coef = whole < 10L && tenth > 0L ? whole + "." + tenth : Long.toString(whole);
        return coef + units[idx];
    }

    private static String trim1(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    /** 流体格显示栈的两个自定义数据键（数量固定 1，原因见 {@link #fluidDisplayStack}） */
    private static final String TAG_DISPLAY_AMOUNT = "PocketFluidAmount";
    private static final String TAG_DISPLAY_FLUID = "PocketFluidStack";

    /**
     * 流体格的显示栈：图标 = 该流体的桶、**数量固定 1**，真实 mB 存量与**流体本身**放进自定义数据。
     * <p>
     * 为什么数量必须是 1：原版槽位渲染会把 {@code ItemStack} 的数量画在右下角，
     * 而显示栈的数量原本就是 mB 存量 —— 于是 1000 mB 会画成"水桶 ×1000"，
     * 看起来像把水存成了 1000 个桶（实测反馈：看到的是水桶而不是流体）。
     * </p>
     * <p>
     * 为什么要把流体本身也塞进去：很多模组流体（机械动力那些）**根本没有桶物品**，
     * 图标只能退化成空桶，界面就没法显示"这是什么流体"了。带上流体本体后，
     * 客户端能拿到 {@link net.minecraftforge.fluids.FluidStack} 去画它的静止贴图、也才能给出正确的 tooltip。
     * 这个栈是**临时显示用**的，会随槽位/同步包原样到客户端（请勿当作真实物品使用）。
     * </p>
     */
    public static ItemStack fluidDisplayStack(FluidEntry entry) {
        ItemStack stack = entry.displayStack();
        stack.setCount(1);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putLong(TAG_DISPLAY_AMOUNT, entry.amount());
        tag.put(TAG_DISPLAY_FLUID, entry.stackTag().copy());
        ItemNbt.setTag(stack, tag);
        return stack;
    }

    /** 读流体显示栈里的真实存量（不是流体显示栈则返回 0） */
    public static long displayFluidAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        net.minecraft.nbt.CompoundTag tag = ItemNbt.getTag(stack);
        return tag == null ? 0L : tag.getLong(TAG_DISPLAY_AMOUNT);
    }

    /** 读流体显示栈里记的**流体本身**（不是流体显示栈、或解不出来则返回 EMPTY） */
    public static net.minecraftforge.fluids.FluidStack displayFluid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return net.minecraftforge.fluids.FluidStack.EMPTY;
        net.minecraft.nbt.CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null || !tag.contains(TAG_DISPLAY_FLUID)) {
            return net.minecraftforge.fluids.FluidStack.EMPTY;
        }
        return decodeFluid(tag.getCompound(TAG_DISPLAY_FLUID));
    }

    /**
     * 这个显示栈是不是"流体格显示栈"（带上流体与存量标记的那个）。
     * <p>
     * ⚠️ 判断流体格**不能**用 {@code displayRefs}：客户端那份 {@code displayRefs} 是空的
     * （{@code replaceDisplay} 只填 {@code displayEntries}），用它判断会导致客户端走物品分支、
     * 把 mB 当数量画出来。显示栈自带的标记两端都在，所以以它为准。
     * </p>
     */
    public static boolean isFluidDisplayStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.nbt.CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && tag.contains(TAG_DISPLAY_FLUID);
    }

    /**
     * 该流体有没有对应的桶物品。
     * <p>
     * 机械动力这类模组流体大多没有桶（{@code Fluid#getBucket()} 返回空气），
     * 界面就得改用流体自己的贴图当图标 —— 界面上靠这个判断走哪条路。
     * </p>
     */
    public static boolean hasBucket(net.minecraftforge.fluids.FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().getBucket() != net.minecraft.world.item.Items.AIR;
    }
}
