package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.util.PocketFluidCarry;
import com.zzq.survival_toolbox.util.PocketFurnace;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import com.zzq.survival_toolbox.util.PocketTrayStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.List;

/**
 * 随身次元袋菜单（多页 + 托盘版）
 * <p>
 * 槽位：0-53 存储格（54 格 = 当前显示：当前页或搜索结果），54-80 玩家背包，81-89 快捷栏，
 * 90-116 托盘（27 格，右侧可展开面板里），117-118 熔炉页（输入 + 燃料）。
 * 左侧 64px 为页列表（新建/删除/重命名/切换），顶部搜索框跨页搜索，
 * 主窗口右侧 22px 是功能页按钮条（托盘 / 整理 / 铁砧 / 锻造台 / 熔炉），点开的页面往右展开（主窗口不移动）。
 * 客户端不预测交互（服务端执行后经同步包回显），避免虚拟堆叠数据分叉。
 * </p>
 */
public class PocketDimensionMenu extends AbstractContainerMenu {

    public static final int PAGE_LIST_WIDTH = 64;
    /** 主窗口宽度（不含右侧按钮条与展开面板） */
    public static final int BASE_WIDTH = 176 + PAGE_LIST_WIDTH;
    /** 主贴图里"箱子区"的宽度：界面按这部分居中摆放（左侧页列表、右侧按钮条算挂在两边的附件），
     *  这样 GUI 里的玩家背包才会和屏幕下方的快捷栏对齐（和原版箱子/背包界面一致） */
    public static final int CHEST_WIDTH = 176;
    /** 右侧功能页按钮条宽度 */
    public static final int BUTTON_STRIP_WIDTH = 22;
    /** 托盘面板宽度：3 列 × 9 行 = 27 格，做成窄条，展开时尽量少占地方（8 + 3×18 + 8 = 68） */
    public static final int TRAY_PANEL_WIDTH = 68;
    /** 托盘的列数 / 行数（27 = 3 × 9） */
    public static final int TRAY_COLS = 3;
    public static final int TRAY_ROWS = 9;
    /** 托盘面板里第一格在主窗口坐标系里的位置 */
    public static final int TRAY_PANEL_X = BASE_WIDTH + BUTTON_STRIP_WIDTH;
    public static final int TRAY_PANEL_SLOT_X = TRAY_PANEL_X + 8;
    public static final int TRAY_PANEL_SLOT_Y = 18;

    /** 功能页编号：0 = 全部收起 */
    public static final int PANEL_NONE = 0;
    public static final int PANEL_TRAY = 1;
    /** 熔炉页（页里就两个格：输入 + 燃料；产物自动收进袋子存储） */
    public static final int PANEL_FURNACE = 2;
    /** 铁砧页（两个输入格 + 产物格 + 改名框；修复/改名/附魔合并全部由原版引擎算） */
    public static final int PANEL_ANVIL = 3;
    /** 锻造台页（模板 + 底座 + 附加 + 产物格） */
    public static final int PANEL_SMITHING = 4;
    /** 合成页（原版工作台逻辑：九宫格 + 产物格） */
    public static final int PANEL_CRAFTING = 5;
    /** 拆解台页（输入 + 九宫格 + 产物格；匹配与消耗全部由拆解台自己那套 {@code DisassembleMenu} 算） */
    public static final int PANEL_DISASSEMBLE = 6;
    /** 磁铁页（吸收：开关 / 范围 / 只吸袋里有的 / 黑白名单 + 9 格名单） */
    public static final int PANEL_MAGNET = 7;
    /** 补货页（开关 + 只补快捷栏 / 快捷栏+背包） */
    public static final int PANEL_RESTOCK = 8;

    /** 切石机页（用原版 StonecutterRecipe 当引擎） */
    public static final int PANEL_STONECUTTER = 9;

    /** 各页的面板宽度：照精妙背包那三个升级页的排布 */
    public static final int FURNACE_PANEL_WIDTH = 84;
    public static final int ANVIL_PANEL_WIDTH = 96;
    public static final int SMITHING_PANEL_WIDTH = 106;
    public static final int CRAFTING_PANEL_WIDTH = 100;
    public static final int DISASSEMBLE_PANEL_WIDTH = 130;
    public static final int MAGNET_PANEL_WIDTH = 150;
    public static final int RESTOCK_PANEL_WIDTH = 130;
    public static final int STONECUTTER_PANEL_WIDTH = 150;

    /** 某个功能页展开时，界面右边要给多宽（0 = 收起） */
    public static int panelWidth(int panel) {
        return switch (panel) {
            case PANEL_TRAY -> TRAY_PANEL_WIDTH;
            case PANEL_FURNACE -> FURNACE_PANEL_WIDTH;
            case PANEL_ANVIL -> ANVIL_PANEL_WIDTH;
            case PANEL_SMITHING -> SMITHING_PANEL_WIDTH;
            case PANEL_CRAFTING -> CRAFTING_PANEL_WIDTH;
            case PANEL_DISASSEMBLE -> DISASSEMBLE_PANEL_WIDTH;
            case PANEL_MAGNET -> MAGNET_PANEL_WIDTH;
            case PANEL_RESTOCK -> RESTOCK_PANEL_WIDTH;
            case PANEL_STONECUTTER -> STONECUTTER_PANEL_WIDTH;
            default -> 0;
        };
    }

    /** 某个功能页展开时，面板总共要占多高（够放内容就行，别撑满整窗） */
    public static int panelHeight(int panel) {
        return switch (panel) {
            case PANEL_FURNACE -> 122;
            case PANEL_ANVIL -> 80;
            case PANEL_SMITHING -> 62;
            case PANEL_CRAFTING -> 88;
            case PANEL_DISASSEMBLE -> 106;
            case PANEL_MAGNET -> 124;
            case PANEL_RESTOCK -> 54;
            case PANEL_STONECUTTER -> 170; // 原版那种大列表（7 行）
            default -> 0; // 托盘用整窗高度（界面那边自己处理）
        };
    }

    /** 槽位区间起点 */
    public static final int INVENTORY_BASE = PocketDimensionContainer.PAGE_SIZE;      // 54
    public static final int HOTBAR_BASE = INVENTORY_BASE + 27;                        // 81
    public static final int TRAY_BASE = HOTBAR_BASE + 9;                              // 90
    /** 熔炉页三个格（输入、燃料、产物） */
    public static final int FURNACE_BASE = TRAY_BASE + PocketTrayStorage.SLOTS;        // 117
    /** 铁砧页：输入1、输入2、产物 */
    public static final int ANVIL_IN_BASE = FURNACE_BASE + PocketFurnace.SLOTS;        // 120
    public static final int ANVIL_RESULT = ANVIL_IN_BASE + 2;                          // 122
    /** 锻造台页：模板、底座、附加、产物 */
    public static final int SMITH_IN_BASE = ANVIL_RESULT + 1;                          // 123
    public static final int SMITH_RESULT = SMITH_IN_BASE + 3;                          // 126
    /** 合成页：九宫格（9 格）+ 产物格 */
    public static final int CRAFT_GRID_BASE = SMITH_RESULT + 1;                        // 127
    public static final int CRAFT_RESULT = CRAFT_GRID_BASE + 9;                        // 136
    /** 拆解台页：输入格 + 九宫格（9 格）+ 产物格 */
    public static final int DIS_IN_BASE = CRAFT_RESULT + 1;                            // 137
    public static final int DIS_MAT_BASE = DIS_IN_BASE + 1;                            // 138
    public static final int DIS_RESULT = DIS_MAT_BASE + 9;                             // 147
    /** 功能页槽位的总长度（含所有页） */
    /** 磁铁页：9 格名单（幽灵条目） */
    public static final int MAG_FILTER_BASE = DIS_RESULT + 1;                          // 148

    // 切石机页：1 个输入格 + 1 个产物格（排在磁铁名单之后）
    public static final int STONE_IN_BASE = MAG_FILTER_BASE
            + com.zzq.survival_toolbox.util.PocketMagnet.FILTER_SIZE;                  // 166
    public static final int STONE_RESULT = STONE_IN_BASE + 1;                          // 167
    /** 功能页槽位的总长度（含所有页） */
    public static final int PAGE_SLOT_COUNT = MAG_FILTER_BASE + com.zzq.survival_toolbox.util.PocketMagnet.FILTER_SIZE;

    /** 各功能页的面板几何：面板都从同一个 X 开始往右展开（照精妙背包的升级页排布） */
    public static final int PAGE_PANEL_X = BASE_WIDTH + BUTTON_STRIP_WIDTH;
    public static final int PAGE_SLOT_X = PAGE_PANEL_X + 8;
    /** 熔炼页：左边一列"输入 → 火 → 燃料"，右边"箭头 → 产物格"（默认产物放格子里，按钮可切成直接进袋子） */
    public static final int FURNACE_INPUT_Y = 26;
    public static final int FURNACE_FLAME_Y = 48;
    public static final int FURNACE_FUEL_Y = 68;
    public static final int FURNACE_RESULT_X = PAGE_PANEL_X + 52;
    public static final int FURNACE_RESULT_Y = 40;
    /** 铁砧页：改名框整行在上，下面"输入 + 输入 → 产物"（原版铁砧的排布，只是紧凑了） */
    public static final int ANVIL_INPUT1_X = PAGE_PANEL_X + 8;
    public static final int ANVIL_INPUT2_X = PAGE_PANEL_X + 38;
    public static final int ANVIL_INPUT_Y = 42;
    public static final int ANVIL_RESULT_X = PAGE_PANEL_X + 70;
    public static final int ANVIL_RESULT_Y = 42;
    /** 锻造台页：模板 / 底座 / 附加 一排，右边"→ 产物"（原版锻造台的排布） */
    public static final int SMITH_INPUT1_X = PAGE_PANEL_X + 8;
    public static final int SMITH_INPUT2_X = PAGE_PANEL_X + 28;
    public static final int SMITH_INPUT3_X = PAGE_PANEL_X + 48;
    public static final int SMITH_INPUT_Y = 30;
    public static final int SMITH_RESULT_X = PAGE_PANEL_X + 80;
    public static final int SMITH_RESULT_Y = 30;
    /** 合成页：九宫格（3×3）+ 右侧产物格（原版工作台的排布，只是紧凑了） */
    public static final int CRAFT_GRID_X = PAGE_SLOT_X;
    public static final int CRAFT_GRID_Y = 26;
    public static final int CRAFT_RESULT_X = PAGE_SLOT_X + 62;
    public static final int CRAFT_RESULT_Y = 44;
    /**
     * 拆解台页：左边输入格、中间九宫格、右边产物格（原版拆解台的排布，只是紧凑了）。
     * 面板宽 {@link #DISASSEMBLE_PANEL_WIDTH}，底下一行放"上一个/下一个变体 + 全部拆解"按钮。
     */
    public static final int DIS_INPUT_X = PAGE_PANEL_X + 8;
    public static final int DIS_INPUT_Y = 44;
    public static final int DIS_MAT_X = PAGE_PANEL_X + 34;
    public static final int DIS_MAT_Y = 26;
    public static final int DIS_RESULT_X = PAGE_PANEL_X + 96;
    public static final int DIS_RESULT_Y = 44;
    /** 底部按钮行（界面上那几个小按钮的 y；菜单只用它来对齐，真正画的是 Screen） */
    public static final int DIS_BUTTON_Y = 84;
    /** 磁铁页：9 格名单（3×3）+ 下面一行按钮（开关 / 范围 −＋ / 模式 / 黑白名单） */
    public static final int MAG_FILTER_X = PAGE_PANEL_X + 8;
    public static final int MAG_FILTER_Y = 26;
    public static final int MAG_BUTTON_Y = 84;
    /** 磁铁页第二行按钮（范围 − / ＋ / 名单方向） */
    public static final int MAG_BUTTON2_Y = 102;
    /** 补货页：一行两个按钮（开关 / 范围） */
    public static final int RES_BUTTON_Y = 30;
    /**
     * 切石机页：左边输入格、右边产物格，再往右是"可用配方"列表（3 列 × 6 行）。
     * <p>
     * ⚠️ 列表顺序 = "按配方 id 排序"，客户端和服务端各自算同一份，所以选中项只传**下标**（见 SELECT 动作）。
     * </p>
     */
    public static final int STONE_INPUT_X = PAGE_PANEL_X + 8;
    public static final int STONE_INPUT_Y = 72;   // ⚠️ 必须在列表区(x=+32..+116)左侧，且别和标题/按钮重叠
    public static final int STONE_RESULT_X = PAGE_PANEL_X + 124;   // 列表右侧
    public static final int STONE_RESULT_Y = 72;
    public static final int STONE_LIST_X = PAGE_PANEL_X + 32;
    public static final int STONE_LIST_Y = 22;
    public static final int STONE_LIST_W = 84;
    public static final int STONE_LIST_H = 136;
    public static final int STONE_ROW_H = 18;
    public static final int STONE_LIST_ROWS = STONE_LIST_H / STONE_ROW_H;   // 7 行
    public static final int STONE_SCROLLBAR_W = 8;

    private final PocketDimensionContainer container;
    /**
     * "提起整格"选中的存储格（-1 = 没提起）。长按把某一格提起来，客户端自己画高亮，
     * 再左键点目标格 = 两格**整条对调**（物品条目 + 流体系目一起换，几千个也不会丢）。
     * <p>
     * ⚠️ 特意**不走**"整格放光标上"：1.20.1 同步光标数量用的是 byte，>127 会被截断。
     * </p>
     */
    private int grabbedSlot = -1;

    /** 客户端长按 → 提起 / 放下某一格（服务端权威；搜索视图只是视图，不给提） */
    public void toggleGrab(int slotId) {
        if (slotId < 0 || slotId >= PocketDimensionContainer.PAGE_SIZE) return;
        if (container.isSearching()) return;
        grabbedSlot = (grabbedSlot == slotId) ? -1 : slotId;
    }

    /** 当前提起的格（-1 = 没提起） */
    public int getGrabbedSlot() {
        return grabbedSlot;
    }

    private final PocketTrayContainer tray;
    private final PocketFurnaceContainer furnace;
    private final PocketPageContainer anvilIn;
    private final PocketResultContainer anvilOut;
    private final PocketPageContainer smithIn;
    private final PocketResultContainer smithOut;
    private final PocketPageContainer craftIn;
    private final PocketResultContainer craftOut;
    private final PocketPageContainer disIn;
    private final PocketPageContainer disMat;
    private final PocketResultContainer disOut;
    /** 切石机页：输入格（持久化在袋子 NBT 里）+ 产物格（每次按选中配方重算） */
    private final PocketPageContainer stoneIn;
    private final PocketResultContainer stoneOut;
    /** 选中的切割配方下标（在"按 id 排序的可用配方"列表里；-1 = 还没有可用配方） */
    private int stoneRecipeIndex = -1;
    /** 磁铁页的 9 格名单（幽灵条目：只读槽位 + 菜单 clicked 里处理放/删） */
    private final PocketFilterContainer magFilter;
    private final Player player;
    private final ItemStack bag;
    /** 客户端：同步来的流体格掩码（存储页） */
    private long fluidMask = 0L;
    /** 当前展开的功能页（服务端权威；客户端点击时先本地切换再发包，等同步包确认） */
    private int panel = PANEL_NONE;

    private final List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> lastSyncedSlots =
            new java.util.ArrayList<>();
    private int lastSyncedPage = 0;
    private boolean entriesDirty = true;
    private int lastSyncedPanel = -1;
    private boolean lastSyncedTrayOut = true;
    private long lastSyncedTrayMask = 0L;
    private long[] lastSyncedTrayAmounts = new long[PocketTrayStorage.SLOTS];
    /** 上一次同步出去的"托盘流体格显示栈"（变了才重发；见 PocketTrayContainer#fluidIcons） */
    private List<ItemStack> lastSyncedTrayIcons =
            new java.util.ArrayList<>(java.util.Collections.nCopies(PocketTrayStorage.SLOTS, ItemStack.EMPTY));
    private int lastSyncedFurnaceBurn = -1;
    private int lastSyncedFurnaceBurnTotal = -1;
    private int lastSyncedFurnaceCook = -1;
    private int lastSyncedFurnaceCookTotal = -1;
    /** 铁砧页：改名框里的字（服务端权威，产物由原版引擎算） */
    private String anvilName = "";
    /** 铁砧页：上一次看到的输入物品（输入换了就把改名清掉，见 refreshPageEngine 里的说明） */
    private ItemStack lastAnvilInput = ItemStack.EMPTY;
    /** 铁砧页：原版算出来的经验等级花费（同步给客户端显示） */
    private int pageCost = 0;
    private int lastSyncedPageCost = -1;
    /** 熔炼页产物去处（服务端权威，写袋子 NBT）：true = 放产物格，false = 直接进储物空间 */
    private boolean furnaceToSlot = true;
    private boolean lastSyncedFurnaceToSlot = true;
    /** 服务端：原版菜单引擎（惰性创建，绝不注册给玩家） */
    private com.zzq.survival_toolbox.util.PocketPageEngine anvilEngine;
    private com.zzq.survival_toolbox.util.PocketPageEngine smithEngine;
    private com.zzq.survival_toolbox.util.PocketPageEngine craftEngine;
    /** 拆解台页：把 mod 自己的拆解台菜单当纯逻辑引擎（不注册给玩家） */
    private com.zzq.survival_toolbox.util.PocketDisassembleEngine disEngine;
    /** 拆解台页的"第几个变体 / 共几个"：由原版槽位数据同步自动发给客户端（见 {@link PanelDataSlot}） */
    private final net.minecraft.world.inventory.DataSlot disIndexSlot = new PanelDataSlot();
    private final net.minecraft.world.inventory.DataSlot disTotalSlot = new PanelDataSlot();

    /**
     * 会同步给客户端的数字槽（页码用）。
     * <p>
     * ⚠️ 不要用 {@code DataSlot.standalone()}：它的 {@code checkAndClearUpdateFlag()} 恒为 false，
     * 原版 {@code broadcastChanges} 永远不会把它发给客户端（客户端永远是 0）。
     * 这里自己记"变了没有"，只在真的变了才让原版发一次包，不会每 tick 刷包。
     * </p>
     */
    private static final class PanelDataSlot extends net.minecraft.world.inventory.DataSlot {
        private int value;
        private boolean dirty;

        @Override
        public int get() {
            return this.value;
        }

        @Override
        public void set(int v) {
            if (v != this.value) this.dirty = true;
            this.value = v;
        }

        @Override
        public boolean checkAndClearUpdateFlag() {
            boolean d = this.dirty;
            this.dirty = false;
            return d;
        }
    }
    /**
     * 拆解页九宫格当前是不是"原版算出来的预览"（拆解模式）。
     * <p>
     * 这个标记要落盘：预览产物不是玩家的东西，如果玩家关掉界面、把输入物品从袋子里拿走，
     * 下次打开时输入槽是空的——若不区分，上次留下的预览就会被当成"玩家自己摆的材料"送给玩家。
     * </p>
     */
    private boolean disPreview;
    /** 磁铁页的开关/范围/模式/名单方向（同步给客户端显示；见 {@link PanelDataSlot}） */
    private final net.minecraft.world.inventory.DataSlot magOnSlot = new PanelDataSlot();
    private final net.minecraft.world.inventory.DataSlot magRangeSlot = new PanelDataSlot();
    private final net.minecraft.world.inventory.DataSlot magOnlySlot = new PanelDataSlot();
    private final net.minecraft.world.inventory.DataSlot magListSlot = new PanelDataSlot();
    /** 补货页的开关/范围（同步给客户端显示） */
    private final net.minecraft.world.inventory.DataSlot resOnSlot = new PanelDataSlot();
    private final net.minecraft.world.inventory.DataSlot resScopeSlot = new PanelDataSlot(); 
    /**
     * 合成页的"自动补充"开关（同步给客户端显示；参照精妙背包的同类按钮实现）。
     * <p>
     * 用 {@link PanelDataSlot}（原版数字槽同步）而不是塞进 {@code PocketDimensionSyncPacket}：
     * 数字槽是"变了才发一个包"，正好适合这种单个开关，也省得动同步包的字段
     * （动同步包就得两端一起换协议，风险大得多）。
     * </p>
     */
    private final net.minecraft.world.inventory.DataSlot craftRefillSlot = new PanelDataSlot();
    /** 引擎出过一次异常就整体停用（宁可没产物，也不许崩服务端把玩家踢出游戏） */
    private boolean pageEngineBroken;

    /** 客户端构造：从玩家主手物品读取次元袋数据 */
    public PocketDimensionMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, playerInv.player.getMainHandItem());
    }

    public PocketDimensionMenu(int windowId, Inventory playerInv, ItemStack bag) {
        super(ModMenus.POCKET_DIMENSION.get(), windowId);
        this.player = playerInv.player;
        this.bag = bag;
        boolean clientSide = playerInv.player.level().isClientSide;
        // 熔炼页产物去处跟着袋子走（读一次状态，服务端/客户端都用同步包里的值刷新）
        if (!clientSide) {
            this.furnaceToSlot = PocketFurnace.read(bag, playerInv.player.level().registryAccess()).toSlot;
        }
        this.container = new PocketDimensionContainer(bag, playerInv.player, playerInv.player.level().registryAccess());
        this.tray = new PocketTrayContainer(bag, playerInv.player.level().registryAccess(), clientSide);
        this.furnace = new PocketFurnaceContainer(bag, playerInv.player.level().registryAccess(),
                playerInv.player.level(), clientSide);
        this.anvilIn = new PocketPageContainer(bag, playerInv.player.level().registryAccess(), "AnvilIn", 2, clientSide);
        this.anvilOut = new PocketResultContainer();
        this.smithIn = new PocketPageContainer(bag, playerInv.player.level().registryAccess(), "SmithIn", 3, clientSide);
        this.smithOut = new PocketResultContainer();
        this.craftIn = new PocketPageContainer(bag, playerInv.player.level().registryAccess(), "CraftIn", 9, clientSide);
        this.craftOut = new PocketResultContainer();
        this.disIn = new PocketPageContainer(bag, playerInv.player.level().registryAccess(), "DisIn", 1, clientSide);
        this.disMat = new PocketPageContainer(bag, playerInv.player.level().registryAccess(), "DisMat", 9, clientSide);
        this.disOut = new PocketResultContainer();
        this.stoneIn = new PocketPageContainer(bag, playerInv.player.level().registryAccess(), "StoneIn", 1, clientSide);
        this.stoneOut = new PocketResultContainer();
        this.magFilter = new PocketFilterContainer(bag, playerInv.player.level().registryAccess(),
                com.zzq.survival_toolbox.util.PocketMagnet.TAG_FILTER,
                com.zzq.survival_toolbox.util.PocketMagnet.FILTER_SIZE, clientSide);
        this.addDataSlot(this.magOnSlot);
        this.addDataSlot(this.magRangeSlot);
        this.addDataSlot(this.magOnlySlot);
        this.addDataSlot(this.magListSlot);
        this.addDataSlot(this.resOnSlot);
        this.addDataSlot(this.resScopeSlot);
        this.addDataSlot(this.craftRefillSlot);
        if (!clientSide) {
            // 合成页的"自动补充"跟着袋子走：开界面时先把真实状态读进数字槽（客户端靠它显示按钮文案）
            this.craftRefillSlot.set(com.zzq.survival_toolbox.util.PocketCraftRefill
                    .read(bag, playerInv.player.level().registryAccess()) ? 1 : 0);
        }
        this.addDataSlot(this.disIndexSlot);
        this.addDataSlot(this.disTotalSlot);
        if (!clientSide) {
            net.minecraft.nbt.CompoundTag root = com.zzq.survival_toolbox.util.ItemNbt.getTag(bag);
            this.disPreview = root != null && root.getBoolean("DisPreview");
        }
        if (clientSide) {
            this.container.setClientSide(true);
        }

        // 存储格（54 格，专用槽位处理虚拟堆叠交互；贴图右移留出左侧页列表）
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new PocketSlot(container, row * 9 + col, PAGE_LIST_WIDTH + 8 + col * 18, 18 + row * 18));
            }
        }
        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, PAGE_LIST_WIDTH + 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, PAGE_LIST_WIDTH + 8 + col * 18, 198));
        }
        // 托盘格（27 格 = 3 列 × 9 行的窄条，位于右侧展开面板；面板收起时槽位 isActive()=false，原版不渲染也不可点）
        // 索引顺序 = 从上到下、每行从左到右，这样"送出从第 1 格起"的阅读顺序和视觉一致
        for (int row = 0; row < TRAY_ROWS; row++) {
            for (int col = 0; col < TRAY_COLS; col++) {
                this.addSlot(new PocketTraySlot(tray, this, row * TRAY_COLS + col,
                        TRAY_PANEL_SLOT_X + col * 18, TRAY_PANEL_SLOT_Y + row * 18));
            }
        }
        // 熔炉页三个格（输入、燃料、产物；面板收起时同样 isActive()=false 不显示也点不到）
        this.addSlot(new PocketPageSlot(furnace, this, PocketFurnace.SLOT_INPUT,
                PAGE_SLOT_X, FURNACE_INPUT_Y, PANEL_FURNACE));
        this.addSlot(new PocketPageSlot(furnace, this, PocketFurnace.SLOT_FUEL,
                PAGE_SLOT_X, FURNACE_FUEL_Y, PANEL_FURNACE));
        this.addSlot(new PocketFurnaceOutputSlot(furnace, this, FURNACE_RESULT_X, FURNACE_RESULT_Y));
        // 铁砧页：输入1 + 输入2 一排，产物在右边（取走由菜单拦截后走原版 onTake）
        this.addSlot(new PocketPageSlot(anvilIn, this, 0, ANVIL_INPUT1_X, ANVIL_INPUT_Y, PANEL_ANVIL));
        this.addSlot(new PocketPageSlot(anvilIn, this, 1, ANVIL_INPUT2_X, ANVIL_INPUT_Y, PANEL_ANVIL));
        this.addSlot(new PocketPageSlot(anvilOut, this, 0, ANVIL_RESULT_X, ANVIL_RESULT_Y, PANEL_ANVIL, true));
        // 锻造台页：模板 / 底座 / 附加 一排，产物在右边
        this.addSlot(new PocketPageSlot(smithIn, this, 0, SMITH_INPUT1_X, SMITH_INPUT_Y, PANEL_SMITHING));
        this.addSlot(new PocketPageSlot(smithIn, this, 1, SMITH_INPUT2_X, SMITH_INPUT_Y, PANEL_SMITHING));
        this.addSlot(new PocketPageSlot(smithIn, this, 2, SMITH_INPUT3_X, SMITH_INPUT_Y, PANEL_SMITHING));
        this.addSlot(new PocketPageSlot(smithOut, this, 0, SMITH_RESULT_X, SMITH_RESULT_Y, PANEL_SMITHING, true));
        // 合成页：九宫格 9 格 + 产物格（产物取走走原版 ResultSlot#onTake，即整套原版合成规则）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new PocketPageSlot(craftIn, this, row * 3 + col,
                        CRAFT_GRID_X + col * 18, CRAFT_GRID_Y + row * 18, PANEL_CRAFTING));
            }
        }
        this.addSlot(new PocketPageSlot(craftOut, this, 0, CRAFT_RESULT_X, CRAFT_RESULT_Y, PANEL_CRAFTING, true));
        // 拆解台页：左输入 + 中九宫格 + 右产物格。
        // 拆解模式（输入槽有东西）下九宫格是原版算出来的"材料预览"，只能看不能动（readOnly），
        // 否则玩家把预览拿走就等于刷物品；合成模式（输入槽空）下它才是玩家自己的合成格。
        this.addSlot(new PocketPageSlot(disIn, this, 0, DIS_INPUT_X, DIS_INPUT_Y, PANEL_DISASSEMBLE));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                this.addSlot(new PocketPageSlot(disMat, this, index,
                        DIS_MAT_X + col * 18, DIS_MAT_Y + row * 18, PANEL_DISASSEMBLE, false,
                        () -> !this.disIn.getItem(0).isEmpty()));
            }
        }
        this.addSlot(new PocketPageSlot(disOut, this, 0, DIS_RESULT_X, DIS_RESULT_Y, PANEL_DISASSEMBLE, true));
        // 磁铁页：9 格名单（幽灵条目：槽位只读，放/删全由 clicked 拦截，绝不把实物留在名单里）
        for (int i = 0; i < com.zzq.survival_toolbox.util.PocketMagnet.FILTER_SIZE; i++) {
            this.addSlot(new PocketPageSlot(magFilter, this, i,
                    MAG_FILTER_X + (i % 6) * 18, MAG_FILTER_Y + (i / 6) * 18, PANEL_MAGNET, true));
        }
        // 切石机页：输入 + 产物
        this.addSlot(new PocketPageSlot(stoneIn, this, 0, STONE_INPUT_X, STONE_INPUT_Y, PANEL_STONECUTTER));
        this.addSlot(new PocketPageSlot(stoneOut, this, 0, STONE_RESULT_X, STONE_RESULT_Y, PANEL_STONECUTTER, true));
    }

    public PocketDimensionContainer getPocketContainer() {
        return container;
    }

    /** 托盘容器（界面读方向/流体数量/格数统计都走它） */
    public PocketTrayContainer getTray() {
        return tray;
    }

    /** 熔炉容器（界面画进度、读两个格都走它） */
    public PocketFurnaceContainer getFurnace() {
        return furnace;
    }

    /** 铁砧页的输入格（界面画格子的统计/提示用） */
    public PocketPageContainer getAnvilInputs() {
        return anvilIn;
    }

    /** 铁砧页的产物格 */
    public PocketResultContainer getAnvilResult() {
        return anvilOut;
    }

    /** 锻造台页的输入格 */
    public PocketPageContainer getSmithingInputs() {
        return smithIn;
    }

    /** 锻造台页的产物格 */
    public PocketResultContainer getSmithingResult() {
        return smithOut;
    }

    /** 合成页的九宫格 */
    public PocketPageContainer getCraftingInputs() {
        return craftIn;
    }

    /** 合成页的产物格 */
    public PocketResultContainer getCraftingResult() {
        return craftOut;
    }

    /** 拆解台页的输入格 */
    public PocketPageContainer getDisassembleInputs() {
        return disIn;
    }

    /** 拆解台页的九宫格（拆解模式下是原版算出来的材料预览） */
    public PocketPageContainer getDisassembleGrid() {
        return disMat;
    }

    /** 拆解台页的产物格 */
    /** 切石机页当前放在输入格里的东西（客户端筛配方列表要用） */
    public ItemStack getStonecutterInput() {
        return stoneIn.getItem(0);
    }

    public PocketResultContainer getDisassembleResult() {
        return disOut;
    }

    /** 拆解页现在是拆解模式（输入槽有东西）还是合成模式 */
    public boolean isDisassemblePreview() {
        return disPreview;
    }

    /** 拆解页当前页码（客户端显示；1 起，0 = 没有） */
    public int getDisPageIndex() {
        return this.disIndexSlot.get();
    }

    /** 拆解页总页数（客户端显示） */
    public int getDisPageTotal() {
        return this.disTotalSlot.get();
    }

    /** 磁铁页的名单容器（界面画格数/提示用） */
    public PocketFilterContainer getMagnetFilter() {
        return magFilter;
    }

    /** 磁铁开没开（客户端按钮文案用；服务端权威） */
    public boolean isMagnetOn() {
        return this.magOnSlot.get() != 0;
    }

    /** 磁铁范围（格） */
    public int getMagnetRange() {
        return this.magRangeSlot.get();
    }

    /** 只吸袋里有的（true）还是什么都吸（false） */
    public boolean isMagnetOnlyExisting() {
        return this.magOnlySlot.get() != 0;
    }

    /** 白名单（true）还是黑名单（false） */
    public boolean isMagnetWhiteList() {
        return this.magListSlot.get() != 0;
    }

    /** 服务端：把磁铁状态读进 DataSlot（客户端按钮文案/数字靠它显示） */
    private void refreshMagnet() {
        if (player.level().isClientSide) return;
        com.zzq.survival_toolbox.util.PocketMagnet.State st =
                com.zzq.survival_toolbox.util.PocketMagnet.read(bag, player.level().registryAccess());
        this.magOnSlot.set(st.on ? 1 : 0);
        this.magRangeSlot.set(st.range);
        this.magOnlySlot.set(st.onlyExisting ? 1 : 0);
        this.magListSlot.set(st.whiteList ? 1 : 0);
    }

    /** 补货开没开（客户端按钮文案用；服务端权威） */
    public boolean isRestockOn() {
        return this.resOnSlot.get() != 0;
    }

    /** 补货是不是"只补快捷栏" */
    public boolean isRestockHotbarOnly() {
        return this.resScopeSlot.get() != 0;
    }

    /** 服务端：把补货状态读进 DataSlot */
    private void refreshRestock() {
        if (player.level().isClientSide) return;
        com.zzq.survival_toolbox.util.PocketRestock.State st =
                com.zzq.survival_toolbox.util.PocketRestock.read(bag, player.level().registryAccess());
        this.resOnSlot.set(st.on ? 1 : 0);
        this.resScopeSlot.set(st.hotbarOnly ? 1 : 0);
    }

    /** 服务端：改补货状态并写回袋子 NBT */
    private void updateRestock(java.util.function.Consumer<com.zzq.survival_toolbox.util.PocketRestock.State> action) {
        com.zzq.survival_toolbox.util.PocketRestock.State st =
                com.zzq.survival_toolbox.util.PocketRestock.read(bag, player.level().registryAccess());
        action.accept(st);
        com.zzq.survival_toolbox.util.PocketRestock.write(bag, st, player.level().registryAccess());
        refreshRestock();
    }

    /** 服务端：改磁铁状态并写回袋子 NBT */
    private void updateMagnet(java.util.function.Consumer<com.zzq.survival_toolbox.util.PocketMagnet.State> action) {
        com.zzq.survival_toolbox.util.PocketMagnet.State st =
                com.zzq.survival_toolbox.util.PocketMagnet.read(bag, player.level().registryAccess());
        action.accept(st);
        com.zzq.survival_toolbox.util.PocketMagnet.write(bag, st, player.level().registryAccess());
        refreshMagnet();
    }

    /** 合成页"自动补充"开着吗（客户端画按钮文案用；服务端权威） */
    public boolean isCraftRefillOn() {
        return this.craftRefillSlot.get() != 0;
    }

    /** 客户端：本地先翻一下（点按钮立刻有反应），服务端同步包随后确认 */
    public void applyCraftRefillLocal(boolean on) {
        this.craftRefillSlot.set(on ? 1 : 0);
    }

    /** 服务端：把"自动补充"的真实状态读进数字槽 */
    private void refreshCraftRefill() {
        if (player.level().isClientSide) return;
        this.craftRefillSlot.set(com.zzq.survival_toolbox.util.PocketCraftRefill
                .read(bag, player.level().registryAccess()) ? 1 : 0);
    }

    /** 服务端：翻转"自动补充"开关并写回袋子 NBT（客户端只发动作包，状态服务端权威） */
    private void toggleCraftRefill() {
        if (player.level().isClientSide) return;
        boolean on = !com.zzq.survival_toolbox.util.PocketCraftRefill
                .read(bag, player.level().registryAccess());
        com.zzq.survival_toolbox.util.PocketCraftRefill.write(bag, on, player.level().registryAccess());
        refreshCraftRefill();
    }

    /** 铁砧页改名框里的字（客户端显示用） */
    public String getAnvilName() {
        return anvilName;
    }

    /** 服务端：改名框的文字（来自客户端包）；空 = 不改名 */
    public void setAnvilName(String name) {
        String text = name == null ? "" : name;
        if (text.length() > com.zzq.survival_toolbox.network.PocketPageTextPacket.MAX_LENGTH) {
            text = text.substring(0, com.zzq.survival_toolbox.network.PocketPageTextPacket.MAX_LENGTH);
        }
        if (text.equals(this.anvilName)) return;
        this.anvilName = text;
        if (!player.level().isClientSide) {
            // ⚠️ 先把"输入物品变了"这件事**记录下来**，再刷新引擎：
            //    refreshPageEngine 里有一句"输入物品变了 → 自动把名字填成物品本名"，
            //    若此刻 lastAnvilInput 还停在上一件东西上，它就会把玩家**刚输入的名字当场覆盖**，
            //    于是花费算 0、产物为空、点了拿不出来（实测：换输入与紧接着输入名字在同一瞬间发生即会命中）。
            //    玩家既然明确发来了名字，就以玩家发的为准。
            ItemStack in0 = anvilIn.getItem(0);
            lastAnvilInput = in0.isEmpty() ? ItemStack.EMPTY : in0.copy();
            refreshPageEngine(PANEL_ANVIL);
        }
    }

    /** 铁砧页原版算出来的经验等级花费（客户端显示用） */
    public int getPageCost() {
        return pageCost;
    }

    /** 熔炼页产物去处：true = 放产物格（默认），false = 直接进储物空间（客户端画按钮文字用） */
    public boolean isFurnaceToSlot() {
        return furnaceToSlot;
    }

    /** 服务端：切换熔炼页产物去处（写袋子 NBT，跟着袋子走） */
    public void setFurnaceOutputToSlot(boolean toSlot) {
        if (this.furnaceToSlot == toSlot) return;
        this.furnaceToSlot = toSlot;
        if (!player.level().isClientSide) {
            PocketFurnace.setToSlot((net.minecraft.server.level.ServerPlayer) player, bag, toSlot,
                    player.level().registryAccess());
            this.furnace.refreshFromBag();
        }
    }

    /** 客户端：本地先切产物去处（点按钮立刻有反应），服务端同步包随后确认 */
    public void applyFurnaceToSlotLocal(boolean toSlot) {
        this.furnaceToSlot = toSlot;
    }

    /** 服务端：玩家从产物格把东西拿走了 → 把攒着的经验结算给他（原版熔炉也是取产物时给经验） */
    public void onFurnaceOutputTaken(Player player) {
        if (player.level().isClientSide) return;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PocketFurnace.awardXp(sp, bag, player.level().registryAccess());
        }
        this.furnace.refreshFromBag();
    }

    /** 这个菜单是不是在用这个袋子（熔炉等服务端逻辑要判断"界面正开着吗"） */
    public boolean isForBag(ItemStack other) {
        return this.bag == other;
    }

    /**
     * 服务端：把东西收进存储。
     * <p>
     * ⚠️ 必须走这里、而不是 {@code PocketStorageHelper#quickDeposit}：界面开着时页数据在
     * {@link PocketDimensionContainer} 里有一份缓存，绕过缓存直接写袋子 NBT 的话，
     * 下一次保存会被旧缓存整份覆盖 —— 表现就是"东西放进去就没了"（曾出现过）。
     * </p>
     *
     * @return 剩余数量（恒为 0）
     */
    public long depositToStorage(ItemStack stack) {
        return container.quickDepositCached(stack);
    }

    /**
     * 服务端：从存储里<b>取料</b>（走本界面的页缓存）。
     * <p>
     * ⚠️ 必须走这里、而不是 {@code PocketStorageHelper#withdrawFromStorage} 直接写 NBT：
     * 界面开着时页数据在 {@link PocketDimensionContainer} 里有一份缓存，
     * 绕过缓存扣减的话，下一次保存会被旧缓存整份盖回来 ——
     * 表现就是"材料取出来了、袋子里一个没少"，也就是 JEI 的 + <b>凭空复制材料</b>（历史缺陷）。
     * </p>
     *
     * @param template  要取的物品（数量会被忽略）
     * @param count     想取多少个
     * @param pageIndex 页下标；{@link PocketStorageHelper#PAGE_ALL} = 所有页
     */
    public ItemStack withdrawFromStorage(ItemStack template, int count, int pageIndex) {
        ItemStack got = container.withdrawCached(template, count, pageIndex);
        if (!got.isEmpty()) this.entriesDirty = true;
        return got;
    }

    /**
     * 服务端/客户端：界面"当前显示的那一页"页号（取料与匹配只认这一页）。
     * <p>
     * 判定规则：<b>打开哪个就匹配哪个</b>。搜索视图是例外的——它本来就是"把所有页的结果摊开看"，
     * 这时候玩家看到的条目可能来自任意一页，所以按"所有页"处理才和他眼前的画面一致。
     * </p>
     */
    public int getDisplayedPageIndex() {
        if (container.isSearching()) return PocketStorageHelper.PAGE_ALL;
        return container.getCurrentPage();
    }

    /**
     * 服务端：把流体收进存储（同样必须走容器缓存，理由见 {@link #depositToStorage}）。
     *
     * @return 剩余数量（袋子无限容量，恒为 0）
     */
    public long depositFluidToStorage(net.neoforged.neoforge.fluids.FluidStack stack) {
        long need = container.insertFluid(stack);
        this.entriesDirty = true;
        return need;
    }

    /**
     * 外部入库（磁铁 / 托盘 / 快速收纳…）要不要改走本界面的页缓存？
     * <p>
     * 判据是"<b>是不是同一份存储</b>"，而不是"是不是同一个袋子实例"：
     * 共享模式下同一个玩家的所有袋子共用一份共享空间，所以哪怕玩家开的是 A 袋、入库的是 B 袋，
     * 也必须走这份缓存 —— 否则 A 界面一保存就把 B 刚入库的东西整份盖掉，
     * 表现就是"东西被吸进去了，打开袋子什么都没有"（历史问题：物品丢失）。
     * </p>
     */
    public boolean shouldRouteDeposit(ItemStack other) {
        if (other == null || other.isEmpty()) return false;
        if (this.bag == other) return true;
        return PocketStorageHelper.isShared(this.bag) && PocketStorageHelper.isShared(other);
    }

    /**
     * 服务端：把东西收进"该玩家当时开着的次元袋界面"的存储；没开界面就正常按袋子自己的模式写。
     * <p>
     * 给熔炉这类"界面外也要干活"的逻辑用（炉子每 tick 都可能出产物）。
     * </p>
     */
    public static long depositToOpenMenu(net.minecraft.server.level.ServerPlayer player,
                                        ItemStack bag, ItemStack stack) {
        if (player.containerMenu instanceof PocketDimensionMenu menu && menu.shouldRouteDeposit(bag)) {
            return menu.depositToStorage(stack);
        }
        return PocketStorageHelper.quickDeposit(player, bag, stack, player.level().registryAccess());
    }

    // ============================================================
    // 功能页（面板）状态
    // ============================================================

    /** 指定功能页是否展开（托盘槽位的 isActive 也用它） */
    public boolean isPanelOpen(int which) {
        return panel == which;
    }

    /** 当前展开的功能页编号（0 = 全收起；界面用它判断布局要不要重建） */
    public int getPanel() {
        return panel;
    }

    /** 托盘面板是否展开 */
    public boolean isTrayOpen() {
        return panel == PANEL_TRAY;
    }

    /** 熔炉页是否展开 */
    public boolean isFurnaceOpen() {
        return panel == PANEL_FURNACE;
    }

    /** 是否有任一功能页展开（界面据此决定主窗口右边要不要留出面板宽度） */
    public boolean isAnyPanelOpen() {
        return panel != PANEL_NONE;
    }

    /** 客户端：本地先切面板（点按钮立刻有反应），服务端同步包随后确认 */
    public void applyPanelLocal(int wanted) {
        this.panel = wanted;
    }

    // ============================================================
    // 铁砧页 / 锻造台页：原版菜单当"引擎"
    // ============================================================

    /** 服务端：惰性创建原版引擎（铁砧 / 锻造台 / 合成台菜单对象，不注册给玩家，只用来算） */
    private com.zzq.survival_toolbox.util.PocketPageEngine engine(int kind) {
        if (kind == com.zzq.survival_toolbox.util.PocketPageEngine.ANVIL) {
            if (anvilEngine == null) {
                anvilEngine = new com.zzq.survival_toolbox.util.PocketPageEngine(
                        com.zzq.survival_toolbox.util.PocketPageEngine.ANVIL, player);
            }
            return anvilEngine;
        }
        if (kind == com.zzq.survival_toolbox.util.PocketPageEngine.SMITHING) {
            if (smithEngine == null) {
                smithEngine = new com.zzq.survival_toolbox.util.PocketPageEngine(
                        com.zzq.survival_toolbox.util.PocketPageEngine.SMITHING, player);
            }
            return smithEngine;
        }
        if (craftEngine == null) {
            craftEngine = new com.zzq.survival_toolbox.util.PocketPageEngine(
                    com.zzq.survival_toolbox.util.PocketPageEngine.CRAFTING, player);
        }
        return craftEngine;
    }

    /** 服务端：拆解页的引擎（mod 自己的拆解台菜单对象，不注册给玩家，只用来算） */
    private com.zzq.survival_toolbox.util.PocketDisassembleEngine disassembleEngine() {
        if (disEngine == null) {
            disEngine = new com.zzq.survival_toolbox.util.PocketDisassembleEngine(player);
        }
        return disEngine;
    }

    /**
     * 拆解页：把引擎（原版拆解台那套逻辑）与页面格子对齐。仅服务端有效。
     * <ul>
     *   <li>合成模式（输入槽空）：九宫格是玩家自己摆的材料 → 喂给引擎，引擎算出产物 → 拉回产物格；</li>
     *   <li>拆解模式（输入槽有东西）：输入喂给引擎，原版自己匹配配方并把材料预览填进九宫格 → 拉回九宫格与产物格。</li>
     * </ul>
     */
    // ============================================================
    // 切石机页（引擎 = 原版 StonecutterRecipe，规则全用原版）
    // ============================================================

    /**
     * 当前输入能切的配方，按**配方 id 排序**。
     * <p>
     * ⚠️ 顺序必须和客户端算的一模一样（客户端也是"筛 + 按 id 排序"），这样选中项只传下标就不会错位；
     * 万一下标对不上，下面还会用 {@code test(input)} 再确认一次，宁可没产物也不给错东西。
     * </p>
     */
    private List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe>>
    stonecutterCandidates(ItemStack input) {
        List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe>> out =
                new java.util.ArrayList<>();
        if (input.isEmpty()) return out;
        for (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe> holder
                : player.level().getRecipeManager()
                        .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING)) {
            var recipe = holder.value();
            var ingredients = recipe.getIngredients();
            if (!ingredients.isEmpty() && ingredients.get(0).test(input)) {
                out.add(holder);
            }
        }
        out.sort(java.util.Comparator.comparing(h -> h.id().toString()));
        return out;
    }

    /** 客户端点了配方列表里的第 N 条（服务端权威：产物由这里给） */
    public void selectStonecutter(int index) {
        if (player.level().isClientSide) return;
        this.stoneRecipeIndex = index;
        refreshStonecutter();
    }

    /** 按"输入 + 选中配方"重算产物格（服务端；输入变了/换页/取走一次之后都要调） */
    private void refreshStonecutter() {
        if (player.level().isClientSide) return;
        List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.StonecutterRecipe>> list =
                stonecutterCandidates(stoneIn.getItem(0));
        if (stoneRecipeIndex < 0 || stoneRecipeIndex >= list.size()) {
            // 没选过（刚打开页）或输入换了导致下标越界：自动挑第一条（客户端也是这个默认）
            stoneRecipeIndex = list.isEmpty() ? -1 : 0;
        }
        stoneOut.setResult(list.isEmpty()
                ? ItemStack.EMPTY
                : list.get(stoneRecipeIndex).value().getResultItem(player.level().registryAccess()).copy());
    }

    /**
     * 取走切石机页的产物：按原版切石机规则，取一次消耗 1 个输入（产物本身照旧是"光标拿着/整叠进袋子"）。
     */
    private void clickedStonecutterResult(ClickType clickType, Player player) {
        refreshStonecutter();
        ItemStack result = stoneOut.getItem(0).copy();
        if (result.isEmpty() || stoneIn.getItem(0).isEmpty()) return;

        if (clickType == ClickType.QUICK_MOVE) {
            // Shift+左键：直接进袋子存储；收不进去就不做（东西不会丢）
            if (depositToStorage(result) > 0) return;
        } else {
            ItemStack cursor = getCarried();
            if (!cursor.isEmpty()) {
                if (!PocketStorageHelper.sameItem(cursor, result)
                        || cursor.getCount() + result.getCount() > cursor.getMaxStackSize()) {
                    return; // 光标上有别的东西或放不下：什么都不做
                }
                cursor.grow(result.getCount());
                setCarried(cursor);
            } else {
                setCarried(result);
            }
        }

        stoneIn.removeItem(0, 1);
        stoneIn.setChanged();
        refreshStonecutter();                 // 输入少了一个：产物按原版重新算（原版切石机也是这样）
    }

    private void refreshDisassemble() {
        if (player.level().isClientSide) return;
        com.zzq.survival_toolbox.util.PocketDisassembleEngine e = disassembleEngine();
        e.tick();
        boolean preview = !disIn.getItem(0).isEmpty();
        if (!preview) {
            // 合成模式：把玩家摆的材料喂进去（只有真的变了才喂，避免重复计算配方）
            for (int i = 0; i < com.zzq.survival_toolbox.util.PocketDisassembleEngine.MAT_COUNT; i++) {
                ItemStack mine = disMat.getItem(i);
                ItemStack theirs = e.getMaterial(i);
                if (!PocketStorageHelper.sameItem(mine, theirs) || mine.getCount() != theirs.getCount()) {
                    e.setMaterial(i, mine);
                }
            }
        }
        ItemStack mine = disIn.getItem(0);
        ItemStack theirs = e.getInput();
        if (!PocketStorageHelper.sameItem(mine, theirs) || mine.getCount() != theirs.getCount()) {
            e.setInput(mine);
        }
        mirrorDisassemble(e, preview);
    }

    /** 把引擎里的拆解页状态拉回页面格子（并落盘 / 把翻页信息发给客户端） */
    private void mirrorDisassemble(com.zzq.survival_toolbox.util.PocketDisassembleEngine e) {
        mirrorDisassemble(e, !disIn.getItem(0).isEmpty());
    }

    private void mirrorDisassemble(com.zzq.survival_toolbox.util.PocketDisassembleEngine e, boolean preview) {
        // ⚠️ 页格子的 setItem 会立刻把内容写进袋子 NBT，所以"内容没变就别写"：
        // 这里每 tick 都会被调一次，无条件写 = 每 tick 落盘 11 个格（手持袋子会反复同步）
        setIfChanged(disIn, 0, e.getInput());
        for (int i = 0; i < com.zzq.survival_toolbox.util.PocketDisassembleEngine.MAT_COUNT; i++) {
            setIfChanged(disMat, i, e.getMaterial(i));
        }
        disOut.setResult(e.getOutput());
        if (preview) {
            this.disIndexSlot.set(e.disassembleIndex());
            this.disTotalSlot.set(e.disassembleTotal());
        } else {
            this.disIndexSlot.set(e.craftIndex());
            this.disTotalSlot.set(e.craftTotal());
        }
        setDisPreviewFlag(preview);
    }

    /** 只有内容和数量真的变了才写页容器（避免每 tick 往袋子 NBT 里写一遍） */
    private static void setIfChanged(PocketPageContainer target, int slot, ItemStack stack) {
        ItemStack now = target.getItem(slot);
        if (PocketStorageHelper.sameItem(now, stack) && now.getCount() == stack.getCount()) return;
        target.setItem(slot, stack);
    }

    /**
     * 记录"九宫格现在是拆解预览还是玩家自己的材料"（写袋子 NBT）。
     * <p>
     * 见 {@link #disPreview} 的说明：不记的话，"预览留下、输入被拿走"会让预览变成凭空获得的材料。
     * 只有变化时才写 NBT，免得每 tick 都动一次袋子数据（会引发布袋每 tick 重新同步、拿在手里时反复刷新）。
     * </p>
     */
    private void setDisPreviewFlag(boolean preview) {
        if (preview == this.disPreview) return;
        this.disPreview = preview;
        net.minecraft.nbt.CompoundTag root = com.zzq.survival_toolbox.util.ItemNbt.copyForEdit(bag);
        root.putBoolean("DisPreview", preview);
        com.zzq.survival_toolbox.util.ItemNbt.setTag(bag, root);
    }

    /**
     * 铁砧页：输入物品是不是"换了一个东西"（换物品 / 拿走物品才算，改名不算）。
     * <p>
     * ⚠️⚠️ <b>绝不能按 NBT 全等判断</b>（这里以前用的就是 {@code sameItem}）：
     * 袋子里的物品每隔一会儿就会被序列化写回袋子 NBT、再读回来，round-trip 出来的 NBT
     * （键顺序、补全出来的默认键）和手里那份不保证字节一致，于是 NBT 全等会时不时判成"变了"，
     * 把玩家刚输入的改名<b>冲回物品本名</b>。而原版铁砧那边：名字 == 物品本名 = 没改名，
     * 花费算 0 → 产物直接是空 —— 实测表现就是"输入名字后，改名后的武器拿不出来"。
     * </p>
     * <p>客户端 {@code PocketDimensionScreen#sameAnvilInput} 用的是同一套判定，两边必须一致。</p>
     */
    private boolean anvilInputChanged(ItemStack in0) {
        if (in0.isEmpty() != lastAnvilInput.isEmpty()) return true;
        if (in0.isEmpty()) return false;
        if (!in0.is(lastAnvilInput.getItem())) return true;
        return !in0.getHoverName().getString().equals(lastAnvilInput.getHoverName().getString());
    }

    /**
     * 服务端：把页里的输入喂给原版引擎、把原版算出来的产物发到产物格。仅服务端有效。
     * <p>
     * 只有内容真的变了才喂（原版 {@code createResult} 每次都要算附魔/耐久，不要每 tick 重复计算）；
     * 名字每次都设一遍（原版自己会判断有没有变化）。
     * </p>
     */
    private void refreshPageEngine(int panel) {
        if (player.level().isClientSide) return;
        if (pageEngineBroken) return;
        try {
            if (panel == PANEL_ANVIL) {
                com.zzq.survival_toolbox.util.PocketPageEngine e =
                        engine(com.zzq.survival_toolbox.util.PocketPageEngine.ANVIL);
                // ⚠️ 改名跟着**输入物品**走（原版铁砧就是这样：AnvilScreen#slotChanged 会把框重填成物品本名，
                //    AnvilMenu 那边 itemName 也就不再适用于新物品）。换物品 / 把物品拿走 = 重填/清空。
                //    以前是"关掉铁砧页就清空 anvilName"，副作用是玩家刚输入的名字、误点页按钮后就丢失；
                //    而且刚放上物品时框是空的（实测"放进去物品不会显示名称"）。
                //    这里填"物品本名"是安全的：名字 == 物品本名时原版不算改名、不额外扣经验（见 AnvilMenu#createResult）。
                ItemStack in0 = anvilIn.getItem(0);
                if (anvilInputChanged(in0)) {
                    lastAnvilInput = in0.isEmpty() ? ItemStack.EMPTY : in0.copy();
                    anvilName = in0.isEmpty() ? "" : in0.getHoverName().getString();
                    if (anvilName.length() > com.zzq.survival_toolbox.network.PocketPageTextPacket.MAX_LENGTH) {
                        anvilName = anvilName.substring(0, com.zzq.survival_toolbox.network.PocketPageTextPacket.MAX_LENGTH);
                    }
                }
                feedEngine(e, anvilIn);
                e.setItemName(anvilName);
                anvilOut.setResult(e.getResult());
                pageCost = e.getCost();
            } else if (panel == PANEL_SMITHING) {
                com.zzq.survival_toolbox.util.PocketPageEngine e =
                        engine(com.zzq.survival_toolbox.util.PocketPageEngine.SMITHING);
                feedEngine(e, smithIn);
                smithOut.setResult(e.getResult());
            } else if (panel == PANEL_CRAFTING) {
                com.zzq.survival_toolbox.util.PocketPageEngine e =
                        engine(com.zzq.survival_toolbox.util.PocketPageEngine.CRAFTING);
                feedEngine(e, craftIn);
                craftOut.setResult(e.getResult());
            } else if (panel == PANEL_DISASSEMBLE) {
                refreshDisassemble();
            } else if (panel == PANEL_STONECUTTER) {
                refreshStonecutter();
            }
        } catch (Throwable t) {
            // 这里是"借用原版菜单对象"算产物，万一碰上别的 mod 或版本差异把原版实现改了，
            // 宁可这一页不显示产物，也绝不能把服务端线程带走（单人模式下 = 直接退出游戏）
            pageEngineBroken = true;
            anvilOut.setResult(ItemStack.EMPTY);
            smithOut.setResult(ItemStack.EMPTY);
            craftOut.setResult(ItemStack.EMPTY);
            disOut.setResult(ItemStack.EMPTY);
            pageCost = 0;
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 铁砧/锻造台/合成/拆解页的原版引擎刷新失败，已停用这几页（详见堆栈）", t);
        }
    }

    /** 把页容器里变了的格子喂给原版引擎（原版自己会重算产物） */
    private static void feedEngine(com.zzq.survival_toolbox.util.PocketPageEngine engine,
                                   PocketPageContainer container) {
        for (int i = 0; i < engine.inputCount(); i++) {
            ItemStack mine = container.getItem(i);
            ItemStack theirs = engine.getInput(i);
            if (!PocketStorageHelper.sameItem(mine, theirs) || mine.getCount() != theirs.getCount()) {
                engine.setInput(i, mine);
            }
        }
    }

    /** 原版 {@code onTake} 会改输入格（消耗材料/清空），把它拉回页容器并落盘 */
    private static void pullFromEngine(com.zzq.survival_toolbox.util.PocketPageEngine engine,
                                       PocketPageContainer container) {
        for (int i = 0; i < engine.inputCount(); i++) {
            container.setItem(i, engine.getInput(i));
        }
    }

    /**
     * 取走铁砧/锻造台的产物。
     * <p>
     * <b>能不能取、取了扣什么，全部由原版判定</b>：能取 = 原版结果格的 {@code mayPickup}
     * （铁砧要求经验够且花费 > 0），取走 = 原版结果格的 {@code onTake}（扣经验等级、消耗输入与材料）。
     * 左键 = 拿到光标（原版手感），Shift+左键 = 直接收进袋子存储（省得再拖回去）。
     * </p>
     */
    private void clickedPageResult(int slotId, ClickType clickType, Player player) {
        if (pageEngineBroken) return;
        int panel;
        int kind;
        PocketPageContainer inputs;
        PocketResultContainer out;
        if (slotId == ANVIL_RESULT) {
            panel = PANEL_ANVIL;
            kind = com.zzq.survival_toolbox.util.PocketPageEngine.ANVIL;
            inputs = anvilIn;
            out = anvilOut;
        } else if (slotId == SMITH_RESULT) {
            panel = PANEL_SMITHING;
            kind = com.zzq.survival_toolbox.util.PocketPageEngine.SMITHING;
            inputs = smithIn;
            out = smithOut;
        } else {
            panel = PANEL_CRAFTING;
            kind = com.zzq.survival_toolbox.util.PocketPageEngine.CRAFTING;
            inputs = craftIn;
            out = craftOut;
        }
        boolean anvil = kind == com.zzq.survival_toolbox.util.PocketPageEngine.ANVIL;
        if (clickType == ClickType.QUICK_MOVE) {
            // ⚠️ 需求：工作台方块上 Shift 批量拿产物的行为，袋子里同样适用。
            //    原版工作台的 Shift+左键是**一直做到材料用完**（反复取产物），这里以前只做一次，
            //    所以袋子里只能一个个拿。上限是防呆：万一某个配方取走产物却不消耗材料，避免把服务端卡死。
            int made = 0;
            while (made < MAX_BATCH_TAKE
                    && takePageResultOnce(panel, kind, inputs, anvil, true, player)) {
                made++;
            }
            return;
        }
        takePageResultOnce(panel, kind, inputs, anvil, false, player);
    }

    /** Shift+左键批量取产物的次数上限（防呆，正常配方材料用完就自己停了） */
    private static final int MAX_BATCH_TAKE = 64;

    /**
     * 取一次产物。
     * <p>
     * <b>能不能取、取了扣什么，全部由原版判定</b>：能取 = 原版结果格的 {@code mayPickup}
     * （铁砧要求经验够且花费 > 0），取走 = 原版结果格的 {@code onTake}（扣经验等级、消耗输入与材料）。
     * </p>
     *
     * @param toStorage true = 直接收进储物空间（Shift+左键，批量就是连着调它）
     * @return 真的取走了一次吗（false = 产物为空 / 不能取 / 袋子收不下）
     */
    private boolean takePageResultOnce(int panel, int kind, PocketPageContainer inputs, boolean anvil,
                                       boolean toStorage, Player player) {
        try {
            refreshPageEngine(panel);
            com.zzq.survival_toolbox.util.PocketPageEngine e = engine(kind);
            ItemStack result = e.getResult();
            if (result.isEmpty() || !e.canTake()) return false;

            ItemStack taken = result.copy();
            // 合成页的"自动补充"：先照一张九宫格快照（每格一份 copy），等原版扣完材料再照着它补回来。
            // ⚠️ 快照必须在 e.take() **之前**拍：取完产物材料已经被原版扣掉了，那时候再拍就无从知道原来是什么。
            //    开关关着时快照恒为 null，下面那段补货逻辑整个跳过（零额外开销）。
            List<ItemStack> craftBefore = panel == PANEL_CRAFTING && isCraftRefillOn()
                    ? snapshotCraftInputs() : null;
            if (toStorage) {
                // Shift+左键：直接进袋子存储；收不进去就不取（东西不会丢）
                if (depositToStorage(taken) > 0) return false;
            } else {
                ItemStack cursor = getCarried();
                if (!cursor.isEmpty()) {
                    if (!PocketStorageHelper.sameItem(cursor, taken)
                            || cursor.getCount() + taken.getCount() > cursor.getMaxStackSize()) {
                        return false; // 光标上有别的东西或放不下：什么都不做
                    }
                    cursor.grow(taken.getCount());
                    setCarried(cursor);
                } else {
                    setCarried(taken);
                }
            }

            e.take(taken);                       // 原版 onTake（铁砧/锻造台扣经验，合成台扣材料与"剩余物品"）
            pullFromEngine(e, inputs);
            if (anvil) {
                anvilName = "";                  // 原版取完工品会清掉改名，本类跟随清空
                e.setItemName("");
            }
            e.refresh();                         // 原版重算一次（清空/剩余材料之后的状态）
            refreshPageEngine(panel);
            // 合成页 + "自动补充"开着：把刚才被原版消耗掉的材料从储物空间补回九宫格
            // （照 snapshotCraftInputs 那张快照补；只补袋子里真有的，绝不凭空生成）
            if (craftBefore != null) {
                com.zzq.survival_toolbox.util.PocketCraftRefill.refill(
                        player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null,
                        this, craftIn, craftBefore);
                refreshPageEngine(panel);        // 补完再让原版算一次产物（下一 tick 的 broadcastChanges 也会算）
            }
            this.entriesDirty = true;
            return true;
        } catch (Throwable t) {
            pageEngineBroken = true;
            anvilOut.setResult(ItemStack.EMPTY);
            smithOut.setResult(ItemStack.EMPTY);
            craftOut.setResult(ItemStack.EMPTY);
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 铁砧/锻造台/合成页的原版引擎取产物失败，已停用这几页（详见堆栈）", t);
            return false;
        }
    }

    /**
     * 取走拆解页的产物格。
     * <p>
     * 两种模式都<b>只由原版（拆解台自己那套代码）判定</b>：
     * <ul>
     *   <li>拆解模式（输入槽有东西）：产物格显示的是"待拆的那件东西"，取走 =
     *       原版 {@code performDisassemble()}——扣 1 个输入、把配方的材料直接给玩家（原版行为）；</li>
     *   <li>合成模式（输入槽空）：产物格是原版算出来的合成产物，先让原版校验并消耗材料，
     *       成功（产物格被原版清空）才把产物给玩家（拿光标上，和原版工作台一致）。</li>
     * </ul>
     * Shift+左键（拆解模式）= 批量拆解（原版按钮 5：把输入槽里那一叠全拆了）。
     * </p>
     */
    private void clickedDisassembleResult(ClickType clickType, Player player) {
        if (pageEngineBroken) return;
        refreshPageEngine(PANEL_DISASSEMBLE);
        com.zzq.survival_toolbox.util.PocketDisassembleEngine e = disassembleEngine();
        try {
            if (e.getOutput().isEmpty() || !e.canTakeOutput()) return;
        } catch (Throwable t) {
            pageEngineBroken = true;
            disOut.setResult(ItemStack.EMPTY);
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 拆解页的原版引擎取产物失败，已停用这一页（详见堆栈）", t);
            return;
        }

        boolean preview = !disIn.getItem(0).isEmpty();
        try {
            if (clickType == ClickType.QUICK_MOVE && preview) {
                e.button(com.zzq.survival_toolbox.util.PocketDisassembleEngine.BUTTON_BULK);
                mirrorDisassemble(e, false);
                this.entriesDirty = true;
                return;
            }
            // ⚠️ 取产物必须照**原版点击语义**：先把产物从产物格拿出来，再让原版结算（onTake）。
            //    旧实现是"先取一份副本、调 onTake、再看产物格空没空"——合成成功但九宫格还有剩料时，
            //    原版会把产物重新画回产物格当下一条的预览，于是"格空了"永远不成立：
            //    材料被吃掉、产物却拿不到；只有"最后一次把材料用光"时才碰巧能拿到
            //    （实测："一直点拿不到产物 / 只消耗拿不到 / 最后一次能拿出来"）。
            ItemStack taken = e.extractOutput();
            if (taken.isEmpty()) {
                mirrorDisassemble(e, preview);
                this.entriesDirty = true;
                return;
            }
            if (!e.lastSettleSucceeded()) {
                // 结算没成（材料在预览后被动过之类）：产物放回去，绝不让玩家凭空获得
                e.putOutputBack(taken);
                mirrorDisassemble(e, preview);
                this.entriesDirty = true;
                return;
            }
            if (!preview) {
                // 合成模式且原版确认合成成功：产物给玩家
                if (clickType == ClickType.QUICK_MOVE) {
                    // Shift+左键 = 直接收进袋子储物空间（和铁砧/合成页的产物格同一套手感）
                    depositToStorage(taken);
                } else {
                    ItemStack cursor = getCarried();
                    if (cursor.isEmpty()) {
                        setCarried(taken);
                    } else if (PocketStorageHelper.sameItem(cursor, taken)
                            && cursor.getCount() + taken.getCount() <= cursor.getMaxStackSize()) {
                        cursor.grow(taken.getCount());
                        setCarried(cursor);
                    } else {
                        // 光标上有别的东西：产物不能弄丢，直接收进袋子储物空间
                        depositToStorage(taken);
                    }
                }
            }
            // 拆解模式：拿到的"产物"只是被拆那件东西的显示副本，原版 performDisassemble 已经把材料
            // 直接给玩家了，这一份绝不能也发出去（否则等于凭空多给出一件输入物品）
            mirrorDisassemble(e, !disIn.getItem(0).isEmpty());
        } catch (Throwable t) {
            pageEngineBroken = true;
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 拆解页的原版引擎取产物失败，已停用这一页（详见堆栈）", t);
        }
        this.entriesDirty = true;
    }

    /**
     * 点击拆解页的九宫格。
     * <p>
     * 九宫格自己不是普通格子：拆解模式下里面是<b>原版算出来的材料预览</b>，
     * 原版拆解台允许玩家"逐个拿走" —— 收费规则在拆解台原有代码里
     * （`onMiddleSlotsChanged`：第一次动九宫格就扣掉 1 个输入物品，之后同一次拆解不再重复扣）。
     * 所以这里只做转发：拿走/放入都通过原版格子（{@code Slot#remove} / {@code Slot#set}），
     * 让原版自己去收费、去重算预览，这里只把结果镜像回页格子。
     * </p>
     */
    private void clickedDisassembleGrid(int index, int button, ClickType clickType, Player player) {
        if (pageEngineBroken) return;
        refreshPageEngine(PANEL_DISASSEMBLE);
        com.zzq.survival_toolbox.util.PocketDisassembleEngine e = disassembleEngine();
        try {
            ItemStack inGrid = e.getMaterial(index);
            if (clickType == ClickType.QUICK_MOVE) {
                // Shift+左键：整格收进袋子储物空间；收不进去就原样留着（东西不会丢）
                if (inGrid.isEmpty()) return;
                ItemStack taken = e.takeMaterial(index, inGrid.getCount());
                if (!taken.isEmpty() && depositToStorage(taken) > 0) {
                    e.setMaterial(index, taken);
                }
            } else if (clickType == ClickType.PICKUP) {
                ItemStack cursor = getCarried();
                if (cursor.isEmpty()) {
                    if (inGrid.isEmpty()) return;
                    int amount = button == 1 ? Math.max(1, inGrid.getCount() / 2) : inGrid.getCount();
                    ItemStack taken = e.takeMaterial(index, amount);
                    if (!taken.isEmpty()) setCarried(taken);
                } else if (e.mayPlaceMaterial(index, cursor)) {
                    // 光标上有东西：空格放入 / 同类合并（原版槽位语义，"放一个"就只放一个）
                    int put = button == 1 ? 1 : cursor.getCount();
                    if (inGrid.isEmpty()) {
                        ItemStack one = cursor.copy();
                        one.setCount(Math.min(put, one.getMaxStackSize()));
                        cursor.shrink(one.getCount());
                        e.setMaterial(index, one);
                    } else {
                        int room = Math.max(0, inGrid.getMaxStackSize() - inGrid.getCount());
                        int move = Math.min(put, room);
                        if (move <= 0) return;
                        ItemStack merged = inGrid.copy();
                        merged.setCount(merged.getCount() + move);
                        cursor.shrink(move);
                        e.setMaterial(index, merged);
                    }
                    setCarried(cursor);
                }
            }
            mirrorDisassemble(e);
        } catch (Throwable t) {
            pageEngineBroken = true;
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 拆解页九宫格的点击转发失败，已停用这一页（详见堆栈）", t);
        }
        this.entriesDirty = true;
    }

    /**
     * 磁铁页名单格（幽灵条目）。
     * <p>
     * 交互约定：往名单里放东西 = <b>复制一份物品信息</b>，手里的实物<b>退回袋子储物空间</b>；
     * 名单里的条目取不出来；<b>右键点条目直接删除</b>。
     * </p>
     */
    private void clickedMagnetFilter(int index, int button, ClickType clickType, Player player) {
        if (clickType != ClickType.PICKUP) return;
        if (button == 1) {
            this.magFilter.removeSample(index);           // 右键：删掉这条
            this.entriesDirty = true;
            return;
        }
        ItemStack cursor = getCarried();
        if (cursor.isEmpty()) return;                     // 空手左键：不动（删除请用右键）
        this.magFilter.addSample(cursor);                 // 只复制"信息"，数量恒为 1
        ItemStack hand = cursor.copy();
        setCarried(ItemStack.EMPTY);
        long left = depositToStorage(hand);               // 手里的实物退回袋子
        if (left > 0) setCarried(cursor);                 // 袋子不收（例如被拉黑的物品）就还给光标，东西不丢
        this.entriesDirty = true;
    }

    /**
     * 拍一张合成页九宫格的快照（服务端；每格一份 copy）。
     * <p>
     * 用途见 {@code clickedPageResult} 里的"自动补充"：原版取产物会把材料扣掉/换成残渣，
     * 只有提前拍一张，事后才知道这一格原来是什么、有多少。
     * </p>
     */
    private List<ItemStack> snapshotCraftInputs() {
        List<ItemStack> snap = new java.util.ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            snap.add(craftIn.getItem(i).copy());
        }
        return snap;
    }

    /** 服务端：处理面板/方向操作包 */
    public void handleTrayAction(int action) {
        if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN) {
            this.panel = PANEL_TRAY;
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_FURNACE) {
            this.panel = PANEL_FURNACE;
            // 打开时同步一次：炉子每 tick 在服务端改 NBT，界面缓存要跟上
            this.furnace.refreshFromBag();
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_FURNACE_TO_SLOT) {
            setFurnaceOutputToSlot(true);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_FURNACE_TO_STORAGE) {
            setFurnaceOutputToSlot(false);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_ANVIL) {
            this.panel = PANEL_ANVIL;
            this.anvilIn.reload();
            refreshPageEngine(PANEL_ANVIL);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_SMITHING) {
            this.panel = PANEL_SMITHING;
            this.smithIn.reload();
            refreshPageEngine(PANEL_SMITHING);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_CRAFTING) {
            this.panel = PANEL_CRAFTING;
            this.craftIn.reload();
            refreshCraftRefill();
            refreshPageEngine(PANEL_CRAFTING);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_CRAFT_REFILL_TOGGLE) {
            // 合成页的"自动补充"开关（存袋子 NBT，客户端按钮立刻翻转、这里随后确认）
            toggleCraftRefill();
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_RESTOCK) {
            this.panel = PANEL_RESTOCK;
            refreshRestock();
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_RES_TOGGLE) {
            updateRestock(st -> st.on = !st.on);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_RES_SCOPE) {
            updateRestock(st -> st.hotbarOnly = !st.hotbarOnly);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_MAGNET) {
            this.panel = PANEL_MAGNET;
            this.magFilter.reload();
            refreshMagnet();
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_MAG_TOGGLE) {
            updateMagnet(st -> st.on = !st.on);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_MAG_RANGE_DOWN) {
            updateMagnet(st -> st.range = com.zzq.survival_toolbox.util.PocketMagnet.clampRange(st.range - 1));
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_MAG_RANGE_UP) {
            updateMagnet(st -> st.range = com.zzq.survival_toolbox.util.PocketMagnet.clampRange(st.range + 1));
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_MAG_MODE) {
            updateMagnet(st -> st.onlyExisting = !st.onlyExisting);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_MAG_LIST_MODE) {
            updateMagnet(st -> st.whiteList = !st.whiteList);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_STONECUTTER) {
            this.panel = PANEL_STONECUTTER;
            this.stoneIn.reload();
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_OPEN_DISASSEMBLE) {
            this.panel = PANEL_DISASSEMBLE;
            this.disIn.reload();
            this.disMat.reload();
            if (this.disPreview) {
                // 上次关界面时九宫格里是"原版算出来的预览"，那不是玩家的东西：
                // 直接丢掉（输入还在的话原版会重新算出来），免得被当成玩家自己摆的材料送给玩家
                this.disMat.clearContent();
            }
            refreshPageEngine(PANEL_DISASSEMBLE);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_DIS_PREV) {
            disassembleButton(!this.disIn.getItem(0).isEmpty()
                    ? com.zzq.survival_toolbox.util.PocketDisassembleEngine.BUTTON_DIS_PREV
                    : com.zzq.survival_toolbox.util.PocketDisassembleEngine.BUTTON_CRAFT_PREV);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_DIS_NEXT) {
            disassembleButton(!this.disIn.getItem(0).isEmpty()
                    ? com.zzq.survival_toolbox.util.PocketDisassembleEngine.BUTTON_DIS_NEXT
                    : com.zzq.survival_toolbox.util.PocketDisassembleEngine.BUTTON_CRAFT_NEXT);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_DIS_BULK) {
            disassembleButton(com.zzq.survival_toolbox.util.PocketDisassembleEngine.BUTTON_BULK);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_CLOSE) {
            this.panel = PANEL_NONE;
            // 关页**不再**清改名：改名归"输入物品"管（换了物品才会清，见 refreshPageEngine 里那段），
            // 否则玩家刚输入的名字会因误点页按钮而丢失（历史问题："改名不生效"）。
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_SET_OUT) {
            this.tray.setOut(true);
        } else if (action == com.zzq.survival_toolbox.network.PocketTrayActionPacket.ACTION_SET_IN) {
            this.tray.setOut(false);
        }
    }

    /** 服务端：点拆解页底部的按钮（翻页 / 批量拆解），走原版 {@code clickMenuButton} */
    private void disassembleButton(int id) {
        if (pageEngineBroken) return;
        try {
            com.zzq.survival_toolbox.util.PocketDisassembleEngine e = disassembleEngine();
            e.button(id);
            mirrorDisassemble(e);
        } catch (Throwable t) {
            pageEngineBroken = true;
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 拆解页的原版按钮执行失败，已停用这一页（详见堆栈）", t);
        }
        this.entriesDirty = true;
    }

    /** 客户端：接收服务端同步的显示数据（槽位条目 + 翻页偏移 + 页名 + 每页条目数 + 当前页 + 存储模式 + 托盘/面板/熔炉进度） */
    public void setSyncedData(int pageOffset, int currentPage, List<String> pageNames,
                              List<Integer> pageCounts,
                              List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> slots,
                              boolean shared, long fluidMask,
                              int panel, boolean trayOut, long trayMask, long[] trayAmounts,
                              List<ItemStack> trayFluidIcons,
                              int furnaceBurn, int furnaceBurnTotal,
                              int furnaceCook, int furnaceCookTotal, int pageCost, boolean furnaceToSlot) {
        this.container.replaceDisplay(slots, pageOffset, pageNames, pageCounts, currentPage);
        this.container.applySyncedShared(shared);
        this.fluidMask = fluidMask;
        this.panel = panel;
        this.tray.applySynced(trayOut, trayMask, trayAmounts);
        // 托盘流体格的"桶图标/流体本体"由同步包补齐（别指望原版槽位同步，见 PocketTrayContainer#fluidIcons）
        this.tray.applySyncedFluidIcons(trayFluidIcons);
        this.furnace.applySyncedProgress(furnaceBurn, furnaceBurnTotal, furnaceCook, furnaceCookTotal);
        this.pageCost = pageCost;
        this.furnaceToSlot = furnaceToSlot;
        this.lastSyncedSlots.clear();
        this.lastSyncedSlots.addAll(slots);
        this.lastSyncedPage = pageOffset;
        this.entriesDirty = false;
    }

    /** 服务端：设置搜索过滤关键词（跨页搜索） */
    public void setSearch(String keyword) {
        container.setSearch(keyword);
        this.entriesDirty = true;
    }

    /** 服务端：页操作（新建/删除/重命名/切换） */
    public void handlePageAction(int action, int index, String name) {
        // 切石机页：客户端点配方列表 → 记住选中的下标并重算产物（列表顺序两端一致，见 STONE_LIST_* 的注释）
        if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_STONE_SELECT) {
            selectStonecutter(index);
            return;
        }
        if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_ADD) {
            container.addPage();
        } else if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_REMOVE) {
            container.removePage(index);
        } else if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_RENAME) {
            container.renamePage(index, name);
        } else if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_SET_PAGE) {
            container.setCurrentPage(index);
        }
        this.entriesDirty = true;
    }

    /** 服务端：切换存储模式（共享 ↔ 本地；两边数据各自保留，只换看哪一份） */
    public void setSharedMode(boolean shared) {
        container.switchMode(shared);
        this.entriesDirty = true;
    }

    /**
     * 服务端：整理存储（同种合并 + 按所选方式排序 + 条目压实）。
     *
     * @param scope  {@link PocketDimensionContainer#SORT_CURRENT_PAGE}（只整理当前页）或
     *               {@link PocketDimensionContainer#SORT_ALL_PAGES}（所有页当一个整体整理）
     * @param sortBy {@link PocketDimensionContainer#SORT_BY_NAME}（不分 mod 按注册名）/
     *               {@link PocketDimensionContainer#SORT_BY_MOD}（同 mod 聚一起）/
     *               {@link PocketDimensionContainer#SORT_BY_COUNT}（按数量）
     */
    public void sort(int scope, int sortBy) {
        boolean done = container.sort(scope, sortBy);
        this.entriesDirty = true;
        if (done && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    scope == PocketDimensionContainer.SORT_ALL_PAGES
                            ? "message.zzq_survival_toolbox.pocket.sort.all"
                            : "message.zzq_survival_toolbox.pocket.sort.current"), true);
        }
    }

    /**
     * 服务端：把本地存储整体并入共享空间（一键转移）。
     * <p>
     * 行为约定：只做本地 → 共享，而且**不覆盖**共享里已有的东西（两边相加）；
     * 完成后本地被清空、袋子切到共享，并在聊天栏报一下搬了多少。
     * </p>
     */
    public void mergeLocalToShared() {
        PocketDimensionContainer.MergeResult result = container.mergeLocalToShared();
        this.entriesDirty = true;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (result.done()) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.zzq_survival_toolbox.pocket.merge.done",
                        result.items(), PocketStorageHelper.formatFluidAmount(result.fluid())), false);
            } else {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.zzq_survival_toolbox.pocket.merge.empty"), true);
            }
        }
    }

    /** 客户端：当前视图哪些格是流体（54 位掩码，存储页用） */
    public boolean isFluidDisplaySlot(int slot) {
        return slot >= 0 && slot < 54 && ((fluidMask >>> slot) & 1L) != 0L;
    }

    /** 客户端/服务端：当前是否共享（末影箱式）存储 */
    public boolean isSharedMode() {
        return container.isShared();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 100) {
            container.setPageOffset(container.getPageOffset() - PocketDimensionContainer.PAGE_SIZE);
            return true;
        } else if (id == 101) {
            container.setPageOffset(container.getPageOffset() + PocketDimensionContainer.PAGE_SIZE);
            return true;
        }
        return false;
    }

    // ============================================================
    // 流体交互
    // ============================================================

    /**
     * 光标 / 手上的流体容器，优先级：**光标 → 主手 → 副手**；null = 都没有。
     * <p>
     * ⚠️ 光标必须算进来（历史缺陷）：以前只看双手，于是"拿着空桶（在光标上）左键点流体格"
     * 会被当成"空手左键 = 拿起流体"，直接把光标上的桶**覆盖**掉 —— 桶没了、也没变成水桶。
     * </p>
     */
    private HeldFluid heldFluidContainer(Player player) {
        ItemStack carried = getCarried();
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(carried).orElse(null);
        if (handler != null) return new HeldFluid(carried, handler, 2);
        ItemStack main = player.getMainHandItem();
        handler = FluidUtil.getFluidHandler(main).orElse(null);
        if (handler != null) return new HeldFluid(main, handler, 0);
        ItemStack off = player.getOffhandItem();
        handler = FluidUtil.getFluidHandler(off).orElse(null);
        if (handler != null) return new HeldFluid(off, handler, 1);
        return null;
    }

    /** 手上的流体容器：物品、能力、手（0 = 主手，1 = 副手） */
    private record HeldFluid(ItemStack stack, IFluidHandlerItem handler, int hand) {
    }

    /**
     * 存储页流体格的点击（交互约定：<b>左键只管"拿/换位置"，装只在右键</b>）：
     * <ul>
     *   <li><b>左键</b>：跟物品格一样只搬东西 ——
     *       光标空 → 整条流体拿到光标上（拖拽，见 {@link PocketFluidCarry}）；
     *       光标上有物品 → <b>交换</b>（物品放进这一格、整条流体到光标上）。
     *       ⚠️ 左键**不再**把流体装进容器：玩家"手里拿着东西左键点水格"应当是换位置，而不是把流体灌进容器。</li>
     *   <li><b>右键</b>：只跟"手上的流体容器"打交道 ——
     *       容器还能装下这种流体（空桶/没装满的罐）→ 从这一格<b>装进容器</b>；
     *       否则（装满了 / 装的是别种流体）→ 把容器里的<b>倒进"指着的那一格"</b>。</li>
     *   <li>空手右键：什么都不做（这一格是流体，不是空格）。</li>
     * </ul>
     */
    private void clickedFluid(int slotId, int button, Player player) {
        if (slotId < 0 || slotId >= PocketDimensionContainer.PAGE_SIZE) return;

        if (button == 0) {
            // 左键 = 搬东西（和物品格同一套手感）
            ItemStack cursor = getCarried();
            if (cursor.isEmpty()) {
                // 空手左键：拿起（整条搬到光标上，数量用 long，超过 int 上限也不丢）
                PocketStorageHelper.FluidEntry entry = container.takeFluidEntry(slotId);
                if (entry == null) return;
                setCarried(PocketFluidCarry.make(entry));
                this.entriesDirty = true;
                return;
            }
            // 光标上有东西 → 交换：先把这一格整条流体收走（此时这一格空了），再把光标上的东西放进这一格。
            // ⚠️ 光标上拿着的是"流体载体"时走不到这里（clicked() 开头就交给 dropCarriedFluid 了），
            //    所以这里的光标一定是真物品 ✓ 放进去不会变成假桶。
            if (container.isSearching()) return;   // 搜索结果只是视图，只允许拿走
            PocketStorageHelper.FluidEntry entry = container.takeFluidEntry(slotId);
            if (entry == null) return;
            container.setItemAt(slotId, cursor);
            setCarried(PocketFluidCarry.make(entry));
            this.entriesDirty = true;
            return;
        }

        // 右键：装/倒都只认"手上的流体容器"
        HeldFluid held = heldFluidContainer(player);
        if (held == null) return;
        if (canAcceptInto(held, slotId)) {
            fillContainerFromSlot(slotId, held, player);
        } else {
            pourContainerIntoSlot(slotId, held, player);
        }
    }

    /** 容器还能不能再装下这一格的流体（决定右键是"装"还是"倒"） */
    private boolean canAcceptInto(HeldFluid held, int slotId) {
        net.neoforged.neoforge.fluids.FluidStack inBag = container.peekFluidAt(slotId);
        if (inBag.isEmpty()) return false;
        int capacity = held.handler().getTankCapacity(0) - held.handler().getFluidInTank(0).getAmount();
        if (capacity <= 0) return false;
        net.neoforged.neoforge.fluids.FluidStack inContainer = held.handler().getFluidInTank(0);
        // 用 is(Fluid)：1.21.1 里 isFluidEqual 已标记为待删除（1.20.1 那边没有 is(Fluid)，只能用它）
        return inContainer.isEmpty() || inContainer.is(inBag.getFluid());
    }

    /**
     * 把这一格的流体装进手上的容器（左键 / 右键的第一种情况）。
     * <p>
     * ⚠️ 取多少要用**容器的剩余容量**，<b>绝不能</b>用 {@code peekFluidAt} 的返回值：
     * 那个是"数量为 1 的模板"，拿它去提取只会取出 1 mB，而桶这类容器装不满 1000 mB 会直接
     * 拒绝（{@code fill} 返回 0）—— 玩家看到的就是"点了没反应、桶也没被吞、也没变成水桶"
     * （历史缺陷，根因就在这里）。
     * </p>
     */
    private void fillContainerFromSlot(int slotId, HeldFluid held, Player player) {
        int capacity = held.handler().getTankCapacity(0) - held.handler().getFluidInTank(0).getAmount();
        if (capacity <= 0) return;
        net.neoforged.neoforge.fluids.FluidStack taken = container.extractFluidAt(slotId, capacity);
        if (taken.isEmpty()) return;
        int accepted = held.handler().fill(taken, IFluidHandler.FluidAction.EXECUTE);
        if (accepted <= 0) {
            // 容器其实装不下（例如这种流体没有对应的桶）：原样放回这一格
            container.insertFluidAt(slotId, taken);
            return;
        }
        if (accepted < taken.getAmount()) {
            net.neoforged.neoforge.fluids.FluidStack back = taken.copy();
            back.setAmount(taken.getAmount() - accepted);
            long left = container.insertFluidAt(slotId, back);
            if (left > 0) {
                back.setAmount((int) left);
                container.insertFluid(back);
            }
        }
        writeHeld(player, held.hand(), held.stack(), held.handler().getContainer());
        this.entriesDirty = true;
    }

    /**
     * 把容器里的流体倒进<b>指定的那一格</b>（右键）。
     * <p>
     * ⚠️ 必须用 {@code insertFluidAt(slotId, ...)}（就这一格），<b>不要</b>用 {@code insertFluid}：
     * 后者是"整个袋子按种类合并"，玩家明明是倒在空格上，水却会跑到别的格、甚至别的页去
     * （历史缺陷所对应的要求："不要合并，放哪是哪"）。合并只应该发生在"整理"和"手动倒进同一格"时。
     * </p>
     */
    private void pourContainerIntoSlot(int slotId, HeldFluid held, Player player) {
        net.neoforged.neoforge.fluids.FluidStack drained =
                held.handler().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return;
        long left = container.insertFluidAt(slotId, drained);
        if (left > 0) {
            // 这一格放不下（被物品占着 / 是另一种流体）：倒回容器，绝不"顺便"塞到别处
            net.neoforged.neoforge.fluids.FluidStack back = drained.copy();
            back.setAmount((int) left);
            held.handler().fill(back, IFluidHandler.FluidAction.EXECUTE);
        }
        writeHeld(player, held.hand(), held.stack(), held.handler().getContainer());
        this.entriesDirty = true;
    }

    /**
     * 把手上的流体容器倒进<b>指着的那一格</b>。
     * <p>
     * 专门补"第一桶"：袋子里还没有这种流体时界面上没有流体格可点，右键空格子走这里；
     * 已经是流体格的情况仍然走 {@link #clickedFluid}。
     * ⚠️ 同样是"放哪是哪"：绝不合并到别的格/别的页（明确约定）；
     * 这一格放不下（被物品占着）就返回 false，把这次点击交回原版处理。
     * </p>
     *
     * @return true = 手上确实有流体容器并且倒进了这一格（这次点击已处理）
     */
    private boolean pourHeldFluid(Player player, int slotId) {
        HeldFluid held = heldFluidContainer(player);
        if (held == null) return false;
        net.neoforged.neoforge.fluids.FluidStack drained =
                held.handler().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return false;
        long left = container.insertFluidAt(slotId, drained);
        if (left > 0) {
            net.neoforged.neoforge.fluids.FluidStack back = drained.copy();
            back.setAmount((int) left);
            held.handler().fill(back, IFluidHandler.FluidAction.EXECUTE);
            return false;
        }
        writeHeld(player, held.hand(), held.stack(), held.handler().getContainer());
        this.entriesDirty = true;
        return true;
    }

    /**
     * 托盘流体格的点击（与存储页完全同一套判定规则）：
     * <b>左键</b> = 拿起来（光标空）/ 与光标上的物品交换；<b>右键</b> = 拿着容器时装或倒。
     */
    private void clickedTrayFluid(int traySlot, int button, Player player) {
        if (button == 0) {
            ItemStack cursor = getCarried();
            if (cursor.isEmpty()) {
                PocketStorageHelper.FluidEntry entry = tray.takeFluidEntry(traySlot);
                if (entry == null) return;
                setCarried(PocketFluidCarry.make(entry));
                return;
            }
            // 交换：先收走这一格的流体（格子就空了），再把光标上的物品放进去
            // 托盘按原版箱子处理（每格一叠、≤64），整叠放不下就不换，避免多余的部分丢失
            if (cursor.getCount() > Math.min(cursor.getMaxStackSize(), tray.getMaxStackSize())) return;
            PocketStorageHelper.FluidEntry entry = tray.takeFluidEntry(traySlot);
            if (entry == null) return;
            tray.setItem(traySlot, cursor);
            setCarried(PocketFluidCarry.make(entry));
            return;
        }
        HeldFluid held = heldFluidContainer(player);
        if (held == null) return;
        if (canAcceptInto(held, traySlot)) {
            // 右键：从托盘灌进容器
            PocketStorageHelper.FluidEntry entry = tray.fluidAt(traySlot);
            if (entry == null) return;
            int capacity = held.handler().getTankCapacity(0) - held.handler().getFluidInTank(0).getAmount();
            if (capacity <= 0) return;
            FluidStack taken = tray.extractFluid(traySlot, Math.min(capacity, entry.amount()));
            if (taken.isEmpty()) return;
            int accepted = held.handler().fill(taken, IFluidHandler.FluidAction.EXECUTE);
            if (accepted <= 0) {
                tray.insertFluid(traySlot, taken);
                return;
            }
            if (accepted < taken.getAmount()) {
                FluidStack back = taken.copy();
                back.setAmount(taken.getAmount() - accepted);
                tray.insertFluid(traySlot, back);
            }
            writeHeld(player, held.hand(), held.stack(), held.handler().getContainer());
        } else {
            // 右键：把手上的容器倒进这一格
            FluidStack drained = held.handler().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) return;
            long left = tray.insertFluid(traySlot, drained);
            if (left > 0) {
                // 这一格放不下（异种流体/被物品占用）：倒回容器
                FluidStack back = drained.copy();
                back.setAmount((int) left);
                held.handler().fill(back, IFluidHandler.FluidAction.EXECUTE);
            }
            writeHeld(player, held.hand(), held.stack(), held.handler().getContainer());
        }
    }

    /**
     * 光标上拿着流体时的"放下"：所有点击都由这里接管，绝不产生假桶。
     * <ul>
     *   <li>托盘格：空格放入 / 同种合并；如果是被**物品**占着且那格能整格拿起 → 与物品**交换**</li>
     *   <li>存储格：空格放入 / 同种合并；同样支持与"整格能拿起的物品"交换</li>
     *   <li>放不进（物品占用且拿不起来、异种流体）就按当前模式收回袋子存储</li>
     *   <li>玩家背包格、界面外：不落地，按当前模式收回袋子存储（物品并未离开袋子）</li>
     * </ul>
     * <p>
     * ⚠️ 交换这条是后续补充的：拿着物品左键点流体格能换位置，拿着流体左键点物品格同样要能换位置 ——
     * 以前拿着流体点物品格会因为"这一格放不下流体"而把流体**塞到别的空位**去，玩家看到的是"它自己找空位跑了"。
     * </p>
     */
    private void dropCarriedFluid(int slotId, PocketStorageHelper.FluidEntry carried, Player player) {
        FluidStack stack = carried.toStack((int) Math.min(carried.amount(), Integer.MAX_VALUE));
        if (stack.isEmpty()) {
            setCarried(ItemStack.EMPTY);
            return;
        }
        long want = stack.getAmount();
        long left = want;
        if (slotId >= TRAY_BASE && slotId < TRAY_BASE + PocketTrayStorage.SLOTS) {
            int traySlot = slotId - TRAY_BASE;
            left = tray.insertFluid(traySlot, stack);
            if (left == want) {
                // 这一格放不进流体：如果是物品占着、而且能整格拿起来 → 交换（物品到光标、流体进这一格）
                int max = Math.min(tray.getItem(traySlot).getMaxStackSize(), tray.getMaxStackSize());
                if (!tray.isFluidAt(traySlot) && !tray.getItem(traySlot).isEmpty()
                        && tray.getItem(traySlot).getCount() <= max) {
                    ItemStack taken = tray.removeItem(traySlot, tray.getItem(traySlot).getCount());
                    long after = tray.insertFluid(traySlot, stack);
                    if (after <= 0) {
                        setCarried(taken);
                        this.entriesDirty = true;
                        return;
                    }
                    tray.setItem(traySlot, taken);   // 万一没放进去：物品放回原格
                    left = want;
                }
                depositFluidToStorage(player, carried);
                setCarried(ItemStack.EMPTY);
                this.entriesDirty = true;
                return;
            }
        } else if (slotId >= 0 && slotId < PocketDimensionContainer.PAGE_SIZE) {
            left = container.insertFluidAt(slotId, stack);
            if (left == want) {
                // 同理：物品占着这一格时，能整格拿起来就交换（拿着流体左键点物品格同样要能换位置）
                if (!container.isSearching() && container.canTakeWholeItem(slotId)) {
                    ItemStack taken = container.takeWholeItem(slotId);
                    long after = container.insertFluidAt(slotId, stack);
                    if (after <= 0) {
                        setCarried(taken);
                        this.entriesDirty = true;
                        return;
                    }
                    container.setItemAt(slotId, taken);   // 万一没放进去：物品放回原格
                }
                // 这一格放不下（物品占用/异种流体/搜索结果）：整条收回袋子存储
                depositFluidToStorage(player, carried);
                left = 0L;
                setCarried(ItemStack.EMPTY);
                this.entriesDirty = true;
                return;
            }
            if (left > 0) {
                // 只放下了一部分（几乎不可能）：剩下的收回存储，别留在光标上
                depositFluidToStorage(player, carried, carried.amount() - (want - left));
                setCarried(ItemStack.EMPTY);
                this.entriesDirty = true;
                return;
            }
        } else {
            // 玩家背包格 / 界面外：不落地，按当前模式收回袋子存储（物品并未离开袋子）
            depositFluidToStorage(player, carried);
            setCarried(ItemStack.EMPTY);
            this.entriesDirty = true;
            return;
        }
        long stillHeld = carried.amount() - (want - left);
        setCarried(stillHeld <= 0 ? ItemStack.EMPTY : PocketFluidCarry.make(carried, stillHeld));
        this.entriesDirty = true;
    }

    /**
     * 把光标上（或指定数量）的流体按袋子当前共享/本地模式写回存储。
     * <p>
     * FluidStack 的数量是 int，单次最多 21 亿 mB，因此大于该上限时循环分批写，
     * 保证"拿起来的东西一定回得去"，不会因为溢出而被吞掉。
     * </p>
     */
    private void depositFluidToStorage(Player player, PocketStorageHelper.FluidEntry entry) {
        depositFluidToStorage(player, entry, entry.amount());
    }

    private void depositFluidToStorage(Player player, PocketStorageHelper.FluidEntry entry, long amount) {
        long left = Math.min(amount, entry.amount());
        for (int guard = 0; left > 0 && guard < 64; guard++) {
            FluidStack chunk = entry.toStack((int) Math.min(left, Integer.MAX_VALUE));
            if (chunk.isEmpty()) break;
            // 同样必须走容器缓存的页（见 depositToStorage 的说明），否则会被旧缓存覆盖掉
            container.insertFluid(chunk);
            left -= chunk.getAmount();
        }
    }

    /** 把容器变化后的物品写回原处（0 = 主手，1 = 副手，2 = 光标） */
    private void writeHeld(Player player, int hand, ItemStack before, ItemStack after) {
        if (after.isEmpty()) return;
        if (hand == 2) {
            // 光标：原版会把光标物品同步回客户端（服务端这边的 setCarried 会跟着走）
            setCarried(after);
            return;
        }
        if (hand == 1) {
            player.getInventory().offhand.set(0, after);
        } else {
            int slot = player.getInventory().selected;
            player.getInventory().items.set(slot, after);
        }
        player.getInventory().setChanged();
    }

    // ============================================================
    // 点击分发
    // ============================================================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide) return ItemStack.EMPTY; // 客户端不预测
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        if (index >= FURNACE_BASE) {
            // ⚠️⚠️ **产物格必须在这里处理**：原版 Shift+点击走的是 {@code quickMoveStack}，
            //     **根本不经过 {@link #clicked}**（`clicked` 只管普通点击）。以前这里直接 `return EMPTY`
            //     （注释写着"走 clickedPageResult"），结果 Shift+点击产物格什么都不做 ——
            //     实测"工作台/拆解台的 Shift 批量拿产物在袋子里不生效"就是这个。
            if (index == ANVIL_RESULT || index == SMITH_RESULT || index == CRAFT_RESULT) {
                clickedPageResult(index, ClickType.QUICK_MOVE, player);
                return ItemStack.EMPTY;
            }
            if (index == DIS_RESULT && panel == PANEL_DISASSEMBLE) {
                clickedDisassembleResult(ClickType.QUICK_MOVE, player);
                return ItemStack.EMPTY;
            }
            if (index == STONE_RESULT && panel == PANEL_STONECUTTER) {
                clickedStonecutterResult(ClickType.QUICK_MOVE, player);
                return ItemStack.EMPTY;
            }
            // 其它功能页槽位（熔炉/铁砧/锻造台/拆解九宫格的输入）→ Shift+点击：**优先塞进储物空间**
            // （照精妙背包 AnvilUpgradeContainer#mergeIntoStorageFirst 的做法：东西多半是从储物格
            //   拖进页里的，Shift 一下就原路收回储物空间，比塞进身上背包更符合操作习惯；
            //   储物空间不收的（例如袋子这类被拉黑的东西）再退回原行为，塞进玩家背包）
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return ItemStack.EMPTY;
            long left = depositToStorage(stack);
            int stored = stack.getCount() - (int) Math.min(left, stack.getCount());
            if (stored > 0) {
                ItemStack rest = stack.copy();
                rest.shrink(stored);
                slot.set(rest.isEmpty() ? ItemStack.EMPTY : rest);
            }
            if (left <= 0) return ItemStack.EMPTY; // 全收进储物空间了
            ItemStack remain = slot.getItem();
            if (remain.isEmpty()) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(remain, INVENTORY_BASE, HOTBAR_BASE + 9, false)) {
                return ItemStack.EMPTY;
            }
            if (remain.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }

        if (index >= TRAY_BASE) {
            // 托盘 Shift+左键 = 把这一叠收回来，**优先收回储物空间**（明确要求：
            // 托盘 Shift 左键应当优先回袋子，而不是进玩家物品栏）。
            // 储物空间不收的那部分（例如袋子这类被拉黑的东西）再退回原有行为：塞玩家背包，满了就留在托盘。
            // 流体格的格内物品只是显示用的桶图标，绝不能当物品转移（否则会刷桶）
            if (tray.isFluidAt(index - TRAY_BASE)) return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // ① 先回袋子。⚠️⚠️ 必须**用 copy 去试**、再按"真进去了多少"扣托盘格里的活堆叠：
            //     直接拿活堆叠去 insert 的话，后面"退回玩家背包"那一支会把已经进袋子的部分再搬一次
            //     = 凭空复制物品（本文件中曾多次出现该缺陷）。
            int before = stack.getCount();
            ItemStack rest = insertIntoStorage(stack.copy());
            int stored = before - (rest.isEmpty() ? 0 : rest.getCount());
            if (stored > 0) {
                stack.shrink(stored);
                this.entriesDirty = true;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }

            // ② 剩下这些储物空间不收 → 原有行为：塞玩家背包（这里必须用格内"活堆叠"，
            //    原版 moveItemStackTo 会直接扣它；用 copy() 的话物品已经进背包、格子里却还在 = 复制物品）
            if (!this.moveItemStackTo(stack, INVENTORY_BASE, HOTBAR_BASE + 9, false)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.zzq_survival_toolbox.inventory_full"), true);
                slot.setChanged();
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }

        if (index < PocketDimensionContainer.PAGE_SIZE) {
            // 次元袋格 → Shift+左键：**面板开着就先放进该功能栏**（需求：
            // 袋子里的物品按 Shift 左键也要优先进旁边的功能区，托盘开着时同样优先进托盘）。
            // ⚠️ 先前版本遗漏了这里：这一处**才是袋子格的那条路**（另一处是托盘格/背包格那条），
            //    所以 1.21.1 上表现成"袋子里的东西 Shift 先进物品栏、从物品栏 Shift 才进托盘"。
            if (panel != PANEL_NONE) {
                ItemStack inCell = container.getItem(index);      // 已经是副本
                if (!inCell.isEmpty()) {
                    ItemStack one = inCell.copy();
                    int moved = moveIntoOpenPanel(one);            // 往面板输入格里放（会就地扣 one）
                    if (moved > 0) {
                        // 面板收下了几个就从袋子里扣几个（moved 必然 ≤ 这一格的数量，所以扣得掉）
                        container.removeItem(index, moved);
                        this.entriesDirty = true;
                        return ItemStack.EMPTY;
                    }
                }
            }
            // 次元袋 → 背包：取一组（按原版堆叠上限），背包满则丢出
            ItemStack taken = container.removeItem(index, container.stackSizeAt(index));
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
            }
            return ItemStack.EMPTY;
        }

        // 玩家背包 → 侧边功能页（面板展开时优先）／托盘／次元袋
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // ① 侧边功能页：**面板展开时优先放进该功能栏**（需求；拆解台页优先放九宫格；
        //    另外要求"**托盘开着也要先进托盘**"，所以托盘现在也走这一支了，见 moveIntoOpenPanel 的 PANEL_TRAY）。
        if (panel != PANEL_NONE) {
            int moved = moveIntoOpenPanel(stack);
            if (moved > 0) {
                if (stack.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                return ItemStack.EMPTY;
            }
            // 一件都没放进去（输入格不匹配/只读）：继续按原有路径落进储物空间，别让 Shift+左键没反应
        }

        // ② 托盘
        if (panel == PANEL_TRAY) {
            ItemStack rest = stack.copy();
            this.moveItemStackTo(rest, TRAY_BASE, TRAY_BASE + PocketTrayStorage.SLOTS, false);
            if (rest.isEmpty()) {
                slot.set(ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
            // 托盘装不下的部分按原逻辑进存储页
            slot.set(insertIntoStorage(rest));
            return ItemStack.EMPTY;
        }

        // ③ 储物空间
        slot.set(insertIntoStorage(stack));
        return ItemStack.EMPTY;
    }

    /**
     * 把物品塞进"当前展开的功能页"的输入格（Shift+左键的优先去处）。
     * <p>
     * 只碰<b>输入类</b>格子：产物格、磁铁名单格、只读格都由各自 {@code mayPlace} 挡住，不用在这里判断。
     * 拆解台页按约定<b>先九宫格、再左边输入格</b>：拆解模式下九宫格是只读预览（放不进去），
     * 于是自然落到输入格——正好是"再塞几件进去拆"想要的结果。
     * </p>
     *
     * @param stack 会被就地扣减（原版 {@code moveItemStackTo} 的语义）
     * @return 实际放进去的数量
     */
    private int moveIntoOpenPanel(ItemStack stack) {
        int before = stack.getCount();
        switch (panel) {
            case PANEL_FURNACE -> {
                this.moveItemStackTo(stack, FURNACE_BASE, FURNACE_BASE + 1, false);       // 先输入
                if (!stack.isEmpty()) this.moveItemStackTo(stack, FURNACE_BASE + 1, FURNACE_BASE + 2, false); // 再燃料
            }
            case PANEL_ANVIL -> this.moveItemStackTo(stack, ANVIL_IN_BASE, ANVIL_IN_BASE + 2, false);
            case PANEL_SMITHING -> this.moveItemStackTo(stack, SMITH_IN_BASE, SMITH_IN_BASE + 3, false);
            case PANEL_CRAFTING -> this.moveItemStackTo(stack, CRAFT_GRID_BASE, CRAFT_GRID_BASE + 9, false);
            case PANEL_DISASSEMBLE -> {
                // ⚠️⚠️ **只往输入格放"一个"**，绝不碰九宫格（实测丢东西）：
                //    九宫格在合成模式下是可写的合成材料格，而页里的 refreshDisassemble() 每 tick 会
                //    把它喂给隐藏的拆解台引擎、再把引擎状态镜像回来；引擎那边的
                //    onMiddleSlotsChanged / returnOrDropMiddleItems 会把"塞进来"的东西当成它自己的材料处理
                //    —— 一部分还给玩家（表现为"一个进了背包"）、一部分被清掉（"还有一个不翼而飞"）。
                //    需求就是"Shift+左键只给拆解台放一个"，所以就放一个到输入格、到此为止。
                net.minecraft.world.inventory.Slot in = this.slots.get(DIS_IN_BASE);
                if (in == null || !in.isActive() || !in.mayPlace(stack)) return 0;
                ItemStack there = in.getItem();
                if (!there.isEmpty() && !PocketStorageHelper.sameItem(there, stack)) return 0;   // 输入格有别的：不强行放入
                if (there.isEmpty()) {
                    ItemStack one = stack.copy();
                    one.setCount(1);
                    in.set(one);
                } else {
                    if (there.getCount() >= there.getMaxStackSize()) return 0;
                    there.grow(1);
                    in.setChanged();
                }
                stack.shrink(1);
            }
            case PANEL_STONECUTTER -> this.moveItemStackTo(stack, STONE_IN_BASE, STONE_IN_BASE + 1, false);
            case PANEL_TRAY -> {
                // 托盘（补充要求：托盘打开时，在袋子里 Shift 左键优先放入托盘）。
                // ⚠️ 必须**跳过流体格**：那种格子里的"桶"只是显示图标，把物品放进去会和流体打架
                //   （历史问题：物品放在流体格上会让流体隐形、整理时被清掉）。
                for (int i = 0; i < PocketTrayStorage.SLOTS && !stack.isEmpty(); i++) {
                    if (tray.isFluidAt(i)) continue;
                    int idx = TRAY_BASE + i;
                    if (idx >= this.slots.size()) break;
                    net.minecraft.world.inventory.Slot s = this.slots.get(idx);
                    if (s == null || !s.isActive() || !s.mayPlace(stack)) continue;
                    this.moveItemStackTo(stack, idx, idx + 1, false);
                }
            }
            default -> {
                return 0;
            }
        }
        return before - stack.getCount();
    }

    /**
     * 把物品放进储物空间（Shift+左键的兜底去处）。
     * <p>
     * 先按原有逻辑"插进当前页的空位"（{@link PocketDimensionContainer#insert}，现在会避开流体格）；
     * 当前页插不下（例如空位全被流体占着）就退回跨页入库，绝不让 Shift+左键变成"没反应"。
     * </p>
     *
     * @return 没放进去的部分（空 = 全部入库）
     */
    private ItemStack insertIntoStorage(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack rest = container.insert(stack);
        if (rest.isEmpty()) return ItemStack.EMPTY;
        long left = depositToStorage(rest);
        return left <= 0 ? ItemStack.EMPTY : rest;
    }

    /**
     * 接管次元袋格子的点击交互：
     * <ul>
     *   <li>光标上拿着流体：全部自己接管（放下/收回存储）</li>
     *   <li>托盘流体格 / 存储流体格：桶交互或"拿起"</li>
     *   <li>托盘物品格：交给原版（原版堆叠语义）</li>
     *   <li>存储格：精确操作该格（左键取走/放入该格，同类合并、异类交换），搜索模式下只允许取走</li>
     * </ul>
     * 客户端直接跳过预测（交互由服务端执行后经同步包回显）。
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player.level().isClientSide) return;

        // 熔炉页展开时，先把界面缓存同步成袋子里的真实状态：
        // 炉子每 tick 在服务端改 NBT（例如烧掉一个输入），如果拿旧缓存去处理点击，
        // 一个 setChanged() 就会把"已经烧掉的输入"又写回去 —— 等于凭空复制物品。
        if (isFurnaceOpen()) furnace.refreshFromBag();
        // 铁砧/锻造台/合成页：点之前先让原版引擎跟上页里的输入（产物是原版算的）
        if (isPanelOpen(PANEL_ANVIL)) refreshPageEngine(PANEL_ANVIL);
        else if (isPanelOpen(PANEL_SMITHING)) refreshPageEngine(PANEL_SMITHING);
        else if (isPanelOpen(PANEL_CRAFTING)) refreshPageEngine(PANEL_CRAFTING);
        else if (isPanelOpen(PANEL_DISASSEMBLE)) refreshPageEngine(PANEL_DISASSEMBLE);
        else if (isPanelOpen(PANEL_STONECUTTER)) refreshPageEngine(PANEL_STONECUTTER);

        // 0) 铁砧/锻造台/合成页的产物格：取走走原版 onTake（自己拦截，绝不让原版路径直接拿走）
        if (slotId == ANVIL_RESULT || slotId == SMITH_RESULT || slotId == CRAFT_RESULT) {
            clickedPageResult(slotId, clickType, player);
            return;
        }
        // 0) 磁铁页名单格：幽灵条目 —— 左键放入"物品信息"（实物退回袋子）、右键删除该条；名单条目取不出来
        if (slotId >= MAG_FILTER_BASE && slotId < MAG_FILTER_BASE
                + com.zzq.survival_toolbox.util.PocketMagnet.FILTER_SIZE) {
            clickedMagnetFilter(slotId - MAG_FILTER_BASE, button, clickType, player);
            return;
        }
        // 0) 拆解页产物格：拆解模式 = 把材料给玩家并扣 1 个输入；合成模式 = 走原版合成消耗
        // 0) 切石机页产物格：取走时按原版切石机规则消耗 1 个输入
        if (slotId == STONE_RESULT) {
            clickedStonecutterResult(clickType, player);
            return;
        }
        if (slotId == DIS_RESULT) {
            clickedDisassembleResult(clickType, player);
            return;
        }
        // 0) 拆解页九宫格：拆解模式（输入槽有东西）下它是"原版算出来的材料预览"，格子本身只读，
        //    点击统一转发给拆解台的引擎——"第一次动九宫格就扣 1 个输入"这条原有收费规则照旧由拆解台自己算。
        //    合成模式（输入槽空）下它就是玩家自己的合成格，交给原版 doClick 处理（拖拽/分堆等全套照旧）。
        if (slotId >= DIS_MAT_BASE && slotId < DIS_MAT_BASE + 9 && !disIn.getItem(0).isEmpty()) {
            clickedDisassembleGrid(slotId - DIS_MAT_BASE, button, clickType, player);
            return;
        }

        // 0) 光标上拿着"流体载体"：所有点击自己处理，避免假桶被放进格子
        PocketStorageHelper.FluidEntry carriedFluid = PocketFluidCarry.entryOf(getCarried());
        if (carriedFluid != null) {
            dropCarriedFluid(slotId, carriedFluid, player);
            return;
        }

        boolean inStorage = slotId >= 0 && slotId < PocketDimensionContainer.PAGE_SIZE;
        boolean inTray = slotId >= TRAY_BASE && slotId < TRAY_BASE + PocketTrayStorage.SLOTS;
        // 1.5) 已经长按提起了一格：左键点目标格 = 两格整条对调（物品 + 流体一起换）；
        //      点同一格 = 放回原处（只取消），其它点击 = 取消提起后照原逻辑走。
        //      ⚠️ 整条搬运不经过光标：袋子里一格几千个，走光标会被 byte 数量截断。
        if (grabbedSlot >= 0) {
            int grabbedFrom = grabbedSlot;
            grabbedSlot = -1;
            // 点回原来那一格 = 纯放下（什么都不做，不再额外拿起一组）
            if (grabbedFrom == slotId) return;
            if (clickType == ClickType.PICKUP && button == 0 && inStorage && !container.isSearching()) {
                container.swapEntries(grabbedFrom, slotId);
                return;
            }
        }


        // 1) 托盘格
        if (inTray) {
            int traySlot = slotId - TRAY_BASE;
            if (tray.isFluidAt(traySlot)) {
                clickedTrayFluid(traySlot, button, player);
            } else {
                super.clicked(slotId, button, clickType, player);
            }
            return;
        }

        // 2) 存储流体格
        if (inStorage && container.isFluidSlot(slotId)) {
            clickedFluid(slotId, button, player);
            return;
        }

        // 2.5) 把手上的/光标上的流体容器倒进袋子（"第一桶"入口）：
        //      袋子里还没有这种流体时界面没有流体格可点，右键任意储物格就走这里。
        //      光标上必须是空的或是流体容器（否则玩家是在"放物品"，不该抢）；容器是空的不影响（倒不出就往下走原版）。
        //      ⚠️ 倒进**点的那一格**（"放哪是哪"），不再全袋子合并；那一格放不下就交回原版（见 pourHeldFluid）。
        if (inStorage && button == 1 && clickType == ClickType.PICKUP && !container.isSearching()) {
            ItemStack carried = getCarried();
            boolean cursorFree = carried.isEmpty()
                    || net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(carried).isPresent();
            if (cursorFree && pourHeldFluid(player, slotId)) return;
        }

        // 3) 存储物品格
        if (inStorage && clickType == ClickType.PICKUP) {
            ItemStack cursor = getCarried();
            if (button == 0) {
                if (cursor.isEmpty()) {
                    // 取走一组（按原版堆叠上限，不能超过 64）
                    ItemStack taken = container.removeItem(slotId, container.stackSizeAt(slotId));
                    if (!taken.isEmpty()) setCarried(taken);
                } else if (!container.isSearching()) {
                    ItemStack inSlot = this.slots.get(slotId).getItem();
                    if (inSlot.isEmpty() || PocketStorageHelper.sameItem(cursor, inSlot)) {
                        // 放入点击的格子（空 = 插到该位置；同类 = 合并到该格）
                        container.setItemAt(slotId, cursor);
                        setCarried(ItemStack.EMPTY);
                    } else {
                        // 异类交换：必须整格拿得起来才换（袋子里一格可能有几千个，光标最多拿一组）。
                        // ⚠️ 旧实现是"只取一组 + setItemAt 覆盖整格"，大堆叠的剩余部分会**丢失**（历史缺陷，已修）。
                        if (!container.canTakeWholeItem(slotId)) return;
                        ItemStack taken = container.takeWholeItem(slotId);
                        container.setItemAt(slotId, cursor);
                        setCarried(taken);
                    }
                }
            } else if (button == 1) {
                if (cursor.isEmpty()) {
                    // 取半组（一组的一半 = 堆叠上限的一半），不是"无限堆叠的一半"：
                    // 袋子里一格里可能存着几十万个，取一半会拿到远超一组的数量
                    ItemStack inSlot = this.slots.get(slotId).getItem();
                    if (!inSlot.isEmpty()) {
                        int half = Math.max(1, container.stackSizeAt(slotId) / 2);
                        setCarried(container.removeItem(slotId, half));
                    }
                } else if (!container.isSearching()) {
                    // 放一个到点击的格子（仅空/同类格）
                    ItemStack inSlot = this.slots.get(slotId).getItem();
                    if (inSlot.isEmpty() || PocketStorageHelper.sameItem(cursor, inSlot)) {
                        ItemStack one = cursor.copy();
                        one.setCount(1);
                        container.setItemAt(slotId, one);
                        cursor.shrink(1);
                        setCarried(cursor);
                    }
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        // 袋子必须在玩家物品栏中（主手/背包/快捷栏）；
        // 被丢弃、挪出物品栏后返回 false，MC 会自动关闭界面。
        for (ItemStack stack : player.getInventory().items) {
            if (stack == bag) return true;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        // 先处理光标上残留的流体：父类会把光标物品塞回背包/丢到地上，
        // 那时它还是本类构造的"标记桶"，必须先收走再让父类处理。
        PocketStorageHelper.FluidEntry carried = PocketFluidCarry.entryOf(getCarried());
        if (carried != null) {
            if (!player.level().isClientSide) {
                depositFluidToStorage(player, carried);
            }
            setCarried(ItemStack.EMPTY);
        }
        super.removed(player);
        // 关闭界面时强制把托盘、熔炉与条目写回袋子 NBT，防止最后状态丢失
        this.tray.save();
        this.furnace.save();
        this.container.save();
    }

    @Override
    public void broadcastChanges() {
        // 熔炉页展开时，先把服务端每 tick 推进的结果读进界面缓存，
        // 这样下面的 super.broadcastChanges() 能把两个格的新内容（输入被消耗）同步给客户端
        if (isFurnaceOpen()) furnace.refreshFromBag();
        // 铁砧/锻造台页展开时：把输入喂给原版引擎、把原版算出来的产物放进产物格
        // （放好之后再 super，原版槽位同步才会把产物发给客户端）
        if (isPanelOpen(PANEL_ANVIL)) refreshPageEngine(PANEL_ANVIL);
        else if (isPanelOpen(PANEL_SMITHING)) refreshPageEngine(PANEL_SMITHING);
        else if (isPanelOpen(PANEL_CRAFTING)) refreshPageEngine(PANEL_CRAFTING);
        else if (isPanelOpen(PANEL_DISASSEMBLE)) refreshPageEngine(PANEL_DISASSEMBLE);
        else if (isPanelOpen(PANEL_STONECUTTER)) refreshPageEngine(PANEL_STONECUTTER);
        super.broadcastChanges();
        // 条目/翻页/过滤/页操作/托盘变化时同步显示数据给客户端
        List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> slots = container.getSyncSlots();
        long trayMask = tray.buildFluidMask();
        long[] trayAmounts = tray.fluidAmounts();
        List<ItemStack> trayIcons = tray.fluidIcons();
        boolean trayChanged = panel != lastSyncedPanel
                || tray.isOut() != lastSyncedTrayOut
                || trayMask != lastSyncedTrayMask
                || !sameAmounts(lastSyncedTrayAmounts, trayAmounts)
                || !sameTrayIcons(lastSyncedTrayIcons, trayIcons);
        boolean furnaceChanged = furnace.getBurn() != lastSyncedFurnaceBurn
                || furnace.getBurnTotal() != lastSyncedFurnaceBurnTotal
                || furnace.getCook() != lastSyncedFurnaceCook
                || furnace.getCookTotal() != lastSyncedFurnaceCookTotal;
        boolean pageChanged = pageCost != lastSyncedPageCost
                || furnaceToSlot != lastSyncedFurnaceToSlot;
        if (entriesDirty
                || container.getPageOffset() != lastSyncedPage
                || !sameSlots(lastSyncedSlots, slots)
                || trayChanged
                || furnaceChanged
                || pageChanged) {
            lastSyncedPage = container.getPageOffset();
            lastSyncedSlots.clear();
            lastSyncedSlots.addAll(slots);
            entriesDirty = false;
            lastSyncedPanel = panel;
            lastSyncedTrayOut = tray.isOut();
            lastSyncedTrayMask = trayMask;
            lastSyncedTrayAmounts = trayAmounts.clone();
            lastSyncedTrayIcons = trayIcons;
            lastSyncedFurnaceBurn = furnace.getBurn();
            lastSyncedFurnaceBurnTotal = furnace.getBurnTotal();
            lastSyncedFurnaceCook = furnace.getCook();
            lastSyncedFurnaceCookTotal = furnace.getCookTotal();
            lastSyncedPageCost = pageCost;
            lastSyncedFurnaceToSlot = furnaceToSlot;
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new com.zzq.survival_toolbox.network.PocketDimensionSyncPacket(
                                container.getPageOffset(), container.getCurrentPage(),
                                container.getPageNames(), container.getPageCounts(), slots,
                                container.isShared(), container.buildFluidMask(),
                                panel, tray.isOut(), trayMask, trayAmounts, trayIcons,
                                furnace.getBurn(), furnace.getBurnTotal(),
                                furnace.getCook(), furnace.getCookTotal(),
                                pageCost, furnaceToSlot));
            }
        }
    }

    private static boolean sameAmounts(long[] a, long[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static boolean sameSlots(List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> a,
                                     List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData x = a.get(i);
            com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData y = b.get(i);
            if (x.slot() != y.slot()) return false;
            if (!PocketStorageHelper.sameItem(x.entry().stack(), y.entry().stack())
                    || x.entry().count() != y.entry().count()) {
                return false;
            }
        }
        return true;
    }

    /** 托盘流体格的显示栈有没有变（变了才重发同步包；对比物品 + 组件） */
    private static boolean sameTrayIcons(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ItemStack x = a.get(i);
            ItemStack y = b.get(i);
            if (x.isEmpty() != y.isEmpty()) return false;
            if (!x.isEmpty() && !ItemStack.matches(x, y)) return false;
        }
        return true;
    }
}
