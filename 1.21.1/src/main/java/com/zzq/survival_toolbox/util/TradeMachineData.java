package com.zzq.survival_toolbox.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易机的报价数据（存在交易机物品自己的 NBT 里，键 {@code Trades}）
 * <p>
 * 需求：shift+右键有交易菜单的 NPC 会把对方报价记下来；同一"交易项"里<b>更实惠的覆盖不实惠的那条</b>
 * （要得更少 / 给得更多）；记下来的报价<b>可以直接成交</b>（随身村民式，不做村民的补货/需求/声望）。
 * </p>
 * <p>
 * 写 NBT 一律走 {@link ItemNbt#edit}（复制→改→写回），绝不就地改物品里那份标签
 * —— 就地改会和网络编码线程抢同一个 HashMap，表现为玩家掉线。
 * </p>
 */
public final class TradeMachineData {

    public static final String TAG_TRADES = "Trades";
    private static final String TAG_A = "A";
    private static final String TAG_B = "B";
    private static final String TAG_OUT = "Out";
    private static final String TAG_NPC = "Npc";
    private static final String TAG_USES = "Uses";
    /** 最多记这么多条（界面分页用） */
    public static final int MAX_TRADES = 63;

    private TradeMachineData() {
    }


    /** 客户端：服务端同步过来的报价列表（界面画列表用；服务端不用它） */
    private static volatile List<Trade> clientTrades = List.of();

    public static void setClientTrades(List<Trade> trades) {
        clientTrades = trades == null ? List.of() : trades;
    }

    public static List<Trade> clientTrades() {
        return clientTrades;
    }

    /** 一条报价：输入 A / 输入 B（可为空）/ 产物 / 来源 NPC 名字 */
    public static final class Trade {
        public ItemStack costA = ItemStack.EMPTY;
        public ItemStack costB = ItemStack.EMPTY;
        public ItemStack result = ItemStack.EMPTY;
        public String npc = "";
        public int uses;

        public Trade copy() {
            Trade t = new Trade();
            t.costA = this.costA.copy();
            t.costB = this.costB.copy();
            t.result = this.result.copy();
            t.npc = this.npc;
            t.uses = this.uses;
            return t;
        }
    }

    /** 把原版村民报价转成本类这条（会丢掉概率/需求那些村民专属参数——随身村民不需要） */
    public static Trade fromOffer(MerchantOffer offer, String npc) {
        Trade t = new Trade();
        // 1.21.1 的 MerchantOffer#getCostA/getCostB 直接给 ItemStack（没有 ItemCost）
        ItemStack a = offer.getCostA();
        ItemStack b = offer.getCostB();
        t.costA = a == null ? ItemStack.EMPTY : a.copy();
        t.costB = b == null ? ItemStack.EMPTY : b.copy();
        t.result = offer.getResult().copy();
        t.npc = npc == null ? "" : npc;
        t.uses = offer.getUses();
        return t;
    }

    // ============================================================
    // 读写
    // ============================================================

    public static List<Trade> read(ItemStack machine, HolderLookup.Provider registries) {
        List<Trade> out = new ArrayList<>();
        CompoundTag root = ItemNbt.getTag(machine);
        if (root == null) return out;
        ListTag list = root.getList(TAG_TRADES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Trade t = new Trade();
            t.costA = ItemStack.parseOptional(registries, c.getCompound(TAG_A));
            t.costB = ItemStack.parseOptional(registries, c.getCompound(TAG_B));
            t.result = ItemStack.parseOptional(registries, c.getCompound(TAG_OUT));
            t.npc = c.getString(TAG_NPC);
            t.uses = c.getInt(TAG_USES);
            if (!t.result.isEmpty()) out.add(t);
        }
        return out;
    }

    public static void write(ItemStack machine, List<Trade> trades, HolderLookup.Provider registries) {
        ItemNbt.edit(machine, root -> {
            ListTag list = new ListTag();
            for (Trade t : trades) {
                if (t == null || t.result.isEmpty()) continue;
                CompoundTag c = new CompoundTag();
                c.put(TAG_A, t.costA.isEmpty() ? new CompoundTag() : t.costA.saveOptional(registries));
                c.put(TAG_B, t.costB.isEmpty() ? new CompoundTag() : t.costB.saveOptional(registries));
                c.put(TAG_OUT, t.result.saveOptional(registries));
                c.putString(TAG_NPC, t.npc == null ? "" : t.npc);
                c.putInt(TAG_USES, t.uses);
                list.add(c);
            }
            root.put(TAG_TRADES, list);
        });
    }

    /** 记录一条报价；返回 1 = 新增，2 = 用更实惠的替换了旧的，0 = 已有更实惠的（没动） */
    public static int record(ItemStack machine, Trade incoming, HolderLookup.Provider registries) {
        if (incoming == null || incoming.result.isEmpty()) return 0;
        List<Trade> trades = read(machine, registries);
        for (int i = 0; i < trades.size(); i++) {
            Trade old = trades.get(i);
            if (!sameDeal(old, incoming)) continue;
            if (better(incoming, old)) {
                trades.set(i, incoming.copy());
                write(machine, trades, registries);
                return 2;
            }
            return 0;
        }
        if (trades.size() >= MAX_TRADES) return 0;
        trades.add(incoming.copy());
        write(machine, trades, registries);
        return 1;
    }

    /** 同一交易项：产物同物 + 输入组合相同（输入顺序无关） */
    public static boolean sameDeal(Trade a, Trade b) {
        if (a == null || b == null) return false;
        if (!PocketStorageHelper.sameItem(a.result, b.result)) return false;
        boolean ab = PocketStorageHelper.sameItem(a.costA, b.costA)
                && PocketStorageHelper.sameItem(a.costB, b.costB);
        boolean ba = PocketStorageHelper.sameItem(a.costA, b.costB)
                && PocketStorageHelper.sameItem(a.costB, b.costA);
        return ab || ba;
    }

    /** 更实惠：产物给得更多，或者同样多但材料要得更少（顺带看第二格材料） */
    public static boolean better(Trade incoming, Trade old) {
        int inOut = incoming.result.getCount();
        int oldOut = old.result.getCount();
        if (inOut != oldOut) return inOut > oldOut;
        int inCost = costUnits(incoming);
        int oldCost = costUnits(old);
        return inCost < oldCost;
    }

    private static int costUnits(Trade t) {
        int n = t.costA.isEmpty() ? 0 : t.costA.getCount();
        n += t.costB.isEmpty() ? 0 : t.costB.getCount();
        return n;
    }

    // ============================================================
    // 成交
    // ============================================================

    /** 材料够不够（只认物品 + 组件，忽略数量以外的东西） */
    public static boolean canAfford(Player player, Trade t) {
        return countOf(player, t.costA) >= (t.costA.isEmpty() ? 0 : t.costA.getCount())
                && countOf(player, t.costB) >= (t.costB.isEmpty() ? 0 : t.costB.getCount());
    }

    private static int countOf(Player player, ItemStack template) {
        if (template.isEmpty()) return Integer.MAX_VALUE;
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && PocketStorageHelper.sameItem(s, template)) n += s.getCount();
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (!s.isEmpty() && PocketStorageHelper.sameItem(s, template)) n += s.getCount();
        }
        return n;
    }

    /** 成交：扣材料、给产物；材料不够返回 false（什么都不动） */
    public static boolean execute(Player player, Trade t) {
        if (t == null || t.result.isEmpty() || !canAfford(player, t)) return false;
        if (!t.costA.isEmpty()) consume(player, t.costA);
        if (!t.costB.isEmpty()) consume(player, t.costB);
        ItemStack out = t.result.copy();
        if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        player.getInventory().setChanged();
        return true;
    }

    private static void consume(Player player, ItemStack template) {
        int need = template.getCount();
        for (ItemStack s : player.getInventory().items) {
            if (need <= 0) break;
            if (s.isEmpty() || !PocketStorageHelper.sameItem(s, template)) continue;
            int take = Math.min(need, s.getCount());
            s.shrink(take);
            need -= take;
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (need <= 0) break;
            if (s.isEmpty() || !PocketStorageHelper.sameItem(s, template)) continue;
            int take = Math.min(need, s.getCount());
            s.shrink(take);
            need -= take;
        }
    }
}