package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import com.zzq.survival_toolbox.screen.PocketPageContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 合成页的"自动补充"开关（<b>与精妙背包的自动补充按钮同语义</b>）
 * <p>
 * 规则：<b>打开</b>时，玩家从次元袋合成页取走产物之后，刚被原版消耗掉的那几格材料
 * 会自动从储物空间补回九宫格（补到取产物之前的样子）；<b>关闭</b>时什么都不补。
 * </p>
 * <p>
 * <b>⚠️ 只补"袋子里真的有的"</b>：每补一件都从储物空间里扣一件（走界面缓存，见
 * {@link #refill}），补不进去就什么都不放——<b>绝不凭空生成物品</b>。
 * 这条是硬性约束：合成页以前出过"料是复制出来的"（见 {@code PocketJeiTransfer} 里那段根因说明），
 * 自动补充如果写成"直接往格子里塞一份"，就是同一个 bug 换了个入口。
 * </p>
 * <p>
 * 状态存在袋子 NBT 里（跟着袋子走），服务端权威；客户端只做"本地先翻一下让按钮立刻有反应"。
 * </p>
 */
public final class PocketCraftRefill {

    /** 开关：true = 取走产物后自动从储物空间补回九宫格 */
    public static final String TAG_ON = "CraftRefillOn";

    private PocketCraftRefill() {
    }

    /** 读开关（缺省 = 关：这是个会改动玩家库存的行为，默认必须是不动手） */
    public static boolean read(ItemStack bag) {
        if (bag == null || bag.isEmpty()) return false;
        CompoundTag root = ItemNbt.getTag(bag);
        return root != null && root.getBoolean(TAG_ON);
    }

    /** 写开关（复制出来改再写回，见 {@link ItemNbt} 的线程安全说明） */
    public static void write(ItemStack bag, boolean on) {
        if (bag == null || bag.isEmpty()) return;
        ItemNbt.edit(bag, root -> root.putBoolean(TAG_ON, on));
    }

    /**
     * 取产物之后把材料补回九宫格（服务端；只在开关打开时由菜单调用）。
     * <p>
     * 做法是"照着一张<b>取产物之前</b>的快照把每一格补回原样"：
     * 原版取产物会把每格各扣 1（堆叠的话）或留个桶之类的残渣，所以对每一格算
     * {@code 差量 = 快照数量 - 现在数量}，再按差量从储物空间取。
     * </p>
     * <p>
     * ⚠️ 取料一律走 {@code menu.withdrawFromStorage}(= 界面页缓存，页号 = 当前显示页)：
     * 绕过缓存直接写袋子 NBT 的扣减会被下一次 {@code save()} 整份盖回来 —— 那就是复制物品。
     * </p>
     * <p>
     * ⚠️ 每格放进去之后都<b>回读确认</b>；放不进去就把刚取出来的这份原样还回储物空间
     * （还回去也失败就掉在玩家脚下），宁可没补上也绝不让物品凭空多出来或者凭空消失。
     * </p>
     *
     * @param player 服务端玩家（兜底掉落用，可为 null）
     * @param menu   次元袋界面（取料走它的缓存）
     * @param grid   合成页九宫格
     * @param before 取产物之前九宫格的快照（每格一份 copy）
     * @return 真的补进去的件数
     */
    public static int refill(ServerPlayer player, PocketDimensionMenu menu, PocketPageContainer grid,
                             List<ItemStack> before) {
        if (menu == null || grid == null || before == null) return 0;
        int moved = 0;
        try {
            for (int i = 0; i < grid.getContainerSize() && i < before.size(); i++) {
                ItemStack was = before.get(i);
                if (was == null || was.isEmpty()) continue;
                ItemStack now = grid.getItem(i);
                // 那一格现在是别的东西（例如原版留下的空桶这类"剩余物品"）：不碰它，以免丢失残渣
                if (!now.isEmpty() && !PocketStorageHelper.sameItem(now, was)) continue;
                int need = was.getCount() - now.getCount();
                if (need <= 0) continue;
                // 只从"当前显示的那一页"取（规则：打开哪个就匹配哪个）
                ItemStack got = menu.withdrawFromStorage(was, need, menu.getDisplayedPageIndex());
                if (got.isEmpty()) continue;
                int put = Math.min(got.getCount(), need);
                if (!place(grid, i, now, was, put)) {
                    // 放不进去：取出来的这份原样还回储物空间（不可留在手中，也不可凭空消失）
                    returnBack(player, menu, got);
                    continue;
                }
                moved += put;
                if (got.getCount() > put) {
                    // 理论上不会多取，多出来的部分同样还回去
                    ItemStack rest = got.copy();
                    rest.setCount(got.getCount() - put);
                    returnBack(player, menu, rest);
                }
            }
        } catch (Throwable t) {
            // 界面点击/取产物的链路上抛异常 = 单人模式直接崩游戏，宁可这次不补
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 合成页自动补充失败（已放弃这一次补充，物品不会重复也不会消失）", t);
        }
        return moved;
    }

    /**
     * 往九宫格第 slot 格里放 put 个（空格放入 / 同类合并），并<b>回读确认</b>真的进去了。
     *
     * @param was 这一格取产物之前的样子（拿它当模板，保证补回来的还是原来那种物品）
     * @return true = 确认放进去了
     */
    private static boolean place(PocketPageContainer grid, int slot, ItemStack now, ItemStack was, int put) {
        if (put <= 0) return false;
        int expect = (now.isEmpty() ? 0 : now.getCount()) + put;
        ItemStack after = was.copy();
        after.setCount(expect);
        grid.setItem(slot, after);
        // ⚠️ 回读确认：写没落盘（或者被原版堆叠上限截断）时这里会露出来
        ItemStack readBack = grid.getItem(slot);
        return !readBack.isEmpty() && PocketStorageHelper.sameItem(readBack, was) && readBack.getCount() >= expect;
    }

    /**
     * 把取出来但没用上的东西还回储物空间（走界面缓存）。
     * <p>
     * 还不到储物空间（理论上不会失败）就塞进玩家背包，最后才掉在脚下——总之不能留在"谁都不认"的地方。
     * </p>
     */
    private static void returnBack(ServerPlayer player, PocketDimensionMenu menu, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        long left = menu.depositToStorage(stack);
        if (left <= 0) return;
        ItemStack rest = stack.copy();
        rest.setCount((int) Math.min(left, stack.getCount()));
        if (player == null || !player.getInventory().add(rest)) {
            if (player != null) player.drop(rest, false);
        }
    }
}
