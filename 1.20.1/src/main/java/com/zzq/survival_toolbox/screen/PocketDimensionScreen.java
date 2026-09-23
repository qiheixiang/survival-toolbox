package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket;
import com.zzq.survival_toolbox.network.PocketDimensionSearchPacket;
import com.zzq.survival_toolbox.network.PocketPageTextPacket;
import com.zzq.survival_toolbox.network.PocketSortPacket;
import com.zzq.survival_toolbox.network.PocketTrayActionPacket;
import com.zzq.survival_toolbox.util.PocketFluidCarry;
import com.zzq.survival_toolbox.util.PocketFurnace;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import com.zzq.survival_toolbox.util.PocketTrayStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.Util;
import net.minecraft.world.inventory.Slot;
import com.zzq.survival_toolbox.network.PocketGrabPacket;
import com.zzq.survival_toolbox.screen.PocketDimensionContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 随身次元袋界面（多页 + 右侧托盘页版）
 * <p>
 * 复用原版大箱子贴图（54 格），左侧 64px 页列表（滚动）：每行页名 + 空页 ✕，
 * 底部 ➕ 新建页；左键行切换页、右键行重命名；顶部搜索框跨页搜索。
 * </p>
 * <p>
 * 主窗口右边 22px 是功能页按钮条（目前只有"托盘"），点开的页面往<b>右</b>展开、
 * 主窗口位置不变：托盘页宽 176，放 9×3 = 27 格，下面放"送出/纳入"切换与操作提示。
 * 面板收起时托盘格靠 {@code Slot#isActive()} 隐身（原版既不渲染也不允许悬停）。
 * </p>
 * <p>
 * 每格右下角显示合并后的真实数量（≥1000 缩写由 {@code AbstractContainerScreenMixin}
 * 走原版渲染路径）；流体格画蓝色 mB 数字；光标上拿着流体（拖拽中）时在鼠标旁边画数量。
 * </p>
 * <p>
 * <b>1.20.1 的写法差异</b>：原版的 {@code renderSlot} 在 1.20.1 里是 {@code private}，
 * 子类没法覆写它来给槽位补画流体数量，所以这里沿用本仓库原来的做法——
 * 在 {@code render()} 里按槽位坐标自己画（存储格 + 面板展开时的托盘格），
 * 效果与 1.21.1 覆写 {@code renderSlot} 一致。
 * </p>
 */
public class PocketDimensionScreen extends AbstractContainerScreen<PocketDimensionMenu> {

    // 原版构造器：ResourceLocation.fromNamespaceAndPath 需要 Forge ≥ 47.3.19，详见 SurvivalToolbox#CHANNEL
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    /** 原版熔炉界面贴图：中间那团火（u=176,v=0,14×14）和进度箭头（u=176,v=14,24×16）都从这里取 */
    private static final ResourceLocation FURNACE_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    /** 原版锻造台界面贴图：第一格（模板格）是"平的亮格"，和另外两格的凹槽不一样 */
    private static final ResourceLocation SMITHING_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/smithing.png");
    private static final int FLAME_W = 14;
    private static final int FLAME_H = 14;
    private static final int ARROW_W = 24;
    private static final int ARROW_H = 16;
    /**
     * "空底"在原版熔炉界面贴图里的位置：灰色火苗 (56,36) 与灰色箭头 (79,34)。
     * 不烧的时候画这两个占位，烧起来再在上面叠进度图 —— 原版（和精妙背包）就是这个观感。
     */
    private static final int FLAME_BG_U = 56;
    private static final int FLAME_BG_V = 36;
    private static final int ARROW_BG_U = 79;
    private static final int ARROW_BG_V = 34;

    private static final int LIST_X = 2;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 60;
    /** 页列表高度：比原来矮一行，给下面的"并入共享"按钮腾位置 */
    private static final int LIST_H = 168;
    private static final int ROW_H = 12;
    private static final int VISIBLE_ROWS = LIST_H / ROW_H;

    /** 页列表下方的"并入共享"按钮（只在本地模式显示） */
    private static final int TRANSFER_X = LIST_X;
    private static final int TRANSFER_Y = LIST_Y + LIST_H + 2;
    private static final int TRANSFER_W = LIST_W;
    private static final int TRANSFER_H = 14;
    /** 再下面的"➕ 新页"行 */
    private static final int ADD_PAGE_Y = TRANSFER_Y + TRANSFER_H + 2;
    private static final int ADD_PAGE_H = ROW_H;

    /** 右侧按钮条里功能页按钮的尺寸/位置（窗口内坐标） */
    private static final int STRIP_BUTTON_SIZE = 18;
    private static final int STRIP_BUTTON_X = PocketDimensionMenu.BASE_WIDTH + 2;
    private static final int STRIP_BUTTON_Y = 18;
    private static final int STRIP_BUTTON_STEP = 20;

    /** 顶部一行：搜索框右边放"整理"的三个按钮 + "页/全"范围按钮（右侧那一溜只留给额外功能页） */
    private static final int SEARCH_X = PocketDimensionMenu.PAGE_LIST_WIDTH + 8;
    private static final int SEARCH_W = 90;
    // 尺寸从 16 缩到 12（加入第四个排序按钮后这一行要放 5 个按钮）
    private static final int TOP_BUTTON_SIZE = 12;
    private static final int TOP_BUTTON_GAP = 2;
    /** 与搜索框同一行（y=2..18），正好不压到下面第一排存储格（y=18 起） */
    private static final int TOP_BUTTON_Y = 2;
    private static final int TOP_BUTTON_X = SEARCH_X + SEARCH_W + 2;
    /** 第 4 个（范围）按钮的索引 */
    /** 第 1 个按钮的索引（整理范围「页/全」）：放在搜索栏后面最前面 */
    private static final int TOP_BUTTON_RANGE = 0;

    /** 铁砧页改名框（面板展开时才有）：整行宽，放在标题下面 */
    private static final int ANVIL_NAME_X = PocketDimensionMenu.PAGE_PANEL_X + 8;
    private static final int ANVIL_NAME_Y = 20;
    private static final int ANVIL_NAME_W = PocketDimensionMenu.ANVIL_PANEL_WIDTH - 16;
    private static final int ANVIL_NAME_H = 14;

    /** 整理范围：默认只整理当前页 */
    private boolean sortAllPages = false;
    /** 本界面自己创建的控件：用来清除其它 mod 添加的按钮 */
    private final java.util.Set<Object> ownWidgets =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private EditBox searchBox;
    private EditBox renameBox;
    private int renamePageIndex = -1;
    private int pageScroll = 0;

    // ---- 切石机页：配方列表（客户端算，选中的下标发给服务端）----
    private final List<String> stoneIds = new java.util.ArrayList<>();
    private final List<ItemStack> stoneIcons = new java.util.ArrayList<>();
    private ItemStack stoneListInput = ItemStack.EMPTY;
    private int stoneSelected = -1;
    private int stoneRow = 0;
    private String stoneSentId = "";
    /** 左上角：存储模式切换按钮（共享/本地） */
    private Button modeButton;
    /** 铁砧页的改名框（仅铁砧页展开时存在） */
    private EditBox nameBox;
    /** 铁砧页改名框：上一次看到的输入物品（用来判断"输入变了 → 重填框"，见 syncAnvilNameBox） */
    private ItemStack lastAnvilInput = ItemStack.EMPTY;
    /** 熔炼页的"产物去处"切换按钮（仅熔炼页展开时存在） */
    private Button furnaceOutputButton;
    private Button disPrevButton;
    private Button disNextButton;
    private Button disBulkButton;
    private Button magToggleButton;
    private Button magRangeDownButton;
    private Button magRangeUpButton;
    private Button magModeButton;
    private Button magListButton;
    private Button resToggleButton;
    private Button resScopeButton;
    /** 合成页的"自动补充"开关按钮（仅合成页展开时存在；状态存袋子 NBT，服务端权威） */
    private Button craftRefillButton;
    /** {@code rebuildTrayWidgets()} 上次是按哪个面板建的（面板一变就要重建控件，见 syncPanelWidgets） */
    private int builtPanel = -2;
    /** 每帧自增的"提示图标计时"（原版是每 30 tick 换一个图标，切换时 4 tick 淡入） */
    private long ghostTick;
    /** 模板格空着时轮换画的两个提示图标（原版 SmithingScreen 用的就是这两个，路径不带 textures/ 前缀与 .png） */
    private static final java.util.List<ResourceLocation> SMITHING_TEMPLATE_GHOSTS = java.util.List.of(
            new ResourceLocation("minecraft", "item/empty_slot_smithing_template_armor_trim"),
            new ResourceLocation("minecraft", "item/empty_slot_smithing_template_netherite_upgrade"));

    /** 托盘页里的"送出/纳入"切换按钮（仅面板展开时存在） */
    private Button trayDirectionButton;
    /** 上次布局时展开的是哪个功能页（同步包可能改变它，用来触发重新布局） */
    private int layoutPanel;
    /** 主窗口（不含右侧面板）居中时的左上角 X：展开面板时主窗口位置以它为基准 */
    private int baseLeftPos;

    public PocketDimensionScreen(PocketDimensionMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // 主窗口固定宽度：右侧按钮条/面板是在其基础上额外扩展出来的，主窗口位置不跟着动
        this.imageWidth = PocketDimensionMenu.BASE_WIDTH;
        this.imageHeight = 222;
        // 标题移到屏幕外（顶部空间让给搜索框）
        this.titleLabelY = -20;
        // "物品栏"标签：玩家背包区（140）上方
        this.inventoryLabelX = 8 + PocketDimensionMenu.PAGE_LIST_WIDTH;
        this.inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        // 先按主窗口宽度调 super.init()，让 topPos 等算好；leftPos 紧接着按"箱子区居中"重置
        this.imageWidth = PocketDimensionMenu.BASE_WIDTH;
        super.init();
        // 界面以 176 宽的箱子区居中（和原版箱子/背包界面同一个位置）：
        // 左侧页列表与右侧按钮条算"挂在两边的附件"，这样 GUI 里的玩家背包才能和屏幕下方的快捷栏对齐
        // —— 原先按整个 240 居中，会让箱子和玩家背包整体偏右 32px。
        this.leftPos = (this.width - PocketDimensionMenu.CHEST_WIDTH) / 2 - PocketDimensionMenu.PAGE_LIST_WIDTH;
        this.baseLeftPos = this.leftPos;
        this.layoutPanel = this.menu.getPanel();
        // ⚠️⚠️ 必须把 builtPanel 也置回"尚未建立"：init() 里 `layoutPanel = getPanel()` 这一句会让
        //       render 里那句"面板变了才重建控件"永远不成立，而 init() 自己又**不**调 rebuildTrayWidgets()
        //       （它只建公共控件）。于是"任何一次重新 init"（从 JEI 配方界面回来、重开袋子……）
        //       都会导致面板控件全部丢失 —— 已确认的两个表现正是这个：
        //       「选完 JEI 配方回来自动补充按钮消失」「拆解台选完配方左右切换按钮消失」。
        //       置 -2 之后，紧跟的第一帧 syncPanelWidgets() 就会把面板控件重建回来。
        this.builtPanel = -2;
        applyPanelWidth();

        // 顶部：搜索框（放在贴图标题区，不遮挡任何槽位）
        // 顶部：搜索框（放在贴图标题区，不遮挡任何槽位），右边留给整理按钮
        this.searchBox = track(new EditBox(this.font, this.leftPos + SEARCH_X, this.topPos + 4, SEARCH_W, 14,
                Component.translatable("gui.zzq_survival_toolbox.pocket.search")));
        this.searchBox.setMaxLength(32);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addWidget(this.searchBox);

        // 页重命名输入框（默认隐藏）
        this.renameBox = track(new EditBox(this.font, this.leftPos + LIST_X + 4, this.topPos + LIST_Y, LIST_W - 20, ROW_H - 2,
                Component.literal("rename")));
        this.renameBox.setMaxLength(16);
        this.renameBox.setResponder(s -> {
        });
        this.renameBox.setVisible(false);
        this.addWidget(this.renameBox);

        // 左上角：存储模式切换（共享 = 末影箱式，数据在服务器存档；本地 = 数据在袋子里）
        this.modeButton = track(Button.builder(modeLabel(), b -> onModeButtonClicked())
                .bounds(this.leftPos + 2, this.topPos + 2, 66, 14)
                .build());
        this.addRenderableWidget(this.modeButton);

        // 托盘页里的方向按钮 / 铁砧页的改名框 / 熔炼页的产物去处按钮
        if (this.menu.isTrayOpen()) {
            addTrayDirectionButton();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_ANVIL)) {
            addAnvilNameBox();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_FURNACE)) {
            addFurnaceOutputButton();
        }
    }

    /** 面板展开时窗口整体向右扩展（主窗口与 54 格位置保持不变） */
    private void applyPanelWidth() {
        // ⚠️ 刻意**不改 imageWidth**：JEI 就是按容器界面自己上报的宽度（IGuiProperties ← getXSize()）
        // 判断"右边还剩多少地方"，一看装不下就把整个配料表藏起来（lang: jei.tooltip.not.enough.space）。
        // 精妙背包的升级页也是这么处理的：面板直接画在界面外面、盖住一部分 JEI，JEI 那边完全不知情。
        // 所以这里 imageWidth 永远保持主窗口宽度，只把"实际画到哪"另算一份，用于屏幕放不下时整体左移。
        this.imageWidth = PocketDimensionMenu.BASE_WIDTH;
        int drawnWidth = PocketDimensionMenu.BASE_WIDTH + PocketDimensionMenu.BUTTON_STRIP_WIDTH
                + PocketDimensionMenu.panelWidth(this.menu.getPanel());
        // 主窗口位置保持不动；只有在"展开后整块屏幕放不下"时才整体左移（例如 GUI 缩放调得很大）
        int oldLeft = this.leftPos;
        this.leftPos = this.baseLeftPos;
        if (this.leftPos + drawnWidth > this.width) {
            this.leftPos = Math.max(0, this.width - drawnWidth);
        }
        moveWidgets(this.leftPos - oldLeft, 0);
    }

    /** 整体平移时把已经建好的控件（搜索框/重命名框/按钮）一起挪，否则它们会跟窗口错位 */
    private void moveWidgets(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        if (this.searchBox != null) {
            this.searchBox.setX(this.searchBox.getX() + dx);
            this.searchBox.setY(this.searchBox.getY() + dy);
        }
        if (this.renameBox != null) {
            this.renameBox.setX(this.renameBox.getX() + dx);
            this.renameBox.setY(this.renameBox.getY() + dy);
        }
        if (this.modeButton != null) {
            this.modeButton.setX(this.modeButton.getX() + dx);
            this.modeButton.setY(this.modeButton.getY() + dy);
        }
        if (this.trayDirectionButton != null) {
            this.trayDirectionButton.setX(this.trayDirectionButton.getX() + dx);
            this.trayDirectionButton.setY(this.trayDirectionButton.getY() + dy);
        }
        if (this.nameBox != null) {
            this.nameBox.setX(this.nameBox.getX() + dx);
            this.nameBox.setY(this.nameBox.getY() + dy);
        }
        if (this.furnaceOutputButton != null) {
            this.furnaceOutputButton.setX(this.furnaceOutputButton.getX() + dx);
            this.furnaceOutputButton.setY(this.furnaceOutputButton.getY() + dy);
        }
        for (Button b : new Button[]{this.disPrevButton, this.disNextButton, this.disBulkButton,
                this.magToggleButton, this.magModeButton, this.magRangeDownButton,
                this.magRangeUpButton, this.magListButton,
                this.resToggleButton, this.resScopeButton}) {
            if (b != null) {
                b.setX(b.getX() + dx);
                b.setY(b.getY() + dy);
            }
        }
    }

    /** 加"送出/纳入"按钮（面板展开时才有；窄条面板放不下长文案，提示走 tooltip） */
    private void addTrayDirectionButton() {
        this.trayDirectionButton = track(Button.builder(trayDirectionLabel(), b -> onTrayDirectionClicked())
                .bounds(this.leftPos + PocketDimensionMenu.TRAY_PANEL_X + 5, this.topPos + 186, 58, 20)
                .build());
        this.addRenderableWidget(this.trayDirectionButton);
    }

    /**
     * 加铁砧页的改名框（只有铁砧页展开时才有）。
     * <p>
     * 改名必须由服务端算（产物和经验花费都是原版 {@code AnvilMenu} 算的），所以框里每次变化都把
     * 文字发给服务端；清空 = 不改名（用物品自己的名字）。
     * </p>
     */
    private void addAnvilNameBox() {
        this.nameBox = track(new EditBox(this.font,
                this.leftPos + ANVIL_NAME_X, this.topPos + ANVIL_NAME_Y, ANVIL_NAME_W, ANVIL_NAME_H,
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.anvil.name_hint")));
        this.nameBox.setMaxLength(PocketPageTextPacket.MAX_LENGTH);
        // ⚠️ 初始值不能取 menu.getAnvilName()：那是**客户端**菜单里的一份副本，服务端那份从没同步过来
        //    （同步包里没有这个字段），所以永远是空串 —— 实测表现为"放入物品不会显示名称"。
        //    框里该显示什么跟原版一样看**输入物品**，所以先置空 lastAnvilInput、再让 sync 填一次。
        this.lastAnvilInput = ItemStack.EMPTY;
        this.nameBox.setValue("");
        this.nameBox.setResponder(text -> {
            // ⚠️ EditBox#setValue 在文字超过 maxLength 时会把**原文**交给 responder（只把框里的值截断），
            //    而 writeUtf(…, 50) 超长会直接抛异常把玩家踢下线 —— 所以这里必须自己再夹一次。
            String safe = text.length() > PocketPageTextPacket.MAX_LENGTH
                    ? text.substring(0, PocketPageTextPacket.MAX_LENGTH) : text;
            SurvivalToolbox.CHANNEL.sendToServer(new PocketPageTextPacket(safe));
        });
        this.addRenderableWidget(this.nameBox);
        // 原版铁砧一打开就是"等待输入"的状态（AnvilScreen#setInitialFocus），这里保持一致
        this.setFocused(this.nameBox);
        // 立刻把框填成输入物品的名字（输入物品非空时），并推给服务端 —— 与 refreshPageEngine 的取值一致
        syncAnvilNameBox();
    }

    /**
     * 改名框跟着<b>输入物品</b>走（照原版 {@code AnvilScreen#slotChanged} 的行为）：
     * 放上物品 → 框里显示它的名字；把物品拿走 → 清空。
     * <p>
     * "原版放物品会在上方自动显示名字、拿走后消失，袋子里没有"这个差异就是缺了这一步。
     * ⚠️ 只在<b>输入物品真的变了</b>的时候动框：否则每帧拿物品名去覆盖，玩家正在输入的文本会被冲掉
     * （原版那个框不允许失去焦点，这里没有采用那种做法，所以需要自行判断）。
     * </p>
     */
    private void syncAnvilNameBox() {
        if (this.nameBox == null) return;
        ItemStack input = this.menu.getAnvilInputs().getItem(0);
        // ⚠️ 只在"输入物品真的换了"时才动框（换物品 / 拿走物品；玩家正在输入的文本不能被冲掉）。
        //    判定只看物品种类 + 本名，**绝不按 NBT 全等**：袋子数据每隔一会儿会写回 NBT 再读回来，
        //    round-trip 的 NBT 细节（键顺序、补全键）不保证一致，NBT 全等会偶发误判成"变了"，
        //    把玩家刚输入的名字覆盖回物品本名 —— 服务端就当成"没改名"（花费 0、产物为空），
        //    实测表现就是「输入名字，改名后的武器拿不出来」。
        //    服务端 PocketDimensionMenu#anvilInputChanged 是同一套判定，两边必须一致。
        if (sameAnvilInput(input)) return;
        this.lastAnvilInput = input.isEmpty() ? ItemStack.EMPTY : input.copy();
        String want = input.isEmpty() ? "" : input.getHoverName().getString();
        if (want.length() > PocketPageTextPacket.MAX_LENGTH) {
            want = want.substring(0, PocketPageTextPacket.MAX_LENGTH);
        }
        if (!want.equals(this.nameBox.getValue())) {
            // setValue 会触发 responder → 把"物品本名 / 空串"发给服务端。
            // 服务端那份和原版一样：名字与物品本名相同 = 不改名，不额外扣经验。
            this.nameBox.setValue(want);
        }
    }

    /** 铁砧输入物品是否还是"同一个物品"（只看物品种类和它自己的名字，不看 NBT 细节，原因见 syncAnvilNameBox） */
    private boolean sameAnvilInput(ItemStack input) {
        if (input.isEmpty() != this.lastAnvilInput.isEmpty()) return false;
        if (input.isEmpty()) return true;
        if (!input.is(this.lastAnvilInput.getItem())) return false;
        return input.getHoverName().getString().equals(this.lastAnvilInput.getHoverName().getString());
    }

    /** 加熔炼页的"产物去处"切换按钮（窄面板放不下长文案，说明走 tooltip） */
    private void addFurnaceOutputButton() {
        this.furnaceOutputButton = track(Button.builder(furnaceOutputLabel(), b -> onFurnaceOutputClicked())
                .bounds(this.leftPos + PocketDimensionMenu.PAGE_PANEL_X + 8, this.topPos + 98, 68, 16)
                .build());
        this.addRenderableWidget(this.furnaceOutputButton);
    }

    /**
     * 点"产物去处"：在"放产物格"（默认，原版熔炉那样）与"直接进储物空间"之间切。
     * <p>
     * 这个状态跟着袋子走（写袋子 NBT），所以走服务端；本地先切一下让按钮立刻有反应。
     * </p>
     */
    private void onFurnaceOutputClicked() {
        boolean toSlot = !this.menu.isFurnaceToSlot();
        this.menu.applyFurnaceToSlotLocal(toSlot);
        SurvivalToolbox.CHANNEL.sendToServer(new PocketTrayActionPacket(toSlot
                ? PocketTrayActionPacket.ACTION_FURNACE_TO_SLOT
                : PocketTrayActionPacket.ACTION_FURNACE_TO_STORAGE));
    }

    /** 产物去处按钮的文案 */
    private Component furnaceOutputLabel() {
        return Component.translatable(this.menu.isFurnaceToSlot()
                ? "gui.zzq_survival_toolbox.pocket.page.furnace.to_slot"
                : "gui.zzq_survival_toolbox.pocket.page.furnace.to_storage");
    }

    /**
     * 加拆解页底部的三个小按钮：上一个变体 / 下一个变体 / 全部拆解。
     * <p>
     * 变体翻页在合成模式下自动变成"上一条/下一条配方"（服务端按输入槽是否为空决定点原版哪个按钮），
     * 所以界面这边只发"上一个/下一个"，不用自己判断模式。
     * </p>
     */
    private void addDisassembleButtons() {
        int x = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int y = this.topPos + PocketDimensionMenu.DIS_BUTTON_Y;
        this.disPrevButton = track(Button.builder(Component.literal("←"), b -> onDisassemblePage(-1))
                .bounds(x + 8, y, 16, 16).build());
        this.disNextButton = track(Button.builder(Component.literal("→"), b -> onDisassemblePage(1))
                .bounds(x + 26, y, 16, 16).build());
        this.disBulkButton = track(Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.pocket.page.disassemble.bulk"),
                        b -> onDisassembleBulk())
                .bounds(x + 76, y, 48, 16).build());
        this.disBulkButton.visible = !this.menu.getDisassembleInputs().getItem(0).isEmpty();
        this.addRenderableWidget(this.disPrevButton);
        this.addRenderableWidget(this.disNextButton);
        this.addRenderableWidget(this.disBulkButton);
    }

    /** 点拆解页的翻页按钮：-1 = 上一个变体/上一条配方，1 = 下一个 */
    private void onDisassemblePage(int step) {
        SurvivalToolbox.CHANNEL.sendToServer(new PocketTrayActionPacket(step < 0
                ? PocketTrayActionPacket.ACTION_DIS_PREV
                : PocketTrayActionPacket.ACTION_DIS_NEXT));
    }

    /** 点"全部拆解"：把输入槽里那一叠全拆了（服务端走原版批量拆解） */
    private void onDisassembleBulk() {
        SurvivalToolbox.CHANNEL.sendToServer(new PocketTrayActionPacket(PocketTrayActionPacket.ACTION_DIS_BULK));
    }

    /** 记录：这是本界面自己的控件（用来清除其它 mod 添加的按钮） */
    private <T extends net.minecraft.client.gui.components.events.GuiEventListener> T track(T widget) {
        ownWidgets.add(widget);
        return widget;
    }

    /**
     * 把"不是本界面自己的"控件全部移除。
     * <p>
     * 其他整理/背包增强 mod 会在任意容器界面上加自己的按钮（有的还带自动排序），
     * 它们并不认识本界面"按类型合并 + long 数量"的虚拟堆叠，点了容易出问题；
     * 因此在 {@code ScreenEvent.Init.Post} 里（此时其它 mod 的按钮已经添加完毕）把它们清掉，
     * 只保留本界面自己的控件——整理统一使用本界面自己的按钮。
     * </p>
     */
    public void removeForeignWidgets() {
        for (net.minecraft.client.gui.components.events.GuiEventListener child
                : new java.util.ArrayList<>(this.children())) {
            if (!ownWidgets.contains(child)) {
                removeWidget(child);
            }
        }
    }

    /** 面板开关后重建页内控件（方向按钮 / 改名框 / 产物去处按钮；面板收起时它们不该存在） */
    private void rebuildTrayWidgets() {
        if (this.trayDirectionButton != null) {
            this.removeWidget(this.trayDirectionButton);
            this.trayDirectionButton = null;
        }
        if (this.nameBox != null) {
            this.removeWidget(this.nameBox);
            this.nameBox = null;
        }
        if (this.furnaceOutputButton != null) {
            this.removeWidget(this.furnaceOutputButton);
            this.furnaceOutputButton = null;
        }
        if (this.disPrevButton != null) {
            this.removeWidget(this.disPrevButton);
            this.disPrevButton = null;
        }
        if (this.disNextButton != null) {
            this.removeWidget(this.disNextButton);
            this.disNextButton = null;
        }
        if (this.disBulkButton != null) {
            this.removeWidget(this.disBulkButton);
            this.disBulkButton = null;
        }
        for (Button b : new Button[]{this.magToggleButton, this.magModeButton,
                this.magRangeDownButton, this.magRangeUpButton, this.magListButton,
                this.resToggleButton, this.resScopeButton, this.craftRefillButton}) {
            if (b != null) this.removeWidget(b);
        }
        this.magToggleButton = null;
        this.magModeButton = null;
        this.magRangeDownButton = null;
        this.magRangeUpButton = null;
        this.magListButton = null;
        this.resToggleButton = null;
        this.resScopeButton = null;
        this.craftRefillButton = null;
        if (this.menu.isTrayOpen()) {
            addTrayDirectionButton();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_ANVIL)) {
            addAnvilNameBox();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_FURNACE)) {
            addFurnaceOutputButton();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_CRAFTING)) {
            addCraftRefillButton();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_DISASSEMBLE)) {
            addDisassembleButtons();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_MAGNET)) {
            addMagnetButtons();
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_RESTOCK)) {
            addRestockButtons();
        }
        this.builtPanel = currentPanelKey();
    }

    /**
     * 现在"展开的功能页"是哪一个（托盘单独算一档，避免和 {@code getPanel()} 的编号撞车）。
     * <p>用来判断"控件要不要重建"：面板状态是服务端同步过来的，随时可能变。</p>
     */
    private int currentPanelKey() {
        return this.menu.isTrayOpen() ? -100 : this.menu.getPanel();
    }

    /**
     * ⚠️ 面板状态一变就重建控件。
     * <p>
     * 面板状态会被<b>服务端同步包</b>改（例如 JEI 的"+"自动打开合成页、从别的界面切回来），
     * 而控件只在 {@code init()} 和"玩家点按钮切页"时建过 —— 于是出现"面板画出来了、按钮却没有"。
     * 实测：从 JEI 配方界面回来、或 JEI 自动打开合成页时，<b>自动补充按钮会消失</b>。
     * 这里每帧比一下当前面板，变了就重建（重建只在变化那一帧发生，不影响性能）。
     * </p>
     */
    private void syncPanelWidgets() {
        if (currentPanelKey() != this.builtPanel) {
            rebuildTrayWidgets();
        }
    }

    /** 当前模式的按钮文案 */
    private Component modeLabel() {
        return Component.translatable(this.menu.isSharedMode()
                ? "gui.zzq_survival_toolbox.pocket.mode.shared"
                : "gui.zzq_survival_toolbox.pocket.mode.local");
    }

    /** 托盘方向按钮文案：窄条里只放得下两个字，说明见 tooltip */
    private Component trayDirectionLabel() {
        return Component.translatable(this.menu.getTray().isOut()
                ? "gui.zzq_survival_toolbox.pocket.tray.out_short"
                : "gui.zzq_survival_toolbox.pocket.tray.in_short");
    }

    /**
     * 点切换按钮：
     * 切到"本地"前先弹确认（本地数据存在袋子里，袋子遗失就找不回来）；
     * 切回"共享"直接切（物品不会丢失，只是换成服务器存档那一份）。
     */
    private void onModeButtonClicked() {
        if (this.menu.isSharedMode()) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new net.minecraft.client.gui.screens.ConfirmScreen(
                            confirmed -> {
                                net.minecraft.client.Minecraft.getInstance().setScreen(this);
                                if (confirmed) {
                                    SurvivalToolbox.CHANNEL.sendToServer(
                                            new com.zzq.survival_toolbox.network.PocketModeTogglePacket(false));
                                }
                            },
                            Component.translatable("gui.zzq_survival_toolbox.pocket.mode.confirm.title"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.mode.confirm.message"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.mode.confirm.yes"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.mode.confirm.no")));
        } else {
            SurvivalToolbox.CHANNEL.sendToServer(new com.zzq.survival_toolbox.network.PocketModeTogglePacket(true));
        }
    }

    /**
     * 点"送出/纳入"：切换托盘方向。
     * 方向存在袋子 NBT 里（关掉界面后 Shift+右键容器还要用它），所以必须走服务端；
     * 本地先切一下让按钮立刻有反应，服务端同步包随后确认。
     */
    private void onTrayDirectionClicked() {
        boolean toOut = !this.menu.getTray().isOut();
        this.menu.getTray().setOut(toOut);
        SurvivalToolbox.CHANNEL.sendToServer(new PocketTrayActionPacket(
                toOut ? PocketTrayActionPacket.ACTION_SET_OUT : PocketTrayActionPacket.ACTION_SET_IN));
    }

    /** 点右侧按钮条：开关托盘页 */
    private void toggleTrayPanel() {
        togglePage(PocketDimensionMenu.PANEL_TRAY, PocketTrayActionPacket.ACTION_OPEN);
    }

    /** 点右侧按钮条：开关合成页 */
    private void toggleCraftingPanel() {
        togglePage(PocketDimensionMenu.PANEL_CRAFTING, PocketTrayActionPacket.ACTION_OPEN_CRAFTING);
    }

    /** 点右侧按钮条：开关拆解台页 */
    private void toggleDisassemblePanel() {
        togglePage(PocketDimensionMenu.PANEL_DISASSEMBLE, PocketTrayActionPacket.ACTION_OPEN_DISASSEMBLE);
    }

    /** 点右侧按钮条：开关磁铁页 */
    private void toggleMagnetPanel() {
        togglePage(PocketDimensionMenu.PANEL_MAGNET, PocketTrayActionPacket.ACTION_OPEN_MAGNET);
    }

    /** 点右侧按钮条：开关切石机页 */
    private void toggleStonecutterPanel() {
        togglePage(PocketDimensionMenu.PANEL_STONECUTTER, PocketTrayActionPacket.ACTION_OPEN_STONECUTTER);
    }

    /** 点右侧按钮条：开关补货页 */
    private void toggleRestockPanel() {
        togglePage(PocketDimensionMenu.PANEL_RESTOCK, PocketTrayActionPacket.ACTION_OPEN_RESTOCK);
    }

    /** 补货页的两个按钮：开关 / 范围（都只发动作包，状态服务端权威） */
    private void addRestockButtons() {
        int x = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int y = this.topPos + PocketDimensionMenu.RES_BUTTON_Y;
        this.resToggleButton = track(Button.builder(restockToggleLabel(), b -> magAction(PocketTrayActionPacket.ACTION_RES_TOGGLE))
                .bounds(x + 8, y, 56, 16).build());
        this.resScopeButton = track(Button.builder(restockScopeLabel(), b -> magAction(PocketTrayActionPacket.ACTION_RES_SCOPE))
                .bounds(x + 68, y, 58, 16).build());
        this.addRenderableWidget(this.resToggleButton);
        this.addRenderableWidget(this.resScopeButton);
    }

    private Component restockToggleLabel() {
        return Component.translatable(this.menu.isRestockOn()
                ? "gui.zzq_survival_toolbox.pocket.page.restock.on"
                : "gui.zzq_survival_toolbox.pocket.page.restock.off");
    }

    private Component restockScopeLabel() {
        return Component.translatable(this.menu.isRestockHotbarOnly()
                ? "gui.zzq_survival_toolbox.pocket.page.restock.hotbar"
                : "gui.zzq_survival_toolbox.pocket.page.restock.all");
    }

    /**
     * 合成页的"自动补充"开关按钮（对照精妙背包的实现增加这样一个切换按钮）。
     * <p>
     * 文案：{@code 自动补充 / 不补充}；说明走 tooltip（面板窄，写不下长句子）。
     * 位置贴着九宫格底下（y = 68，面板高 88，不会顶到底边）。
     * </p>
     */
    private void addCraftRefillButton() {
        // 位置：面板**右上角** ——以前放 y=68 会压在九宫格最下面那一行上（实测重叠）。
        // 九宫格从 y=26 开始、占 54px 高，标题在左上角占一行，所以右上角这条 48×14 是空的。
        int w = PocketDimensionMenu.CRAFTING_PANEL_WIDTH;
        this.craftRefillButton = track(Button.builder(craftRefillLabel(), b -> onCraftRefillClicked())
                .bounds(this.leftPos + PocketDimensionMenu.PAGE_PANEL_X + w - 50, this.topPos + 2, 48, 14)
                .build());
        this.addRenderableWidget(this.craftRefillButton);
    }

    /**
     * 点"自动补充"：在"自动补充 / 不补充"之间切。
     * <p>
     * 状态跟着袋子走（写袋子 NBT），所以走服务端；本地先切换一次让按钮立刻有反应，
     * 服务端的数字槽同步随后确认（和精妙背包以及"产物去处"按钮的做法一致）。
     * </p>
     */
    private void onCraftRefillClicked() {
        boolean on = !this.menu.isCraftRefillOn();
        this.menu.applyCraftRefillLocal(on);
        SurvivalToolbox.CHANNEL.sendToServer(
                new PocketTrayActionPacket(PocketTrayActionPacket.ACTION_CRAFT_REFILL_TOGGLE));
    }

    /** "自动补充"按钮的文案 */
    private Component craftRefillLabel() {
        return Component.translatable(this.menu.isCraftRefillOn()
                ? "gui.zzq_survival_toolbox.pocket.page.crafting.refill.on"
                : "gui.zzq_survival_toolbox.pocket.page.crafting.refill.off");
    }

    /**
     * 磁铁页的 5 个按钮：开关 / 模式 / 范围 −＋ / 名单方向。
     * <p>状态都是服务端权威（存袋子 NBT），客户端只发动作包，文案每帧按同步来的值刷新。</p>
     */
    private void addMagnetButtons() {
        int x = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int y1 = this.topPos + PocketDimensionMenu.MAG_BUTTON_Y;
        int y2 = this.topPos + PocketDimensionMenu.MAG_BUTTON2_Y;
        this.magToggleButton = track(Button.builder(magnetToggleLabel(), b -> magAction(PocketTrayActionPacket.ACTION_MAG_TOGGLE))
                .bounds(x + 8, y1, 56, 16).build());
        this.magModeButton = track(Button.builder(magnetModeLabel(), b -> magAction(PocketTrayActionPacket.ACTION_MAG_MODE))
                .bounds(x + 68, y1, 58, 16).build());
        this.magRangeDownButton = track(Button.builder(Component.literal("-"), b -> magAction(PocketTrayActionPacket.ACTION_MAG_RANGE_DOWN))
                .bounds(x + 8, y2, 16, 16).build());
        this.magRangeUpButton = track(Button.builder(Component.literal("+"), b -> magAction(PocketTrayActionPacket.ACTION_MAG_RANGE_UP))
                .bounds(x + 26, y2, 16, 16).build());
        this.magListButton = track(Button.builder(magnetListLabel(), b -> magAction(PocketTrayActionPacket.ACTION_MAG_LIST_MODE))
                .bounds(x + 108, this.topPos + 1, 38, 16).build());
        this.addRenderableWidget(this.magToggleButton);
        this.addRenderableWidget(this.magModeButton);
        this.addRenderableWidget(this.magRangeDownButton);
        this.addRenderableWidget(this.magRangeUpButton);
        this.addRenderableWidget(this.magListButton);
    }

    private void magAction(int action) {
        SurvivalToolbox.CHANNEL.sendToServer(new PocketTrayActionPacket(action));
    }

    private Component magnetToggleLabel() {
        return Component.translatable(this.menu.isMagnetOn()
                ? "gui.zzq_survival_toolbox.pocket.page.magnet.on"
                : "gui.zzq_survival_toolbox.pocket.page.magnet.off");
    }

    private Component magnetModeLabel() {
        return Component.translatable(this.menu.isMagnetOnlyExisting()
                ? "gui.zzq_survival_toolbox.pocket.page.magnet.mode_existing"
                : "gui.zzq_survival_toolbox.pocket.page.magnet.mode_all");
    }

    private Component magnetListLabel() {
        return Component.translatable(this.menu.isMagnetWhiteList()
                ? "gui.zzq_survival_toolbox.pocket.page.magnet.whitelist"
                : "gui.zzq_survival_toolbox.pocket.page.magnet.blacklist");
    }

    /** 点右侧按钮条：开关熔炉页 */
    private void toggleFurnacePanel() {
        togglePage(PocketDimensionMenu.PANEL_FURNACE, PocketTrayActionPacket.ACTION_OPEN_FURNACE);
    }

    /** 点右侧按钮条：开关铁砧页 */
    private void toggleAnvilPanel() {
        togglePage(PocketDimensionMenu.PANEL_ANVIL, PocketTrayActionPacket.ACTION_OPEN_ANVIL);
    }

    /** 点右侧按钮条：开关锻造台页 */
    private void toggleSmithingPanel() {
        togglePage(PocketDimensionMenu.PANEL_SMITHING, PocketTrayActionPacket.ACTION_OPEN_SMITHING);
    }

    /** 开关某个功能页（同时只展开一个；点已展开的按钮 = 收起） */
    private void togglePage(int panel, int openAction) {
        boolean open = !this.menu.isPanelOpen(panel);
        this.menu.applyPanelLocal(open ? panel : PocketDimensionMenu.PANEL_NONE);
        SurvivalToolbox.CHANNEL.sendToServer(new PocketTrayActionPacket(
                open ? openAction : PocketTrayActionPacket.ACTION_CLOSE));
        this.layoutPanel = this.menu.getPanel();
        applyPanelWidth();
        rebuildTrayWidgets();
    }

    /** 搜索输入 → 发送关键词到服务端跨页搜索 */
    private void onSearchChanged(String text) {
        SurvivalToolbox.CHANNEL.sendToServer(new PocketDimensionSearchPacket(text));
    }

    /** 长按提起整格：按住多久算"长按" */
    private static final long GRAB_HOLD_MS = 400L;
    /** 当前按住不放的格子（-1 = 没按住） */
    private int grabHoldSlot = -1;
    private long grabHoldStart = 0L;
    /** 这一按是否已经当成"长按提起"处理过（决定松开时要不要吞掉这次点击） */
    private boolean grabFired = false;
    /** 本地记的"已提起"格，只用来画高亮；真正的状态在服务端（PocketGrabPacket） */
    private int grabbedSlot = -1;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.renameBox.isVisible()) {
            if (this.renameBox.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(this.renameBox);
                return true;
            }
            // 点击输入框外：提交当前输入
            submitRename();
            this.setFocused(null);
            this.renameBox.setVisible(false);
            this.renamePageIndex = -1;
        }
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.searchBox);
            return true;
        }
        if (this.nameBox != null && this.nameBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.nameBox);
            return true;
        }
        // 铁砧页：输入物品换了就把名字框重填（原版铁砧的行为）
        syncAnvilNameBox();
        // ⚠️⚠️ 点产物格时**绝对不能**去动那个改名框（哪怕只是"清干净"）：
        //      EditBox#setValue 是**会触发 responder 的**，也就是说这一下清空会把一个**空名字**
        //      发给服务端 —— 而且这个文本包排在那一下"点击产物格"的包**之前**。
        //      服务端于是先收到"不改名了"，重算一遍：名字 == 物品本名 = 没改名 → 花费 0 → **产物直接变空**；
        //      紧接着那个点击包到达时，clickedPageResult 看到产物为空就 return 了 —— 玩家看到的就是
        //      「输入名字，改名后的物品拿不出来」（两个版本都存在此问题，已多次出现）。
        //      清理改名必须**只由服务端在真的取走之后**做（clickedPageResult 里的 anvilName = ""），
        //      客户端这边跟着"输入物品被取空"自动清框（syncAnvilNameBox）就够了。
        //      这里反过来做：点产物格之前把框里现有的名字**再发一次**，保证服务端拿到的就是玩家眼前这个名字。
        if (this.nameBox != null && isOverAnvilResult(mouseX, mouseY)) {
            String safe = this.nameBox.getValue();
            if (safe.length() > PocketPageTextPacket.MAX_LENGTH) {
                safe = safe.substring(0, PocketPageTextPacket.MAX_LENGTH);
            }
            SurvivalToolbox.CHANNEL.sendToServer(new PocketPageTextPacket(safe));
        }
        if (handlePageListClick(mouseX, mouseY, button)) {
            return true;
        }
        if (handleTopButtonsClick(mouseX, mouseY, button)) {
            return true;
        }
        if (handleStonecutterListClick(mouseX, mouseY)) {
            return true;
        }
        if (handleStripClick(mouseX, mouseY, button)) {
            return true;
        }
        // 长按提起整格：按下先记着，按住不动才算长按。
        // 原版的格子点击是在**松开**时才发生的，所以按住期间什么都不会发生，
        // 松手时再判断"是长按还是单击"——单击行为完全不变。
        startGrabHold(button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.stoneDraggingThumb = false;
        if (this.grabFired && button == 0) {
            // 这一按已经算"长按提起整格"：这次松开**不能**再当单击（否则会连带把一组物品拿起来）
            this.grabHoldSlot = -1;
            this.grabFired = false;
            return true;
        }
        this.grabHoldSlot = -1;
        this.grabFired = false;
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        // 普通点击之后服务端也会清掉"提起"状态，客户端高亮跟着清
        this.grabbedSlot = -1;
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.handleStonecutterScrollDrag(mouseY)) return true;
        if (this.grabFired) {
            // 已经提起整格了：这一拖不该再触发原版的"拖拽分配"
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * 长按判定：在袋子里的格子上按住 {@link #GRAB_HOLD_MS} 毫秒不动 = 提起整格。
     * <p>
     * 只对袋子自己的存储格生效（托盘 / 玩家背包不受影响）；搜索视图只是视图，不给移动。
     * 已经提起一格时按下 = "落位"，不再进长按。
     * </p>
     */
    private void startGrabHold(int button) {
        if (button != 0) return;
        if (this.menu.getPocketContainer().isSearching()) return;
        Slot hovered = this.getSlotUnderMouse();
        if (hovered == null) return;
        int index = hovered.index;
        if (index < 0 || index >= PocketDimensionContainer.PAGE_SIZE) return;
        if (this.grabbedSlot >= 0 && this.grabbedSlot != index) return;
        this.grabHoldSlot = index;
        this.grabHoldStart = Util.getMillis();
        this.grabFired = false;
    }

    /** 每帧查一次长按计时（放在 render 里比按 tick 判断精细；光标移到别的格子就作废） */
    private void updateGrabHold() {
        if (this.grabHoldSlot < 0 || this.grabFired) return;
        Slot hovered = this.getSlotUnderMouse();
        if (hovered == null || hovered.index != this.grabHoldSlot) {
            this.grabHoldSlot = -1;
            return;
        }
        if (Util.getMillis() - this.grabHoldStart < GRAB_HOLD_MS) return;
        this.grabFired = true;
        // 再长按同一格 = 放下（取消）。服务端按同一套规则自己算，两边状态一致。
        this.grabbedSlot = (this.grabbedSlot == this.grabHoldSlot) ? -1 : this.grabHoldSlot;
        SurvivalToolbox.CHANNEL.sendToServer(new PocketGrabPacket(this.grabHoldSlot));
    }

    /** "提起整格"高亮：画在提起的那一格上（黄框 + 半透明底） */
    private void renderGrabHighlight(GuiGraphics gui) {
        if (this.grabbedSlot < 0 || this.grabbedSlot >= this.menu.slots.size()) return;
        Slot slot = this.menu.slots.get(this.grabbedSlot);
        int x = this.leftPos + slot.x;
        int y = this.topPos + slot.y;
        gui.fill(x, y, x + 16, y + 16, 0x66FFFF55);
        gui.fill(x, y, x + 16, y + 1, 0xFFFFEE44);
        gui.fill(x, y + 15, x + 16, y + 16, 0xFFFFEE44);
        gui.fill(x, y, x + 1, y + 16, 0xFFFFEE44);
        gui.fill(x + 15, y, x + 16, y + 16, 0xFFFFEE44);
    }

    /**
     * 按键处理：搜索/重命名输入框获得焦点时，按键必须先由输入框消费。
     * <p>
     * 原版 {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen#keyPressed}
     * 会把「打开/关闭背包」按键（默认 E）当作关闭界面处理；而 EditBox 对字母键
     * 返回 false（字母是在 charTyped 中插入的），按键于是冒泡到父类，
     * 造成在搜索框里输入 e 就关闭次元袋界面。
     * 此处参照原版铁砧界面的处理方式：输入框可消费输入时不再调用父类，
     * 仅放行 ESC，保证仍能正常关闭界面。
     * </p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (this.searchBox != null && this.searchBox.canConsumeInput()) {
                this.searchBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
            if (this.renameBox != null && this.renameBox.canConsumeInput()) {
                this.renameBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
            if (this.nameBox != null && this.nameBox.canConsumeInput()) {
                this.nameBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 右侧按钮条点击：只放额外功能页 —— [0] 托盘页、[1] 铁砧页、[2] 锻造台页、[3] 熔炉页。
     * <p>
     * 铁砧/锻造台同样像托盘、熔炉那样<b>在次元袋界面里展开</b>（不离开主界面），
     * 所以这里改成开关本界面自己的页，不再去开原版界面。
     * 整理相关按钮在顶部搜索框右边那一行（见 {@link #handleTopButtonsClick}）。
     * </p>
     */
    private boolean handleStripClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isOverStripButton(mouseX, mouseY, 0)) {
            toggleTrayPanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 1)) {
            toggleAnvilPanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 2)) {
            toggleSmithingPanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 3)) {
            toggleFurnacePanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 4)) {
            toggleCraftingPanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 5)) {
            toggleDisassemblePanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 6)) {
            toggleMagnetPanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 8)) {
            toggleStonecutterPanel();
            return true;
        }
        if (isOverStripButton(mouseX, mouseY, 7)) {
            toggleRestockPanel();
            return true;
        }
        return false;
    }

    /**
     * 鼠标是否点在铁砧页的产物格上。
     * <p>
     * ⚠️ 以前这里叫 {@code clearAnvilNameIfResultClicked}，会连带把改名框清空 —— 这是**有害的**：
     * {@code EditBox#setValue} 会触发 responder，清空就等于给服务端发了个"不改名"，
     * 排在那一下点击之前到达 → 产物被算成空 → 点了拿不出来（详见 mouseClicked 里的说明）。
     * 改名框的清理只由服务端在**真的取走之后**负责。
     * </p>
     */
    private boolean isOverAnvilResult(double mouseX, double mouseY) {
        if (!this.menu.isPanelOpen(PocketDimensionMenu.PANEL_ANVIL)) return false;
        double lx = mouseX - this.leftPos - PocketDimensionMenu.ANVIL_RESULT_X;
        double ly = mouseY - this.topPos - PocketDimensionMenu.ANVIL_RESULT_Y;
        return lx >= 0 && lx < 18 && ly >= 0 && ly < 18;
    }

    /**
     * 顶部一行按钮点击：[0] 整理范围（页 / 全，放在最前面）、[1~3] 三种整理方式
     * （点击哪个就按"当前范围 + 该方式"直接整理）。
     */
    private boolean handleTopButtonsClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isOverTopButton(mouseX, mouseY, TOP_BUTTON_RANGE)) {
            toggleSortRange();
            return true;
        }
        for (int i = 0; i < 4; i++) {   // ⚠️ 加入"按标签"档后就是 4 个（写死 3 会导致第 4 个按钮点击无反应）
            if (isOverTopButton(mouseX, mouseY, 1 + i)) {
                requestSort(i);
                return true;
            }
        }
        return false;
    }

    /** 直接按指定方式整理（范围用当前设置：页 / 全） */
    private void requestSort(int sortBy) {
        SurvivalToolbox.CHANNEL.sendToServer(new PocketSortPacket(
                this.sortAllPages ? PocketSortPacket.SCOPE_ALL_PAGES : PocketSortPacket.SCOPE_CURRENT_PAGE,
                sortBy));
    }

    /** 切换整理范围：当前页 ⇄ 全部页 */
    private void toggleSortRange() {
        this.sortAllPages = !this.sortAllPages;
    }

    /** 范围小按钮上的短标签：页 / 全 */
    private Component sortRangeLabel() {
        return Component.translatable(this.sortAllPages
                ? "gui.zzq_survival_toolbox.pocket.sort.range.all"
                : "gui.zzq_survival_toolbox.pocket.sort.range.page");
    }

    /** tooltip 里的长标签：当前页 / 全部页（整体排序） */
    private Component sortRangeLongLabel() {
        return Component.translatable(this.sortAllPages
                ? "gui.zzq_survival_toolbox.pocket.sort.range.all_long"
                : "gui.zzq_survival_toolbox.pocket.sort.range.page_long");
    }

    /** 第 sortBy 个整理按钮的说明（0 = 名字 / 1 = 同 mod / 2 = 数量） */
    private Component sortTipLabel(int sortBy) {
        return Component.translatable(switch (sortBy) {
            case PocketSortPacket.BY_MOD -> "gui.zzq_survival_toolbox.pocket.sort.by.mod_tip";
            case PocketSortPacket.BY_COUNT -> "gui.zzq_survival_toolbox.pocket.sort.by.count_tip";
            case PocketSortPacket.BY_TAG -> "gui.zzq_survival_toolbox.pocket.sort.by.tag_tip";
            default -> "gui.zzq_survival_toolbox.pocket.sort.by.name_tip";
        });
    }

    /** 页列表点击：行 = 切页/删除/重命名，"并入共享" = 一键转移，底部 ➕ = 新建 */
    private boolean handlePageListClick(double mouseX, double mouseY, int button) {
        double lx = mouseX - this.leftPos;
        double ly = mouseY - this.topPos;
        if (lx < LIST_X || lx > LIST_X + LIST_W) return false;
        List<String> names = this.menu.getPocketContainer().getPageNames();
        // ➕ 新建按钮
        if (ly >= ADD_PAGE_Y && ly <= ADD_PAGE_Y + ADD_PAGE_H) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_ADD, 0, ""));
            return true;
        }
        // "并入共享"（只在本地模式显示；本地为空时不可点）
        if (!this.menu.isSharedMode() && isOverTransferButton(mouseX, mouseY)) {
            if (button == 0 && hasLocalContent()) {
                onMergeToSharedClicked();
            }
            return true;
        }
        if (ly < LIST_Y || ly >= LIST_Y + LIST_H) return false;
        int row = (int) ((ly - LIST_Y) / ROW_H);
        int index = pageScroll + row;
        if (index < 0 || index >= names.size()) return false;
        // ✕ 删除（仅空页）
        if (button == 0 && lx >= LIST_X + LIST_W - 10 && isPageEmpty(index)) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_REMOVE, index, ""));
            return true;
        }
        if (button == 0) {
            // 左键：切换页
            SurvivalToolbox.CHANNEL.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_SET_PAGE, index, ""));
        } else if (button == 1) {
            // 右键：重命名
            startRename(index);
        }
        return true;
    }

    private boolean isPageEmpty(int index) {
        List<Integer> counts = this.menu.getPocketContainer().getPageCounts();
        if (index < 0 || index >= counts.size()) return false;
        return counts.get(index) == 0;
    }

    private void startRename(int index) {
        List<String> names = this.menu.getPocketContainer().getPageNames();
        if (index < 0 || index >= names.size()) return;
        this.renamePageIndex = index;
        this.renameBox.setValue(names.get(index));
        this.renameBox.setVisible(true);
        this.renameBox.setY(this.topPos + LIST_Y + (index - pageScroll) * ROW_H);
        this.renameBox.setX(this.leftPos + LIST_X + 2);
        this.setFocused(this.renameBox);
    }

    private void submitRename() {
        if (this.renamePageIndex >= 0) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketDimensionPageActionPacket(
                    PocketDimensionPageActionPacket.ACTION_RENAME, this.renamePageIndex, this.renameBox.getValue()));
        }
        this.renameBox.setVisible(false);
        this.renamePageIndex = -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 切石机页的配方列表：滚轮在列表矩形里 = 翻页
        if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_STONECUTTER)
                && this.stoneRowCount() > PocketDimensionMenu.STONE_LIST_ROWS) {
            int sx = (int) mouseX - this.leftPos;
            int sy = (int) mouseY - this.topPos;
            if (sx >= PocketDimensionMenu.STONE_LIST_X - 2
                    && sx <= PocketDimensionMenu.STONE_LIST_X + PocketDimensionMenu.STONE_LIST_W
                    && sy >= PocketDimensionMenu.STONE_LIST_Y - 2
                    && sy <= PocketDimensionMenu.STONE_LIST_Y + PocketDimensionMenu.STONE_LIST_H) {
                int maxRow = this.stoneRowCount() - PocketDimensionMenu.STONE_LIST_ROWS;
                this.stoneRow = Math.max(0, Math.min(this.stoneRow - (int) delta, maxRow));
                return true;
            }
        }
        double lx = mouseX - this.leftPos;
        if (lx >= LIST_X && lx <= LIST_X + LIST_W) {
            List<String> names = this.menu.getPocketContainer().getPageNames();
            int maxScroll = Math.max(0, names.size() - VISIBLE_ROWS);
            this.pageScroll = Math.max(0, Math.min(pageScroll - (int) delta, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        gui.blit(TEXTURE, this.leftPos + PocketDimensionMenu.PAGE_LIST_WIDTH, this.topPos, 0, 0,
                176, this.imageHeight, 256, 256);
        // 右侧功能页按钮条（深色底，和左侧页列表一个风格）
        int stripX = this.leftPos + PocketDimensionMenu.BASE_WIDTH;
        gui.fill(stripX, this.topPos, stripX + PocketDimensionMenu.BUTTON_STRIP_WIDTH,
                this.topPos + this.imageHeight, 0xC0101010);
        // 展开的功能页面板：照精妙背包那样做成一块"浮在主窗口右边的卡片"（带立体边框），
        // 高度按内容给，不像以前那样拉满整窗留一大片空白
        int openPanel = this.menu.getPanel();
        if (openPanel != PocketDimensionMenu.PANEL_NONE) {
            int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
            int py = this.topPos;
            int w = PocketDimensionMenu.panelWidth(openPanel);
            int h = openPanel == PocketDimensionMenu.PANEL_TRAY
                    ? this.imageHeight : PocketDimensionMenu.panelHeight(openPanel);
            drawPanelCard(gui, px, py, w, h);
            // 槽位凹槽：直接复用原版贴图里的单格（第一格凹槽在 u=7,v=17，尺寸 18×18）
            if (openPanel == PocketDimensionMenu.PANEL_TRAY) {
                for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
                    int gx = px + 8 + (i % PocketDimensionMenu.TRAY_COLS) * 18;
                    int gy = py + PocketDimensionMenu.TRAY_PANEL_SLOT_Y + (i / PocketDimensionMenu.TRAY_COLS) * 18;
                    drawSlotGroove(gui, gx, gy);
                }
            } else if (openPanel == PocketDimensionMenu.PANEL_FURNACE) {
                drawSlotGroove(gui, px + 8, py + PocketDimensionMenu.FURNACE_INPUT_Y);
                drawSlotGroove(gui, px + 8, py + PocketDimensionMenu.FURNACE_FUEL_Y);
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.FURNACE_RESULT_X,
                        py + PocketDimensionMenu.FURNACE_RESULT_Y);
            } else if (openPanel == PocketDimensionMenu.PANEL_ANVIL) {
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.ANVIL_INPUT1_X,
                        py + PocketDimensionMenu.ANVIL_INPUT_Y);
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.ANVIL_INPUT2_X,
                        py + PocketDimensionMenu.ANVIL_INPUT_Y);
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.ANVIL_RESULT_X,
                        py + PocketDimensionMenu.ANVIL_RESULT_Y);
            } else if (openPanel == PocketDimensionMenu.PANEL_SMITHING) {
                // 第一格（锻造模板格）用原版锻造台贴图里那个"平亮格"，另外两格用普通凹槽
                drawTemplateSlot(gui, this.leftPos + PocketDimensionMenu.SMITH_INPUT1_X,
                        py + PocketDimensionMenu.SMITH_INPUT_Y);
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.SMITH_INPUT2_X,
                        py + PocketDimensionMenu.SMITH_INPUT_Y);
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.SMITH_INPUT3_X,
                        py + PocketDimensionMenu.SMITH_INPUT_Y);
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.SMITH_RESULT_X,
                        py + PocketDimensionMenu.SMITH_RESULT_Y);
            } else if (openPanel == PocketDimensionMenu.PANEL_CRAFTING) {
                // 九宫格 + 产物格（原版工作台的排布）
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.CRAFT_GRID_X + col * 18,
                                py + PocketDimensionMenu.CRAFT_GRID_Y + row * 18);
                    }
                }
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.CRAFT_RESULT_X,
                        py + PocketDimensionMenu.CRAFT_RESULT_Y);
            } else if (openPanel == PocketDimensionMenu.PANEL_DISASSEMBLE) {
                // 左输入 + 中九宫格 + 右产物格（原版拆解台的排布）
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.DIS_INPUT_X,
                        py + PocketDimensionMenu.DIS_INPUT_Y);
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.DIS_MAT_X + col * 18,
                                py + PocketDimensionMenu.DIS_MAT_Y + row * 18);
                    }
                }
                drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.DIS_RESULT_X,
                        py + PocketDimensionMenu.DIS_RESULT_Y);
            } else if (openPanel == PocketDimensionMenu.PANEL_MAGNET) {
                for (int i = 0; i < com.zzq.survival_toolbox.util.PocketMagnet.FILTER_SIZE; i++) {
                    drawSlotGroove(gui,
                            this.leftPos + PocketDimensionMenu.MAG_FILTER_X + (i % 6) * 18,
                            py + PocketDimensionMenu.MAG_FILTER_Y + (i / 6) * 18);
                }
            }
        }
    }

    /** 当前展开的功能页面板在屏幕上的矩形（没有展开返回 null）。给 JEI 用：让它"让开"这一块地方 */
    public net.minecraft.client.renderer.Rect2i openPanelArea() {
        int panel = this.menu.getPanel();
        if (panel == PocketDimensionMenu.PANEL_NONE) return null;
        int h = panel == PocketDimensionMenu.PANEL_TRAY
                ? this.imageHeight : PocketDimensionMenu.panelHeight(panel);
        return new net.minecraft.client.renderer.Rect2i(
                this.leftPos + PocketDimensionMenu.PAGE_PANEL_X, this.topPos,
                PocketDimensionMenu.panelWidth(panel), h);
    }

    /** 锻造模板格：原版锻造台界面里第一格是"平的亮格"（u=7,v=47 的 18×18），照它来 */
    private void drawTemplateSlot(GuiGraphics gui, int sx, int sy) {
        gui.blit(SMITHING_TEXTURE, sx - 1, sy - 1, 7, 47, 18, 18, 256, 256);
    }

    /** 功能页面板的"卡片"底：灰色面 + 深色描边 + 左上高光/右下阴影（原版 GUI 的立体感） */
    private void drawPanelCard(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        gui.fill(x, y, x + w, y + 1, 0xFFFFFFFF);          // 上高光
        gui.fill(x, y, x + 1, y + h, 0xFFFFFFFF);          // 左高光
        gui.fill(x, y + h - 1, x + w, y + h, 0xFF555555);  // 下阴影
        gui.fill(x + w - 1, y, x + w, y + h, 0xFF555555);  // 右阴影
    }

    /** 功能页标题：一格物品图标 + 文字（照精妙背包的做法） */
    private void drawPanelTitle(GuiGraphics gui, int x, int y, net.minecraft.world.item.ItemStack icon,
                                Component title) {
        gui.renderItem(icon, x, y);
        gui.drawString(this.font, title, x + 20, y + 4, 0x404040, false);
    }

    /** 各功能页面板的高度：够放内容即可，不撑满整窗（要求紧凑，避免留下大片空白） */
    private int panelHeight(int panel) {
        return switch (panel) {
            case PocketDimensionMenu.PANEL_FURNACE -> 88;
            case PocketDimensionMenu.PANEL_ANVIL -> 92;
            case PocketDimensionMenu.PANEL_SMITHING -> 80;
            default -> this.imageHeight; // 托盘要占满（底部还有"送出/纳入"按钮）
        };
    }

    /** 画一格原版凹槽（贴图里 u=7,v=17 的单格，尺寸 18×18） */
    private void drawSlotGroove(GuiGraphics gui, int sx, int sy) {
        gui.blit(TEXTURE, sx - 1, sy - 1, 7, 17, 18, 18, 256, 256);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // 面板状态可能被服务端同步改变：布局不一致就立刻跟上
        // 切石机页：每帧对一下"输入 → 可用配方列表"
        if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_STONECUTTER)) {
            this.refreshStoneList();
        }
        if (this.layoutPanel != this.menu.getPanel()) {
            this.layoutPanel = this.menu.getPanel();
            applyPanelWidth();
            rebuildTrayWidgets();
        }
        // 模式/方向按钮文案每帧跟随实际状态
        if (this.modeButton != null) {
            this.modeButton.setMessage(modeLabel());
        }
        if (this.trayDirectionButton != null) {
            this.trayDirectionButton.setMessage(trayDirectionLabel());
        }
        if (this.furnaceOutputButton != null) {
            this.furnaceOutputButton.setMessage(furnaceOutputLabel());
        }
        // 磁铁页的按钮文案每帧跟随同步来的状态（服务端权威）
        if (this.magToggleButton != null) {
            this.magToggleButton.setMessage(magnetToggleLabel());
            this.magModeButton.setMessage(magnetModeLabel());
            this.magListButton.setMessage(magnetListLabel());
        }
        if (this.resToggleButton != null) {
            this.resToggleButton.setMessage(restockToggleLabel());
            this.resScopeButton.setMessage(restockScopeLabel());
        }
        // 合成页"自动补充"按钮的文案每帧跟随服务端同步来的状态
        if (this.craftRefillButton != null) {
            this.craftRefillButton.setMessage(craftRefillLabel());
        }
        // 拆解页："全部拆解"只在拆解模式（输入槽有物品）下有用，模式随玩家放入物品变化，每帧同步一次
        if (this.disBulkButton != null) {
            this.disBulkButton.visible = !this.menu.getDisassembleInputs().getItem(0).isEmpty();
        }
        // 铁砧页：改名框跟着输入物品走（原版铁砧就是这样；只有输入真的变了才动框）
        if (this.nameBox != null) {
            syncAnvilNameBox();
        }
        // 面板一变（同步包/服务端动作，例如 JEI 自动打开合成页）就把控件重建，否则按钮会不见
        syncPanelWidgets();
        // 补货页的卡片底必须画在控件之前，否则会盖住那两个按钮（表现为按钮背景被盖住、只剩文字）
        if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_RESTOCK)) {
            this.renderRestockPanel(gui);
        }
        super.render(gui, mouseX, mouseY, partialTick);
        // hoveredSlot 由上面这句更新，长按判定放在它后面取最新值
        this.updateGrabHold();
        this.renderGrabHighlight(gui);

        // 流体格的蓝色 mB 数字（1.20.1 的 renderSlot 是 private，覆写不了，只能在这里按坐标画）
        // 注意：这里已经在 super.render 之后、pose 已经弹回来了，所以下面用的是**屏幕绝对坐标**；
        //       1.21.1 那边是在 renderSlot 覆写里画（那时 pose 还平移着），两边坐标基准不同，请勿照搬。
        for (int i = 0; i < PocketDimensionContainer.PAGE_SIZE; i++) {
            if (this.menu.isFluidDisplaySlot(i)) {
                // 存量与流体本身都在显示栈的自定义数据里（显示栈数量恒为 1，避免原版把 mB 当物品数量画出来）
                ItemStack icon = this.menu.getPocketContainer().getItem(i);
                net.minecraftforge.fluids.FluidStack fluid = PocketStorageHelper.displayFluid(icon);
                int sx = this.leftPos + PocketDimensionMenu.PAGE_LIST_WIDTH + 8 + (i % 9) * 18;
                int sy = this.topPos + 18 + (i / 9) * 18;
                // 没有桶物品的模组流体（例如机械动力）：原版画出来是个空桶 → 用流体自己的贴图盖上去
                if (!fluid.isEmpty()) {
                    drawFluidIcon(gui, sx, sy, fluid);
                }
                // ⚠️ 存量 > 0 才画蓝字：显示栈万一空了，`formatFluidAmount(0)` 会在空格子上写一个 "0 mB"
                //    （和"空格子画 0"属于同一类问题）
                long amount = PocketStorageHelper.displayFluidAmount(icon);
                if (amount > 0L) {
                    drawFluidAmount(gui, sx, sy, amount);
                }
            }
        }
        // 托盘流体格（面板展开时才画：收起时槽位 isActive()=false，原版连槽位都不渲染）
        if (this.menu.isTrayOpen()) {
            for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
                if (!this.menu.getTray().isFluidAt(i)) continue;
                // 托盘是 3 列 × 9 行的窄条：列/行必须按 TRAY_COLS 算，不能按 9 算
                int sx = this.leftPos + PocketDimensionMenu.TRAY_PANEL_SLOT_X
                        + (i % PocketDimensionMenu.TRAY_COLS) * 18;
                int sy = this.topPos + PocketDimensionMenu.TRAY_PANEL_SLOT_Y
                        + (i / PocketDimensionMenu.TRAY_COLS) * 18;
                // ⚠️ 流体本体不能只看服务端的 fluidAt()：它在客户端恒为 null（显示数据走同步掩码 + 数量），
                //    老写法因此在客户端**从来不画**托盘流体格的图标，表现为"水放进托盘后图标消失"。
                //    现在图标栈随同步包一起下来（PocketTrayContainer#fluidIcons），这里直接读格子里的那件"桶"。
                ItemStack icon = this.menu.getTray().getItem(i);
                net.minecraftforge.fluids.FluidStack fluid = PocketStorageHelper.displayFluid(icon);
                if (fluid.isEmpty()) {
                    fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(icon)
                            .orElse(net.minecraftforge.fluids.FluidStack.EMPTY);
                }
                if (!fluid.isEmpty()) {
                    drawFluidIcon(gui, sx, sy, fluid);
                }
                long trayAmount = this.menu.getTray().fluidAmount(i);
                if (trayAmount > 0L) {              // 同理：0 不写 "0 mB"
                    drawFluidAmount(gui, sx, sy, trayAmount);
                }
            }
        }

        // 搜索结果超过一页时提示细化关键词
        if (this.menu.getPocketContainer().isSearching()
                && this.menu.getPocketContainer().isSearchTruncated()) {
            gui.drawString(this.font,
                    Component.translatable("gui.zzq_survival_toolbox.pocket.search_too_many"),
                    this.leftPos + PocketDimensionMenu.PAGE_LIST_WIDTH + 8, this.topPos + 20, 0xFF5555, false);
        }
        this.renderPageList(gui, mouseX, mouseY);
        this.renderTopButtons(gui, mouseX, mouseY);
        this.renderStrip(gui, mouseX, mouseY);
        if (this.menu.isTrayOpen()) {
            this.renderTrayPanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_FURNACE)) {
            this.renderFurnacePanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_ANVIL)) {
            this.renderAnvilPanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_SMITHING)) {
            this.renderSmithingPanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_CRAFTING)) {
            this.renderCraftingPanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_DISASSEMBLE)) {
            this.renderDisassemblePanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_MAGNET)) {
            this.renderMagnetPanel(gui);
        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_RESTOCK)) {
   // 补货页的卡片底+标题（此前一直没有调用，页面才会空白）

        } else if (this.menu.isPanelOpen(PocketDimensionMenu.PANEL_STONECUTTER)) {
            this.renderStonecutterPanel(gui, mouseX, mouseY);
        }
        this.renderCarriedFluid(gui, mouseX, mouseY);
        this.renderTooltip(gui, mouseX, mouseY);
        // 锻造台页的引导提示要画在物品 tooltip 之后（空格子上原版本来就没有物品提示）
        this.renderSmithingOnboardingTooltip(gui, mouseX, mouseY);
        this.searchBox.render(gui, mouseX, mouseY, partialTick);
        this.renameBox.render(gui, mouseX, mouseY, partialTick);
        if (this.nameBox != null) {
            this.nameBox.render(gui, mouseX, mouseY, partialTick);
        }
        this.renderButtonTooltips(gui, mouseX, mouseY);
    }

    /** 流体数量文字：恒定带 mB 后缀，>=100 起按 百/万/亿 进位（与提示消息共用同一份实现） */
    static String fluidAmountText(long amount) {
        return PocketStorageHelper.formatFluidAmount(amount);
    }

    /** 格子右下角的蓝色 mB 数字（0.6 倍字号；sx/sy = 格子在屏幕上的左上角） */
    private void drawFluidAmount(GuiGraphics gui, int sx, int sy, long amount) {
        String text = fluidAmountText(amount);
        gui.pose().pushPose();
        gui.pose().translate(sx + 16.0F, sy + 11.0F, 300.0F);
        gui.pose().scale(0.6F, 0.6F, 1.0F);
        gui.drawString(this.font, text, -this.font.width(text), 0, 0x66CCFF, true);
        gui.pose().popPose();
    }

    /** 已经因为"取不到贴图"警告过的流体（只记录一次，避免每帧刷日志） */
    private static final java.util.Set<Object> FLUID_ICON_WARNED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * 把一个流体的"静止贴图"（带染色）画到格子上，当流体格的图标。
     * <p>
     * 流体格**一律**这么画（有桶的水/岩浆也一样）：模组储罐就是这么画的
     * （流体贴图 + {@code getTintColor} 染色），而且用桶图标会把"流体"看成"物品" ——
     * 水（流体）与水桶（物品）在显示上必须分开。
     * 1.20.1 的 {@code renderSlot} 是 private 覆写不了，所以这里是在 {@code super.render()} 之后
     * **盖**在原版图标（桶/空桶）上面的。
     * </p>
     * <p>
     * 贴图取不到（或其它模组流体实现异常）时**绝不退回"桶"的样子**：改画一块该流体的染色块，
     * 并记一条一次性日志说明原因（避免静默退化成桶、事后无法排查）。
     * </p>
     */
    private void drawFluidIcon(GuiGraphics gui, int sx, int sy,
                               net.minecraftforge.fluids.FluidStack fluid) {
        drawFluidIcon(gui, sx, sy, fluid, 0.0F);
    }

    /**
     * 同上，额外抬高 z（{@code zOffset}）。
     * <p>
     * ⚠️ 光标上那份必须抬高：原版把光标物品画在 **z + 232**，而且 {@code AbstractContainerScreen#render}
     * 结尾会 {@code enableDepthTest()} —— 直接在 z=0 画会被深度测试挡在光标物品后面，
     * 结果"拿起来的流体"仍然显示为一个桶。槽位里那段是关着深度测试画的，所以格子没事。
     * </p>
     */
    private void drawFluidIcon(GuiGraphics gui, int sx, int sy,
                               net.minecraftforge.fluids.FluidStack fluid, float zOffset) {
        gui.pose().pushPose();
        if (zOffset != 0.0F) gui.pose().translate(0.0F, 0.0F, zOffset);
        try {
            int tint = 0xFFFFFFFF;
            try {
                net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions ext =
                        net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                                .of(fluid.getFluid().getFluidType());
                tint = ext.getTintColor(fluid);
                net.minecraft.resources.ResourceLocation still = ext.getStillTexture(fluid);
                if (still != null) {
                    net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                            net.minecraft.client.Minecraft.getInstance()
                                    .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                                    .apply(still);
                    float alpha = ((tint >>> 24) & 0xFF) / 255.0F;
                    if (alpha <= 0.0F) alpha = 1.0F;
                    gui.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                            (tint & 0xFF) / 255.0F, alpha);
                    gui.blit(sx, sy, 0, 16, 16, sprite);
                    return;
                }
                warnFluidIcon(fluid, "没有静止贴图");
            } catch (Throwable t) {
                warnFluidIcon(fluid, String.valueOf(t));
            } finally {
                gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            // 兜底：该流体的染色块（绝不留下"桶"的样子）
            gui.fill(sx, sy, sx + 16, sy + 16, 0xFF000000 | (tint & 0xFFFFFF));
        } finally {
            gui.pose().popPose();
        }
    }

    private static void warnFluidIcon(net.minecraftforge.fluids.FluidStack fluid, String why) {
        Object key = fluid.getFluid();
        if (FLUID_ICON_WARNED.add(key)) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "[次元袋] 取不到流体贴图（{}），这个流体改用染色块显示：{}", why, key);
        }
    }

    /**
     * 工具提示：流体格显示的是"该流体的桶"，原版 tooltip 会写"水桶"，
     * 容易被当成"袋子里存的是桶" → 流体格换成"流体名 + 真实 mB 数量"。
     */
    @Override
    protected java.util.List<net.minecraft.network.chat.Component> getTooltipFromContainerItem(
            net.minecraft.world.item.ItemStack stack) {
        long amount = PocketStorageHelper.displayFluidAmount(stack);
        if (amount > 0L) {
            net.minecraftforge.fluids.FluidStack fluid = PocketStorageHelper.displayFluid(stack);
            if (fluid.isEmpty()) {
                // 老显示栈（没带流体本身）兜底：从桶里反推
                fluid = net.minecraftforge.fluids.FluidUtil.getFluidContained(stack)
                        .orElse(net.minecraftforge.fluids.FluidStack.EMPTY);
            }
            if (!fluid.isEmpty()) {
                return java.util.List.of(fluid.getDisplayName(),
                        net.minecraft.network.chat.Component
                                .literal(PocketStorageHelper.formatFluidAmount(amount))
                                .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
        }
        return super.getTooltipFromContainerItem(stack);
    }

    /** 光标上拿着流体时：把载体物品（原版光标只能放物品，所以是个桶）盖上流体贴图，并在鼠标旁显示数量 */
    private void renderCarriedFluid(GuiGraphics gui, int mouseX, int mouseY) {
        PocketStorageHelper.FluidEntry carried = PocketFluidCarry.entryOf(this.menu.getCarried());
        if (carried == null) return;
        // 光标上那个"载体"是一个桶，外观上等同于"拿着桶" → 盖上流体自己的贴图。
        // 位置跟着原版光标物品（mouseX-8, mouseY-8）；**必须关掉深度测试 + 抬高 z**，
        // 否则会被画在 z+232 的光标物品挡在后面（见 drawFluidIcon 的注释）。
        net.minecraftforge.fluids.FluidStack fluid = carried.toStack(1);
        if (!fluid.isEmpty()) {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            drawFluidIcon(gui, mouseX - 8, mouseY - 8, fluid, 300.0F);
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        }
        String text = fluidAmountText(carried.amount());
        int x = mouseX + 9;
        int y = mouseY + 9;
        gui.fill(x - 1, y - 1, x + this.font.width(text) + 1, y + 9, 0x90000000);
        gui.drawString(this.font, text, x, y, 0x66CCFF, true);
    }

    /**
     * 按钮悬停提示（右侧按钮条 + 托盘方向按钮）。
     * <p>
     * 托盘面板做成 3×9 的窄条后放不下说明文字，所以把"怎么用"和格数统计都收到 tooltip 里。
     * </p>
     */
    private void renderButtonTooltips(GuiGraphics gui, int mouseX, int mouseY) {
        // 合成页的"自动补充"按钮：说明写在这里（面板只有 100 宽，按钮上放不下长句子）
        if (this.craftRefillButton != null && this.craftRefillButton.isMouseOver(mouseX, mouseY)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable(this.menu.isCraftRefillOn()
                                    ? "gui.zzq_survival_toolbox.pocket.page.crafting.refill.on"
                                    : "gui.zzq_survival_toolbox.pocket.page.crafting.refill.off"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.crafting.refill.tooltip")),
                    mouseX, mouseY);
            return;
        }
        if (isOverStripButton(mouseX, mouseY, 0)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.hint_head"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.hint_out"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.hint_in"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.hint_scoop"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.hint_place"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.status",
                                    this.menu.getTray().itemSlotCount(), this.menu.getTray().fluidSlotCount())),
                    mouseX, mouseY);
        } else if (isOverTrayDirectionButton(mouseX, mouseY)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable(this.menu.getTray().isOut()
                                    ? "gui.zzq_survival_toolbox.pocket.tray.out"
                                    : "gui.zzq_survival_toolbox.pocket.tray.in"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.tray.hint_click")),
                    mouseX, mouseY);
        } else if (!this.menu.isSharedMode() && isOverTransferButton(mouseX, mouseY)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.merge.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.merge.tooltip")),
                    mouseX, mouseY);
        } else if (isOverTopButton(mouseX, mouseY, 1) || isOverTopButton(mouseX, mouseY, 2)
                || isOverTopButton(mouseX, mouseY, 3) || isOverTopButton(mouseX, mouseY, 4)) {
            // 提示保持一句话（不写长解释）
            int sortBy = isOverTopButton(mouseX, mouseY, 1) ? PocketSortPacket.BY_NAME
                    : (isOverTopButton(mouseX, mouseY, 2) ? PocketSortPacket.BY_MOD
                    : (isOverTopButton(mouseX, mouseY, 3) ? PocketSortPacket.BY_COUNT : PocketSortPacket.BY_TAG));
            gui.renderTooltip(this.font, sortTipLabel(sortBy), mouseX, mouseY);
        } else if (isOverTopButton(mouseX, mouseY, TOP_BUTTON_RANGE)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.sort.range.tooltip",
                                    sortRangeLongLabel()),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.sort.range.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 1)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.anvil.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.anvil.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 2)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.smithing.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.smithing.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 3)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.furnace.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.furnace.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 4)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.crafting.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.crafting.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 5)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.disassemble.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.disassemble.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 6)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.magnet.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.magnet.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 8)) {
            gui.renderTooltip(this.font, joinLines(
                    Component.translatable("gui.zzq_survival_toolbox.pocket.stone.button"),
                    Component.translatable("gui.zzq_survival_toolbox.pocket.stone.hint")),
                    mouseX, mouseY);
        } else if (isOverStripButton(mouseX, mouseY, 7)) {
            gui.renderTooltip(this.font, joinLines(
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.restock.button"),
                            Component.translatable("gui.zzq_survival_toolbox.pocket.page.restock.hint")),
                    mouseX, mouseY);
        }
    }

    /** 鼠标是否在"并入共享"按钮上 */
    private boolean isOverTransferButton(double mouseX, double mouseY) {
        double lx = mouseX - this.leftPos - TRANSFER_X;
        double ly = mouseY - this.topPos - TRANSFER_Y;
        return lx >= 0 && lx < TRANSFER_W && ly >= 0 && ly < TRANSFER_H;
    }

    /** 本地（当前显示的）存储里是否有内容：用来把"并入共享"按钮画灰 */
    private boolean hasLocalContent() {
        for (int count : this.menu.getPocketContainer().getPageCounts()) {
            if (count > 0) return true;
        }
        return false;
    }

    /**
     * 点"并入共享"：先弹确认。
     * <p>
     * 这是不可逆的搬运（完成后本地会被清空，只保留并入共享的结果），所以确认框要写清楚
     * "不覆盖、两边相加"，避免玩家误以为会丢物品。
     * </p>
     */
    private void onMergeToSharedClicked() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            net.minecraft.client.Minecraft.getInstance().setScreen(this);
                            if (confirmed) {
                                SurvivalToolbox.CHANNEL.sendToServer(
                                        new com.zzq.survival_toolbox.network.PocketMergeToSharedPacket());
                            }
                        },
                        Component.translatable("gui.zzq_survival_toolbox.pocket.merge.confirm.title"),
                        Component.translatable("gui.zzq_survival_toolbox.pocket.merge.confirm.message"),
                        Component.translatable("gui.zzq_survival_toolbox.pocket.merge.confirm.yes"),
                        Component.translatable("gui.zzq_survival_toolbox.pocket.merge.confirm.no")));
    }

    /** 多行 tooltip 拼成一个组件（原版 tooltip 会把 \n 拆成多行；1.20.1 没有 List 版重载） */
    private static Component joinLines(Component... lines) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append("\n");
            out.append(lines[i]);
        }
        return out;
    }

    /** 鼠标是否在"送出/纳入"按钮上（它是个 widget，位置随面板走） */
    private boolean isOverTrayDirectionButton(double mouseX, double mouseY) {
        return this.trayDirectionButton != null
                && mouseX >= this.trayDirectionButton.getX()
                && mouseX < this.trayDirectionButton.getX() + this.trayDirectionButton.getWidth()
                && mouseY >= this.trayDirectionButton.getY()
                && mouseY < this.trayDirectionButton.getY() + this.trayDirectionButton.getHeight();
    }

    /**
     * 右侧按钮条：只放额外功能页 —— [0] 托盘页、[1] 铁砧页、[2] 锻造台页、[3] 熔炉页
     * （点哪个就在右边展开本界面自己的页，不会离开次元袋界面）。
     * <p>
     * 整理相关按钮（三种方式 + 页/全）不放在这一溜，改放顶部搜索框右边那一行
     * （见 {@link #renderTopButtons}）；后续如需增加功能页（例如拆解台），按 {@code STRIP_BUTTON_STEP} 的倍数往下排。
     * </p>
     */
    private void renderStrip(GuiGraphics gui, int mouseX, int mouseY) {
        int x = this.leftPos + STRIP_BUTTON_X;
        ItemStack[] icons = {
                new ItemStack(Items.CHEST),          // 托盘页
                new ItemStack(Items.ANVIL),          // 铁砧页
                new ItemStack(Items.SMITHING_TABLE), // 锻造台页
                new ItemStack(Items.FURNACE),        // 熔炼页
                new ItemStack(Items.CRAFTING_TABLE), // 合成页
                new ItemStack(com.zzq.survival_toolbox.registry.ModBlocks.DISASSEMBLE_TABLE.get()), // 拆解台页
                new ItemStack(Items.LODESTONE),      // 磁铁页
                new ItemStack(Items.HOPPER),         // 补货页
                new ItemStack(Items.STONECUTTER),    // 切石机页
        };
        int[] panels = {
                PocketDimensionMenu.PANEL_TRAY,
                PocketDimensionMenu.PANEL_ANVIL,
                PocketDimensionMenu.PANEL_SMITHING,
                PocketDimensionMenu.PANEL_FURNACE,
                PocketDimensionMenu.PANEL_CRAFTING,
                PocketDimensionMenu.PANEL_DISASSEMBLE,
                PocketDimensionMenu.PANEL_MAGNET,
                PocketDimensionMenu.PANEL_RESTOCK,
                PocketDimensionMenu.PANEL_STONECUTTER,
        };
        for (int i = 0; i < icons.length; i++) {
            int y = this.topPos + STRIP_BUTTON_Y + i * STRIP_BUTTON_STEP;
            boolean hover = isOverStripButton(mouseX, mouseY, i);
            boolean open = this.menu.isPanelOpen(panels[i]);
            gui.fill(x, y, x + STRIP_BUTTON_SIZE, y + STRIP_BUTTON_SIZE,
                    open ? 0xA0FFFFFF : (hover ? 0x80808080 : 0x30FFFFFF));
            gui.renderItem(icons[i], x + 1, y + 1);
        }
    }

    /**
     * 顶部一行（搜索框右边）：三个整理按钮 + "页/全"范围按钮，16×16 正好排满主窗口这一行剩下的宽度。
     * <p>
     * 图标不用文字，说明在 tooltip 里（提示要短）。
     * </p>
     */
    private void renderTopButtons(GuiGraphics gui, int mouseX, int mouseY) {
        // 0) 整理范围（页 / 全）—— 放在搜索栏后面最前面
        int y = this.topPos + TOP_BUTTON_Y;
        int rx = topButtonX(TOP_BUTTON_RANGE);
        gui.fill(rx, y, rx + TOP_BUTTON_SIZE, y + TOP_BUTTON_SIZE,
                isOverTopButton(mouseX, mouseY, TOP_BUTTON_RANGE) ? 0xE0808080 : 0xC0404040);
        gui.drawString(this.font, sortRangeLabel(), rx + 3, y + 2, 0xFFFFFF, false);
        // 1~3) 三种整理方式
        ItemStack[] icons = {
                new ItemStack(Items.NAME_TAG),   // 不分 mod 按注册名
                new ItemStack(Items.BUNDLE),     // 同 mod 放一起
                new ItemStack(Items.HOPPER),     // 按数量
                new ItemStack(Items.ITEM_FRAME), // 按标签（同类的放一起）
        };
        for (int i = 0; i < icons.length; i++) {
            int bx = topButtonX(1 + i);
            gui.fill(bx, y, bx + TOP_BUTTON_SIZE, y + TOP_BUTTON_SIZE,
                    isOverTopButton(mouseX, mouseY, 1 + i) ? 0x80808080 : 0x30FFFFFF);
            // 图标是 16×16 的，按钮现在只有 12×12 → 缩到 0.75 倍正好放下
            gui.pose().pushPose();
            gui.pose().translate(bx, y, 0.0F);
            gui.pose().scale(0.75F, 0.75F, 1.0F);
            gui.renderItem(icons[i], 0, 0);
            gui.pose().popPose();
        }
    }

    /** 第 index 个顶部按钮的屏幕 X */
    private int topButtonX(int index) {
        return this.leftPos + TOP_BUTTON_X + index * (TOP_BUTTON_SIZE + TOP_BUTTON_GAP);
    }

    /** 鼠标是否在第 index 个顶部按钮上 */
    private boolean isOverTopButton(double mouseX, double mouseY, int index) {
        double lx = mouseX - topButtonX(index);
        double ly = mouseY - this.topPos - TOP_BUTTON_Y;
        return lx >= 0 && lx < TOP_BUTTON_SIZE && ly >= 0 && ly < TOP_BUTTON_SIZE;
    }

    /** 是否悬停在第 index 个功能页按钮上（按钮挨着往下排） */
    private boolean isOverStripButton(double mouseX, double mouseY, int index) {
        double lx = mouseX - this.leftPos - STRIP_BUTTON_X;
        double ly = mouseY - this.topPos - STRIP_BUTTON_Y - index * STRIP_BUTTON_STEP;
        return lx >= 0 && lx < STRIP_BUTTON_SIZE && ly >= 0 && ly < STRIP_BUTTON_SIZE;
    }

    /**
     * 托盘页：只画标题。
     * <p>
     * 面板做成 3×9 的窄条后放不下说明文字：用法（Shift+右键容器、送出/纳入的差别、格数统计）
     * 全部移到右侧托盘按钮与"送出/纳入"按钮的 tooltip 里。
     * </p>
     */
    private void renderTrayPanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.TRAY_PANEL_X;
        gui.drawString(this.font, Component.translatable("gui.zzq_survival_toolbox.pocket.tray.title"),
                px + 6, this.topPos + 5, 0x404040, false);
    }

    /**
     * 熔炼页：照原版熔炉的排布 —— 左边一列「输入格 / 火 / 燃料格」，右边「箭头 → 产物格」。
     * <p>
     * 火和箭头直接用<b>原版熔炉界面贴图</b>里的那两个图（火的剩余高度 = 当前燃料还能烧多久，
     * 箭头从左往右长 = 当前物品的烧炼进度），不再自己画色块。
     * 产物默认放右边那个格子里（和原版熔炉一样），页面下方按钮可以切成"直接进储物空间"，
     * 那种模式下格子会一直空着。
     * </p>
     */
    private void renderFurnacePanel(GuiGraphics gui) {
        PocketFurnaceContainer f = this.menu.getFurnace();
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.FURNACE),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.furnace.title"));

        int slotX = this.leftPos + PocketDimensionMenu.PAGE_SLOT_X;
        // 空底：原版熔炉界面里那个灰色火苗/箭头占位（就在 furnace.png 的 56,36 与 79,34），
        // 先画它，这样不烧的时候也能看出"这里是一团火/一个箭头"，烧起来再往上叠进度图
        // （进度图在 1.20.1 也在 furnace.png 里：火 u=176,v=0 14×14；箭头 u=176,v=14 24×16）
        int flameX = slotX + 2;
        int flameY = py + PocketDimensionMenu.FURNACE_FLAME_Y;
        gui.blit(FURNACE_TEXTURE, flameX, flameY, FLAME_BG_U, FLAME_BG_V, FLAME_W, FLAME_H, 256, 256);
        // 火：从下往上按"还剩多少燃料"裁着画（火苗越少越矮）
        if (f.getBurnTotal() > 0 && f.getBurn() > 0) {
            // 原版算法：ceil(剩余燃料比例 * 13) + 1
            int k = (int) Math.min(FLAME_H,
                    Math.ceil(f.getBurn() / (double) f.getBurnTotal() * 13.0) + 1);
            gui.blit(FURNACE_TEXTURE, flameX, flameY + FLAME_H - k, 176, FLAME_H - k,
                    FLAME_W, k, 256, 256);
        }
        // 箭头：同样先画空底，再从左往右按烧炼进度一点点画满
        int arrowX = slotX + 20;
        int arrowY = flameY - 1;
        gui.blit(FURNACE_TEXTURE, arrowX, arrowY, ARROW_BG_U, ARROW_BG_V, ARROW_W, ARROW_H, 256, 256);
        if (f.getCookTotal() > 0 && f.getCook() > 0) {
            // 原版算法：ceil(烧炼进度 * 24)
            int l = (int) Math.min(ARROW_W,
                    Math.ceil(f.getCook() / (double) f.getCookTotal() * 24.0));
            if (l > 0) {
                gui.blit(FURNACE_TEXTURE, arrowX, arrowY, 176, 14, l, ARROW_H, 256, 256);
            }
        }
    }

    /**
     * 铁砧页：参照精妙背包的排布 —— 上面整行改名框，下面「输入 + 输入 → 产物」。
     * <p>
     * 产物格里的物品和"花费多少级"都是原版 {@code AnvilMenu} 算出来的（见
     * {@link com.zzq.survival_toolbox.util.PocketPageEngine}）；左键取到光标、Shift+左键直接进储物空间。
     * </p>
     */
    private void renderAnvilPanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.ANVIL),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.anvil.title"));

        // 两个输入之间画个加号，右边画箭头（原版铁砧的样子）
        int rowY = py + PocketDimensionMenu.ANVIL_INPUT_Y;
        gui.drawString(this.font, "+", this.leftPos + PocketDimensionMenu.ANVIL_INPUT1_X + 20, rowY + 5,
                0x606060, false);
        gui.drawString(this.font, "→", this.leftPos + PocketDimensionMenu.ANVIL_INPUT2_X + 20, rowY + 5,
                0x606060, false);
        // 花费（原版算的；不够级数画红字）
        int cost = this.menu.getPageCost();
        if (cost > 0) {
            net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
            boolean canPay = p != null && (p.getAbilities().instabuild || p.experienceLevel >= cost);
            gui.drawString(this.font, Component.translatable("gui.zzq_survival_toolbox.pocket.page.anvil.cost", cost),
                    px + 8, rowY + 22, canPay ? 0x3C8C3C : 0xCC3333, false);
        }
    }

    /** 锻造台页：参照精妙背包的排布 —— 「模板 / 底座 / 附加 → 产物」（产物同样是原版算的） */
    private void renderSmithingPanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.SMITHING_TABLE),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.smithing.title"));
        gui.drawString(this.font, "→", this.leftPos + PocketDimensionMenu.SMITH_INPUT3_X + 20,
                py + PocketDimensionMenu.SMITH_INPUT_Y + 5, 0x606060, false);
        renderSmithingGhostIcons(gui);
    }

    /**
     * 空格子上画原版那种"提示图标"（ghost 图标）。
     * <p>
     * 原版 {@code SmithingScreen} 就是这么干的：模板格空着时画锻造模板的图标（并在两个模板图标之间每 30 tick
     * 轮换一次、切换时 4 tick 淡入），底座/附加格空着时按"当前放的模板"画它对应的提示图标。
     * 关于"放锻造模板的格子背景有花纹"这一点 —— 那不是格子本身的花纹，是叠在上面的提示图标。
     * 图标尺寸 16×16，画在格子左上角（和原版一致）。
     * </p>
     */
    private void renderSmithingGhostIcons(GuiGraphics gui) {
        PocketPageContainer in = this.menu.getSmithingInputs();
        ItemStack template = in.getItem(0);
        ItemStack base = in.getItem(1);
        ItemStack addition = in.getItem(2);
        int x = this.leftPos;
        int y = this.topPos + PocketDimensionMenu.SMITH_INPUT_Y;
        long tick = this.ghostTick++;

        if (template.isEmpty()) {
            // 模板格空着 → 轮换画两个模板提示图标（原版就是这样）
            drawGhostIcon(gui, x + PocketDimensionMenu.SMITH_INPUT1_X, y, SMITHING_TEMPLATE_GHOSTS, tick);
        } else if (template.getItem() instanceof net.minecraft.world.item.SmithingTemplateItem templateItem) {
            // 放了模板 → 底座/附加格按模板给的提示图标画
            if (base.isEmpty()) {
                drawGhostIcon(gui, x + PocketDimensionMenu.SMITH_INPUT2_X, y,
                        templateItem.getBaseSlotEmptyIcons(), tick);
            }
            if (addition.isEmpty()) {
                drawGhostIcon(gui, x + PocketDimensionMenu.SMITH_INPUT3_X, y,
                        templateItem.getAdditionalSlotEmptyIcons(), tick);
            }
        }
    }

    /**
     * 画一个轮换的提示图标（原版 CyclingSlotBackground 的做法：每 30 tick 换一个，切换时 4 tick 淡入）。
     * 列表为空时什么都不画。
     */
    private void drawGhostIcon(GuiGraphics gui, int x, int y, java.util.List<ResourceLocation> icons, long tick) {
        if (icons == null || icons.isEmpty()) return;
        int index = (int) ((tick / 30) % icons.size());
        float alpha = icons.size() > 1 ? Math.min(tick % 30, 4) / 4.0F : 1.0F;
        if (alpha < 1.0F) {
            drawGhostIconSprite(gui, x, y, icons.get(Math.floorMod(index - 1, icons.size())), 1.0F - alpha);
        }
        drawGhostIconSprite(gui, x, y, icons.get(index), alpha);
    }

    /** 1.20.1 的提示图标是普通贴图（textures/item/xxx.png），不是贴图集 sprite，所以这里要自己拼路径 */
    private void drawGhostIconSprite(GuiGraphics gui, int x, int y, ResourceLocation icon, float alpha) {
        ResourceLocation texture = new ResourceLocation(
                icon.getNamespace(), "textures/" + icon.getPath() + ".png");
        gui.setColor(1.0F, 1.0F, 1.0F, alpha);
        gui.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 合成页：3×3 九宫格 + 右侧产物格（原版工作台的排布，只是紧凑了）。
     * <p>
     * 产物和材料消耗<b>全部是原版的</b>：九宫格喂给隐藏的原版 {@code CraftingMenu}，
     * 取产物走原版 {@code ResultSlot#onTake}（扣材料、处理桶这类"剩余物品"、记成就）。
     * </p>
     */
    private void renderCraftingPanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.CRAFTING_TABLE),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.crafting.title"));
        // 九宫格 → 产物 的箭头（和原版工作台一样画在中间那一行）
        gui.drawString(this.font, "→", this.leftPos + PocketDimensionMenu.CRAFT_GRID_X + 56,
                py + PocketDimensionMenu.CRAFT_RESULT_Y + 5, 0x606060, false);
    }

    /**
     * 拆解台页：左输入 + 中九宫格 + 右产物格（原版拆解台的排布，只是紧凑了）。
     * <p>
     * 匹配、变体翻页、材料消耗<b>全部由拆解台自身的代码（{@code DisassembleMenu}）算出</b>：
     * 输入喂给隐藏的拆解台菜单，它把材料预览填进九宫格；产物格点一下就走原版"取走"逻辑。
     * 输入槽有物品时九宫格是<b>只读预览</b>（那是原版算出来的材料，不是玩家的物品）。
     * </p>
     */
    private void renderDisassemblePanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        drawPanelTitle(gui, px + 7, py + 3,
                new ItemStack(com.zzq.survival_toolbox.registry.ModBlocks.DISASSEMBLE_TABLE.get()),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.disassemble.title"));
        // 输入 → 九宫格 → 产物 的两个箭头（和原版拆解台一样）
        gui.drawString(this.font, "→", this.leftPos + PocketDimensionMenu.DIS_INPUT_X + 22,
                py + PocketDimensionMenu.DIS_INPUT_Y + 5, 0x606060, false);
        gui.drawString(this.font, "→", this.leftPos + PocketDimensionMenu.DIS_MAT_X + 58,
                py + PocketDimensionMenu.DIS_RESULT_Y + 5, 0x606060, false);
        // 配方页数（拆解模式 = 配方变体；合成模式 = 候选配方），没有就留空
        int total = this.menu.getDisPageTotal();
        if (total > 0) {
            gui.drawString(this.font, this.menu.getDisPageIndex() + "/" + total,
                    px + 46, py + PocketDimensionMenu.DIS_BUTTON_Y + 4, 0x404040, false);
        }
    }

    /**
     * 磁铁页：9 格名单 + 两行按钮（开关 / 模式 / 范围 −＋ / 黑白名单）。
     * <p>名单里放的是"物品信息"（幽灵条目）：放进去只复制信息、实物退回袋子；条目取不出来，右键删除。</p>
     */
    /** 列表里一共有多少条配方 */
    private int stoneRowCount() {
        return this.stoneIcons.size();
    }

    /** 滚动条滑块在屏幕上的位置（画的时候算好，点击/拖动时用同一份） */
    private int stoneThumbY = 0;
    private int stoneThumbH = 0;
    /** 是否正在拖滚动条滑块 */
    private boolean stoneDraggingThumb = false;


    /**
     * 输入变了就重算"可用切割配方"列表；选中项变了就发一次包给服务端。
     * <p>
     * ⚠️ 列表是客户端自己按配方表算的（和 JEI 一个路子），**顺序必须和服务端一致**：
     * 两边都是"筛掉不匹配的 → 按配方 id 字符串排序"。服务端收到的是下标，
     * 下标对不上时它还会用 test(input) 再确认一次，宁可不出产物也不给出错误物品。
     * </p>
     */
    private void refreshStoneList() {
        if (this.minecraft == null || this.minecraft.level == null) return;
        ItemStack input = this.menu.getStonecutterInput();
        boolean changed = !PocketStorageHelper.sameItem(input, this.stoneListInput)
                || input.getCount() != this.stoneListInput.getCount();
        if (changed) {
            this.stoneListInput = input.copy();
            this.stoneIds.clear();
            this.stoneIcons.clear();
            if (!input.isEmpty()) {
                List<net.minecraft.world.item.crafting.StonecutterRecipe> found = new java.util.ArrayList<>();
                for (net.minecraft.world.item.crafting.StonecutterRecipe recipe
                        : this.minecraft.level.getRecipeManager()
                                .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING)) {
                    var ingredients = recipe.getIngredients();
                    if (!ingredients.isEmpty() && ingredients.get(0).test(input)) {
                        found.add(recipe);
                    }
                }
                found.sort(java.util.Comparator.comparing(r -> r.getId().toString()));
                for (net.minecraft.world.item.crafting.StonecutterRecipe recipe : found) {
                    this.stoneIds.add(recipe.getId().toString());
                    this.stoneIcons.add(recipe.getResultItem(this.minecraft.level.registryAccess()).copy());
                }
            }
            this.stoneSelected = this.stoneIds.isEmpty() ? -1 : 0;
            this.stoneRow = 0;
        }
        // 选中项变了（刚打开页、换了输入、点了列表）→ 告诉服务端，让它算产物
        String id = this.stoneSelected >= 0 && this.stoneSelected < this.stoneIds.size()
                ? this.stoneIds.get(this.stoneSelected) : "";
        if (!id.equals(this.stoneSentId)) {
            this.stoneSentId = id;
            SurvivalToolbox.CHANNEL.sendToServer(new com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket(
                    com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_STONE_SELECT,
                    this.stoneSelected, ""));
        }
    }

    /**
     * 点配方列表（单列，每行一个产物图标；右侧是竖直滚动条）：
     * 点某一行 = 选中它并把下标发给服务端；点滚动条 = 翻页或开始拖滑块。
     *
     * @return 是否消费了这次点击
     */
    private boolean handleStonecutterListClick(double mouseX, double mouseY) {
        if (!this.menu.isPanelOpen(PocketDimensionMenu.PANEL_STONECUTTER)) return false;
        int lx = this.leftPos + PocketDimensionMenu.STONE_LIST_X;
        int ly = this.topPos + PocketDimensionMenu.STONE_LIST_Y;
        int lw = PocketDimensionMenu.STONE_LIST_W;
        int lh = PocketDimensionMenu.STONE_LIST_H;
        boolean overList = mouseX >= lx && mouseX < lx + lw && mouseY >= ly && mouseY < ly + lh;
        // ⚠️ 落在真实槽位上的点击一律让给原版（否则槽位会被列表拦截，表现为"放不进去"）
        if (this.getSlotUnderMouse() != null) return false;
        int sbX = lx + lw - PocketDimensionMenu.STONE_SCROLLBAR_W;
        if (!overList) return false;

        // 1) 滚动条：拖滑块 / 点轨道翻页（和原版一样，滚动条在最右边）
        if (mouseX >= sbX) {
            if (this.stoneRowCount() > PocketDimensionMenu.STONE_LIST_ROWS) {
                if (mouseY >= this.stoneThumbY && mouseY < this.stoneThumbY + this.stoneThumbH) {
                    this.stoneDraggingThumb = true;          // 按住滑块：进入拖动状态（见 mouseDragged）
                } else {
                    this.stoneRow = setStoneScroll(this.stoneRow
                            + (mouseY < this.stoneThumbY ? -PocketDimensionMenu.STONE_LIST_ROWS
                                                         : PocketDimensionMenu.STONE_LIST_ROWS));
                }
            }
            return true;
        }

        // 2) 某一行：选中它
        int index = this.stoneRow + (int) ((mouseY - ly) / PocketDimensionMenu.STONE_ROW_H);
        if (index < 0 || index >= this.stoneIds.size()) return true;   // 空白行：消费点击事件，不要向下穿透
        this.stoneSelected = index;
        this.stoneSentId = this.stoneIds.get(index);
        SurvivalToolbox.CHANNEL.sendToServer(new com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket(
                com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_STONE_SELECT, index, ""));
        return true;
    }

    /** 把滚动位置夹在合法范围内 */
    private int setStoneScroll(int value) {
        int max = Math.max(0, this.stoneRowCount() - PocketDimensionMenu.STONE_LIST_ROWS);
        return Math.max(0, Math.min(value, max));
    }

    /** 拖滚动条滑块（原版那种：按住滑块往下拖） */
    private boolean handleStonecutterScrollDrag(double mouseY) {
        if (!this.stoneDraggingThumb) return false;
        int ly = this.topPos + PocketDimensionMenu.STONE_LIST_Y;
        int trackH = PocketDimensionMenu.STONE_LIST_H;
        int max = Math.max(1, this.stoneRowCount() - PocketDimensionMenu.STONE_LIST_ROWS);
        double ratio = (mouseY - ly - this.stoneThumbH / 2.0) / Math.max(1.0, trackH - this.stoneThumbH);
        this.stoneRow = setStoneScroll((int) Math.round(ratio * max));
        return true;
    }


    /**
     * 切石机页（照原版切石机的排布）：左边输入格、右边产物格，
     * 中间一大块深色配方列表面板（单列、每行一个产物图标），面板右边缘一条竖直滚动条。
     */
    private void renderStonecutterPanel(GuiGraphics gui, int mouseX, int mouseY) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        int h = PocketDimensionMenu.panelHeight(PocketDimensionMenu.PANEL_STONECUTTER);
        drawPanelCard(gui, px, py, PocketDimensionMenu.STONECUTTER_PANEL_WIDTH, h);
        // 两个格子底（其它功能页都有；此前漏画，导致看起来"没有物品格子"）
        drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.STONE_INPUT_X, this.topPos + PocketDimensionMenu.STONE_INPUT_Y);
        drawSlotGroove(gui, this.leftPos + PocketDimensionMenu.STONE_RESULT_X, this.topPos + PocketDimensionMenu.STONE_RESULT_Y);
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.STONECUTTER),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.stonecutter.title"));

        int lx = this.leftPos + PocketDimensionMenu.STONE_LIST_X;
        int ly = this.topPos + PocketDimensionMenu.STONE_LIST_Y;
        int lw = PocketDimensionMenu.STONE_LIST_W;
        int lh = PocketDimensionMenu.STONE_LIST_H;
        int sbX = lx + lw - PocketDimensionMenu.STONE_SCROLLBAR_W;

        // 深色列表面板 + 描边（原版那块列表底）
        gui.fill(lx, ly, lx + lw, ly + lh, 0xFF4A4A4A);
        gui.fill(lx, ly, lx + lw, ly + 1, 0xFF202020);
        gui.fill(lx, ly + lh - 1, lx + lw, ly + lh, 0xFF202020);
        gui.fill(lx, ly, lx + 1, ly + lh, 0xFF202020);
        gui.fill(sbX - 1, ly, sbX, ly + lh, 0xFF202020);

        if (this.stoneIds.isEmpty()) {
            gui.drawString(this.font,
                    Component.translatable("gui.zzq_survival_toolbox.pocket.page.stonecutter.empty"),
                    lx + 5, ly + 6, 0xFFB0B0B0, false);
            return;
        }

        // 行：单列，每行 16px 图标 + 整行可点；选中行绿底、悬浮行亮一点
        int rows = PocketDimensionMenu.STONE_LIST_ROWS;
        for (int r = 0; r < rows; r++) {
            int index = this.stoneRow + r;
            if (index >= this.stoneIcons.size()) break;
            int rowY = ly + r * PocketDimensionMenu.STONE_ROW_H;
            boolean selected = index == this.stoneSelected;
            boolean hover = mouseX >= lx && mouseX < sbX && mouseY >= rowY
                    && mouseY < rowY + PocketDimensionMenu.STONE_ROW_H;
            if (selected) {
                gui.fill(lx + 1, rowY, sbX, rowY + PocketDimensionMenu.STONE_ROW_H, 0xFF3F7F3F);
            } else if (hover) {
                gui.fill(lx + 1, rowY, sbX, rowY + PocketDimensionMenu.STONE_ROW_H, 0x40FFFFFF);
            }
            gui.renderItem(this.stoneIcons.get(index), lx + 3, rowY + 1);
        }

        // 滚动条：轨道 + 滑块（滑块位置存下来给点击/拖动用）
        int trackH = lh;
        int total = this.stoneIcons.size();
        if (total > rows) {
            this.stoneThumbH = Math.max(12, (int) ((long) trackH * rows / total));
            int max = total - rows;
            this.stoneThumbY = ly + (int) ((long) (trackH - this.stoneThumbH) * this.stoneRow / Math.max(1, max));
        } else {
            this.stoneThumbH = 0;
            this.stoneThumbY = ly;
        }
        gui.fill(sbX, ly, sbX + PocketDimensionMenu.STONE_SCROLLBAR_W, ly + lh, 0xFF2B2B2B);
        if (this.stoneThumbH > 0) {
            gui.fill(sbX, this.stoneThumbY, sbX + PocketDimensionMenu.STONE_SCROLLBAR_W,
                    this.stoneThumbY + this.stoneThumbH, 0xFFA8A8A8);
        }

        // 鼠标悬浮在某一行上时显示产物名字
        if (mouseX >= lx && mouseX < sbX && mouseY >= ly && mouseY < ly + lh) {
            int index = this.stoneRow + (int) ((mouseY - ly) / PocketDimensionMenu.STONE_ROW_H);
            if (index >= 0 && index < this.stoneIcons.size()) {
                gui.renderTooltip(this.font, this.stoneIcons.get(index), mouseX, mouseY);
            }
        }
    }


    private void renderMagnetPanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.LODESTONE),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.magnet.title"));
        gui.drawString(this.font,
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.magnet.range",
                        this.menu.getMagnetRange()),
                px + 46, py + PocketDimensionMenu.MAG_BUTTON2_Y + 4, 0x404040, false);
    }

    /**
     * 补货页：两个按钮（开关 / 范围），没有格子。
     * <p>补货规则见 {@code util/PocketRestock}：只补"已经存在"的堆叠，空格不放入新种类；袋子界面打开时不执行补货。</p>
     */
    private void renderRestockPanel(GuiGraphics gui) {
        int px = this.leftPos + PocketDimensionMenu.PAGE_PANEL_X;
        int py = this.topPos;
        // ⚠️ 底板必须有：这一页的标题是深灰色文字，画在世界背景上等于隐形（否则表现为"没图标没文字"）
        drawPanelCard(gui, px, py, PocketDimensionMenu.RESTOCK_PANEL_WIDTH,
                PocketDimensionMenu.panelHeight(PocketDimensionMenu.PANEL_RESTOCK));
        drawPanelTitle(gui, px + 7, py + 3, new ItemStack(Items.HOPPER),
                Component.translatable("gui.zzq_survival_toolbox.pocket.page.restock.title"));
    }

    /**
     * 锻造台页的"引导提示"（照原版 {@code SmithingScreen#renderOnboardingTooltips}）：
     * <ul>
     *   <li>模板格空着 → 悬浮显示"放入锻造模板"（原版语言键 {@code container.upgrade.missing_template_tooltip}）；</li>
     *   <li>放了模板、底座/附加格空着 → 显示该模板自己给出的说明
     *       （{@code SmithingTemplateItem#getBaseSlotDescription/getAdditionSlotDescription}）；</li>
     *   <li>三格都放了、但产物格是空的（配方不成立）→ 悬浮产物格显示原版的错误提示
     *       （{@code container.upgrade.error_tooltip}）。</li>
     * </ul>
     * 文案全部用原版的语言键，所以自动跟随玩家的语言。
     */
    private void renderSmithingOnboardingTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (!this.menu.isPanelOpen(PocketDimensionMenu.PANEL_SMITHING)) return;
        if (this.hoveredSlot == null) return;
        int index = this.hoveredSlot.index;
        PocketPageContainer in = this.menu.getSmithingInputs();
        ItemStack template = in.getItem(0);
        Component tip = null;

        if (index == PocketDimensionMenu.SMITH_IN_BASE) {
            // 模板格：空着就提示"放入锻造模板"
            if (template.isEmpty()) {
                tip = Component.translatable("container.upgrade.missing_template_tooltip");
            }
        } else if (index == PocketDimensionMenu.SMITH_IN_BASE + 1) {
            // 底座格：有模板且这格空着 → 用模板给的说明
            if (in.getItem(1).isEmpty()
                    && template.getItem() instanceof net.minecraft.world.item.SmithingTemplateItem t) {
                tip = t.getBaseSlotDescription();
            }
        } else if (index == PocketDimensionMenu.SMITH_IN_BASE + 2) {
            // 附加格：同上
            if (in.getItem(2).isEmpty()
                    && template.getItem() instanceof net.minecraft.world.item.SmithingTemplateItem t) {
                tip = t.getAdditionSlotDescription();
            }
        } else if (index == PocketDimensionMenu.SMITH_RESULT) {
            // 产物格：三格都放了却没有产物 → 配方不成立，给原版那个错误提示
            if (this.menu.getSmithingResult().getItem(0).isEmpty()
                    && !template.isEmpty() && !in.getItem(1).isEmpty() && !in.getItem(2).isEmpty()) {
                tip = Component.translatable("container.upgrade.error_tooltip");
            }
        }
        if (tip == null) return;
        gui.renderTooltip(this.font, wrappedTooltip(this.font, tip), mouseX, mouseY);
    }

    /** 把一段长说明按原版那样折行（原版用 font.split(…, 115)），再拼成多行 tooltip */
    private static Component wrappedTooltip(net.minecraft.client.gui.Font font, Component text) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, 115);
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append("\n");
            out.append(Component.literal(charSequenceToString(lines.get(i))));
        }
        return out;
    }

    /** FormattedCharSequence → 纯文本（原版这些说明没有样式，直接取字符就够） */
    private static String charSequenceToString(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /** 左侧页列表：半透明背景 + 页行（当前页高亮、空页 ✕）+ "并入共享" + 底部 ➕ */
    private void renderPageList(GuiGraphics gui, int mouseX, int mouseY) {
        int x0 = this.leftPos + LIST_X;
        int y0 = this.topPos + LIST_Y;
        // 背景
        gui.fill(x0, y0, x0 + LIST_W, y0 + LIST_H, 0xC0101010);
        List<String> names = this.menu.getPocketContainer().getPageNames();
        int current = this.menu.getPocketContainer().getCurrentPage();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = pageScroll + row;
            if (index >= names.size()) break;
            int y = y0 + row * ROW_H;
            boolean selected = index == current;
            // 选中页：亮背景 + 左侧白色竖条 + 白字，便于识别当前页
            if (selected) {
                gui.fill(x0, y, x0 + LIST_W, y + ROW_H, 0x90FFFFFF);
                gui.fill(x0, y, x0 + 2, y + ROW_H, 0xFFFFFFFF);
            }
            String name = names.get(index);
            if (this.font.width(name) > LIST_W - 16) {
                name = this.font.plainSubstrByWidth(name, LIST_W - 16);
            }
            int tx = selected ? x0 + 5 : x0 + 3;
            gui.drawString(this.font, name, tx, y + 2, selected ? 0xFFFFFF : 0xB0B0B0, false);
            // 空页显示 ✕（仅真正为空的页）
            if (isPageEmpty(index)) {
                gui.drawString(this.font, "✕", x0 + LIST_W - 9, y + 2, 0xFF5555, false);
            }
        }
        // 页列表下方：把本地存储整体并入共享（只在本地模式显示；本地是空的时候画灰不可点）
        if (!this.menu.isSharedMode()) {
            int ty = this.topPos + TRANSFER_Y;
            boolean enabled = hasLocalContent();
            boolean hovered = isOverTransferButton(mouseX, mouseY);
            int bg = !enabled ? 0x80202020 : (hovered ? 0xE0808080 : 0xC0404040);
            gui.fill(x0, ty, x0 + TRANSFER_W, ty + TRANSFER_H, bg);
            gui.drawString(this.font, Component.translatable("gui.zzq_survival_toolbox.pocket.merge.button"),
                    x0 + 3, ty + 3, enabled ? 0xFFFFFF : 0x808080, false);
        }
        // 底部 ➕ 新建
        int py = this.topPos + ADD_PAGE_Y;
        gui.fill(x0, py, x0 + LIST_W, py + ADD_PAGE_H, 0xC0303030);
        gui.drawString(this.font, "➕ 新页", x0 + 3, py + 2, 0xFFFFFF, false);
    }
}
