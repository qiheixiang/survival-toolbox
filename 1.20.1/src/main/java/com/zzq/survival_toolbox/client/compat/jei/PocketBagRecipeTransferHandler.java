package com.zzq.survival_toolbox.client.compat.jei;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import com.zzq.survival_toolbox.util.PocketJeiTransfer;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 让 JEI 的"+"号能从次元袋取料（合成台 / 锻造台 / 次元袋自己的合成页、拆解页）
 * <p>
 * <b>⚠️ 为什么不能"只把料取到背包再交给 JEI"</b>（实测曾出现的失败模式，请勿回退此行为）：
 * JEI 的 {@code BasicRecipeTransferHandler} 只跑在<b>客户端</b>，它按"玩家背包 + 输入槽"算能不能转，
 * 算不出来就直接报 {@code jei.tooltip.error.recipe.transfer.missing}（"材料不足"）——<b>根本不会发包</b>；
 * 能转时它发的 {@code PacketRecipeTransfer} 在服务端由 {@code BasicRecipeTransferHandlerServer}
 * 直接摆格，那段是 JEI 自己的静态代码，<b>不经过本模组</b>。
 * 于是"先往背包里补料"这件事既没有发生的机会（客户端判定在前），也没有生效的通道（服务端那段不归本模组）。
 * "袋里明明有 1.8 千云杉木板，点 + 却提示材料不足"（实测）就是这个原因。
 * </p>
 * <p>
 * <b>现在的做法</b>：
 * <ol>
 *   <li><b>可转移判定</b>（{@code doTransfer=false}）：按"背包 + 副手 + 输入槽 + 袋子"算一遍。
 *       凑得齐 → 返回 null（"+" 亮着）；确实凑不齐 → 交给 JEI 报它自己的"材料不足"。<br>
 *       ⚠️ 老代码在"共享模式袋子"上<b>一律乐观放行</b>（客户端看不见服务器存档里的数据，
 *       为了不误报就干脆全放），结果就是<b>凑不齐时 + 号照样能点</b>（新袋子默认共享模式，
 *       因此默认配置下都会遇到，"1.20.1 就算物品不足 + 号也能点"（实测）就是这个）。<br>
 *       现在共享袋子的答案<b>必须来自服务端</b>：次元袋界面开着时用客户端手上那份"当前显示页"的
 *       同步数据算（那就是权威数据，还顺带满足"打开哪页就匹配哪页"）；没有这种数据时才发
 *       {@code PocketJeiQueryPacket} 去问，答案按签名缓存在 {@link PocketJeiAvailabilityCache}
 *       里（TTL 约 0.75 秒）。<b>在拿到答案之前一律不乐观放行</b>；</li>
 *   <li><b>真转移</b>（{@code doTransfer=true}）：把"配方每一格的候选物品表 + 输入区间 + 打开的页号"
 *       发给本模组自己的包，由服务端<b>按格对位</b>取料摆格（背包 → 袋子，严格按当前共享/本地模式，
 *       并且只从玩家打开的那一页取），<b>不再交给 JEI 搬</b>。</li>
 * </ol>
 * 本类只在装了 JEI 的客户端被加载（{@code @JeiPlugin} 扫描时才载入），因此可以直接引用 JEI 类型。
 * </p>
 * <p>
 * <b>输入区间（规则：「打开哪个匹配哪个」）</b>：
 * 原版工作台 / 锻造台用注册时写死的那一段；<b>次元袋界面</b>按"当前展开的是哪一页"动态决定
 * （工作台页 → {@link PocketDimensionMenu#CRAFT_GRID_BASE}，拆解页 → {@link PocketDimensionMenu#DIS_MAT_BASE}），
 * 两页都没开就<b>什么都不做</b>——老代码会让服务端自动打开工作台页再摆料，
 * 玩家看到的是「袋子里的工作台和拆解台界面都没开的时候，点 jei 配方还是会自动匹配，匹配的是工作台」。
 * 服务端 {@code PocketJeiTransfer} 用的是<b>同一套判断</b>（见 {@link PocketJeiTransfer#resolveInputSlotStart}）。
 * </p>
 */
public class PocketBagRecipeTransferHandler<C extends AbstractContainerMenu, R>
        implements IRecipeTransferHandler<C, R> {

    private final IRecipeTransferHandler<C, R> delegate;
    /**
     * JEI 的转移辅助：用来造"先打开工作台页或拆解页"这类<b>玩家可见</b>的错误提示
     * （{@link IRecipeTransferHandlerHelper#createUserErrorWithTooltip(Component)}）。
     */
    private final IRecipeTransferHandlerHelper helper;
    /** true = 锻造配方（3 格），false = 合成配方（最多 9 格） */
    private final boolean smithing;
    /** 配方输入在容器里的起始槽位（合成台 = 1（0 是产物），锻造台 = 0） */
    private final int inputSlotStart;
    /** 配方输入槽位数（合成 = 9，锻造 = 3） */
    private final int inputSlotCount;
    /** 单格候选物品上限（与网络包侧保持一致，避免包体过大） */
    private static final int MAX_CANDIDATES = 32;
    /** 次元袋九宫格的列数 / 行数（工作台页与拆解页的九宫格都是 3×3，且都是行优先） */
    private static final int GRID_COLS = 3;
    private static final int GRID_ROWS = 3;
    /** 次元袋九宫格格数（3×3 = 9；和服务端 {@link PocketJeiTransfer#GRID_SLOTS} 是同一个数） */
    private static final int GRID_SLOTS = PocketJeiTransfer.GRID_SLOTS;

    public PocketBagRecipeTransferHandler(IRecipeTransferHandler<C, R> delegate, boolean smithing,
                                          int inputSlotStart, int inputSlotCount,
                                          IRecipeTransferHandlerHelper helper) {
        this.delegate = delegate;
        this.helper = helper;
        this.smithing = smithing;
        this.inputSlotStart = inputSlotStart;
        this.inputSlotCount = inputSlotCount;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return delegate.getContainerClass();
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return delegate.getMenuType();
    }

    @Override
    public RecipeType<R> getRecipeType() {
        return delegate.getRecipeType();
    }

    @Override
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlots,
                                               Player player, boolean maxTransfer, boolean doTransfer) {
        // ① 输入区间：原版工作台 / 锻造台 = 注册时写死的那一段（区间由容器自己决定，和面板无关）；
        //    次元袋界面 = 当前展开的功能页那一块九宫格（工作台页 CRAFT_GRID_BASE / 拆解页 DIS_MAT_BASE）
        int rangeStart = this.inputSlotStart;
        int rangeCount = this.inputSlotCount;
        if (container instanceof PocketDimensionMenu pocket) {
            int openStart = PocketJeiTransfer.resolveInputSlotStart(pocket);
            if (openStart < 0) {
                // ⚠️⚠️ 「打开哪个匹配哪个」：工作台页和拆解页都没开的时候，点 + 什么都不该发生。
                //    老代码由服务端 handleTrayAction(ACTION_OPEN_CRAFTING) **自动打开工作台页**再摆料，
                //    表现为「袋子里的工作台和拆解台界面都没开的时候，点 jei 配方还是会自动匹配，
                //    匹配的是工作台」。这里先在客户端拦住：
                //    ！不能回退给 JEI（它看不见袋子，只会误报"材料不足"，与事实不符），
                //    而是用 createUserErrorWithTooltip 给一句可直接展示给玩家的提示 —— "+"号悬停就能看到。
                //    拆解页"正在预览材料"时九宫格是只读预览（不能碰），提示词也不一样。
                String hintKey = pocket.isPanelOpen(PocketDimensionMenu.PANEL_DISASSEMBLE)
                        ? "gui.zzq_survival_toolbox.jei.dis_preview_hint"
                        : "gui.zzq_survival_toolbox.jei.open_panel_hint";
                if (!doTransfer) {
                    return helper.createUserErrorWithTooltip(Component.translatable(hintKey));
                }
                return null;   // 真点了 + 也不发包：绝不打开面板、绝不摆料（服务端同样会拦，见 PocketJeiTransfer#apply）
            }
            rangeStart = openStart;
            rangeCount = GRID_SLOTS;
        }

        ItemStack bag = findBag(player);
        // 身上没有袋子：这件事与本模组无关，原样交给 JEI（它自己的行为更好）
        if (bag.isEmpty()) {
            return delegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
        // ② 配方每一格的候选物品表：区间是九宫格时按 3×3 网格对齐（造型配方必须这样），否则按顺序对位
        List<List<ItemStack>> plan = buildPlan(recipe, rangeCount);
        if (plan.isEmpty()) {
            // 认不出的配方（原版"特殊配方"的 getIngredients() 就是空的）：老行为交给 JEI，
            // 它自己会按配方槽位视图处理这类配方。
            return delegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
        // 打开的袋子页：次元袋界面开着 = 它当前显示的那一页；别的界面 = 没有页开着（看所有页）
        int pageIndex = displayedPageIndex(container);

        if (doTransfer) {
            // 自己搬：服务端按格对位取料摆格（见 PocketJeiTransfer）。返回 null = JEI 认为转移成功。
            // 区间（rangeStart/rangeCount）随包发上去——服务端据此再确认"那一页确实开着"，
            // 并只往那一块九宫格里摆料。
            SurvivalToolbox.CHANNEL.sendToServer(
                    new com.zzq.survival_toolbox.network.PocketJeiTransferPacket(
                            plan, rangeStart, rangeCount, pageIndex));
            return null;
        }

        // 可转移判定：背包 + 副手 + 输入槽 + 袋子（+ 只从打开的那一页）
        int answer = availability(container, player, bag, plan, pageIndex, rangeStart, rangeCount);
        // 只有"客户端算得准"（YES，含袋子里的东西）才直接放行返回 null ——
        // ⚠️ 这一步不能交给 JEI：它看不见袋子，客户端说凑得齐而它说凑不齐的时候，交给它就会误报"材料不足"。
        if (answer == PocketJeiAvailabilityCache.YES) return null;
        // NO（确实凑不齐）/ UNKNOWN（查询包刚发出去、还没有答案）→ 一律交给 JEI 自己的判定：
        //   ⚠️ UNKNOWN **绝不乐观放行**（那正是"凑不齐 + 号也能点"的根源）；
        //   服务端的回复到了缓存里就有答案了，JEI 下一次刷新这个按钮时会按新答案重算，
        //   所以"第一次悬停报了材料不足"不会一直挂着。
        return delegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, false);
    }

    /**
     * 现在这些材料凑得齐吗（客户端能算就算，算不了才问服务端）。
     *
     * @param rangeStart 输入区间在容器里的起始槽位（次元袋界面 = 当前展开那一页的九宫格）
     * @param rangeCount 输入区间的格数（九宫格 = 9、锻造 = 3、原版工作台 = 9）
     * @return {@link PocketJeiAvailabilityCache#YES} / {@link PocketJeiAvailabilityCache#NO} /
     *         {@link PocketJeiAvailabilityCache#UNKNOWN}（已发出询问、等回复）
     */
    private int availability(C container, Player player, ItemStack bag, List<List<ItemStack>> plan, int pageIndex,
                            int rangeStart, int rangeCount) {
        // ① 次元袋界面开着：客户端手上就有"当前显示页"的权威同步数据 —— 直接算，还省一次往返。
        //    规则是"打开哪个就匹配哪个"，这里用的正是玩家眼睛看到的那 54 格。
        if (container instanceof PocketDimensionMenu pocket) {
            List<ItemStack> pool = new ArrayList<>();
            List<Long> poolCount = new ArrayList<>();
            collectPlayerAndInputs(container, player, pool, poolCount, rangeStart, rangeCount);
            for (int i = 0; i < PocketStorageHelper.Page.SLOTS; i++) {
                ItemStack shown = pocket.getPocketContainer().getItem(i);
                if (shown == null || shown.isEmpty()) continue;
                // 流体格的显示栈只是"桶图标"，不是真物品，绝不能当材料算进去
                if (PocketStorageHelper.isFluidDisplayStack(shown)) continue;
                addToPool(pool, poolCount, shown, shown.getCount());
            }
            return fits(plan, pool, poolCount) ? PocketJeiAvailabilityCache.YES : PocketJeiAvailabilityCache.NO;
        }
        // ② 本地模式袋子：页数据就存在袋子 NBT 里，客户端读得到（这时候没有"打开的页"，按所有页算）
        if (!PocketStorageHelper.isShared(bag)) {
            List<ItemStack> pool = new ArrayList<>();
            List<Long> poolCount = new ArrayList<>();
            collectPlayerAndInputs(container, player, pool, poolCount, rangeStart, rangeCount);
            for (PocketStorageHelper.Page page : PocketStorageHelper.readPages(bag)) {
                for (PocketStorageHelper.Entry e : page.entries) {
                    if (e == null || e.stack().isEmpty() || e.count() <= 0) continue;
                    addToPool(pool, poolCount, e.stack(), e.count());
                }
            }
            return fits(plan, pool, poolCount) ? PocketJeiAvailabilityCache.YES : PocketJeiAvailabilityCache.NO;
        }
        // ③ 共享模式袋子 + 客户端看不见内容（原版工作台 / 锻造台这种没有袋子页开着的界面）：
        //    只能问服务端。答案按签名缓存；没有答案时**不乐观放行**。
        long signature = signatureOf(bag, plan, container, pageIndex, rangeStart, rangeCount);
        int cached = PocketJeiAvailabilityCache.lookup(signature);
        if (cached != PocketJeiAvailabilityCache.UNKNOWN) return cached;
        if (PocketJeiAvailabilityCache.canSendQuery()) {
            PocketJeiAvailabilityCache.markPending(signature);
            SurvivalToolbox.CHANNEL.sendToServer(
                    new com.zzq.survival_toolbox.network.PocketJeiQueryPacket(
                            plan, rangeStart, rangeCount, pageIndex, signature));
        }
        return PocketJeiAvailabilityCache.UNKNOWN;
    }

    /**
     * 客户端算的缓存签名：袋子身份 + 打开的页号 + 输入区间 + 输入格内容 + 配方每格的候选表。
     * <p>
     * 任何一样变了签名就变（换配方 / 翻页 / <b>换一个面板（区间变了）</b> / 摆了一个材料 / 换成另一个袋子），
     * 缓存自然失效并重新问一次服务端 —— 不会拿旧答案误导玩家。
     * </p>
     */
    private long signatureOf(ItemStack bag, List<List<ItemStack>> plan, C container, int pageIndex,
                             int rangeStart, int rangeCount) {
        long h = 1125899906842597L;
        h = 31L * h + System.identityHashCode(bag);
        h = 31L * h + pageIndex;
        h = 31L * h + rangeStart;
        h = 31L * h + rangeCount;
        int from = Math.max(rangeStart, 0);
        for (int i = from; i < rangeStart + rangeCount && i < container.slots.size(); i++) {
            ItemStack in = container.getSlot(i).getItem();
            h = 31L * h + itemKey(in);
            h = 31L * h + (in == null ? 0 : in.getCount());
        }
        for (List<ItemStack> candidates : plan) {
            h = 31L * h + candidates.size();
            for (ItemStack s : candidates) {
                h = 31L * h + itemKey(s);
            }
        }
        return h;
    }

    /** 物品的快速指纹（注册表 id + NBT 哈希；空格 = 0） */
    private static int itemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int id = BuiltInRegistries.ITEM.getId(stack.getItem());
        int tag = stack.getTag() == null ? 0 : stack.getTag().hashCode();
        return id * 31 + tag;
    }

    /**
     * 玩家背包 + 副手 + 输入格里的东西（输入格里"本来就是对的"那几件不用重摆，算进池子）。
     *
     * @param rangeStart 输入区间起始槽位（次元袋界面 = 当前展开那一页的九宫格）
     */
    private void collectPlayerAndInputs(C container, Player player, List<ItemStack> pool, List<Long> poolCount,
                                       int rangeStart, int rangeCount) {
        for (ItemStack stack : player.getInventory().items) addToPool(pool, poolCount, stack);
        for (ItemStack stack : player.getInventory().offhand) addToPool(pool, poolCount, stack);
        int from = Math.max(rangeStart, 0);
        for (int i = from; i < rangeStart + rangeCount && i < container.slots.size(); i++) {
            addToPool(pool, poolCount, container.getSlot(i).getItem());
        }
    }

    /**
     * 池子够不够按配方格顺序摆一遍（每格从它的候选表里挑第一个池子里还有的，挑到就扣掉一个）。
     * <p>
     * ⚠️ 顺序必须和服务端 {@code PocketJeiTransfer#canFill} 一致（都是"按配方格顺序消耗"，空格子跳过），
     * 否则会出现"客户端说能点、服务端点不上"或者反过来。
     * </p>
     */
    private static boolean fits(List<List<ItemStack>> plan, List<ItemStack> pool, List<Long> poolCount) {
        for (List<ItemStack> candidates : plan) {
            if (candidates == null || candidates.isEmpty()) continue;   // 配方这一格是空的
            boolean satisfied = false;
            for (ItemStack want : candidates) {
                if (want == null || want.isEmpty()) continue;
                for (int i = 0; i < pool.size(); i++) {
                    if (poolCount.get(i) <= 0L) continue;
                    if (!PocketStorageHelper.sameItem(pool.get(i), want)) continue;
                    poolCount.set(i, poolCount.get(i) - 1L);
                    satisfied = true;
                    break;
                }
                if (satisfied) break;
            }
            if (!satisfied) return false;
        }
        return true;
    }

    /**
     * 客户端看到的"打开的袋子页"页号（没有袋子页开着 = {@link PocketStorageHelper#PAGE_ALL}）。
     * <p>
     * ⚠️ 搜索视图是跨页的（把各页结果摊开看），那种情况下必须按"所有页"算：
     * 玩家眼前明明列着第 7 页的东西，只认"当前页"就会莫名其妙地说材料不足。
     * 服务端 {@code PocketDimensionMenu#getDisplayedPageIndex()} 用的是同一套判断，两边必须一致。
     * </p>
     */
    private static int displayedPageIndex(AbstractContainerMenu container) {
        if (container instanceof PocketDimensionMenu menu) {
            return menu.getDisplayedPageIndex();
        }
        return PocketStorageHelper.PAGE_ALL;
    }

    /**
     * 配方每一格的候选物品表（按配方格顺序；空列表 = 这一格配方是空的）。
     * <p>
     * 用原版通用的 {@link Recipe#getIngredients()}：合成配方与锻造配方（SmithingTransform/TrimRecipe）
     * 都实现了它，不需要按配方类型分别取字段（{@code SmithingRecipe} 接口本身只有
     * "是不是模板/基础/材料"的判断方法，没有返回 Ingredient 的 getter）。
     * </p>
     * <p>
     * ⚠️ <b>必须按格位对齐</b>（空格子留空列表）：服务端要靠下标知道"这一格摆什么"。
     * 造型配方（shaped）里空格子的 Ingredient 是空的那一个，正好对应"这一格必须留空"。
     * 候选物品也不能只取第一个——{@code #minecraft:planks} 这类标签的第一个可能是橡木，
     * 玩家袋里只有云杉木板，只报第一个就永远填不上（实测遇到的正是这种"木板"配方）。
     * </p>
     *
     * @param rangeCount 输入区间的格数：<b>9（3×3 九宫格）时造型配方要按网格对齐</b>，否则按顺序对位
     */
    private List<List<ItemStack>> buildPlan(R recipe, int rangeCount) {
        List<List<ItemStack>> plan = new ArrayList<>();
        Recipe<?> vanillaRecipe = unwrapRecipe(recipe);
        if (vanillaRecipe == null) return plan;
        // ⚠️⚠️ 造型配方必须按 3×3 网格对齐（实测的"门变活板门"）
        //    —— 详见 buildGridPlan 的说明。
        if (rangeCount == GRID_SLOTS && vanillaRecipe instanceof ShapedRecipe shaped) {
            return buildGridPlan(shaped);
        }
        for (Ingredient ingredient : vanillaRecipe.getIngredients()) {
            plan.add(candidatesOf(ingredient));
        }
        return plan;
    }

    /**
     * 造型配方的"网格对齐"计划：<b>正好 9 项</b>，第 {@code col + row * 3} 项 = 配方图案第 {@code row} 行第 {@code col} 列的材料。
     * <p>
     * <b>⚠️⚠️ 为什么不能直接用 {@link Recipe#getIngredients()} 的顺序</b>（实测案例：
     * 橡木门的图案是 <b>2 宽 × 3 高</b>（两竖列木板），而活板门是 <b>3 宽 × 2 高</b>（两横排木板））：
     * {@code getIngredients()} 是<b>按配方自己包围盒的行优先</b>（下标 = col + row * 宽），
     * 而服务端是"配方第 k 格 → 容器槽 inputSlotStart + k"地<b>顺序</b>摆进一个<b>3 宽</b>的九宫格。
     * 于是 2×3 的门被摊成 3×2 —— 玩家看到的正是"选门摆出来是活板门"。
     * 这里改成"格位对齐"：把图案放进 3×3 网格的左上方，
     * 只用到的行/列填材料，其余项留空（原版会忽略空行空列，1.21.1 甚至会把 3×3 输入收缩到材料的包围盒再比对）。
     * </p>
     * <p>
     * 无序配方（shapeless）不看位置，仍然走"顺序对位"，不用这套。
     * </p>
     */
    private List<List<ItemStack>> buildGridPlan(ShapedRecipe shaped) {
        List<List<ItemStack>> plan = new ArrayList<>(GRID_SLOTS);
        for (int i = 0; i < GRID_SLOTS; i++) {
            plan.add(new ArrayList<>());          // 先铺满 9 个空格子（服务端"空格子 = 这一格必须留空"）
        }
        NonNullList<Ingredient> ingredients = shaped.getIngredients();
        int width = shaped.getWidth();
        int height = shaped.getHeight();
        for (int row = 0; row < height && row < GRID_ROWS; row++) {
            for (int col = 0; col < width && col < GRID_COLS; col++) {
                int index = row * width + col;                    // 配方自身包围盒里的下标（行优先）
                if (index < 0 || index >= ingredients.size()) continue;
                plan.set(col + row * GRID_COLS, candidatesOf(ingredients.get(index)));
            }
        }
        return plan;
    }

    /** 一个 Ingredient 的候选物品表（空 = 这一格是空的；袋子本身不能当材料） */
    private static List<ItemStack> candidatesOf(Ingredient ingredient) {
        List<ItemStack> candidates = new ArrayList<>();
        if (ingredient == null || ingredient.isEmpty()) return candidates;
        ItemStack[] items = ingredient.getItems();
        for (int i = 0; i < items.length && candidates.size() < MAX_CANDIDATES; i++) {
            ItemStack stack = items[i];
            if (stack == null || stack.isEmpty()) continue;
            // 袋子本身不能当材料塞进格子
            if (stack.is(ModItems.POCKET_DIMENSION.get())) continue;
            candidates.add(stack.copy());
        }
        return candidates;
    }

    /**
     * 从 JEI 交上来的对象里取出真正的配方。
     * <p>
     * ⚠️ 1.20.1 的 JEI 15 直接把配方对象交给处理器
     * （{@code RecipeTypes.CRAFTING} = {@code RecipeType<CraftingRecipe>}），所以这里总是直接命中。
     * 1.21.1 的 JEI 19 交上来的是 {@code RecipeHolder}，那边这份方法多一层拆包 —— 两边行为一致，
     * 只是 API 不同（见 1.21.1 树里同名方法的说明）。
     * </p>
     *
     * @return 真正的配方；认不出来时返回 null（调用方按"交给 JEI"处理）
     */
    private static Recipe<?> unwrapRecipe(Object recipe) {
        return recipe instanceof Recipe<?> vanilla ? vanilla : null;
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

    /** 玩家身上的次元袋（主手优先，其次副手、背包） */
    private static ItemStack findBag(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.POCKET_DIMENSION.get())) return main;
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.POCKET_DIMENSION.get())) return off;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.POCKET_DIMENSION.get())) return stack;
        }
        return ItemStack.EMPTY;
    }
}
