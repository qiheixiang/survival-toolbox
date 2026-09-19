package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.DisassembleBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlocks;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 拆解台菜单容器
 * <p>
 * 槽位布局：
 * <ul>
 *   <li>0：左侧输入槽</li>
 *   <li>1-9：中间九宫格</li>
 *   <li>10：右侧输出槽</li>
 * </ul>
 * 功能：根据输入物品自动匹配拆解配方或合成配方，支持翻页浏览多种配方变体。
 * </p>
 */
public class DisassembleMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;
    private final Player player;

    private List<Recipe<?>> disassembleRecipes = new ArrayList<>();
    private int disassembleRecipeIdx = 0;
    private List<RecipeVariantPair> variantPairs = new ArrayList<>();
    private int currentPairIndex = 0;
    private int materialVariantIdx = 0;

    private List<PotionSingleStep> currentPotionSteps = new ArrayList<>();
    private int currentPotionStepIndex = 0;

    private List<Object> craftRecipes = new ArrayList<>();
    private int craftRecipeIdx = 0;

    /**
     * 全局配方索引：按"输出物品"和"材料物品"双向索引配方。
     * 在后台线程异步构建（见 {@link #buildRecipeIndex}），交互时只查缓存，
     * 绝不在服务器线程上全量扫描配方——否则 ATM 这种上万配方的大型整合包会阻塞服务器数十秒。
     */
    private static final java.util.concurrent.ConcurrentMap<Item, List<Recipe<?>>> RECIPES_BY_OUTPUT =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 按"物品|NBT 标识（tacz GunId）"精确索引输出配方：枪械等"同物品不同 NBT 区分"的物品能精确找到自己的配方 */
    private static final java.util.concurrent.ConcurrentMap<String, List<Recipe<?>>> RECIPES_BY_OUTPUT_DETAIL =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<Item, List<Recipe<?>>> RECIPES_BY_INGREDIENT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 全部锻造（Smithing）配方。
     * <p>
     * 锻造配方的材料可能枚举不出来（部分模组/自定义实现在 1.21 不实现 getIngredients），
     * 那样就进不了"材料→配方"索引，合成页永远看不到锻造配方。这里单独留一份列表，
     * 用 {@code isTemplateIngredient/isBaseIngredient/isAdditionIngredient} 三个角色判定
     * 直接匹配九宫格，与材料能否枚举、摆放位置都无关。
     * </p>
     */
    private static final java.util.List<Recipe<?>> SMITHING_RECIPES =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    /** 阶段一：产出→配方 索引已就绪（拆解可用） */
    private static volatile boolean recipeIndexOutputReady = false;
    /** 阶段二：材料→配方 索引已就绪（合成可用） */
    private static volatile boolean recipeIndexReady = false;
    private static volatile boolean recipeIndexBuilding = false;
    /**
     * 索引代数：resetRecipeIndex 时 +1，后台构建记录开始时代数。
     * 构建中若代数已变（被重置），说明读取的是旧配方的快照，立即放弃，
     * 避免退出世界/F3+T 后残留的旧构建线程把就绪标记写回去（导致新世界直接"已就绪"、进度不显示）。
     */
    private static volatile int indexGeneration = 0;

    /** 阶段二构建进度（后台线程更新）：已处理 / 总数，用于客户端显示 "X/Y" */
    private static volatile int recipeIndexProcessed = 0;
    private static volatile int recipeIndexTotal = 0;
    /** 客户端最近一次收到的进度（由 SyncRecipeIndexProgressPacket 写入） */
    private static volatile int clientIndexProcessed = 0;
    private static volatile int clientIndexTotal = 0;
    /** 每个菜单实例上次已推送的进度，避免每 tick 重复发包 */
    private int lastSentIndexProgress = -1;
    /** 每个菜单实例上次发包时间戳，进一步限频（阶段一计数飞快，仅靠数量阈值会刷包） */
    private long lastSentIndexTime = 0;

    /**
     * 读取 ingredients 会抛异常的配方类型缓存（如被 AllTheLeaks 锁定的现代工业化 MachineRecipe）。
     * 同一类型只抛一次，之后直接跳过。
     */
    private static final java.util.Set<Class<?>> UNREADABLE_RECIPE_TYPES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 阶段二每条配方材料解析的超时；超时视为该配方类型"挂起/死锁"，跳过并记入 UNREADABLE_RECIPE_TYPES。
     * 正常解析只耗时几毫秒，2s 余量已很充足，仅真死锁才会触发；给弱机器/极端卡顿留足余量避免误杀。
     */
    private static final long RECIPE_INGREDIENT_TIMEOUT_MS = 2000L;
    /** 用于带超时地解析配方材料；个别配方类型会占住线程（挂起），缓存池可避免阻塞主构建线程 */
    private static final java.util.concurrent.ExecutorService RECIPE_INGREDIENT_EXECUTOR =
            java.util.concurrent.Executors.newCachedThreadPool();

    /** 本菜单已处理过的索引状态位（bit0=拆解就绪, bit1=合成就绪），避免每 tick 重复触发重算 */
    private int handledIndexState = 0;
    private boolean isUpdating = false;
    private boolean leftConsumed = false;
    private boolean middleModified = false;
    private boolean isFillingMaterials = false;
    private boolean isTakingResult = false;

    private List<ItemStack> currentProductList = new ArrayList<>();
    /**
     * 额外的"中心/激活/催化"材料（每次解析配方时重建，只用于填九宫格）。
     * <p>
     * ⚠️ 判定规则：拆解方向上这些**就是配方的一部分，照常返还** ——
     * 拆一件用它们做出来的成品，应该连中心物品一起还回来（诡厄巫法祭坛的"中间黑暗魔杖"就是这种）。
     * 所以九宫格后半那几格**既能拿也能放**，`performDisassemble` / 全部拆解也都会把它们发出去。
     * （合成方向不需要特别处理：那边九宫格里本来就是玩家自己摆的材料。）
     * </p>
     */
    private static final java.util.Map<Object, List<ItemStack>> EXTRA_DISPLAY = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    public boolean isButtonMode = false;
    private final DataSlot buttonModeDataSlot = DataSlot.standalone();
    private ItemStack[] previousMiddleStacks = new ItemStack[9];

    /**
     * 产物结算结果（{@link #performDisassemble()} / {@link #performCraft()} 这一轮到底成没成）。
     * <p>
     * 给"次元袋里的拆解页"用：那边不能拿"槽 10 是不是空了"当成功判据——合成成功但九宫格里
     * 还剩材料时，原版会把产物重新画回槽 10（预览），于是判据永远不成立、产物永远拿不到
     * （实测："光消耗拿不到产物、最后一次能拿出来"）。
     * </p>
     */
    private boolean lastSettleOk = false;

    private final DataSlot disassembleIndexSlot = DataSlot.standalone();
    private final DataSlot disassembleTotalSlot = DataSlot.standalone();
    private final DataSlot craftIndexSlot = DataSlot.standalone();
    private final DataSlot craftTotalSlot = DataSlot.standalone();
    /** 配方索引是否就绪（客户端据此显示"构建中"提示） */
    private final DataSlot recipeReadyDataSlot = DataSlot.standalone();

    // ---- 内部辅助类 ----

    private static class RecipeVariantPair {
        int recipeIdx, variantIdx;

        RecipeVariantPair(int r, int v) {
            recipeIdx = r;
            variantIdx = v;
        }
    }

    private static class PotionSingleStep {
        final Potion from;
        final Ingredient ingredient;
        final Potion to;

        PotionSingleStep(Potion from, Ingredient ingredient, Potion to) {
            this.from = from;
            this.ingredient = ingredient;
            this.to = to;
        }
    }

    private static class BlockEntityContainer implements Container {
        private final DisassembleBlockEntity be;

        BlockEntityContainer(DisassembleBlockEntity be) {
            this.be = be;
        }

        @Override
        public int getContainerSize() {
            return DisassembleBlockEntity.TOTAL_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < getContainerSize(); i++) {
                if (!be.getItem(i).isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return be.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = be.getItem(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = stack.split(amount);
            if (stack.isEmpty()) be.setItem(slot, ItemStack.EMPTY);
            else be.setItem(slot, stack);
            be.setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack s = be.getItem(slot);
            be.setItem(slot, ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            be.setItem(slot, stack);
        }

        @Override
        public void setChanged() {
            be.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < getContainerSize(); i++) {
                be.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ---- 构造函数 ----

    public DisassembleMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(DisassembleBlockEntity.TOTAL_SLOTS), ContainerLevelAccess.NULL);
    }

    public DisassembleMenu(int id, Inventory inv, DisassembleBlockEntity be) {
        this(id, inv, new BlockEntityContainer(be), ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
    }

    private DisassembleMenu(int id, Inventory inv, Container cont, ContainerLevelAccess acc) {
        super(ModMenus.DISASSEMBLE.get(), id);
        this.addDataSlot(buttonModeDataSlot);
        this.addDataSlot(disassembleIndexSlot);
        this.addDataSlot(disassembleTotalSlot);
        this.addDataSlot(craftIndexSlot);
        this.addDataSlot(craftTotalSlot);
        this.addDataSlot(recipeReadyDataSlot);

        this.container = cont;
        this.access = acc;
        this.player = inv.player;
        // 打开菜单即开始后台构建配方索引，玩家放东西时通常已就绪
        ensureRecipeIndex(this.player.level());
        this.handledIndexState = (recipeIndexOutputReady ? 1 : 0) | (recipeIndexReady ? 2 : 0);
        clearMiddleCache();

        // ---- 输入槽 ----
        this.addSlot(new Slot(container, 0, 19, 28) {
            @Override
            public void setChanged() {
                super.setChanged();
                // ⚠️⚠️ 这里必须兜住 **Throwable**（不只是 Exception），而且要打日志：
                //      历史问题：「1.21.1 往拆解方块里放东西直接闪退」——那一下走的就是这里。
                //      大整合包里某个 mod 的配方一旦触发 Error（NoSuchMethodError / AbstractMethodError /
                //      NoClassDefFoundError 这类），它**不**属于 Exception，会一路穿到服务端 tick 上直接把游戏带走。
                //      兜住之后最多是"这一件物品找不到拆解配方"（并且日志里留下是哪个配方干的），绝不会闪退。
                try {
                    onLeftSlotChanged();
                } catch (Throwable t) {
                    com.mojang.logging.LogUtils.getLogger().error(
                            "[次元袋·拆解台] 处理输入槽时出错（已兜住，不影响游戏）：物品={}",
                            container.getItem(0), t);
                    for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                    container.setItem(10, ItemStack.EMPTY);
                }
            }
        });

        // ---- 九宫格（1-9） ----
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                final int idx = 1 + r * 3 + c;
                this.addSlot(new Slot(container, idx, 57 + c * 18, 18 + r * 18) {
                    @Override
                    public boolean mayPickup(Player p) {
                        return !isButtonMode();
                    }

                    @Override
                    public boolean mayPlace(ItemStack s) {
                        return !isButtonMode();
                    }

                    @Override
                    public void setChanged() {
                        super.setChanged();
                        onMiddleSlotsChanged();
                    }
                });
            }
        }

        // ---- 输出槽 ----
        this.addSlot(new Slot(container, 10, 139, 28) {
            @Override
            public boolean mayPlace(@NotNull ItemStack s) {
                return false;
            }

            @Override
            public void onTake(@NotNull Player p, @NotNull ItemStack s) {
                super.onTake(p, s);
                onCraftResultTaken();
            }
        });

        // ---- 玩家背包 ----
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c, 8 + c * 18, 142));
        }
    }

    // ---- 公共方法 ----

    public boolean isButtonMode() {
        return buttonModeDataSlot.get() == 1;
    }

    private void setButtonMode(boolean m) {
        buttonModeDataSlot.set(m ? 1 : 0);
        broadcastChanges();
    }

    public int getDisassembleRecipeIndex() {
        return disassembleIndexSlot.get();
    }

    public int getDisassembleRecipeTotal() {
        return disassembleTotalSlot.get();
    }

    public int getCraftRecipeIndex() {
        return craftIndexSlot.get();
    }

    public int getCraftRecipeTotal() {
        return craftTotalSlot.get();
    }

    // ---- 药水辅助 ----

    private static Potion getPotion(ItemStack stack) {
        net.minecraft.world.item.alchemy.PotionContents pc =
                stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (pc == null) return null;
        return pc.potion().map(net.minecraft.core.Holder::value).orElse(null);
    }

    private static ItemStack setPotion(ItemStack stack, Potion potion, net.minecraft.core.HolderLookup.Provider registries) {
        if (potion == null) return stack;
        // 1.21.1 药水是数据驱动注册表，mod 药水不在 BuiltInRegistries 里。
        // 用世界的数据驱动注册表解析 holder，取不到再退回 direct，保证 mod 药水也能正确序列化。
        net.minecraft.core.HolderLookup.RegistryLookup<Potion> reg = registries.lookupOrThrow(net.minecraft.core.registries.Registries.POTION);
        net.minecraft.core.Holder<Potion> holder = reg.listElements()
                .filter(h -> h.value() == potion)
                .findFirst()
                .<net.minecraft.core.Holder<Potion>>map(h -> h)
                .orElseGet(() -> net.minecraft.core.Holder.direct(potion));
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(holder));
        return stack;
    }

    // ---- 酿造步骤获取 ----

    // 酿造步骤只在数据包重载时变化，按 PotionBrewing 实例缓存，避免每次操作都反射扫描。
    private static final java.util.IdentityHashMap<PotionBrewing, List<PotionSingleStep>> BREWING_STEPS_CACHE = new java.util.IdentityHashMap<>();

    private List<PotionSingleStep> getAllBrewingSteps() {
        PotionBrewing brewing = player.level().potionBrewing();
        if (brewing == null) return List.of();
        List<PotionSingleStep> cached = BREWING_STEPS_CACHE.get(brewing);
        if (cached != null) return cached;
        List<PotionSingleStep> steps = buildBrewingSteps(brewing);
        BREWING_STEPS_CACHE.put(brewing, steps);
        return steps;
    }

    private static List<PotionSingleStep> buildBrewingSteps(PotionBrewing brewing) {
        List<PotionSingleStep> steps = new ArrayList<>();

        // 1.21.1 中 PotionBrewing 为实例类（每个维度/数据包各一份），原版配方
        // 存放于 potionMixes / containerMixes 两个实例字段中（1.20.1 为静态字段，
        // 无法通过静态反射 field.get(null) 访问）。此处获取实例后反射读取，
        // 使原版与采用标准 POTION_CONTENTS 组件的 mod 配方均能被解析。
        for (String fieldName : new String[]{"potionMixes", "containerMixes"}) {
            try {
                java.lang.reflect.Field f = brewing.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(brewing);
                if (val instanceof List<?> list) {
                    for (Object mix : list) {
                        PotionSingleStep step = extractPotionSingleStep(mix);
                        if (step != null) steps.add(step);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // NeoForge 注册的通用酿造配方（其他 mod 通过 BrewingRecipeRegistry 注册）
        for (IBrewingRecipe recipe : brewing.getRecipes()) {
            if (recipe instanceof BrewingRecipe br) {
                ItemStack[] inputs = br.getInput().getItems();
                if (inputs.length == 0) continue;
                Potion from = getPotion(inputs[0]);
                Potion to = getPotion(br.getOutput());
                if (from != null && to != null) {
                    steps.add(new PotionSingleStep(from, br.getIngredient(), to));
                }
            } else {
                Potion from = null, to = null;
                Ingredient ing = null;
                for (java.lang.reflect.Field f : recipe.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(recipe);
                        if (val instanceof Potion pot) {
                            if (from == null) from = pot;
                            else if (to == null) to = pot;
                        } else if (val instanceof Holder<?> h && h.value() instanceof Potion pot) {
                            if (from == null) from = pot;
                            else if (to == null) to = pot;
                        } else if (val instanceof Ingredient ingredient) {
                            ing = ingredient;
                        }
                    } catch (IllegalAccessException ignored) {
                    }
                }
                if (from != null && to != null && ing != null) {
                    steps.add(new PotionSingleStep(from, ing, to));
                }
            }
        }
        return steps;
    }

    private static PotionSingleStep extractPotionSingleStep(Object mix) {
        try {
            Potion from = null, to = null;
            Ingredient ingredient = null;
            for (java.lang.reflect.Field f : mix.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(mix);
                String fname = f.getName();
                if (val instanceof Potion pot) {
                    if ("to".equals(fname)) to = pot;
                    else if ("from".equals(fname)) from = pot;
                    else if (to == null) from = pot;
                    else to = pot;
                } else if (val instanceof Holder<?> h && h.value() instanceof Potion pot) {
                    if ("to".equals(fname)) to = pot;
                    else if ("from".equals(fname)) from = pot;
                    else if (to == null) to = pot;
                    else from = pot;
                } else if (val instanceof Ingredient ing) {
                    ingredient = ing;
                } else if (val instanceof net.minecraft.core.HolderSet<?> hs) {
                    List<ItemLike> items = hs.stream()
                            .filter(h -> h.value() instanceof ItemLike)
                            .map(h -> (ItemLike) h.value())
                            .toList();
                    if (!items.isEmpty()) {
                        ingredient = Ingredient.of(items.toArray(new ItemLike[0]));
                    }
                }
            }
            if (from != null && to != null && ingredient != null && !from.equals(to)) {
                return new PotionSingleStep(from, ingredient, to);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---- 通用辅助 ----

    private static ItemStack getRecipeOutput(Object recipeOrData, RegistryAccess access) {
        try {
            // ⚠️ 一律 copy：模组配方的 getResultItem / getOutput 很可能**直接把内部那个栈返回给调用方**
            //（实测 Create 的 ProcessingRecipe#getResultItem 就是 getRollableResults().getFirst().getStack()，
            //  连 assemble 都没 copy）—— 不复制的话，玩家从产物格拿走/扣数量会把**配方本体**改掉：
            //  JEI 界面上那个产物当场变空，只有重进世界才恢复（实测：一个青金石出两个蓝色染料
            //  拿走之后，Create 的研磨配方里那两条蓝染料没了）。
            if (recipeOrData instanceof BrewingRecipe br) return br.getOutput().copy();
            if (recipeOrData instanceof Recipe<?> r) return r.getResultItem(access).copy();
        } catch (Exception ignored) {
            // 部分模组配方读取结果时可能被 AllTheLeaks 等性能模组拦截抛异常，优雅跳过。
        }
        return ItemStack.EMPTY;
    }

    /**
     * 生成合成产物：优先调用配方的 assemble 组装（保留输入物品的 NBT/组件状态，
     * 如精妙背包等模组的升级配方、锻造台配方的附魔保留），无法组装时退回静态输出，
     * 并回退复制"同物品类型"输入的状态（排除耐久，避免修复类配方反向保留损坏值）。
     * <p>
     * 必须在消耗材料（tryConsumeIngredients）之前调用，输入取自九宫格副本。
     * </p>
     */
    private ItemStack assembleRecipeOutput(Recipe<?> recipe, RegistryAccess access) {
        try {
            if (recipe instanceof CraftingRecipe crafting) {
                List<ItemStack> items = new ArrayList<>();
                for (int i = 1; i <= 9; i++) {
                    items.add(container.getItem(i).copy());
                }
                CraftingInput input = CraftingInput.of(3, 3, items);
                ItemStack assembled = crafting.assemble(input, access);
                if (!assembled.isEmpty()) {
                    assembled = assembled.copy();   // ⚠️ 模组配方的 assemble 可能把内部栈直接返回给调用方（见 getRecipeOutput）
                    copyStateFromMatchingInput(assembled, access);
                    return assembled;
                }
            } else if (recipe instanceof SmithingRecipe smithing) {
                ItemStack assembled = assembleSmithingOutput(smithing, access);
                if (!assembled.isEmpty()) {
                    assembled = assembled.copy();
                    copyStateFromMatchingInput(assembled, access);
                    return assembled;
                }
            }
        } catch (Exception ignored) {
            // 部分模组配方组装异常时退回静态输出
        }

        // 回退 1：非 CraftingRecipe/SmithingRecipe 但可接受九宫格输入的配方
        //（如精妙背包等模组的自定义升级配方，其 assemble 自带状态/物品保留逻辑）
        ItemStack assembled = assembleAnyRecipe(recipe, access);
        if (!assembled.isEmpty()) return assembled;

        // 回退 2：非标准配方类型中，若输出与某个输入为同一种物品（"自身升级"类配方），
        // 把该输入的状态复制到产物，保证合成后状态不丢失。
        ItemStack result = getRecipeOutput(recipe, access);
        if (result.isEmpty()) return result;
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty() || s.getItem() != result.getItem()) continue;
            copyState(s, result);
            break;
        }
        return result;
    }

    /**
     * 锻造配方组装：按 模板/基础/材料 槽位从九宫格匹配输入并调用 assemble，
     * 保留基础物品的附魔/NBT（与原版锻造台一致）。匹配不齐时返回空。
     */
    private ItemStack assembleSmithingOutput(SmithingRecipe smithing, RegistryAccess access) {
        // 优先用配方自己的角色判定挑出 模板/基础/材料：与摆放位置、材料能否枚举都无关
        ItemStack[] byRole = pickSmithingRoleItems(smithing);
        if (byRole != null) {
            // 复制：把配方自己的栈交出去会被玩家的取走动作改坏（见 getRecipeOutput 的说明）
            return smithing.assemble(new SmithingRecipeInput(byRole[0], byRole[1], byRole[2]), access).copy();
        }

        // 回退：按配方材料顺序在九宫格里对号入座（角色判定不可用的老式实现）
        List<Ingredient> smithIngs = getIngredientsFromRecipe(smithing);
        if (smithIngs.size() < 3) return ItemStack.EMPTY;
        boolean[] used = new boolean[9];
        ItemStack[] ordered = new ItemStack[3];
        for (int slot = 0; slot < 3; slot++) {
            Ingredient ing = smithIngs.get(slot);
            for (int i = 1; i <= 9; i++) {
                if (used[i - 1]) continue;
                ItemStack s = container.getItem(i);
                if (!s.isEmpty() && ing.test(s)) {
                    used[i - 1] = true;
                    ordered[slot] = s.copy();
                    break;
                }
            }
            if (ordered[slot] == null) return ItemStack.EMPTY;
        }
        SmithingRecipeInput input = new SmithingRecipeInput(ordered[0], ordered[1], ordered[2]);
        return smithing.assemble(input, access).copy();
    }

    /**
     * 按角色从九宫格挑出 模板/基础/材料 三件（顺序即 模板、基础、材料）。
     * <p>
     * 位置无关：只看物品是否符合该配方的某个角色，因此在九宫格里怎么摆都能合成，
     * 也兼容 JEI 直接把三格材料填进九宫格的用法。
     * </p>
     *
     * @return 长度 3 的数组；凑不齐三件时返回 {@code null}
     */
    private ItemStack[] pickSmithingRoleItems(SmithingRecipe smithing) {
        ItemStack[] result = new ItemStack[3];
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            if (result[0] == null && smithing.isTemplateIngredient(s)) {
                result[0] = s.copy();
            } else if (result[1] == null && smithing.isBaseIngredient(s)) {
                result[1] = s.copy();
            } else if (result[2] == null && smithing.isAdditionIngredient(s)) {
                result[2] = s.copy();
            }
        }
        return result[0] != null && result[1] != null && result[2] != null ? result : null;
    }

    /** 复制输入物品的状态（组件）到产物，排除耐久组件 */
    private static void copyState(ItemStack from, ItemStack to) {
        to.applyComponents(from.getComponents());
        to.remove(net.minecraft.core.component.DataComponents.DAMAGE);
    }

    /** 合成产物与某输入为同种物品（含容器类，如精妙背包升级）时，复制该输入的状态到产物 */
    private void copyStateFromMatchingInput(ItemStack result, RegistryAccess access) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty() || s.getItem() != result.getItem()) continue;
            copyState(s, result);
            break;
        }
    }

    /** 对任意配方尝试用九宫格输入调用其自身 assemble（模组自定义配方的状态保留逻辑） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ItemStack assembleAnyRecipe(Recipe<?> recipe, RegistryAccess access) {
        try {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 1; i <= 9; i++) {
                items.add(container.getItem(i).copy());
            }
            CraftingInput input = CraftingInput.of(3, 3, items);
            ItemStack assembled = ((Recipe) recipe).assemble(input, access);
            if (!assembled.isEmpty()) {
                // ⚠️⚠️ 这里**必须**复制：Create 的 ProcessingRecipe#assemble 直接
                //    `return getResultItem(registries)`，而它内部是 `getRollableResults().getFirst().getStack()`
                //    —— 也就是**配方自己那个结果栈**。原样放进产物格之后，玩家一拿走/合并数量就把它 shrink 了，
                //    于是 JEI 里这条配方的产物当场变空、只有重进世界才恢复（实测的"青金石→2 蓝染料"）。
                assembled = assembled.copy();
                copyStateFromMatchingInput(assembled, access);
                return assembled;
            }
        } catch (Exception ignored) {
            // 配方输入类型不匹配时跳过，退回静态输出
        }
        return ItemStack.EMPTY;
    }

    /**
     * 完整材料清单 = getIngredients() + 额外 getter（中心/激活/催化物品）。
     * <p>
     * 合成方向（材料→产物）的校验与消耗都用它 —— 需求："合成时中心那件也要被消耗掉"；
     * 拆解方向（产物→材料）仍然只用 getIngredients()，中心物品只显示不返还（避免凭空造物）。
     * </p>
     */
    private static List<Ingredient> getFullIngredients(Recipe<?> recipe) {
        List<Ingredient> all = new ArrayList<>(getIngredientsFromRecipe(recipe));
        java.util.List<ItemStack> extras = EXTRA_DISPLAY.get(recipe);
        if (extras == null) {
            getIngredientsFromRecipe(recipe);          // 顺带把额外物品解析出来（按配方缓存）
            extras = EXTRA_DISPLAY.get(recipe);
        }
        if (extras != null) {
            for (ItemStack s : extras) {
                if (!s.isEmpty()) all.add(Ingredient.of(s.getItem()));
            }
        }
        return all;
    }
    private static List<Ingredient> getIngredientsFromRecipe(Object recipeOrData) {
        if (!(recipeOrData instanceof Recipe<?> recipe)) return Collections.emptyList();
        if (UNREADABLE_RECIPE_TYPES.contains(recipe.getClass())) return Collections.emptyList();
        NonNullList<Ingredient> ings;
        try {
            ings = recipe.getIngredients();
        } catch (Exception ignored) {
            // 部分模组配方（如现代工业化 MachineRecipe）读取 ingredients 时会对缓存的 ItemStack 调 setCount，
            // 被 AllTheLeaks 等性能模组锁定后抛异常。记录该配方类型，之后同类配方直接跳过，避免重复抛异常。
            UNREADABLE_RECIPE_TYPES.add(recipe.getClass());
            ings = NonNullList.create();
        }
        // 补：有些自定义配方把"中心 / 激活 / 催化"物品放在自己的字段里，getIngredients() 不包含它
        // —— 典型：诡厄巫法祭坛 RitualRecipe#getActivationItem（实测拆解台只列出外围材料、少了中间那件）。
        // 反射试一小撮常见 getter：返回 Ingredient 直接加，返回非空 ItemStack 就包成 Ingredient。
        // ⚠️ 绝不碰产物 getter（getResultItem / getResult），免得把产物当材料；出错一律静默跳过。
        try {
            java.util.List<ItemStack> extraFound = new java.util.ArrayList<>();
            for (String extraName : new String[]{"getActivationItem", "getCatalyst", "getCenterItem",
                    "getCoreItem", "getSacrificeItem", "getBaseItem"}) {
                try {
                    java.lang.reflect.Method em = recipe.getClass().getMethod(extraName);
                    if (em.getParameterCount() != 0) continue;
                    Object ev = em.invoke(recipe);
                    if (ev instanceof Ingredient extraIng) {
                        if (!extraIng.isEmpty() && extraIng.getItems().length > 0) { extraFound.add(extraIng.getItems()[0].copy()); EXTRA_DISPLAY.put(recipe, extraFound); }
                    } else if (ev instanceof ItemStack extraStack) {
                        if (!extraStack.isEmpty()) { extraFound.add(extraStack.copy()); EXTRA_DISPLAY.put(recipe, extraFound); }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        if (!ings.isEmpty()) return ings.stream().filter(i -> !i.isEmpty()).collect(Collectors.toList());

        // tacz 枪匠台配方：不实现 getIngredients()，材料在 getInputs()（List<GunSmithTableIngredient{Ingredient,count}>）
        if (recipe.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe")) {
            List<Ingredient> result = new ArrayList<>();
            try {
                Object inputs = recipe.getClass().getMethod("getInputs").invoke(recipe);
                if (inputs instanceof java.util.List<?> list) {
                    for (Object inp : list) {
                        if (inp == null) continue;
                        Object ing = inp.getClass().getMethod("getIngredient").invoke(inp);
                        Object cnt = inp.getClass().getMethod("getCount").invoke(inp);
                        int count = cnt instanceof Number n ? n.intValue() : 1;
                        if (!(ing instanceof Ingredient igin) || igin.isEmpty()) continue;
                        for (int i = 0; i < count; i++) result.add(igin);
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!result.isEmpty()) return result;
        }

        if (recipe instanceof BrewingRecipe br) {
            return Arrays.asList(br.getInput(), br.getIngredient());
        }

        // 锻造配方强制提取（含模组自定义的 SmithingRecipe 实现）：
        // 1.21 的原版实现不再重写 getIngredients，材料只能从字段里取。
        if (recipe instanceof SmithingRecipe) {
            List<Ingredient> result = new ArrayList<>();
            String[] fieldNames = {"template", "base", "addition", "left", "right", "middle", "input1", "input2", "input3"};
            for (String n : fieldNames) {
                try {
                    java.lang.reflect.Field f = recipe.getClass().getDeclaredField(n);
                    f.setAccessible(true);
                    Object val = f.get(recipe);
                    if (val instanceof Ingredient ing) result.add(ing);
                } catch (NoSuchFieldException | IllegalAccessException ignored) {
                }
            }
            if (result.isEmpty()) {
                try {
                    for (java.lang.reflect.Field f : recipe.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        // 只收 Ingredient 类型字段，绝不接受 ItemStack：
                        // 生产环境（srg 混淆）下 template/base/addition 字段名是 f_44514_ 之类，
                        // 按名匹配必然失败从而走到这里，而锻造配方的 result 字段正是 ItemStack（最终产物）——
                        // 若接受 ItemStack，成品本身就会被当材料列出来。Ingredient 恰好只有
                        // template/base/addition 三个，按类型过滤与字段名无关，彻底排除产物。
                        // （此前按字段名排除 result/output/out 在混淆名下无效，已废弃。）
                        Object val = f.get(recipe);
                        if (val instanceof Ingredient ing) result.add(ing);
                    }
                } catch (Exception ignored) {
                }
            }
            return result;
        }

        // 其他配方不再做全字段反射扫描：对复杂模组配方（如现代工业化）反射会深度递归、
        // 宽幅爆炸类配方会持续消耗大量 CPU 且无法终止；这类配方材料本就无法可靠提取，返回空让其跳过即可。
        return Collections.emptyList();
    }

    private static void collectIngredientsFromObject(Object obj, Set<Ingredient> result) {
        collectIngredientsFromObject(obj, result, new HashSet<>(), 0);
    }

    private static void collectIngredientsFromObject(Object obj, Set<Ingredient> result,
                                                     Set<Object> visited, int depth) {
        if (obj == null || depth > 50) return;
        if (visited.contains(obj)) return;
        visited.add(obj);

        if (obj instanceof Ingredient ing) {
            result.add(ing);
            return;
        }
        if (obj instanceof ItemStack stack && !stack.isEmpty()) {
            result.add(Ingredient.of(stack));
            return;
        }
        if (obj instanceof Optional<?> opt) {
            opt.ifPresent(v -> collectIngredientsFromObject(v, result, visited, depth + 1));
            return;
        }
        if (obj instanceof Supplier<?> sup) {
            collectIngredientsFromObject(sup.get(), result, visited, depth + 1);
            return;
        }
        if (obj instanceof Collection<?> col) {
            for (Object item : col) {
                collectIngredientsFromObject(item, result, visited, depth + 1);
            }
            return;
        }

        for (java.lang.reflect.Field field : getAllFields(obj.getClass())) {
            field.setAccessible(true);
            try {
                Object val = field.get(obj);
                if (val == null || val instanceof Number || val instanceof String ||
                        val instanceof Boolean || val instanceof ResourceLocation) continue;
                String name = field.getName().toLowerCase();
                if (name.contains("result") || name.contains("output") || name.contains("out")) continue;
                collectIngredientsFromObject(val, result, visited, depth + 1);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static List<java.lang.reflect.Field> getAllFields(Class<?> clazz) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    /**
     * 从配方材料中剔除"输出物本身"（自身），保留其余材料。
     * <p>
     * 用于处理"自身 + 其他 → 输出"的特殊配方（部分整合包的升级/精炼配方）：
     * 匹配时不会把成品本身当材料，也不会误删同一材料槽中的其他物品
     * （原实现按整个 Ingredient 剔除，混合标签会把其他物品一起删掉）。
     * </p>
     *
     * @param recipe 配方
     * @param ings   配方材料（已过滤空槽）
     * @param access 注册表访问
     * @return 剔除输出物后的材料列表
     */
    private static List<Ingredient> removeOutputItemFromIngredients(Object recipe, List<Ingredient> ings, RegistryAccess access) {
        ItemStack out = getRecipeOutput(recipe, access);
        if (out.isEmpty()) return ings;
        List<Ingredient> result = new ArrayList<>();
        for (Ingredient ing : ings) {
            ItemStack[] stacks = ing.getItems();
            // 仅当材料变体与输出"完全相同（同物品且组件相同）"时视为自身剔除，
            // 避免拔刀剑升级（拔刀剑+材料→另一把拔刀剑）被误剔：材料刀与输出刀组件不同
            boolean containsOutput = Arrays.stream(stacks).anyMatch(s -> sameItemWithNbt(s, out));
            if (!containsOutput) {
                result.add(ing);
                continue;
            }
            // 含自身：只剔除自身物品，同一槽的其他物品保留
            ItemStack[] rest = Arrays.stream(stacks)
                    .filter(s -> !sameItemWithNbt(s, out))
                    .toArray(ItemStack[]::new);
            if (rest.length > 0) result.add(Ingredient.of(rest));
        }
        return result;
    }

    /** 同物品且组件完全相同（数量无关） */
    private static boolean sameItemWithNbt(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        return a.getComponents().equals(b.getComponents());
    }

    /** 从物品提取 tacz 枪械 GunId（组件版，无则返回空串） */
    private static String extractGunId(ItemStack stack) {
        try {
            var cd = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (cd != null) {
                net.minecraft.nbt.CompoundTag tag = cd.copyTag();
                if (tag.contains("GunId")) return tag.getString("GunId");
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * 按变体索引从材料槽中选一个物品（不剔除任何变体，材料如实来自配方）。
     * 拔刀剑"刀→刀"升级类配方输入/输出为同一物品，若按输出物剔除会误删合成用的刀刃。
     */
    private ItemStack pickVariant(ItemStack[] stacks, int variantIdx) {
        if (stacks.length == 0) return null;
        return stacks[variantIdx % stacks.length].copy();
    }

    // ---- 事件处理 ----

    private void onLeftSlotChanged() {
        if (player.level().isClientSide || isUpdating) return;
        isUpdating = true;

        ItemStack input = container.getItem(0);
        // 九宫格里可能还留着上一轮拆出来的产物（leftConsumed：输入物品已为这些产物消耗过）
        // 或玩家自己放进去的材料（middleModified）。换输入物品前先还给玩家，不能直接清空——
        // 原先这里只判断 middleModified，而它全类从未被置为 true，等于永远走"直接清空"，
        // 于是"拿走一部分产物后换掉输入物品"时，九宫格里剩下的产物会凭空消失。
        if (leftConsumed || middleModified) returnOrDropMiddleItems();
        middleModified = leftConsumed = false;
        disassembleRecipeIdx = materialVariantIdx = 0;
        disassembleRecipes.clear();
        variantPairs.clear();
        currentPairIndex = 0;
        currentPotionSteps.clear();
        currentPotionStepIndex = 0;
        currentProductList.clear();
        setButtonMode(false);

        disassembleIndexSlot.set(0);
        disassembleTotalSlot.set(0);
        craftIndexSlot.set(0);
        craftTotalSlot.set(0);

        isFillingMaterials = true;
        for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
        container.setItem(10, ItemStack.EMPTY);
        clearMiddleCache();

        if (!input.isEmpty()) fillMaterialsFromLeft();
        else updateCraftingResultByItems();

        isFillingMaterials = false;
        broadcastChanges();
        isUpdating = false;
    }

    private void onMiddleSlotsChanged() {
        if (player.level().isClientSide || isUpdating || isTakingResult) return;
        if (isButtonMode()) return;

        if (container.getItem(0).isEmpty()) {
            isUpdating = true;
            // 无输入物品 = 合成模式：九宫格里是玩家自己放的材料，标记一下，
            // 之后往输入槽放东西时会把这些材料还给玩家（见 onLeftSlotChanged）
            middleModified = true;
            // 记住当前这一页是哪条配方：材料改动后重算，只要它还能合成就不跳页
            Object previous = currentCraftRecipe();
            craftRecipes.clear();
            updateCraftingResultByItems();
            restoreCraftPage(previous);
            isUpdating = false;
            broadcastChanges();
            return;
        }

        isUpdating = true;
        boolean changed = false;
        if (!isFillingMaterials) {
            for (int i = 0; i < 9; i++) {
                if (!ItemStack.matches(container.getItem(i + 1), previousMiddleStacks[i])) {
                    changed = true;
                    break;
                }
            }
        }
        if (changed && !leftConsumed && !container.getItem(0).isEmpty()) {
            container.getItem(0).shrink(1);
            if (container.getItem(0).getCount() <= 0) container.setItem(0, ItemStack.EMPTY);
            leftConsumed = true;
            updateMiddleCache();

            boolean empty = true;
            for (int i = 1; i <= 9; i++) {
                if (!container.getItem(i).isEmpty()) {
                    empty = false;
                    break;
                }
            }
            if (empty) tryFillNext();
        }
        updateDisassemblePreview();
        isUpdating = false;
        broadcastChanges();
    }

    // ---- 拆解填充 ----

    private void fillMaterialsFromLeft() {
        ItemStack input = container.getItem(0);
        if (input.isEmpty()) return;

        Level level = player.level();
        ensureRecipeIndex(level);

        if (input.getItem() == Items.TIPPED_ARROW) {
            Potion potion = getPotion(input);
            if (potion != null) {
                currentProductList.clear();
                currentProductList.add(new ItemStack(Items.ARROW));
                currentProductList.add(setPotion(new ItemStack(Items.LINGERING_POTION), potion, player.level().registryAccess()));
                setButtonMode(false);
                updateMiddleGridFromProductList();
                updateDisassembleCount(1, 1);
                broadcastChanges();
                return;
            }
        }

        if (isPotion(input)) {
            Potion potion = getPotion(input);
            if (potion != null) {
                List<PotionSingleStep> steps = getAllBrewingSteps().stream()
                        .filter(s -> s.to.equals(potion))
                        .collect(Collectors.toList());
                if (!steps.isEmpty()) {
                    currentPotionSteps = steps;
                    currentPotionStepIndex = 0;
                    showCurrentPotionStep();
                    updateDisassembleCount(1, steps.size());
                    broadcastChanges();
                    return;
                }
                if (isSplash(input)) {
                    currentProductList.clear();
                    currentProductList.add(new ItemStack(Items.GUNPOWDER));
                    currentProductList.add(createBasePotion(input, potion));
                    setButtonMode(false);
                    updateMiddleGridFromProductList();
                    updateDisassembleCount(1, 1);
                    broadcastChanges();
                    return;
                } else if (isLingering(input)) {
                    currentProductList.clear();
                    currentProductList.add(new ItemStack(Items.DRAGON_BREATH));
                    currentProductList.add(createSplashVariant(input, potion));
                    setButtonMode(false);
                    updateMiddleGridFromProductList();
                    updateDisassembleCount(1, 1);
                    broadcastChanges();
                    return;
                }
            }
            for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
            container.setItem(10, input.copy());
            updateDisassembleCount(0, 0);
            clearMiddleCache();
            return;
        }

        net.minecraft.world.item.enchantment.ItemEnchantments ench = input.getEnchantments();
        if (!ench.isEmpty()) {
            ItemStack plain = input.copy();
            // 1.21.1 附魔存在 DataComponents.ENCHANTMENTS / STORED_ENCHANTMENTS 组件里，
            // 不再像 1.20.1 那样写在 NBT 的 Enchantments 标签里，所以要从组件里移除。
            plain.remove(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            plain.remove(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
            currentProductList.clear();
            currentProductList.add(plain);
            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) {
                currentProductList.add(EnchantedBookItem.createForEnchantment(
                        new EnchantmentInstance(e.getKey(), e.getIntValue())));
            }
            setButtonMode(currentProductList.size() > 9);
            updateMiddleGridFromProductList();
            updateDisassembleCount(1, 1);
            return;
        }

        // 直接从后台构建的输出索引取候选，索引未就绪时先返回空（构建是异步的，不会卡服务器）。
        // tacz 枪械按 GunId 精确匹配（同物品不同 NBT），匹配不到时回退全量（按物品）
        List<Recipe<?>> candidates;
        if (recipeIndexOutputReady) {
            String gunId = extractGunId(input);
            candidates = RECIPES_BY_OUTPUT_DETAIL.get(input.getItem().toString() + "|" + gunId);
            if (candidates == null || candidates.isEmpty()) {
                candidates = RECIPES_BY_OUTPUT.getOrDefault(input.getItem(), Collections.emptyList());
            }
        } else {
            candidates = Collections.emptyList();
        }

        disassembleRecipes = new ArrayList<>(candidates);
        disassembleRecipes = disassembleRecipes.stream()
                .filter(r -> {
                    try {
                        List<Ingredient> ings = getIngredientsFromRecipe(r);
                        if (ings.isEmpty()) return false;
                        // 配方材料中含与输入同物品（"自身升级/修复"类）→ 可拆
                        for (Ingredient ing : ings) {
                            for (ItemStack s : ing.getItems()) {
                                if (s.getItem() == input.getItem()) return true;
                            }
                        }
                        // 合成配方（非熔炉/切石等加工配方）→ 允许拆解。
                        // 拔刀剑等特殊材料配方（如：白鞘刀+煤块+金锭 → 基础刀）材料与成品
                        // 物品不同，若不放行将永远识别不到可拆配方。
                        if (r instanceof net.minecraft.world.item.crafting.CraftingRecipe) return true;
                        // tacz 枪匠台配方（自定义 Recipe<Inventory>，非 CraftingRecipe）同样放行
                        if (r.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe")) return true;
                        // ⚠️ 自定义配方类型放行：无尽贪婪 avaritia:extreme_shaped/shapeless、诡厄巫法 goety:ritual/brazier
                        //   这类配方不是 CraftingRecipe，但能枚举出材料（需求："祭坛/仪式一起放行"）。
                        //   只排除熔炉/切石这类"纯加工"配方 —— 那类拆了没意义，原本也刻意不放行。
                        // 纯加工配方（熔炉/烟熏/切石等）也一并放行 —— 需求："这些纯加工配方也要放行"。
                        // 只有锻造保留它自己的专门规则（下面那段），避免绕过产物判定。
                        if (!(r instanceof net.minecraft.world.item.crafting.SmithingRecipe)) {
                            return true;
                        }
                        // 锻造配方：产物与输入同物品 → 放行，允许拆回材料（下界合金胸甲→钻石胸甲+材料等）。
                        // 材料列表已不会包含产物（见 getIngredientsFromRecipe 的 Ingredient 类型过滤），
                        // 此处放行是为了保证"输入=最终产物"的锻造配方仍能出现在拆解列表中。
                        if (r instanceof net.minecraft.world.item.crafting.SmithingRecipe) {
                            ItemStack out = getRecipeOutput(r, level.registryAccess());
                            if (!out.isEmpty() && out.getItem() == input.getItem()) return true;
                        }
                    } catch (Throwable e) {
                        return false;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        if (disassembleRecipes.isEmpty()) {
            for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
            container.setItem(10, input.copy());
            updateDisassembleCount(0, 0);
            clearMiddleCache();
            return;
        }

        variantPairs.clear();
        for (int i = 0; i < disassembleRecipes.size(); i++) {
            int max = getMaxVariantCount(disassembleRecipes.get(i));
            for (int v = 0; v < max; v++) {
                variantPairs.add(new RecipeVariantPair(i, v));
            }
        }
        currentPairIndex = 0;
        applyPair(currentPairIndex);
        updateDisassembleCount(1, variantPairs.size());
    }

    /**
     * 判断物品是否属于“药水”。不硬编码原版物品类，而是优先看有没有标准的
     * {@code POTION_CONTENTS} 数据组件（1.20.5+ 所有药水的统一数据载体），
     * 再回退检查是否继承 {@link PotionItem}，从而兼容其他 mod 的自定义药水。
     */
    private boolean isPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof PotionItem
                || stack.has(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
    }

    private boolean isSplash(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SplashPotionItem || item == Items.SPLASH_POTION ||
                item.getClass().getName().toLowerCase().contains("splash") ||
                BuiltInRegistries.ITEM.getKey(item).getPath().contains("splash");
    }

    private boolean isLingering(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof LingeringPotionItem || item == Items.LINGERING_POTION ||
                item.getClass().getName().toLowerCase().contains("lingering") ||
                BuiltInRegistries.ITEM.getKey(item).getPath().contains("lingering");
    }

    private ItemStack createBasePotion(ItemStack splash, Potion potion) {
        Item base = Items.POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(splash.getItem());
        String path = name.getPath().replace("splash_", "").replace("_splash", "");
        base = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), path));
        if (base == Items.AIR) base = Items.POTION;
        return setPotion(new ItemStack(base), potion, player.level().registryAccess());
    }

    private ItemStack createSplashVariant(ItemStack lingering, Potion potion) {
        Item splash = Items.SPLASH_POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(lingering.getItem());
        String path = name.getPath().replace("lingering_", "").replace("_lingering", "");
        splash = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), path));
        if (splash == Items.AIR) splash = Items.SPLASH_POTION;
        return setPotion(new ItemStack(splash), potion, player.level().registryAccess());
    }

    private ItemStack createSplashPotion(ItemStack base, Potion potion) {
        Item splash = Items.SPLASH_POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(base.getItem());
        String path = name.getPath().replace("splash_", "").replace("_splash", "");
        splash = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "splash_" + path));
        if (splash == Items.AIR) splash = Items.SPLASH_POTION;
        return setPotion(new ItemStack(splash), potion, player.level().registryAccess());
    }

    private ItemStack createLingeringPotion(ItemStack base, Potion potion) {
        Item lingering = Items.LINGERING_POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(base.getItem());
        String path = name.getPath().replace("lingering_", "").replace("_lingering", "");
        lingering = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "lingering_" + path));
        if (lingering == Items.AIR) lingering = Items.LINGERING_POTION;
        return setPotion(new ItemStack(lingering), potion, player.level().registryAccess());
    }

    private int getMaxVariantCount(Recipe<?> r) {
        return getIngredientsFromRecipe(r).stream()
                .mapToInt(ing -> ing.getItems().length)
                .max().orElse(1);
    }

    private void applyPair(int idx) {
        if (variantPairs.isEmpty()) return;
        RecipeVariantPair p = variantPairs.get(idx);
        disassembleRecipeIdx = p.recipeIdx;
        materialVariantIdx = p.variantIdx;

        isFillingMaterials = true;
        for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
        computeProductListForCurrentPair();
        isFillingMaterials = false;
        broadcastChanges();
    }

    private void computeProductListForCurrentPair() {
        currentProductList.clear();
        ItemStack input = container.getItem(0);
        if (input.isEmpty() || disassembleRecipes.isEmpty()) {
            setButtonMode(false);
            return;
        }

        Recipe<?> recipe = disassembleRecipes.get(disassembleRecipeIdx);
        // 合并同一材料（同 Ingredient 实例：tacz count 展开、普通配方同 symbol 多槽）：
        // 每个材料显示为一个槽位 × 配方数量（与 JEI 展示一致，避免拆出多组）
        Map<Ingredient, Integer> merged = new LinkedHashMap<>();
        for (Ingredient ing : getIngredientsFromRecipe(recipe)) {
            merged.merge(ing, 1, Integer::sum);
        }
        for (Map.Entry<Ingredient, Integer> e : merged.entrySet()) {
            ItemStack[] stacks = e.getKey().getItems();
            if (stacks.length == 0) continue;
            // 不再用"输出物本体"剔除材料变体：
            // 拔刀剑"刀→刀"升级类配方（输入/输出同物品，靠 NBT 区分）会误删合成用的刀刃，
            // 材料如实来自配方 ingredients（配方本身不含输出物，不会有"拆解出自己"）。
            ItemStack mat = pickVariant(stacks, materialVariantIdx);
            if (mat == null || mat.isEmpty()) continue;
            int n = e.getValue();
            mat.setCount(Math.max(1, Math.min(n, mat.getMaxStackSize())));
            currentProductList.add(mat);
        }
        setButtonMode(currentProductList.size() > 9);
        updateMiddleGridFromProductList();
    }

    private void showCurrentPotionStep() {
        if (currentPotionSteps.isEmpty()) return;
        PotionSingleStep step = currentPotionSteps.get(currentPotionStepIndex);
        currentProductList.clear();

        Item potionItem = container.getItem(0).getItem();
        currentProductList.add(setPotion(new ItemStack(potionItem), step.from, player.level().registryAccess()));
        ItemStack[] mats = step.ingredient.getItems();
        if (mats.length > 0) currentProductList.add(mats[0].copy());

        setButtonMode(false);
        updateMiddleGridFromProductList();
    }

    private void updateMiddleGridFromProductList() {
        // 显示 = 材料 + 额外物品（中心/激活/催化）。⚠️ 判定规则：这些额外物品
        // 在拆解方向上**也要返还**（拆一件成品应该连中心物品一起还回来），所以它们和材料一样能拿。
        java.util.List<ItemStack> showList = new ArrayList<>(currentProductList);
        List<ItemStack> extrasForThis = disassembleRecipes.isEmpty() ? null
                : EXTRA_DISPLAY.get(disassembleRecipes.get(Math.max(0, Math.min(disassembleRecipeIdx, disassembleRecipes.size() - 1))));
        for (ItemStack extraShow : (extrasForThis == null ? java.util.List.<ItemStack>of() : extrasForThis)) {
            if (showList.size() >= 9) break;
            showList.add(extraShow.copy());
        }
        for (int i = 1; i <= 9; i++) {
            container.setItem(i, i <= showList.size() ? showList.get(i - 1).copy() : ItemStack.EMPTY);
        }
        container.setItem(10, container.getItem(0).copy());
        updateMiddleCache();
    }

    /** 材料之后的那些格（中心/激活物品）从第几格开始（1 基）：全部拆解要照发一次 */
    private int extraMaterialStart() {
        return Math.min(9, currentProductList.size());
    }

    private void updateDisassemblePreview() {
        container.setItem(10, container.getItem(0).copy());
    }

    private void updateDisassembleCount(int index, int total) {
        disassembleIndexSlot.set(index);
        disassembleTotalSlot.set(total);
    }

    // ---- 合成匹配 ----

    private void updateCraftingResultByItems() {
        List<ItemStack> placed = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || stack.getCount() <= 0) {
                container.setItem(i, ItemStack.EMPTY);
            } else {
                placed.add(stack.copy());
            }
        }

        if (placed.isEmpty()) {
            craftRecipes.clear();
            craftRecipeIdx = 0;
            container.setItem(10, ItemStack.EMPTY);
            craftIndexSlot.set(0);
            craftTotalSlot.set(0);
            return;
        }

        Level level = player.level();
        craftRecipes.clear();
        ensureRecipeIndex(level);

        // 用后台构建的"材料→配方"索引收集候选，再精确匹配。
        // 绝不在此全量遍历所有配方，否则大型整合包会阻塞服务器线程。
        if (recipeIndexReady) {
            java.util.LinkedHashSet<Recipe<?>> craftCandidates = new java.util.LinkedHashSet<>();
            for (ItemStack p : placed) {
                List<Recipe<?>> byIng = RECIPES_BY_INGREDIENT.get(p.getItem());
                if (byIng != null) craftCandidates.addAll(byIng);
            }
            for (Recipe<?> r : craftCandidates) {
                if (r instanceof TippedArrowRecipe) continue;

                // ⚠️⚠️ 匹配（决定"产物格显不显示"）必须用**完整材料清单**：中心/激活物品也算。
                //    以前这里用 getIngredientsFromRecipe（不含中心物品），于是"缺中心物品也能出产物预览"，
                //    而消耗那步用的是完整清单 → 校验失败 → 玩家把预览拿走就未消耗即获得一个（实测"缺黑暗魔杖
                //    也能合成风之魔杖、还能无限拿"）。预览与消耗必须同一套判据，不然必出刷物品。
                List<Ingredient> ings = getFullIngredients(r).stream()
                        .filter(i -> !i.isEmpty())
                        .collect(Collectors.toList());
                if (ings.isEmpty()) continue;

                // 材料中含输出物本身（"自身 + 其他 → 输出"的特殊配方，如整合包升级配方）：
                // 只剔除自身物品，保留其他材料参与匹配；不再将整条配方跳过或误删同槽其他物品。
                ings = removeOutputItemFromIngredients(r, ings, level.registryAccess());
                if (ings.isEmpty()) continue;

                if (matchesIngredients(placed, ings)) craftRecipes.add(r);
            }
        }

        // 锻造配方兜底：材料枚举不出来的实现（1.21 不重写 getIngredients）进不了上面的索引，
        // 这里按 模板/基础/材料 角色直接判定九宫格，保证锻造台配方在合成页也能用。
        for (Recipe<?> r : SMITHING_RECIPES) {
            if (craftRecipes.contains(r)) continue;
            if (r instanceof SmithingRecipe smithing && pickSmithingRoleItems(smithing) != null) {
                craftRecipes.add(r);
            }
        }

        // 药水箭合成
        List<ItemStack> arrows = placed.stream()
                .filter(s -> s.getItem() == Items.ARROW)
                .collect(Collectors.toList());
        List<ItemStack> lingeringPotions = placed.stream()
                .filter(this::isLingering)
                .collect(Collectors.toList());

        if (!arrows.isEmpty() && !lingeringPotions.isEmpty()) {
            for (ItemStack potionStack : lingeringPotions) {
                Potion potion = getPotion(potionStack);
                if (potion != null) {
                    craftRecipes.add(setPotion(new ItemStack(Items.TIPPED_ARROW), potion, player.level().registryAccess()));
                }
            }
        }

        // 药水类型转换
        for (ItemStack s : placed) {
            if (isPotion(s)) {
                Potion potion = getPotion(s);
                if (potion == null) continue;

                boolean hasGunpowder = placed.stream().anyMatch(p -> p.getItem() == Items.GUNPOWDER);
                boolean hasDragonBreath = placed.stream().anyMatch(p -> p.getItem() == Items.DRAGON_BREATH);

                if (!isSplash(s) && hasGunpowder) {
                    craftRecipes.add(createSplashPotion(s, potion));
                }
                if (!isLingering(s) && hasDragonBreath) {
                    craftRecipes.add(createLingeringPotion(s, potion));
                }
            }
        }

        // 药水效果合成
        for (PotionSingleStep step : getAllBrewingSteps()) {
            for (ItemStack s : placed) {
                if (isPotion(s)) {
                    Potion current = getPotion(s);
                    if (current == step.from) {
                        boolean hasIngredient = placed.stream()
                                .anyMatch(ps -> !ps.equals(s) && step.ingredient.test(ps));
                        if (hasIngredient) {
                            craftRecipes.add(setPotion(new ItemStack(s.getItem()), step.to, player.level().registryAccess()));
                            break;
                        }
                    }
                }
            }
        }

        if (!craftRecipes.isEmpty()) {
            craftRecipeIdx = Math.min(craftRecipeIdx, craftRecipes.size() - 1);
            displayCurrentCraftResult();
            craftIndexSlot.set(craftRecipeIdx + 1);
            craftTotalSlot.set(craftRecipes.size());
        } else {
            container.setItem(10, ItemStack.EMPTY);
            craftRecipeIdx = 0;
            craftIndexSlot.set(0);
            craftTotalSlot.set(0);
        }
    }

    /**
     * 当前这一页对应的配方（越界时取最后一页），用于重算前记下"玩家正停在哪条配方上"。
     *
     * @return 当前配方；列表为空时返回 {@code null}
     */
    private Object currentCraftRecipe() {
        if (craftRecipes.isEmpty()) return null;
        return craftRecipes.get(Math.min(craftRecipeIdx, craftRecipes.size() - 1));
    }

    /**
     * 重算配方列表后恢复页码。
     * <p>
     * 取走产物后原先直接写 {@code craftRecipeIdx = 0}，于是"第三页取走产物、材料还有剩余"
     * 会跳回第一页。现在改为：原配方只要仍在候选列表里就定位回它；确实不在时保留
     * {@link #updateCraftingResultByItems()} 夹取后的页码（越界夹到最后一页），不再跳回第一页。
     * </p>
     *
     * @param previous 重算前那一页的配方，可为 {@code null}
     */
    private void restoreCraftPage(Object previous) {
        if (previous == null || craftRecipes.isEmpty()) return;
        int idx = indexOfCraftRecipe(previous);
        if (idx < 0 || idx == craftRecipeIdx) return;
        craftRecipeIdx = idx;
        displayCurrentCraftResult();
        craftIndexSlot.set(craftRecipeIdx + 1);
        craftTotalSlot.set(craftRecipes.size());
    }

    /**
     * 在候选列表里找同一条配方：配方对象来自注册表，可直接按引用比较；
     * 药水类产物是每次重算新建的 {@link ItemStack}，按物品与组件比较。
     *
     * @param target 目标配方或产物
     * @return 下标，找不到返回 -1
     */
    private int indexOfCraftRecipe(Object target) {
        for (int i = 0; i < craftRecipes.size(); i++) {
            Object o = craftRecipes.get(i);
            if (o == target) return i;
            if (o instanceof ItemStack a && target instanceof ItemStack b && ItemStack.matches(a, b)) return i;
        }
        return -1;
    }

    private boolean matchesIngredients(List<ItemStack> placed, List<Ingredient> ings) {
        List<ItemStack> temp = placed.stream().map(ItemStack::copy).collect(Collectors.toCollection(ArrayList::new));
        for (Ingredient ing : ings) {
            boolean found = false;
            for (ItemStack have : temp) {
                if (!have.isEmpty() && ing.test(have)) {
                    have.shrink(1);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void displayCurrentCraftResult() {
        if (craftRecipes.isEmpty() || craftRecipeIdx >= craftRecipes.size()) {
            container.setItem(10, ItemStack.EMPTY);
            return;
        }

        Object obj = craftRecipes.get(craftRecipeIdx);
        if (obj instanceof ItemStack stack) {
            container.setItem(10, stack.copy());
        } else if (obj instanceof Recipe<?> r) {
            // 用 assemble 生成预览（与最终取走时一致的产物）：
            // 精妙背包升级等配方需要从输入复制 NBT/组件，静态 getResultItem 是空物品。
            ItemStack preview = assembleRecipeOutput(r, player.level().registryAccess());
            if (preview.isEmpty()) preview = getRecipeOutput(r, player.level().registryAccess());
            if (preview.isEmpty()) {
                container.setItem(10, ItemStack.EMPTY);
                return;
            }
            // 预览与取走产物约定一致（assemble 幂等）：直接用于窗口显示
            container.setItem(10, preview);
        }
    }

    // ---- 取走产物 ----

    private void onCraftResultTaken() {
        isTakingResult = true;
        if (!container.getItem(0).isEmpty()) {
            performDisassemble();
        } else {
            performCraft();
        }
        isTakingResult = false;
    }

    private void performDisassemble() {
        isUpdating = true;
        lastSettleOk = false;
        try {
            ItemStack left = container.getItem(0);
            if (left.isEmpty() || currentProductList.isEmpty()) return;

            left.shrink(1);
            if (left.isEmpty()) container.setItem(0, ItemStack.EMPTY);

            for (int i = 1; i <= 9; i++) {
                // 中心/激活物品（配方末尾那几格）也一起返还：判定规则见 EXTRA_DISPLAY 的注释
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (!player.getInventory().add(s.copy())) {
                        player.drop(s.copy(), false);
                    }
                    container.setItem(i, ItemStack.EMPTY);
                }
            }

            // 到这里就是真的拆掉了一个：输入扣了、材料给了
            lastSettleOk = true;

            container.setItem(10, ItemStack.EMPTY);
            currentProductList.clear();
            leftConsumed = true;
            middleModified = false;
            setButtonMode(false);
            clearMiddleCache();

            if (left.isEmpty()) {
                for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                container.setItem(10, ItemStack.EMPTY);
                clearMiddleCache();
                currentProductList.clear();
                updateDisassembleCount(0, 0);
                leftConsumed = false;
            } else {
                refreshCurrentDisassemble();
            }
        } finally {
            isUpdating = false;
        }
        broadcastChanges();
    }

    private void performCraft() {
        lastSettleOk = false;
        if (craftRecipes.isEmpty() || craftRecipeIdx >= craftRecipes.size()) return;

        Object obj = craftRecipes.get(craftRecipeIdx);
        // 这一页可能还有剩余材料可继续合成：重算后按 obj 找回同一页（见 restoreCraftPage）

        if (obj instanceof ItemStack resultItem) {
            if (resultItem.isEmpty()) return;
            Item resultType = resultItem.getItem();

            // 药水箭
            if (resultType == Items.TIPPED_ARROW) {
                Potion potion = getPotion(resultItem);
                if (potion == null) return;
                if (!tryConsumeItem(Items.ARROW)) return;
                if (!tryConsumeAnyLingering(potion)) return;
                container.setItem(10, resultItem.copy());
                container.setChanged();
            }
            // 喷溅药水
            else if (isSplash(resultItem)) {
                Potion rPotion = getPotion(resultItem);
                PotionSingleStep step = getAllBrewingSteps().stream()
                        .filter(s -> s.to.equals(rPotion))
                        .findFirst().orElse(null);
                if (step != null) {
                    Item fromItem = findPotionByEffect(step.from, true);
                    if (fromItem != null && tryConsumePotion(fromItem, step.from) &&
                            tryConsumeIngredient(step.ingredient)) {
                        container.setItem(10, resultItem.copy());
                        container.setChanged();
                    }
                } else {
                    if (tryConsumeItem(Items.GUNPOWDER) && tryConsumeAnyBasePotion(rPotion)) {
                        container.setItem(10, resultItem.copy());
                        container.setChanged();
                    }
                }
            }
            // 滞留药水
            else if (isLingering(resultItem)) {
                Potion rPotion = getPotion(resultItem);
                PotionSingleStep step = getAllBrewingSteps().stream()
                        .filter(s -> s.to.equals(rPotion))
                        .findFirst().orElse(null);
                if (step != null) {
                    Item fromItem = findPotionByEffect(step.from, true);
                    if (fromItem != null && tryConsumePotion(fromItem, step.from) &&
                            tryConsumeIngredient(step.ingredient)) {
                        container.setItem(10, resultItem.copy());
                        container.setChanged();
                    }
                } else {
                    if (tryConsumeItem(Items.DRAGON_BREATH) && tryConsumeAnySplashPotion(rPotion)) {
                        container.setItem(10, resultItem.copy());
                        container.setChanged();
                    }
                }
            }
            // 普通药水
            else if (isPotion(resultItem)) {
                Potion rPotion = getPotion(resultItem);
                PotionSingleStep step = getAllBrewingSteps().stream()
                        .filter(s -> s.to.equals(rPotion))
                        .findFirst().orElse(null);
                if (step != null) {
                    Item fromItem = findPotionByEffect(step.from, false);
                    if (fromItem != null && tryConsumePotion(fromItem, step.from) &&
                            tryConsumeIngredient(step.ingredient)) {
                        container.setItem(10, resultItem.copy());
                        container.setChanged();
                    }
                }
            }

            updateCraftingResultByItems();
            restoreCraftPage(obj);
            lastSettleOk = true;
            broadcastChanges();
            return;
        }

        // 标准配方（含锻造）
        if (!(obj instanceof Recipe<?> recipe)) return;

        // 玩家点击时已经拿走了槽 10 的预览物（displayCurrentCraftResult 用 assemble 生成，
        // 与这里结果一致）。这里只需校验并消耗材料；消耗失败则把产物放回槽 10。
        ItemStack result = assembleRecipeOutput(recipe, player.level().registryAccess());
        if (result.isEmpty()) return;

        List<Ingredient> ings = getFullIngredients(recipe).stream()
                .filter(i -> !i.isEmpty())
                .collect(Collectors.toList());

        // 注意：合成确认时不做"剔除输出物"处理——若剔除自身，玩家可无本钱合成，
        // 造成刷物品。"自身 + 其他 → 输出"的配方应消耗全部材料（含自身）。

        // 锻造配方：材料枚举不出来时按角色消耗，避免"不消耗材料即获得产物"
        if (ings.isEmpty() && recipe instanceof SmithingRecipe smithing) {
            if (!consumeSmithingRoleItems(smithing)) {
                failCraft();
                return;
            }
        } else if (!hasAllIngredients(ings) || !tryConsumeIngredients(ings)) {
            failCraft();
            return;
        }

        updateCraftingResultByItems();
        restoreCraftPage(obj);
        lastSettleOk = true;
        broadcastChanges();
    }

    /**
     * 合成结算失败时的收尾。
     * <p>
     * ⚠️⚠️ <b>绝不能把产物放回槽 10</b>：原版点击路径是"<b>先</b>把产物从产物格取走、<b>再</b>调 {@code onTake}"，
     * 所以只要结算失败时产物还在格子里，下一次点击就又能把它拿走 —— 玩家实测的"缺中心物品也能合成、
     * 而且无限拿不消耗"就是这么来的。正确做法是：<b>清空产物格 + 重算</b>；
     * 重算时若材料其实够（含中心物品），预览会自己回来，不够就一直空着。
     * </p>
     */
    private void failCraft() {
        container.setItem(10, ItemStack.EMPTY);
        container.setChanged();
        updateCraftingResultByItems();
    }

    // ---- 物品消耗辅助 ----

    private boolean tryConsumeItem(Item item) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() == item) {
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setItem(i, s);
                return true;
            }
        }
        return false;
    }

    private boolean tryConsumePotion(Item potionItem, Potion potion) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() == potionItem && getPotion(s) == potion) {
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setItem(i, s);
                return true;
            }
        }
        return false;
    }

    private boolean tryConsumeAnyBasePotion(Potion potion) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (isPotion(s) && !isSplash(s) && !isLingering(s) &&
                    getPotion(s) == potion) {
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setItem(i, s);
                return true;
            }
        }
        return false;
    }

    private boolean tryConsumeAnySplashPotion(Potion potion) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (isSplash(s) && getPotion(s) == potion) {
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setItem(i, s);
                return true;
            }
        }
        return false;
    }

    private boolean tryConsumeAnyLingering(Potion potion) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (isLingering(s) && getPotion(s) == potion) {
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setItem(i, s);
                return true;
            }
        }
        return false;
    }

    private boolean tryConsumeIngredient(Ingredient ing) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (ing.test(s)) {
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setItem(i, s);
                return true;
            }
        }
        return false;
    }

    /**
     * 按 模板/基础/材料 角色各消耗 1 个（用于材料枚举不出来的锻造配方）。
     *
     * @return 三件都消耗成功返回 {@code true}；凑不齐或某件已不在九宫格里返回 {@code false}
     */
    private boolean consumeSmithingRoleItems(SmithingRecipe smithing) {
        ItemStack[] picked = pickSmithingRoleItems(smithing);
        if (picked == null) return false;
        for (ItemStack need : picked) {
            boolean consumed = false;
            for (int i = 1; i <= 9; i++) {
                ItemStack s = container.getItem(i);
                if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, need)) continue;
                s.shrink(1);
                if (s.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                else container.setChanged();
                consumed = true;
                break;
            }
            if (!consumed) return false;
        }
        return true;
    }

    /**
     * 先验证「九宫格里每种材料都够」，够了才允许真扣。
     * <p>
     * ⚠️ 这是为了解决实测中出现的两个严重问题：扣材料是"边找边扣"，扣到一半失败就出现
     * 「材料没了、产物也拿不到」（干消耗）或「没扣成本却拿到产物」（无限刷）——
     * 合成必须是**全有或全无**。这里只做校验、不改动九宫格。
     * </p>
     */
    private boolean hasAllIngredients(List<Ingredient> ings) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            grid.add(container.getItem(i).copy());
        }
        for (Ingredient ing : ings) {
            if (ing == null || ing.isEmpty()) continue;
            boolean found = false;
            for (int i = 0; i < grid.size(); i++) {
                ItemStack s = grid.get(i);
                if (s.isEmpty() || !ing.test(s)) continue;
                s.shrink(1);
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }
    private boolean tryConsumeIngredients(List<Ingredient> ings) {
        List<ItemStack> temp = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            temp.add(container.getItem(i).copy());
        }

        for (Ingredient ing : ings) {
            boolean found = false;
            for (int j = 0; j < temp.size(); j++) {
                ItemStack t = temp.get(j);
                if (!t.isEmpty() && ing.test(t)) {
                    t.shrink(1);
                    if (t.isEmpty()) temp.set(j, ItemStack.EMPTY);
                    else temp.set(j, t);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        for (int i = 1; i <= 9; i++) {
            container.setItem(i, temp.get(i - 1).isEmpty() ? ItemStack.EMPTY : temp.get(i - 1));
        }
        return true;
    }

    private boolean checkIngredientsAvailable(List<Ingredient> ings) {
        for (Ingredient ing : ings) {
            boolean found = false;
            for (int i = 1; i <= 9; i++) {
                if (!container.getItem(i).isEmpty() && ing.test(container.getItem(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private Item findPotionByEffect(Potion effect, boolean preferAdvancedType) {
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (isPotion(s) && getPotion(s) == effect) {
                if (preferAdvancedType && (isSplash(s) || isLingering(s))) return s.getItem();
                if (!preferAdvancedType && !isSplash(s) && !isLingering(s)) return s.getItem();
            }
        }
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (isPotion(s) && getPotion(s) == effect) return s.getItem();
        }
        return null;
    }

    // ---- 辅助 ----

    private void updateMiddleCache() {
        for (int i = 0; i < 9; i++) {
            previousMiddleStacks[i] = container.getItem(i + 1).copy();
        }
    }

    private void clearMiddleCache() {
        Arrays.fill(previousMiddleStacks, ItemStack.EMPTY);
    }

    /** 这一轮取产物到底结算成功没有（见 {@link #lastSettleOk} 的说明） */
    public boolean lastSettleSucceeded() {
        return lastSettleOk;
    }

    /**
     * 供"次元袋里的拆解页"调用：把产物从槽 10 拿出来并让原版结算（照原版
     * {@code AbstractContainerMenu#doClick} 的点击语义——先取出产物、再调 {@code onTake}）。
     * <p>
     * 为什么必须"先拿再结算"而不是"先结算、再看槽 10 空不空"：合成成功但九宫格还有剩余材料时，
     * 原版 {@code updateCraftingResultByItems()} 会把产物<b>重新画回槽 10</b> 当下一条的预览，
     * 于是"槽 10 空了"这个判据永远不成立，产物就拿不到了（实测）。
     * 结算成没成看 {@link #lastSettleSucceeded()}；没成的话调用方要把产物放回（{@link #setOutputSlot}）。
     * </p>
     */
    public ItemStack extractOutputSlot(Player p) {
        Slot slot = this.getSlot(10);
        ItemStack before = slot.getItem();
        if (before.isEmpty() || !slot.mayPickup(p)) return ItemStack.EMPTY;
        ItemStack taken = slot.remove(before.getCount());
        slot.setChanged();
        if (taken.isEmpty()) return ItemStack.EMPTY;
        slot.onTake(p, taken);
        return taken;
    }

    /** 把产物放回槽 10（结算失败时用；只是预览/展示，不代表玩家已付过钱） */
    public void setOutputSlot(ItemStack stack) {
        container.setItem(10, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    private void returnOrDropMiddleItems() {
        for (int i = 1; i <= 9; i++) {
            // 中心/激活物品也一起返还（判定规则见 EXTRA_DISPLAY 的注释）
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) {
                if (!player.getInventory().add(s.copy())) {
                    dropItemAtAccess(s.copy());
                }
                container.setItem(i, ItemStack.EMPTY);
            }
        }
        clearMiddleCache();
    }

    private void dropItemAtAccess(ItemStack stack) {
        access.execute((level, pos) -> {
            ItemEntity e = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        });
    }

    private void tryFillNext() {
        boolean empty = true;
        for (int i = 1; i <= 9; i++) {
            if (!container.getItem(i).isEmpty()) {
                empty = false;
                break;
            }
        }
        if (!empty) return;

        if (container.getItem(0).isEmpty()) {
            clearMiddleCache();
            container.setItem(10, ItemStack.EMPTY);
            updateDisassembleCount(0, 0);
            return;
        }

        int savedPairIndex = currentPairIndex;
        int savedPotionStepIndex = currentPotionStepIndex;
        boolean hadPair = !variantPairs.isEmpty();
        boolean hadPotion = !currentPotionSteps.isEmpty();

        fillMaterialsFromLeft();

        if (hadPair && savedPairIndex < variantPairs.size()) {
            currentPairIndex = savedPairIndex;
            if (currentPairIndex > 0) {
                applyPair(currentPairIndex);
                updateDisassembleCount(currentPairIndex + 1, variantPairs.size());
            }
        } else if (hadPotion && savedPotionStepIndex < currentPotionSteps.size()) {
            currentPotionStepIndex = savedPotionStepIndex;
            if (currentPotionStepIndex > 0) {
                showCurrentPotionStep();
                updateDisassembleCount(currentPotionStepIndex + 1, currentPotionSteps.size());
            }
        }

        leftConsumed = false;
        updateMiddleCache();
    }

    private void refreshCurrentDisassemble() {
        if (variantPairs.isEmpty() || currentPairIndex >= variantPairs.size()) {
            currentPairIndex = 0;
            if (variantPairs.isEmpty()) {
                for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                container.setItem(10, ItemStack.EMPTY);
                clearMiddleCache();
                currentProductList.clear();
                updateDisassembleCount(0, 0);
                leftConsumed = false;
                return;
            }
        }
        applyPair(currentPairIndex);
        updateDisassembleCount(currentPairIndex + 1, variantPairs.size());
        leftConsumed = false;
        middleModified = false;
        broadcastChanges();
    }

    /**
     * 确保配方索引已开始构建（异步，不阻塞服务器线程）。
     * 索引未就绪时交互返回空结果，构建完成后自动可用。
     */
    /** 服务器启动时预构建配方索引，确保玩家打开拆解台时已就绪。 */
    public static void ensureRecipeIndex(Level level) {
        // 只在服务端构建；客户端不需要配方索引，避免浪费。
        // 只做后台构建，绝不在此同步全量扫描（否则大型整合包会阻塞服务器线程）。
        if (recipeIndexReady || recipeIndexBuilding || level == null || level.isClientSide) return;
        recipeIndexBuilding = true;
        java.util.concurrent.CompletableFuture.runAsync(() -> buildRecipeIndexAsync(level));
    }

    /**
     * 后台线程分两阶段构建配方索引，带 CPU 节流（低占空比，避免在 4 核旧 CPU + 大型整合包上阻塞服务器）。
     * <p>阶段一：产出→配方（拆解）——只用 getResultItem，快且不碰 AllTheLeaks 锁，拆解先可用。
     * 阶段二：材料→配方（合成）——用 getIngredients，最耗 CPU，用更低的占空比以免持续阻塞服务器。</p>
     */
    private static final long INDEX_WORK_NS = 5_000_000L;     // 阶段一：每次连续处理 ~5ms
    private static final long INDEX_SLEEP_MS = 15L;            // 休眠 15ms（约 25% 占空比）
    private static final long INGREDIENT_WORK_NS = 2_000_000L; // 阶段二：每次仅 ~2ms
    private static final long INGREDIENT_SLEEP_MS = 25L;       // 休眠 25ms（约 7% 占空比，整机约 2%，几乎无感知）

    private static void buildRecipeIndexAsync(Level level) {
        final int gen = indexGeneration;
        try {
            // 先快照配方列表，避免后台迭代时配方管理器变更导致并发修改
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes =
                    new java.util.ArrayList<>(level.getRecipeManager().getRecipes());

            // ---- 阶段一：产出→配方（拆解） ----
            RECIPES_BY_OUTPUT.clear();
            RECIPES_BY_OUTPUT_DETAIL.clear();
            SMITHING_RECIPES.clear();
            recipeIndexProcessed = 0;
            recipeIndexTotal = recipes.size();
            long batchEnd = System.nanoTime() + INDEX_WORK_NS;
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipes) {
                if (gen != indexGeneration) return; // 构建期间被重置，放弃旧快照
                Recipe<?> r = holder.value();
                try {
                    if (r instanceof net.minecraft.world.item.crafting.SmithingRecipe) {
                        SMITHING_RECIPES.add(r);
                    }
                    ItemStack out = getRecipeOutput(r, level.registryAccess());
                    if (!out.isEmpty()) {
                        RECIPES_BY_OUTPUT.computeIfAbsent(out.getItem(),
                                k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(r);
                        // tacz 枪械等"同物品靠 NBT 区分"的输出：按 GunId 精确索引
                        String nbtKey = extractGunId(out);
                        RECIPES_BY_OUTPUT_DETAIL.computeIfAbsent(
                                out.getItem().toString() + "|" + nbtKey,
                                k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(r);
                    }
                } catch (Throwable ignored) {
                }
                recipeIndexProcessed++;
                if (System.nanoTime() >= batchEnd) {
                    try {
                        Thread.sleep(INDEX_SLEEP_MS);
                    } catch (InterruptedException ignored) {
                    }
                    batchEnd = System.nanoTime() + INDEX_WORK_NS;
                }
            }
            if (gen != indexGeneration) return;
            recipeIndexOutputReady = true;

            // ---- 阶段二：材料→配方（合成），极低占空比避免持续阻塞 ----
            RECIPES_BY_INGREDIENT.clear();
            recipeIndexProcessed = 0;
            recipeIndexTotal = recipes.size();
            batchEnd = System.nanoTime() + INGREDIENT_WORK_NS;
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipes) {
                if (gen != indexGeneration) return; // 构建期间被重置，放弃旧快照
                indexRecipeIngredientsSafely(holder.value(), level);
                recipeIndexProcessed++;
                if (System.nanoTime() >= batchEnd) {
                    try {
                        Thread.sleep(INGREDIENT_SLEEP_MS);
                    } catch (InterruptedException ignored) {
                    }
                    batchEnd = System.nanoTime() + INGREDIENT_WORK_NS;
                }
            }
            if (gen != indexGeneration) return;
            recipeIndexReady = true;
        } catch (Throwable ignored) {
            // 后台失败：下次 ensureRecipeIndex 会重新尝试（仍是后台，不冻结服务器）
        } finally {
            if (gen == indexGeneration) recipeIndexBuilding = false;
        }
    }

    private static void indexRecipeIngredients(Recipe<?> r, Level level) {
        try {
            List<Ingredient> ings = getIngredientsFromRecipe(r);
            if (!ings.isEmpty()) {
                for (Ingredient ing : ings) {
                    for (ItemStack s : ing.getItems()) {
                        if (!s.isEmpty()) {
                            RECIPES_BY_INGREDIENT.computeIfAbsent(s.getItem(),
                                    k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(r);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 单条配方读取出错（含模组配方代码抛 Error）不中断整个构建
        }
    }

    /**
     * 带超时地解析一条配方的材料并建立索引。
     * 个别模组配方（如被 AllTheLeaks 锁定的）读取材料时会挂起/死锁，不返回也不抛异常，
     * 若直接在后台线程调用会永远卡住构建。这里把解析放到独立线程并等待超时，
     * 超时则跳过该配方类型（记入 UNREADABLE_RECIPE_TYPES），让构建继续。
     */
    private static void indexRecipeIngredientsSafely(Recipe<?> r, Level level) {
        if (UNREADABLE_RECIPE_TYPES.contains(r.getClass())) return;
        java.util.concurrent.Future<?> f = RECIPE_INGREDIENT_EXECUTOR.submit(() -> indexRecipeIngredients(r, level));
        try {
            f.get(RECIPE_INGREDIENT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            // 该配方类型读取挂起/死锁：跳过，之后同类不再尝试。
            // cancel(true) 会中断任务线程，防止其持续占用大量 CPU（getIngredients 里的代码
            // 若不响应中断则线程仍会泄漏，但至少可尽力中止）。
            f.cancel(true);
            UNREADABLE_RECIPE_TYPES.add(r.getClass());
        } catch (Throwable ignored) {
            f.cancel(true);
            UNREADABLE_RECIPE_TYPES.add(r.getClass());
        }
    }

    // ---- AbstractContainerMenu 重写 ----

    /**
     * 服务端每 tick 调用：确保索引在构建，且索引就绪后自动重算一次当前内容，
     * 这样后台构建完成时玩家能立刻看到结果，无需手动重新放物品。
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player == null) return;
        if (player.level().isClientSide) return;
        // DataSlot：bit0=拆解索引就绪, bit1=合成索引就绪
        recipeReadyDataSlot.set((recipeIndexOutputReady ? 1 : 0) | (recipeIndexReady ? 2 : 0));
        if (!recipeIndexReady) {
            ensureRecipeIndex(player.level());
        }
        int state = (recipeIndexOutputReady ? 1 : 0) | (recipeIndexReady ? 2 : 0);
        if ((state & ~this.handledIndexState) != 0) {
            this.handledIndexState |= state;
            recomputeAfterIndexReady();
        }
        // 索引构建中：把 "已处理/总数" 进度推给客户端（限频：变化满 50 且距上次 ≥200ms 才发）
        if (recipeIndexTotal > 0 && !recipeIndexReady) {
            int p = recipeIndexProcessed;
            long now = System.currentTimeMillis();
            if (Math.abs(p - this.lastSentIndexProgress) >= 50 && now - this.lastSentIndexTime >= 200L) {
                this.lastSentIndexProgress = p;
                this.lastSentIndexTime = now;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (net.minecraft.server.level.ServerPlayer) player,
                        new com.zzq.survival_toolbox.network.SyncRecipeIndexProgressPacket(p, recipeIndexTotal));
            }
        }
    }

    /**
     * 把拆解输入槽里的物品退还给玩家，并切回合成模式。
     * <p>
     * 用于 JEI 的"+"号填入材料之前：输入槽有东西时菜单处于拆解模式，材料填进九宫格也不会
     * 出合成预览，所以先把该槽清空。细则：
     * <ul>
     *   <li>还没被消耗的输入物品原样退还；</li>
     *   <li>九宫格里已经"付款"（leftConsumed）的拆解产物一并退还；</li>
     *   <li>还没付款的自动填入预览产物直接丢弃——否则 JEI 会把它们当材料退回背包，等于未消耗即获得。</li>
     * </ul>
     * </p>
     */
    public void returnInputToPlayer() {
        if (player.level().isClientSide) return;
        ItemStack input = container.getItem(0);
        if (input.isEmpty()) return;

        if (leftConsumed) {
            returnOrDropMiddleItems();
        } else {
            for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
            clearMiddleCache();
        }

        ItemStack give = input.copy();
        container.setItem(0, ItemStack.EMPTY);
        if (!player.getInventory().add(give)) {
            player.drop(give, false);
        }

        // 清掉拆解侧状态，按当前九宫格重算合成预览（此时是合成模式）
        disassembleRecipeIdx = materialVariantIdx = 0;
        disassembleRecipes.clear();
        variantPairs.clear();
        currentPairIndex = 0;
        currentProductList.clear();
        currentPotionSteps.clear();
        currentPotionStepIndex = 0;
        leftConsumed = false;
        middleModified = false;
        setButtonMode(false);
        disassembleIndexSlot.set(0);
        disassembleTotalSlot.set(0);
        craftRecipes.clear();
        craftRecipeIdx = 0;
        container.setItem(10, ItemStack.EMPTY);
        updateCraftingResultByItems();
        broadcastChanges();
    }

    /** 客户端读取：索引状态（0=拆解构建中, 1=拆解就绪/合构建中, 2+=全部就绪），用于显示提示。 */
    public int getRecipeIndexState() {
        return recipeReadyDataSlot.get();
    }

    /** 客户端：记录从进度包收到的"已处理/总数" */
    public static void setClientIndexProgress(int processed, int total) {
        clientIndexProcessed = processed;
        clientIndexTotal = total;
    }

    /** 客户端：返回用于显示的进度文本，如 "12345/91010"；无进度时返回空串 */
    public static String getClientIndexProgressText() {
        if (clientIndexTotal <= 0) return "";
        return clientIndexProcessed + "/" + clientIndexTotal;
    }

    /**
     * 重置全局配方索引。数据包重载（F3+T）或切换世界时调用，
     * 避免沿用旧世界的缓存配方。
     * <p>
     * 同时清空 UNREADABLE_RECIPE_TYPES 黑名单：被拉黑的配方类型在下次重建时会重新探测一次。
     * 若上次只是误判（极端卡顿导致超时），这次有机会正常进索引；若仍是真死锁，会再次超时并被重新拉黑。
     * </p>
     */
    public static void resetRecipeIndex() {
        // 代数+1：让正在进行的旧构建立即失效（它记录的是重置前的代数，检测到不匹配会放弃）
        indexGeneration++;
        RECIPES_BY_OUTPUT.clear();
        RECIPES_BY_OUTPUT_DETAIL.clear();
        RECIPES_BY_INGREDIENT.clear();
        recipeIndexOutputReady = false;
        recipeIndexReady = false;
        recipeIndexBuilding = false;
        recipeIndexProcessed = 0;
        recipeIndexTotal = 0;
        clientIndexProcessed = 0;
        clientIndexTotal = 0;
        UNREADABLE_RECIPE_TYPES.clear();
    }

    private void recomputeAfterIndexReady() {
        if (isUpdating) return;
        boolean hasLeft = !container.getItem(0).isEmpty();
        if (hasLeft) {
            // ⚠️ 同样兜 Throwable：索引构建完会重算一次，这里出错也不能把游戏带走（理由见输入槽 setChanged 那段）
            try {
                onLeftSlotChanged();
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().error(
                        "[次元袋·拆解台] 索引就绪后重算出错（已兜住，不影响游戏）：物品={}",
                        container.getItem(0), t);
            }
            return;
        }
        boolean hasGrid = false;
        for (int i = 1; i <= 9; i++) {
            if (!container.getItem(i).isEmpty()) { hasGrid = true; break; }
        }
        if (hasGrid) updateCraftingResultByItems();
    }

    @Override
    public void removed(Player p) {
        if (p.level().isClientSide) {
            super.removed(p);
            return;
        }

        ItemStack left = container.getItem(0);
        if (!left.isEmpty()) {
            if (leftConsumed) {
                for (int i = 1; i <= 9; i++) {
                    // 中心/激活物品也一起返还（判定规则见 EXTRA_DISPLAY 的注释）
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty()) {
                        if (!p.getInventory().add(s.copy())) {
                            dropItemAtAccess(s.copy());
                        }
                        container.setItem(i, ItemStack.EMPTY);
                    }
                }
            } else {
                for (int i = 1; i <= 9; i++) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
            if (!left.isEmpty()) {
                if (!p.getInventory().add(left.copy())) {
                    dropItemAtAccess(left.copy());
                }
                container.setItem(0, ItemStack.EMPTY);
            }
        } else {
            for (int i = 1; i <= 9; i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (!p.getInventory().add(s.copy())) {
                        dropItemAtAccess(s.copy());
                    }
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        container.setItem(10, ItemStack.EMPTY);
        BREWING_STEPS_CACHE.clear();
        super.removed(p);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player p, int idx) {
        Slot slot = this.slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack src = slot.getItem();
        ItemStack copy = src.copy();

        if (idx == 10) {
            if (!this.moveItemStackTo(src, 11, 47, true)) return ItemStack.EMPTY;
            this.onCraftResultTaken();
            return copy;
        }

        if (idx < 11) {
            if (!this.moveItemStackTo(src, 11, 47, true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(src, 1, 10, false)) {
                if (!this.moveItemStackTo(src, 0, 1, false)) return ItemStack.EMPTY;
            }
        }

        if (src.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        switch (id) {
            case 6: // JEI 用"+"填材料前：把拆解输入槽退还给玩家，切到合成模式
                returnInputToPlayer();
                return true;
            case 0: // 拆解上一页
                if (!container.getItem(0).isEmpty()) {
                    if (currentPotionSteps.size() > 1) {
                        if (leftConsumed) returnOrDropMiddleItems();
                        else for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                        container.setItem(10, ItemStack.EMPTY);
                        currentPotionStepIndex = (currentPotionStepIndex - 1 + currentPotionSteps.size()) % currentPotionSteps.size();
                        showCurrentPotionStep();
                        updateDisassembleCount(currentPotionStepIndex + 1, currentPotionSteps.size());
                        leftConsumed = false;
                        middleModified = false;
                        return true;
                    }
                    if (variantPairs.size() <= 1) return true;
                    if (leftConsumed) returnOrDropMiddleItems();
                    else for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                    container.setItem(10, ItemStack.EMPTY);
                    currentPairIndex = (currentPairIndex - 1 + variantPairs.size()) % variantPairs.size();
                    applyPair(currentPairIndex);
                    updateDisassembleCount(currentPairIndex + 1, variantPairs.size());
                    leftConsumed = false;
                    middleModified = false;
                }
                return true;
            case 1: // 拆解下一页
                if (!container.getItem(0).isEmpty()) {
                    if (currentPotionSteps.size() > 1) {
                        if (leftConsumed) returnOrDropMiddleItems();
                        else for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                        container.setItem(10, ItemStack.EMPTY);
                        currentPotionStepIndex = (currentPotionStepIndex + 1) % currentPotionSteps.size();
                        showCurrentPotionStep();
                        updateDisassembleCount(currentPotionStepIndex + 1, currentPotionSteps.size());
                        leftConsumed = false;
                        middleModified = false;
                        return true;
                    }
                    if (variantPairs.size() <= 1) return true;
                    if (leftConsumed) returnOrDropMiddleItems();
                    else for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
                    container.setItem(10, ItemStack.EMPTY);
                    currentPairIndex = (currentPairIndex + 1) % variantPairs.size();
                    applyPair(currentPairIndex);
                    updateDisassembleCount(currentPairIndex + 1, variantPairs.size());
                    leftConsumed = false;
                    middleModified = false;
                }
                return true;
            case 2: // 合成上一页
                if (container.getItem(0).isEmpty() && craftRecipes.size() > 1) {
                    craftRecipeIdx = (craftRecipeIdx - 1 + craftRecipes.size()) % craftRecipes.size();
                    displayCurrentCraftResult();
                    craftIndexSlot.set(craftRecipeIdx + 1);
                }
                return true;
            case 3: // 合成下一页
                if (container.getItem(0).isEmpty() && craftRecipes.size() > 1) {
                    craftRecipeIdx = (craftRecipeIdx + 1) % craftRecipes.size();
                    displayCurrentCraftResult();
                    craftIndexSlot.set(craftRecipeIdx + 1);
                }
                return true;
            case 4: // 一键拆解（产物超过9格时使用）
                if (isButtonMode() && !container.getItem(0).isEmpty()) {
                    performDisassemble();
                    broadcastChanges();
                }
                return true;
            case 5: // 批量拆解（所有输入物品一次性拆解）
                if (!container.getItem(0).isEmpty() && !currentProductList.isEmpty()) {
                    performBulkDisassemble();
                }
                return true;
            default:
                return super.clickMenuButton(p, id);
        }
    }

    private void performBulkDisassemble() {
        ItemStack left = container.getItem(0);
        if (left.isEmpty() || currentProductList.isEmpty()) return;

        List<ItemStack> prods = currentProductList.stream().map(ItemStack::copy).collect(Collectors.toList());

        // ⚠️ 中心/激活物品（材料之后的那几格）**只发一次**，不能跟着每个输入物品重复发
        //（需求："全部拆解也要给中间的黑暗魔杖"，但同时不能把一根魔法杖乘上整叠数量）。
        List<ItemStack> extras = new ArrayList<>();
        for (int i = extraMaterialStart() + 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) extras.add(s.copy());
        }

        while (!left.isEmpty()) {
            left.shrink(1);
            if (left.isEmpty()) container.setItem(0, ItemStack.EMPTY);

            for (ItemStack prod : prods) {
                if (!player.getInventory().add(prod.copy())) {
                    player.drop(prod, false);
                }
            }
            if (container.getItem(0).isEmpty()) break;
        }

        for (ItemStack extra : extras) {
            if (!player.getInventory().add(extra.copy())) {
                player.drop(extra.copy(), false);
            }
        }

        for (int i = 1; i <= 9; i++) container.setItem(i, ItemStack.EMPTY);
        container.setItem(10, ItemStack.EMPTY);
        clearMiddleCache();
        currentProductList.clear();
        setButtonMode(false);
        leftConsumed = false;
        middleModified = false;
        updateDisassembleCount(0, 0);
        broadcastChanges();
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return stillValid(this.access, p, ModBlocks.DISASSEMBLE_TABLE.get());
    }
}