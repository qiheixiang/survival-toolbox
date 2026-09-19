package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.item.PocketDimensionItem;
import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 的"+"号在<b>服务端</b>真正摆料（见 {@code PocketJeiTransferPacket} 的说明）
 * <p>
 * 顺序：
 * <ol>
 *   <li>先把输入格里"这次配方用不上"的东西退回玩家背包（照 JEI 自己的做法：先腾格子）；</li>
 *   <li><b>按格对位</b>逐格摆料：配方第 k 格（行优先，含空格的造型配方也照样对齐）→ 容器输入槽
 *       {@code inputSlotStart + k}；取料顺序是 玩家背包/副手 → 袋子；</li>
 *   <li>每一件都<b>回读确认</b>（扣减真的落盘、摆放真的进格），对不上就当场回滚。</li>
 * </ol>
 * 只搬运玩家<b>自己已有的</b>物品，绝不凭空生成；某几格实在凑不齐时给玩家一句明确的话
 * （JEI 那边看不到袋子里的东西，报不了这个错）。
 * </p>
 * <p>
 * <b>⚠️⚠️ 为什么每一步都要"回读确认 + 回滚"</b>（实测的复制 bug，请勿再简化掉）：
 * 老写法是"先往格子里摆一份、再从袋子/背包扣一份"，两件事<b>各自独立</b>，
 * 任何一步没生效（界面开着时绕过页缓存扣减会被缓存整份盖回来、原版槽位不接受这个物品……）
 * 的结果都是<b>格子里的材料凭空多出一份</b>，而且玩家能把它再放回袋子 = 无限刷物品
 * （实测表现："物品不消耗，放进去的不是袋子里的，是额外复制出来的，甚至能放回袋子"）。
 * 所以现在写成"先真的扣掉（并确认扣掉了），再摆；摆不进去就把刚扣的还回去"，
 * 顺序不能反，回读也不能省。
 * </p>
 * <p>
 * 公共类，不引用任何客户端类型（专用服务器会加载它）。
 * </p>
 */
public final class PocketJeiTransfer {

    /** 次元袋九宫格的格数（工作台页 CRAFT_GRID_BASE、拆解页 DIS_MAT_BASE 都是 3×3 = 9） */
    public static final int GRID_SLOTS = 9;

    private PocketJeiTransfer() {
    }

    /**
     * 「打开哪个匹配哪个」：当前展开的功能页对应的九宫格在容器里的起始槽位。
     * <p>
     * 规则：袋子界面下点 JEI 的 +，<b>哪个面板开着就摆进哪个面板的九宫格</b>；
     * 工作台页和拆解页都没开就什么都不摆（老代码会在这里自动打开工作台页，
     * 玩家看到的是「没开面板也照样匹配，匹配的还是工作台」）。
     * </p>
     * <p>
     * <b>客户端（{@code PocketBagRecipeTransferHandler}）和服务端（{@link #apply}/{@link #canFill}）
     * 都用这一个方法</b>，两边判断必须一致：客户端算出来的区间随包发上来，服务端再自己确认一遍。
     * 这里只用菜单的公开只读状态（{@code isPanelOpen}、{@code getSlot} 与三个槽位常量），不碰任何 UI。
     * </p>
     * <p>
     * <b>⚠️ 拆解页的九宫格为什么还看"左边拆解槽空不空"</b>：那一块 9 格有两种身份
     * （见 {@code PocketDimensionMenu} 里加槽位时那段说明）：
     * <ul>
     *   <li>左边拆解槽<b>空着</b> = 玩家自己的合成格（可写）→ 可以摆料，这就是"拆解页也能当工作台用"；</li>
     *   <li>左边拆解槽<b>有东西</b> = 原版算出来的"材料预览"，只读（{@code mayPlace}/{@code mayPickup} 都是 false），
     *       而且每 tick 都会被引擎重算并覆盖。<b>这时候绝不能碰它</b>：摆了会被下一 tick 覆盖掉（= 凭空损失玩家的材料），
     *       更不能走 {@link #apply} 的"腾格子"（那会把预览出来的材料当成实物还给玩家 = 凭空多一份）。
     *       所以预览模式下这里返回 -1（"没有可用的输入格"）。</li>
     * </ul>
     * </p>
     *
     * @return {@link PocketDimensionMenu#CRAFT_GRID_BASE}（工作台页开着）、
     *         {@link PocketDimensionMenu#DIS_MAT_BASE}（拆解页开着且处于合成模式）、
     *         <b>-1 = 没有可用的输入九宫格</b>（两个面板都没开 / 拆解页正在预览材料）
     */
    public static int resolveInputSlotStart(PocketDimensionMenu menu) {
        if (menu == null) return -1;
        if (menu.isPanelOpen(PocketDimensionMenu.PANEL_CRAFTING)) return PocketDimensionMenu.CRAFT_GRID_BASE;
        if (menu.isPanelOpen(PocketDimensionMenu.PANEL_DISASSEMBLE)) {
            // 预览模式（左槽有东西）→ 九宫格是只读的材料预览，不是玩家的格子
            if (!menu.getSlot(PocketDimensionMenu.DIS_IN_BASE).getItem().isEmpty()) return -1;
            return PocketDimensionMenu.DIS_MAT_BASE;
        }
        return -1;
    }

    /**
     * 按客户端给的"配方每格候选表"摆料。
     * <p>
     * ⚠️ 次元袋界面下还要先过一道"面板闸"：{@code inputSlotStart} 必须是
     * {@link #resolveInputSlotStart} 算出来的那一页（工作台页 / 拆解页），
     * 否则<b>直接什么都不做</b>（既不摆料，也<b>不</b>替玩家打开面板）。
     * </p>
     *
     * @param player         服务端玩家
     * @param plan           配方每一格的候选物品（按配方格顺序；空列表 = 配方这一格是空的）
     * @param inputSlotStart 配方第一格对应的容器槽位下标
     * @param inputSlotCount 配方格数
     * @param pageIndex      客户端给的"打开的袋子页"下标（{@link PocketStorageHelper#PAGE_ALL} = 所有页）；
     *                       服务端开着次元袋界面时以<b>服务端自己的当前页</b>为准（见 {@link #effectivePage}）
     */
    public static void apply(ServerPlayer player, List<List<ItemStack>> plan, int inputSlotStart, int inputSlotCount,
                             int pageIndex) {
        if (player == null || plan == null || plan.isEmpty() || inputSlotCount <= 0) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        // ⚠️⚠️ 「打开哪个匹配哪个」（实测：「袋子里的工作台和拆解台界面都没开的时候，
        //    点 jei 配方还是会自动匹配，匹配的是工作台」）：
        //    次元袋界面下，目标九宫格必须是**当前正开着的那一页**的九宫格，否则一律不摆料。
        //    老代码在这里 handleTrayAction(ACTION_OPEN_CRAFTING) 替玩家把工作台页打开了，
        //    那就是"没开也自动匹配"的根因 —— 现在**绝不**替玩家开面板，也绝不摆料。
        //    （区间对不上还包括"客户端拿的是过期的面板状态"：比如它按工作台页算的区间发上来，
        //     而服务端这边开着的是拆解页 —— 那就宁可不摆，也不能摆错格子。）
        if (menu instanceof PocketDimensionMenu pocket
                && resolveInputSlotStart(pocket) != inputSlotStart) {
            return;
        }

        ItemStack bag = findBag(player);
        int page = effectivePage(player, bag, pageIndex);

        // ① 腾格子：输入格里"这次用不上"的东西退回玩家背包（放不下会掉在脚下，和 JEI 的行为一致）
        for (int k = 0; k < inputSlotCount; k++) {
            int slotIndex = inputSlotStart + k;
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) break;
            Slot slot = menu.getSlot(slotIndex);
            ItemStack inSlot = slot.getItem();
            if (inSlot.isEmpty()) continue;
            List<ItemStack> candidates = k < plan.size() ? plan.get(k) : List.of();
            if (matchesAny(candidates, inSlot)) continue;      // 这一格本来就对，留着
            ItemStack back = inSlot.copy();
            slot.set(ItemStack.EMPTY);
            // ⚠️ 回读确认：这一格真的清空了才把这件东西还给他。
            //    没清掉（格子是只读/被别的逻辑挡住）就保持原样：
            //    否则"还给玩家的"和"还留在格子里的"是同一件 → 又凭空多出一份
            if (!slot.getItem().isEmpty()) continue;
            returnToOwner(player, bag, back);
        }

        // ② 逐格对位摆料：配方第 k 格 → 容器槽 inputSlotStart + k（造型配方里"空格子"的候选表是空的，
        //    直接 continue：那一格必须留空，原版才认得出造型）
        List<ItemStack> missing = new ArrayList<>();
        for (int k = 0; k < plan.size() && k < inputSlotCount; k++) {
            int slotIndex = inputSlotStart + k;
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) break;
            List<ItemStack> candidates = plan.get(k);
            if (candidates == null || candidates.isEmpty()) continue;   // 配方这一格是空的
            Slot slot = menu.getSlot(slotIndex);
            ItemStack inSlot = slot.getItem();
            if (matchesAny(candidates, inSlot)) continue;               // 这一格本来就对
            // 先真的拿到一件（背包/副手 → 袋子），拿到手的才算数
            ItemStack one = takeOne(player, bag, candidates, page);
            if (one.isEmpty()) {
                missing.add(firstNonEmpty(candidates));
                continue;
            }
            one.setCount(1);
            slot.set(one);
            slot.setChanged();
            // 回读确认：原版槽位可能拒收（isActive()=false 的功能页格子等），
            // 那样这一份就得还回去，否则就是"凭空多出一份"（实测的复制 bug）
            if (!matchesAny(candidates, slot.getItem())) {
                returnToOwner(player, bag, one);
                missing.add(firstNonEmpty(candidates));
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < missing.size() && i < 4; i++) {
                if (i > 0) sb.append("、");
                sb.append(missing.get(i).getHoverName().getString());
            }
            if (missing.size() > 4) sb.append("…");
            player.displayClientMessage(Component.translatable(
                    "message.zzq_survival_toolbox.jei.missing", sb.toString()), false);
        }
    }

    /**
     * 这套材料现在凑得齐吗（服务端权威；给"可转移判定"的查询包用，见 {@code PocketJeiQueryPacket}）。
     * <p>
     * 算法与 {@link #apply} 的取料顺序一致：按配方格顺序，先用"背包 + 副手 + 已经在输入格里的"，
     * 再动袋子里的库存，动过的都从池子里扣掉。
     * 共享袋子的数据在服务器存档里、客户端看不见，所以只能由服务端来回答这个问题
     * （实测："1.20.1 就算物品不足 + 号也能点"就是这个原因）。
     * </p>
     * <p>
     * 这里是<b>只读</b>的：拿不到东西就返回 false，绝不会因为"问一下"而改动任何库存。
     * </p>
     */
    public static boolean canFill(ServerPlayer player, List<List<ItemStack>> plan, int inputSlotStart,
                                  int inputSlotCount, int pageIndex) {
        if (player == null || plan == null || plan.isEmpty() || inputSlotCount <= 0) return false;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return false;
        // ⚠️ 「打开哪个匹配哪个」的同一道闸（和 apply 一致）：目标九宫格那一页没开着的时候，
        //    那些格子 isActive()=false、摆了也不算数 → 一律按"凑不齐"回答，
        //    让 + 号干脆点不动，而不是点了没反应（客户端那边还会给一句"先打开工作台页/拆解页"）。
        if (menu instanceof PocketDimensionMenu pocket
                && resolveInputSlotStart(pocket) != inputSlotStart) {
            return false;
        }
        ItemStack bag = findBag(player);
        // 池子①：玩家背包 + 副手
        List<ItemStack> pool = new ArrayList<>();
        List<Long> poolCount = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) addToPool(pool, poolCount, inv.getItem(i));
        for (int i = 0; i < inv.offhand.size(); i++) addToPool(pool, poolCount, inv.offhand.get(i));
        // 池子②：输入格里"这一次用得上"的东西（已经摆对的不需要重摆）
        for (int k = 0; k < plan.size() && k < inputSlotCount; k++) {
            int slotIndex = inputSlotStart + k;
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) break;
            List<ItemStack> candidates = plan.get(k);
            if (candidates == null || candidates.isEmpty()) continue;
            ItemStack inSlot = menu.getSlot(slotIndex).getItem();
            if (matchesAny(candidates, inSlot)) addToPool(pool, poolCount, inSlot);
        }
        // 池子③：袋子库存（按当前模式读；页号按"打开着的那一页"来）
        int page = effectivePage(player, bag, pageIndex);
        if (!bag.isEmpty()) {
            try {
                for (PocketStorageHelper.Page p : PocketStorageHelper.readPagesForMode(player, bag, page)) {
                    for (PocketStorageHelper.Entry e : p.entries) {
                        if (e == null || e.stack().isEmpty() || e.count() <= 0) continue;
                        addToPool(pool, poolCount, e.stack(), e.count());
                    }
                }
            } catch (Throwable ignored) {
                // 读袋子出错：当作"袋子里没有"，判定为凑不齐（宁可不给点，也不要放任它点了没反应）
                return false;
            }
        }
        // 按配方格顺序消耗池子
        for (List<ItemStack> candidates : plan) {
            if (candidates == null || candidates.isEmpty()) continue;
            boolean ok = false;
            for (ItemStack want : candidates) {
                if (want == null || want.isEmpty()) continue;
                for (int i = 0; i < pool.size(); i++) {
                    if (poolCount.get(i) <= 0L) continue;
                    if (!PocketStorageHelper.sameItem(pool.get(i), want)) continue;
                    poolCount.set(i, poolCount.get(i) - 1L);
                    ok = true;
                    break;
                }
                if (ok) break;
            }
            if (!ok) return false;
        }
        return true;
    }

    /**
     * "取料到底该看哪几页"（服务端权威）。
     * <p>
     * 规则：<b>袋子界面开着的时候，打开哪页就只用哪页</b>
     * （玩家看得见的东西才算数）；原版工作台 / 锻造台那种"没有袋子页开着"的界面才看所有页。
     * 客户端也会把它看到的页号发上来，但**服务端自己开着的那个界面说了算**：
     * 客户端那份可能已经过期（翻页包还在路上），以它为准会取到玩家没看到的页。
     * </p>
     */
    private static int effectivePage(ServerPlayer player, ItemStack bag, int fromClient) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof PocketDimensionMenu pocket && pocket.shouldRouteDeposit(bag)) {
            int page = pocket.getDisplayedPageIndex();
            if (page != PocketStorageHelper.PAGE_ALL) return page;
        }
        return fromClient;
    }

    /**
     * 取一个这种物品：先玩家背包/副手，再袋子（按当前模式、只从指定页取）。
     * 候选表里的物品是"这个配方格接受哪些物品"，按顺序找第一个拿得到的。
     * <p>
     * <b>⚠️ 从袋子里取的每一步都要回读确认</b>：取之前数一次、取之后数一次，
     * 只有真的少掉那么多才算取到（界面开着时绕过页缓存的扣减会被缓存盖回来，
     * 那时候"取到了"是假的，摆进格子就是凭空多出一份）。
     * </p>
     */
    private static ItemStack takeOne(ServerPlayer player, ItemStack bag, List<ItemStack> candidates, int pageIndex) {
        Inventory inv = player.getInventory();
        for (ItemStack want : candidates) {
            if (want == null || want.isEmpty()) continue;
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack have = inv.getItem(i);
                if (have.isEmpty() || !PocketStorageHelper.sameItem(have, want)) continue;
                int before = have.getCount();
                ItemStack one = have.copy();
                one.setCount(1);
                have.shrink(1);
                // 回读确认：数量真的少了才算拿到
                if (have.getCount() != before - 1) continue;
                // 这一格空了就清掉（拿走的是一份"活堆叠"，is() 判定到这里已经确认过是这一格本身）
                if (have.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                inv.setChanged();
                return one;
            }
            for (int i = 0; i < inv.offhand.size(); i++) {
                ItemStack have = inv.offhand.get(i);
                if (have.isEmpty() || !PocketStorageHelper.sameItem(have, want)) continue;
                int before = have.getCount();
                ItemStack one = have.copy();
                one.setCount(1);
                have.shrink(1);
                if (have.getCount() != before - 1) continue;
                if (have.isEmpty()) inv.offhand.set(i, ItemStack.EMPTY);
                inv.setChanged();
                return one;
            }
        }
        if (bag.isEmpty()) return ItemStack.EMPTY;
        for (ItemStack want : candidates) {
            if (want == null || want.isEmpty()) continue;
            // 模式感知 + 页感知：共享袋子走服务器存档、本地袋子走袋子自己的 NBT；
            // 次元袋界面开着时由 withdrawFromStorage 转交给界面缓存（见那边的说明）
            long beforeCount = PocketStorageHelper.countInStorage(player, bag, want, pageIndex);
            if (beforeCount <= 0L) continue;
            ItemStack got = PocketStorageHelper.withdrawFromStorage(player, bag, want, 1, pageIndex);
            if (got.isEmpty()) continue;
            long afterCount = PocketStorageHelper.countInStorage(player, bag, want, pageIndex);
            if (afterCount > beforeCount - 1L) {
                // 扣减没落盘（会被界面缓存整份盖回来）：这一份不能算数，
                // 原样放回去（放回背包；背包也放不下就掉在脚下，绝不能凭空多出来）
                returnToOwner(player, bag, got);
                continue;
            }
            got.setCount(1);
            return got;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 把物品还给玩家：先塞背包，再塞副手，再收回袋子存储，最后才掉在脚下。
     * <p>
     * ⚠️ 这是所有"回滚"的唯一出口：回滚必须真的把东西放到玩家拿得到的地方，
     * 不能悄悄 {@code setCount(0)} 丢掉（那就是另一种形式的吃物品）。
     * </p>
     */
    private static void returnToOwner(ServerPlayer player, ItemStack bag, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        if (player.getInventory().add(copy)) {
            player.getInventory().setChanged();
            return;
        }
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            ItemStack off = player.getInventory().offhand.get(i);
            if (off.isEmpty()) {
                player.getInventory().offhand.set(i, copy);
                player.getInventory().setChanged();
                return;
            }
            if (PocketStorageHelper.sameItem(off, copy)
                    && off.getCount() + copy.getCount() <= off.getMaxStackSize()) {
                off.grow(copy.getCount());
                player.getInventory().setChanged();
                return;
            }
        }
        if (!bag.isEmpty()) {
            long left = PocketStorageHelper.quickDeposit(player, bag, copy);
            if (left <= 0) return;
            copy.setCount((int) Math.min(left, copy.getCount()));
        }
        player.drop(copy, false);
    }

    private static boolean matchesAny(List<ItemStack> candidates, ItemStack stack) {
        if (stack == null || stack.isEmpty() || candidates == null) return false;
        for (ItemStack want : candidates) {
            if (want != null && !want.isEmpty() && PocketStorageHelper.sameItem(stack, want)) return true;
        }
        return false;
    }

    private static ItemStack firstNonEmpty(List<ItemStack> candidates) {
        for (ItemStack want : candidates) {
            if (want != null && !want.isEmpty()) return want;
        }
        return ItemStack.EMPTY;
    }

    private static void addToPool(List<ItemStack> pool, List<Long> counts, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) return;
        addToPool(pool, counts, stack, stack.getCount());
    }

    private static void addToPool(List<ItemStack> pool, List<Long> counts, ItemStack template, long count) {
        if (template == null || template.isEmpty() || count <= 0) return;
        for (int i = 0; i < pool.size(); i++) {
            if (PocketStorageHelper.sameItem(pool.get(i), template)) {
                counts.set(i, counts.get(i) + count);
                return;
            }
        }
        pool.add(template.copy());
        counts.add(count);
    }

    /** 玩家身上的次元袋（主手优先，其次副手、背包）——与 JEI 处理器里那份保持一致 */
    public static ItemStack findBag(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.POCKET_DIMENSION.get())) return main;
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.POCKET_DIMENSION.get())) return off;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.POCKET_DIMENSION.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 这东西是不是本模组的袋子（避免把袋子当材料塞进格子） */
    public static boolean isBag(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PocketDimensionItem;
    }
}
