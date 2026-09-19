package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.screen.DisassembleMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 拆解台页的"原版逻辑引擎"（复用 mod 自己的 {@link DisassembleMenu}）
 * <p>
 * 拆解台也要像铁砧 / 锻造台 / 合成台那样<b>在次元袋界面里展开</b>，
 * 而且"逻辑去引用原本的，不要自己写"。所以这里照 {@link PocketPageEngine} 的既有做法：
 * 在服务端偷偷建一个<b>拆解台菜单对象</b>当纯逻辑引擎——它不注册给玩家
 * （玩家的 {@code containerMenu} 始终是次元袋菜单），只是被喂输入格 / 点原版的按钮：
 * <ul>
 *   <li>喂输入：{@code engine.getSlot(0).set(stack)} —— 原版 {@code Slot#setChanged}
 *       → {@code onLeftSlotChanged()} → 自动填九宫格（拆解预览）或重算合成产物；</li>
 *   <li>看九宫格：{@code getSlot(1..9)} —— 拆解模式下是原版按配方匹配出来的材料预览，
 *       合成模式下是玩家自己摆的材料；</li>
 *   <li>取产物：{@code getSlot(10).onTake(player, stack)} —— 原版 {@code onCraftResultTaken()}
 *       → {@code performDisassemble()} / {@code performCraft()}（扣输入、把材料或产物给玩家）；</li>
 *   <li>翻页 / 一键拆解：{@code clickMenuButton(player, id)} —— 原版按钮（0/1 拆解上下页、
 *       2/3 合成上下页、4 一键拆解、5 批量拆解、6 把输入退还给玩家并切回合成模式，供 JEI 的"+"用）。</li>
 * </ul>
 * 也就是说：拆解匹配、变体翻页、药水/附魔书籍拆解、合成预览、材料消耗——<b>全是原版（本 mod 原有）那套代码算的</b>，
 * 这个类只负责按槽位号转发。
 * </p>
 */
public final class PocketDisassembleEngine {

    /** 拆解输入格（原版布局：0 = 左侧输入） */
    public static final int INPUT_SLOT = 0;
    /** 九宫格第一格（1..9） */
    public static final int MAT_BASE = 1;
    /** 九宫格格数 */
    public static final int MAT_COUNT = 9;
    /** 产物格 */
    public static final int OUTPUT_SLOT = 10;

    /** 原版按钮号（见 {@code DisassembleMenu#clickMenuButton}） */
    public static final int BUTTON_DIS_PREV = 0;
    public static final int BUTTON_DIS_NEXT = 1;
    public static final int BUTTON_CRAFT_PREV = 2;
    public static final int BUTTON_CRAFT_NEXT = 3;
    public static final int BUTTON_TAKE_ALL = 4;
    public static final int BUTTON_BULK = 5;
    /** 把输入槽里的东西退还给玩家并切回合成模式（JEI 的"+"填材料前先调它） */
    public static final int BUTTON_RETURN_INPUT = 6;

    /** 引擎菜单的容器编号：取一个足够大的数值以免与真实菜单冲突；该菜单不注册给玩家，也不会用来发包 */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(5000);

    private final Player player;
    private final DisassembleMenu menu;

    public PocketDisassembleEngine(Player player) {
        this.player = player;
        this.menu = new DisassembleMenu(NEXT_ID.getAndIncrement(), player.getInventory());
    }

    /** 让原版菜单自己跑一遍每 tick 逻辑（配方索引构建进度、索引就绪后重算一次） */
    public void tick() {
        this.menu.broadcastChanges();
    }

    // ---- 输入格 ----

    public ItemStack getInput() {
        return this.menu.getSlot(INPUT_SLOT).getItem();
    }

    public void setInput(ItemStack stack) {
        this.menu.getSlot(INPUT_SLOT).set(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    // ---- 九宫格 ----

    public ItemStack getMaterial(int index) {
        if (index < 0 || index >= MAT_COUNT) return ItemStack.EMPTY;
        return this.menu.getSlot(MAT_BASE + index).getItem();
    }

    public void setMaterial(int index, ItemStack stack) {
        if (index < 0 || index >= MAT_COUNT) return;
        this.menu.getSlot(MAT_BASE + index).set(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    /**
     * 从九宫格拿走若干（左键 = 整格、右键 = 半格）。
     * <p>
     * ⚠️ 必须走<b>原版格子</b>的 {@code remove}：拆解台原有代码在 {@code onMiddleSlotsChanged} 里
     * 判断"九宫格被玩家动过"→ 第一次动就扣掉 1 个输入物品（`leftConsumed`），
     * 这是"拆一部分材料"的收费规则。直接改容器会绕过收费 = 凭空获得材料。
     * </p>
     */
    public ItemStack takeMaterial(int index, int amount) {
        if (index < 0 || index >= MAT_COUNT || amount <= 0) return ItemStack.EMPTY;
        net.minecraft.world.inventory.Slot slot = this.menu.getSlot(MAT_BASE + index);
        ItemStack taken = slot.remove(amount);
        // ⚠️⚠️ 必须补这一下 {@code setChanged()}：原版 {@code AbstractContainerMenu#doClick} 在每次点击
        //   结束前都会调 {@code slot.setChanged()}，拆解台"第一次动九宫格就扣掉 1 个输入物品"
        //   （{@code onMiddleSlotsChanged} 里的 leftConsumed 收费）就是靠它触发的。
        //   而 {@code Slot#remove} 只走 {@code container.removeItem}，**不会**调到槽位的 setChanged，
        //   少了它就成了"从九宫格凭空拿材料、左边输入格一点没扣"（实测，方块那边正常）。
        slot.setChanged();
        return taken;
    }

    public boolean mayPlaceMaterial(int index, ItemStack stack) {
        if (index < 0 || index >= MAT_COUNT) return false;
        return this.menu.getSlot(MAT_BASE + index).mayPlace(stack);
    }

    // ---- 产物格 ----

    public ItemStack getOutput() {
        return this.menu.getSlot(OUTPUT_SLOT).getItem();
    }

    public boolean canTakeOutput() {
        return this.menu.getSlot(OUTPUT_SLOT).mayPickup(this.player);
    }

    /** 取产物：走原版 {@code onTake}（拆解 → 材料给玩家并扣 1 个输入；合成 → 校验并消耗材料） */
    public void takeOutput(ItemStack stack) {
        this.menu.getSlot(OUTPUT_SLOT).onTake(this.player, stack);
    }

    /**
     * 取走产物（<b>照原版点击语义</b>：先把产物从槽 10 拿出来，再让原版结算）。
     * <p>
     * 为什么不能"先结算、再看槽 10 空不空"：合成成功但九宫格还有剩料时，原版会把产物重新画回槽 10
     * 作为下一条的预览，于是"槽 10 空了"这个判据永远不成立 → 材料被消耗、产物却拿不到
     * （实测：材料被消耗却拿不到产物，只有最后一次能拿出来）。
     * </p>
     *
     * @return 实际拿到的产物（空 = 现在不能取）；<b>结算成没成看 {@link #lastSettleSucceeded()}</b>，
     * 没成的话调用方必须用 {@link #putOutputBack(ItemStack)} 把产物放回去，绝不能让玩家凭空获得产物。
     */
    public ItemStack extractOutput() {
        return this.menu.extractOutputSlot(this.player);
    }

    /** 这一轮取产物到底结算成功了没有（见 {@link #extractOutput()}） */
    public boolean lastSettleSucceeded() {
        return this.menu.lastSettleSucceeded();
    }

    /** 结算失败时把产物放回槽 10（只是放回预览，不代表玩家已付过钱） */
    public void putOutputBack(ItemStack stack) {
        this.menu.setOutputSlot(stack);
    }

    // ---- 原版按钮 ----

    public boolean button(int id) {
        return this.menu.clickMenuButton(this.player, id);
    }

    // ---- 翻页信息（面板上显示"第几页/共几页"） ----

    /** 拆解模式下一共有多少种配方变体 */
    public int disassembleTotal() {
        return this.menu.getDisassembleRecipeTotal();
    }

    /** 拆解模式当前是第几个变体（从 1 开始；0 = 没有） */
    public int disassembleIndex() {
        return this.menu.getDisassembleRecipeIndex();
    }

    /** 合成模式下一共有多少条候选配方 */
    public int craftTotal() {
        return this.menu.getCraftRecipeTotal();
    }

    /** 合成模式当前是第几条配方（从 1 开始；0 = 没有） */
    public int craftIndex() {
        return this.menu.getCraftRecipeIndex();
    }
}
