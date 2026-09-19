package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 补货 —— 从袋子储物空间把玩家身上"已经有的那几堆"补齐
 * <p>
 * 需求：开关 + 范围可切（<b>只补快捷栏</b> ↔ <b>快捷栏 + 整个背包</b>）。
 * </p>
 * <p>
 * ⚠️ 与精妙背包的差异（读它的 jar 核对过）：上游的 "Restock" 实际是
 * "把背包里的东西搬进玩家潜行右键的那个容器"（交互触发，不是补玩家自己）；
 * 这里要的是"从袋子补玩家身上"，所以按此需求实现，不要照上游搬。
 * </p>
 * <p>
 * 规则：
 * <ul>
 *   <li>只补<b>已经存在</b>的堆叠（空格不塞新种类），补到堆叠上限为止；</li>
 *   <li>不碰袋子物品本身；</li>
 *   <li><b>袋子界面开着时不动手</b>：界面开着时页数据在容器的内存缓存里，
 *       绕过缓存去写袋子 NBT 会把东西弄丢；</li>
 *   <li>节流：每 {@value #COOLDOWN_TICKS} tick 跑一次，每次最多补 {@value #MAX_PER_PASS} 堆
 *       （取件要读一遍页数据，不要一次补一堆导致服务器卡顿）。</li>
 * </ul>
 * </p>
 */
public final class PocketRestock {

    /** 开关：true = 补货 */
    public static final String TAG_ON = "ResOn";
    /** 范围：true = 只补快捷栏，false = 快捷栏 + 主背包 */
    public static final String TAG_HOTBAR_ONLY = "ResHotbarOnly";

    private static final int COOLDOWN_TICKS = 10;
    private static final int MAX_PER_PASS = 2;
    /** 全局共享的下一次扫描时刻（服务端 tick 计数） */
    private static long nextScanTick = 0L;

    private PocketRestock() {
    }

    /** 补货状态 */
    public static final class State {
        public boolean on;
        /** 默认只补快捷栏（两种范围都提供，默认取更克制的那个） */
        public boolean hotbarOnly = true;
    }

    public static State read(ItemStack bag, HolderLookup.Provider registries) {
        State st = new State();
        CompoundTag root = ItemNbt.getTag(bag);
        if (root == null) return st;
        st.on = root.getBoolean(TAG_ON);
        st.hotbarOnly = !root.contains(TAG_HOTBAR_ONLY) || root.getBoolean(TAG_HOTBAR_ONLY);
        return st;
    }

    /** 写回（复制出来改、再整份写回，见 ItemNbt 的线程安全说明） */
    public static void write(ItemStack bag, State st, HolderLookup.Provider registries) {
        ItemNbt.edit(bag, root -> {
            root.putBoolean(TAG_ON, st.on);
            root.putBoolean(TAG_HOTBAR_ONLY, st.hotbarOnly);
        });
    }

    /** 每 tick 的快速判断：只看开关键，不解析别的 */
    public static boolean mayBeActive(ItemStack bag) {
        CompoundTag root = ItemNbt.getTag(bag);
        return root != null && root.getBoolean(TAG_ON);
    }

    /**
     * 补一次货（服务端）。返回这次补进去的总件数。
     */
    public static int tick(ServerPlayer player, ItemStack bag, HolderLookup.Provider registries) {
        long now = player.level().getGameTime();
        if (now < nextScanTick) return 0;
        nextScanTick = now + COOLDOWN_TICKS;

        // 界面开着就不动手（页数据在界面缓存里，绕过它会丢东西）
        if (player.containerMenu instanceof PocketDimensionMenu menu && menu.isForBag(bag)) return 0;

        State st = read(bag, registries);
        if (!st.on) return 0;

        // getInventory().items 是 NonNullList（不是数组）
        net.minecraft.core.NonNullList<ItemStack> items = player.getInventory().items;
        int limit = st.hotbarOnly ? Math.min(9, items.size()) : items.size();
        int moved = 0;
        int stacks = 0;
        for (int i = 0; i < limit && stacks < MAX_PER_PASS; i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty() || slot == bag) continue;         // 空格不补；袋子自己跳过
            int max = Math.min(slot.getMaxStackSize(), 64);
            int need = max - slot.getCount();
            if (need <= 0) continue;
            ItemStack got = PocketStorageHelper.withdrawFromStorage(player, bag, slot, need, registries);
            if (got.isEmpty()) continue;
            slot.grow(got.getCount());
            moved += got.getCount();
            stacks++;
        }
        if (moved > 0) {
            player.getInventory().setChanged();
        }
        return moved;
    }
}
