package com.zzq.survival_toolbox.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 功能页的"原版逻辑引擎"（铁砧 / 锻造台）
 * <p>
 * 需求：铁砧页与锻造台页要<b>留在次元袋界面里</b>（在右侧展开一个小面板），不许像以前那样
 * 直接把玩家丢进原版界面；同时"调用原版逻辑，不自己写"。
 * </p>
 * <p>
 * 做法：在服务端构造一个不对外开放的<b>原版菜单对象</b>（{@link AnvilMenu} / {@link SmithingMenu}）当纯逻辑引擎——
 * 它不注册给玩家（玩家的 {@code containerMenu} 始终是次元袋菜单），只是被喂输入格：
 * <ul>
 *   <li>喂输入：{@code engine.getSlot(i).set(stack)} —— 原版 {@code Slot#set} 会走
 *       {@code Container#setChanged} → {@code ItemCombinerMenu#slotsChanged} → {@code createResult()}，
 *       所以产物、经验花费、耐久修复、附魔合并、模板/底座/附加的合法性<b>全是原版算的</b>；</li>
 *   <li>取产物：{@code engine.getSlot(结果格).onTake(player, stack)} —— 走原版 {@code onTake}
 *       （扣经验等级、消耗输入、消耗材料、触发 NeoForge/Forge 的 onAnvilRepair 等）；</li>
 *   <li>能不能取：{@code engine.getSlot(结果格).mayPickup(player)} —— 原版规则（铁砧要求经验够且花费 > 0）。</li>
 * </ul>
 * 1.20.1 与 1.21.1 的原版菜单结构一致（输入格 0..n、结果格最后，{@code createResult()} 为 public）。
 * </p>
 */
public final class PocketPageEngine {

    /** 铁砧页 */
    public static final int ANVIL = 0;
    /** 锻造台页 */
    public static final int SMITHING = 1;
    /** 合成页（原版工作台的逻辑：CraftingMenu + ResultSlot#onTake 里就是整套合成规则） */
    public static final int CRAFTING = 2;

    /** 引擎菜单的容器编号：取一个足够大的数，该编号不会注册给玩家，也不会用来发包 */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private final int kind;
    private final Player player;
    private final AbstractContainerMenu menu;

    public PocketPageEngine(int kind, Player player) {
        this.kind = kind;
        this.player = player;
        Inventory inventory = player.getInventory();
        if (kind == ANVIL) {
            this.menu = new AnvilMenu(NEXT_ID.getAndIncrement(), inventory);
        } else if (kind == SMITHING) {
            this.menu = new SmithingMenu(NEXT_ID.getAndIncrement(), inventory);
        } else {
            // 合成台：必须给一个"真能跑回调"的 ContainerLevelAccess，
            // 因为 CraftingMenu#slotsChanged 是在 access.execute(...) 里重算产物的，
            // 用 ContainerLevelAccess.NULL 的话回调根本不会执行（产物永远是空）
            this.menu = new net.minecraft.world.inventory.CraftingMenu(
                    NEXT_ID.getAndIncrement(), inventory,
                    net.minecraft.world.inventory.ContainerLevelAccess.create(
                            player.level(), player.blockPosition()));
        }
    }

    /** 输入格数量（铁砧 2、锻造台 3、合成台 9） */
    public int inputCount() {
        if (kind == ANVIL) return 2;
        if (kind == SMITHING) return 3;
        return 9;
    }

    /**
     * 第 index 个输入格在引擎菜单里的槽位号。
     * <p>
     * 铁砧/锻造台是"输入在前、产物在后"（{@code ItemCombinerMenu} 的布局）；
     * 原版合成台反过来：{@code CraftingMenu} 里 0 = 产物、1~9 = 九宫格 —— 这里把差异收在一处。
     * </p>
     */
    public int inputSlot(int index) {
        return kind == CRAFTING ? 1 + index : index;
    }

    /** 结果格下标（铁砧 2、锻造台 3、合成台 0） */
    public int resultSlot() {
        if (kind == ANVIL) return 2;
        if (kind == SMITHING) return 3;
        return 0;
    }

    /** 把输入喂给原版菜单（会自动触发原版 createResult） */
    public void setInput(int index, ItemStack stack) {
        if (index < 0 || index >= inputCount()) return;
        this.menu.getSlot(inputSlot(index)).set(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    /** 原版菜单里当前的输入（取完产物后原版会改它，用来同步回袋子 NBT） */
    public ItemStack getInput(int index) {
        return index >= 0 && index < inputCount()
                ? this.menu.getSlot(inputSlot(index)).getItem() : ItemStack.EMPTY;
    }

    /** 让原版重算一次产物（正常不用手动调，{@link #setInput} 会触发） */
    public void refresh() {
        if (kind == ANVIL) {
            ((AnvilMenu) this.menu).createResult();
        } else if (kind == SMITHING) {
            ((SmithingMenu) this.menu).createResult();
        }
        // 合成台：九宫格一变 CraftingMenu#slotsChanged 就已经重算过产物了，这里不用做事
    }

    /** 原版算出来的产物 */
    public ItemStack getResult() {
        return this.menu.getSlot(resultSlot()).getItem();
    }

    /** 现在能不能取（原版规则：铁砧要经验够 + 花费 > 0） */
    public boolean canTake() {
        return this.menu.getSlot(resultSlot()).mayPickup(this.player);
    }

    /** 取走产物：走原版 onTake（扣经验、消耗输入与材料） */
    public void take(ItemStack stack) {
        this.menu.getSlot(resultSlot()).onTake(this.player, stack);
    }

    /**
     * 铁砧：设置改名用的名字（"" = 不改名，用物品自己的名字）。
     * <p>
     * ⚠️ 原版 {@code AnvilMenu#setItemName} 的 {@code validateName()} 里直接
     * {@code StringUtil.filterText(name)}，<b>传 null 会 NPE 并把游戏崩掉</b>（实测出现过：
     * 一打开铁砧页就"退出游戏"）。所以这里必须把 null 转成空串。
     * </p>
     */
    public void setItemName(String name) {
        if (kind == ANVIL) {
            ((AnvilMenu) this.menu).setItemName(name == null ? "" : name);
        }
    }

    /** 铁砧：原版算出来的经验等级花费（锻造台恒为 0） */
    public int getCost() {
        return kind == ANVIL ? ((AnvilMenu) this.menu).getCost() : 0;
    }
}
