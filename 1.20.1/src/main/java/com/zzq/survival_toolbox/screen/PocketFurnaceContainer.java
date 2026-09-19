package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.util.PocketFurnace;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 熔炉页的两个格（输入 + 燃料）
 * <p>
 * 走**原版堆叠语义**（每格一叠、不超过 64），所以物品交互直接交给原版
 * {@code AbstractContainerMenu#doClick}：左键整叠、右键放1/取半、Shift 转、拖拽分配全都标准。
 * 真实数据存在袋子 NBT（{@link PocketFurnace}），本类只是它的一层读写视图：
 * <ul>
 *   <li>服务端：{@link #refreshFromBag()} 从 NBT 读、{@link #setChanged()} 写回 NBT。
 *       每次点击前都会先刷新（见菜单），否则"服务端每 tick 改 NBT"与"玩家点击改缓存"会相互冲突，
 *       出现把烧掉的输入又写回去这种复制物品的问题。</li>
 *   <li>客户端：格子内容由原版槽位同步写入，进度（火焰/箭头）由次元袋同步包写入，只用于显示。</li>
 * </ul>
 * 页里没有输出槽：产物烧好直接按当前共享/本地模式收进袋子存储（即产物自动回仓库）。
 * </p>
 */
public class PocketFurnaceContainer implements Container {

    private final ItemStack bag;
    /** 只用来取游戏时间（进度是"现在 - 开始时刻"现算的，见 PocketFurnace 的类注释） */
    private final net.minecraft.world.level.Level level;
    private boolean clientSide = false;

    /** 格子显示内容（服务端：真实数据；客户端：同步写入） */
    private final ItemStack[] display = new ItemStack[PocketFurnace.SLOTS];

    /** 燃烧进度（服务端：来自 NBT；客户端：来自同步包） */
    private int burn;
    private int burnTotal;
    /** 烹饪进度（同上） */
    private int cook;
    private int cookTotal;

    public PocketFurnaceContainer(ItemStack bag, net.minecraft.world.level.Level level, boolean clientSide) {
        this.bag = bag;
        this.level = level;
        this.clientSide = clientSide;
        for (int i = 0; i < PocketFurnace.SLOTS; i++) {
            display[i] = ItemStack.EMPTY;
        }
        if (!clientSide) {
            refreshFromBag();
        }
    }

    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    /**
     * 服务端：刷新（点击前、每次广播前都调；客户端不做，客户端数据来自同步）。
     * <p>
     * 袋子 NBT 里存的是"时刻"（燃料烧到几点、这段从几点开始烧），
     * 所以这里用"现在 - 开始时刻"现算出剩余燃料与烧炼进度给界面用。
     * </p>
     */
    public void refreshFromBag() {
        if (clientSide) return;
        PocketFurnace.State st = PocketFurnace.read(bag);
        long now = level == null ? 0L : level.getGameTime();
        display[PocketFurnace.SLOT_INPUT] = st.input;
        display[PocketFurnace.SLOT_FUEL] = st.fuel;
        display[PocketFurnace.SLOT_OUTPUT] = st.toSlot ? st.output : ItemStack.EMPTY;
        this.burn = PocketFurnace.remainingBurn(st, now);
        this.burnTotal = st.burnTotal;
        this.cook = (int) Math.min(Integer.MAX_VALUE, PocketFurnace.cookProgress(st, now));
        this.cookTotal = st.cookTotal;
    }

    /**
     * 服务端：把三个格写回袋子 NBT（玩家在页面上动格子是"结构性变化"，必须立刻落盘）。
     * <p>
     * 注意：进度（{@code burn}/{@code cook}）是现算的，<b>不写回</b> —— 写回的话每 tick 都会改 NBT，
     * 原版就会每 tick 重新同步玩家手里的袋子（界面会持续抖动）。
     * </p>
     */
    public void save() {
        if (clientSide) return;
        PocketFurnace.State st = PocketFurnace.read(bag);
        st.input = display[PocketFurnace.SLOT_INPUT];
        st.fuel = display[PocketFurnace.SLOT_FUEL];
        st.output = display[PocketFurnace.SLOT_OUTPUT];
        PocketFurnace.write(bag, st);
    }

    /** 客户端：同步包里的进度（只用于显示） */
    public void applySyncedProgress(int burn, int burnTotal, int cook, int cookTotal) {
        this.burn = burn;
        this.burnTotal = burnTotal;
        this.cook = cook;
        this.cookTotal = cookTotal;
    }

    // ============================================================
    // 进度查询（界面画火焰条/箭头用）
    // ============================================================

    public int getBurn() {
        return burn;
    }

    public int getBurnTotal() {
        return burnTotal;
    }

    public int getCook() {
        return cook;
    }

    public int getCookTotal() {
        return cookTotal;
    }

    /** 火在烧吗（画火焰图标用） */
    public boolean isLit() {
        return burn > 0;
    }

    /** 还剩多少 tick 烧完当前物品（没在烧返回 -1） */
    public int remainingCookTicks() {
        if (cookTotal <= 0 || cook <= 0) return -1;
        return Math.max(0, cookTotal - cook);
    }

    // ============================================================
    // Container 接口（原版堆叠语义）
    // ============================================================

    @Override
    public int getContainerSize() {
        return PocketFurnace.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : display) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= PocketFurnace.SLOTS) return ItemStack.EMPTY;
        // 与原版容器一致：返回格内实时堆叠（原版 doClick 会直接改它，之后调 setChanged 写回）
        return display[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (clientSide || slot < 0 || slot >= PocketFurnace.SLOTS || amount <= 0) return ItemStack.EMPTY;
        ItemStack current = display[slot];
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        if (current.isEmpty()) display[slot] = ItemStack.EMPTY;
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= PocketFurnace.SLOTS) return;
        if (clientSide) {
            display[slot] = stack.copy(); // 客户端：接受原版槽位同步
            return;
        }
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            int max = Math.min(copy.getMaxStackSize(), getMaxStackSize());
            if (copy.getCount() > max) copy.setCount(max);
        }
        display[slot] = copy;
        setChanged();
    }

    @Override
    public void setChanged() {
        if (clientSide) return;
        save();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        if (clientSide) return;
        for (int i = 0; i < PocketFurnace.SLOTS; i++) {
            display[i] = ItemStack.EMPTY;
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}
