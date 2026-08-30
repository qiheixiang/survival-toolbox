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
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
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
     * 在后台线程异步构建，交互时只查缓存，绝不在服务器线程上全量扫描配方，
     * 否则大整合包（上万配方）会阻塞服务器线程数十秒。
     */
    private static final java.util.concurrent.ConcurrentMap<Item, List<Recipe<?>>> RECIPES_BY_OUTPUT =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 按"物品|NBT 标识（tacz GunId）"精确索引输出配方：枪械等"同物品不同 NBT 区分"的物品能精确找到自己的配方 */
    private static final java.util.concurrent.ConcurrentMap<String, List<Recipe<?>>> RECIPES_BY_OUTPUT_DETAIL =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<Item, List<Recipe<?>>> RECIPES_BY_INGREDIENT =
            new java.util.concurrent.ConcurrentHashMap<>();
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
    /** 每个菜单实例上次发包时间戳，进一步限频 */
    private long lastSentIndexTime = 0;

    /**
     * 读取 ingredients 会抛异常的配方类型缓存（如被 AllTheLeaks 锁定的现代工业化 MachineRecipe）。
     * 同一类型只抛一次，之后直接跳过。
     */
    private static final java.util.Set<Class<?>> UNREADABLE_RECIPE_TYPES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 阶段二每条配方材料解析的超时；超时视为该配方类型"挂起/死锁"，跳过并记入 UNREADABLE_RECIPE_TYPES。
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
    public boolean isButtonMode = false;
    private final DataSlot buttonModeDataSlot = DataSlot.standalone();
    private ItemStack[] previousMiddleStacks = new ItemStack[9];

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
                onLeftSlotChanged();
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

    // ---- 酿造步骤获取 ----

    private static List<PotionSingleStep> getAllBrewingSteps() {
        List<PotionSingleStep> steps = new ArrayList<>();
        List<?> mixes = findPotionMixes();
        if (mixes != null) {
            for (Object mix : mixes) {
                PotionSingleStep step = extractPotionSingleStep(mix);
                if (step != null) steps.add(step);
            }
        }
        for (IBrewingRecipe recipe : BrewingRecipeRegistry.getRecipes()) {
            if (recipe instanceof BrewingRecipe br) {
                Potion from = PotionUtils.getPotion(br.getInput().getItems()[0]);
                Potion to = PotionUtils.getPotion(br.getOutput());
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

    private static List<?> findPotionMixes() {
        for (String name : new String[]{"POTION_MIXES", "ALL_POTIONS", "MIXES", "f_43542_"}) {
            try {
                java.lang.reflect.Field f = PotionBrewing.class.getDeclaredField(name);
                f.setAccessible(true);
                Object val = f.get(null);
                if (val instanceof List<?> list && !list.isEmpty()) return list;
            } catch (Exception ignored) {
            }
        }
        for (java.lang.reflect.Field field : PotionBrewing.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            try {
                Object val = field.get(null);
                if (val instanceof List<?> list && !list.isEmpty()) {
                    if (list.get(0).getClass().getName().contains("Mix")) return list;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static PotionSingleStep extractPotionSingleStep(Object mix) {
        try {
            Potion from = null, to = null;
            Ingredient ingredient = null;
            for (java.lang.reflect.Field f : mix.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(mix);
                if (val instanceof Potion pot) {
                    if (from == null) from = pot;
                    else if (to == null) to = pot;
                } else if (val instanceof Holder<?> h && h.value() instanceof Potion pot) {
                    if (from == null) from = pot;
                    else if (to == null) to = pot;
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
            if (recipeOrData instanceof BrewingRecipe br) return br.getOutput();
            if (recipeOrData instanceof Recipe<?> r) return r.getResultItem(access).copy();
        } catch (Exception ignored) {
            // 部分模组配方读取结果时可能被 AllTheLeaks 等性能模组拦截抛异常，优雅跳过。
        }
        return ItemStack.EMPTY;
    }

    /**
     * 生成合成产物：优先调用配方的 assemble 组装（保留输入物品的 NBT 状态，
     * 如精妙背包等模组的升级配方、锻造台配方的附魔保留），无法组装时退回静态输出，
     * 并回退复制"同物品类型"输入的状态（排除耐久，避免修复类配方反向保留损坏值）。
     * <p>
     * 必须在消耗材料（tryConsumeIngredients）之前调用，输入取自九宫格副本。
     * </p>
     */
    private ItemStack assembleRecipeOutput(Recipe<?> recipe, RegistryAccess access) {
        try {
            if (recipe instanceof CraftingRecipe crafting) {
                TransientCraftingContainer input = new TransientCraftingContainer(this, 3, 3);
                for (int i = 1; i <= 9; i++) {
                    input.setItem(i - 1, container.getItem(i).copy());
                }
                ItemStack assembled = crafting.assemble(input, access);
                if (!assembled.isEmpty()) {
                    copyStateFromMatchingInput(assembled);
                    return assembled;
                }
            } else if (recipe instanceof SmithingRecipe smithing) {
                ItemStack assembled = assembleSmithingOutput(smithing, access);
                if (!assembled.isEmpty()) {
                    copyStateFromMatchingInput(assembled);
                    return assembled;
                }
            }
        } catch (Exception ignored) {
            // 部分模组配方组装异常时退回静态输出
        }

        // 回退方案 1：非 CraftingRecipe/SmithingRecipe 但可接受九宫格输入的配方
        //（如精妙背包等模组的自定义升级配方，其 assemble 自带状态/物品保留逻辑）
        ItemStack assembled = assembleAnyRecipe(recipe, access);
        if (!assembled.isEmpty()) return assembled;

        // 回退方案 2：非标准配方类型中，若输出与某个输入为同一种物品（"自身升级"类配方），
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
        SimpleContainer input = new SimpleContainer(3);
        input.setItem(0, ordered[0]);
        input.setItem(1, ordered[1]);
        input.setItem(2, ordered[2]);
        return smithing.assemble(input, access);
    }

    /** 复制输入物品的状态（NBT）到产物，排除耐久键 */
    private static void copyState(ItemStack from, ItemStack to) {
        if (from.hasTag()) {
            CompoundTag tag = from.getTag().copy();
            tag.remove("Damage");
            to.setTag(tag);
        }
    }

    /** 合成产物与某输入为同种物品（含容器类，如精妙背包升级）时，复制该输入的状态到产物 */
    private void copyStateFromMatchingInput(ItemStack result) {
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
            TransientCraftingContainer input = new TransientCraftingContainer(this, 3, 3);
            for (int i = 1; i <= 9; i++) {
                input.setItem(i - 1, container.getItem(i).copy());
            }
            ItemStack assembled = ((Recipe) recipe).assemble(input, access);
            if (!assembled.isEmpty()) {
                copyStateFromMatchingInput(assembled);
                return assembled;
            }
        } catch (Exception ignored) {
            // 配方输入类型不匹配时跳过，退回静态输出
        }
        return ItemStack.EMPTY;
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

        // 锻造配方材料强制提取
        if (recipe instanceof SmithingTransformRecipe || recipe instanceof SmithingTrimRecipe) {
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
                        // 生产环境（srg 混淆）下 template/base/addition 字段名为 f_44514_ 等，
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

        // 其他配方不再做全字段反射扫描：对复杂模组配方（如现代工业化）反射会深度递归并持续占用大量 CPU。
        // 这类配方材料本就无法可靠提取，返回空列表并跳过即可。
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
            // 仅当材料变体与输出"完全相同（同物品且 NBT 相同）"时视为自身剔除，
            // 避免拔刀剑升级（拔刀剑+材料→另一把拔刀剑）被误剔：材料刀与输出刀 NBT 不同
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

    /** 同物品且 NBT 完全相同（数量无关） */
    private static boolean sameItemWithNbt(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getItem() != b.getItem()) return false;
        net.minecraft.nbt.CompoundTag ta = a.getTag();
        net.minecraft.nbt.CompoundTag tb = b.getTag();
        if (ta == null) return tb == null;
        return ta.equals(tb);
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
        if (middleModified) returnOrDropMiddleItems();
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
            craftRecipes.clear();
            craftRecipeIdx = 0;
            updateCraftingResultByItems();
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
            Potion potion = PotionUtils.getPotion(input);
            if (potion != Potions.EMPTY) {
                currentProductList.clear();
                currentProductList.add(new ItemStack(Items.ARROW));
                currentProductList.add(PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION), potion));
                setButtonMode(false);
                updateMiddleGridFromProductList();
                updateDisassembleCount(1, 1);
                broadcastChanges();
                return;
            }
        }

        Item item = input.getItem();
        if (isPotion(item)) {
            Potion potion = PotionUtils.getPotion(input);
            if (potion != Potions.EMPTY) {
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

        Map<Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(input);
        if (!ench.isEmpty()) {
            ItemStack plain = input.copy();
            CompoundTag tag = plain.getTag();
            if (tag != null) {
                tag.remove("Enchantments");
                tag.remove("StoredEnchantments");
                if (tag.isEmpty()) plain.setTag(null);
            }
            currentProductList.clear();
            currentProductList.add(plain);
            for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) {
                currentProductList.add(EnchantedBookItem.createForEnchantment(
                        new EnchantmentInstance(e.getKey(), e.getValue())));
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
            String gunId = input.getTag() != null && input.getTag().contains("GunId")
                    ? input.getTag().getString("GunId") : "";
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
                    if (r instanceof CraftingRecipe) return true;
                    // tacz 枪匠台配方（自定义 Recipe<Inventory>，非 CraftingRecipe）同样放行
                    if (r.getClass().getName().equals("com.tacz.guns.crafting.GunSmithTableRecipe")) return true;
                    // 锻造配方：产物与输入同物品 → 放行，允许拆回材料（下界合金胸甲→钻石胸甲+材料等）。
                    // 材料列表已不会包含产物（见 getIngredientsFromRecipe 的 Ingredient 类型过滤），
                    // 此处放行是为了保证"输入=最终产物"的锻造配方仍能出现在拆解列表中。
                    if (r instanceof SmithingRecipe) {
                        ItemStack out = getRecipeOutput(r, level.registryAccess());
                        if (!out.isEmpty() && out.getItem() == input.getItem()) return true;
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

    private boolean isPotion(Item item) {
        return item instanceof PotionItem || item == Items.POTION ||
                item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private boolean isSplash(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SplashPotionItem || item == Items.SPLASH_POTION ||
                item.getClass().getName().toLowerCase().contains("splash");
    }

    private boolean isLingering(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof LingeringPotionItem || item == Items.LINGERING_POTION ||
                item.getClass().getName().toLowerCase().contains("lingering");
    }

    private ItemStack createBasePotion(ItemStack splash, Potion potion) {
        Item base = Items.POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(splash.getItem());
        String path = name.getPath().replace("splash_", "").replace("_splash", "");
        base = BuiltInRegistries.ITEM.get(new ResourceLocation(name.getNamespace(), path));
        if (base == Items.AIR) base = Items.POTION;
        return PotionUtils.setPotion(new ItemStack(base), potion);
    }

    private ItemStack createSplashVariant(ItemStack lingering, Potion potion) {
        Item splash = Items.SPLASH_POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(lingering.getItem());
        String path = name.getPath().replace("lingering_", "").replace("_lingering", "");
        splash = BuiltInRegistries.ITEM.get(new ResourceLocation(name.getNamespace(), path));
        if (splash == Items.AIR) splash = Items.SPLASH_POTION;
        return PotionUtils.setPotion(new ItemStack(splash), potion);
    }

    private ItemStack createSplashPotion(ItemStack base, Potion potion) {
        Item splash = Items.SPLASH_POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(base.getItem());
        String path = name.getPath().replace("splash_", "").replace("_splash", "");
        splash = BuiltInRegistries.ITEM.get(new ResourceLocation(name.getNamespace(), "splash_" + path));
        if (splash == Items.AIR) splash = Items.SPLASH_POTION;
        return PotionUtils.setPotion(new ItemStack(splash), potion);
    }

    private ItemStack createLingeringPotion(ItemStack base, Potion potion) {
        Item lingering = Items.LINGERING_POTION;
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(base.getItem());
        String path = name.getPath().replace("lingering_", "").replace("_lingering", "");
        lingering = BuiltInRegistries.ITEM.get(new ResourceLocation(name.getNamespace(), "lingering_" + path));
        if (lingering == Items.AIR) lingering = Items.LINGERING_POTION;
        return PotionUtils.setPotion(new ItemStack(lingering), potion);
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
        currentProductList.add(PotionUtils.setPotion(new ItemStack(potionItem), step.from));
        ItemStack[] mats = step.ingredient.getItems();
        if (mats.length > 0) currentProductList.add(mats[0].copy());

        setButtonMode(false);
        updateMiddleGridFromProductList();
    }

    private void updateMiddleGridFromProductList() {
        for (int i = 1; i <= 9; i++) {
            container.setItem(i, i <= currentProductList.size() ? currentProductList.get(i - 1).copy() : ItemStack.EMPTY);
        }
        container.setItem(10, container.getItem(0).copy());
        updateMiddleCache();
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
        // 绝不在此全量遍历所有配方，否则大整合包会阻塞服务器线程。
        if (recipeIndexReady) {
            java.util.LinkedHashSet<Recipe<?>> craftCandidates = new java.util.LinkedHashSet<>();
            for (ItemStack p : placed) {
                List<Recipe<?>> byIng = RECIPES_BY_INGREDIENT.get(p.getItem());
                if (byIng != null) craftCandidates.addAll(byIng);
            }
            for (Recipe<?> r : craftCandidates) {
                if (r instanceof TippedArrowRecipe) continue;

                List<Ingredient> ings = getIngredientsFromRecipe(r).stream()
                        .filter(i -> !i.isEmpty())
                        .collect(Collectors.toList());
                if (ings.isEmpty()) continue;

                // 材料中含输出物本身（"自身 + 其他 → 输出"的特殊配方，如整合包升级配方）：
                // 只剔除自身物品，保留其他材料参与匹配；不再整配方跳过或误删同槽其他物品。
                ings = removeOutputItemFromIngredients(r, ings, level.registryAccess());
                if (ings.isEmpty()) continue;

                if (matchesIngredients(placed, ings)) craftRecipes.add(r);
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
                Potion potion = PotionUtils.getPotion(potionStack);
                if (potion != Potions.EMPTY) {
                    craftRecipes.add(PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW), potion));
                }
            }
        }

        // 药水类型转换
        for (ItemStack s : placed) {
            Item it = s.getItem();
            if (isPotion(it)) {
                Potion potion = PotionUtils.getPotion(s);
                if (potion == Potions.EMPTY) continue;

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
                if (isPotion(s.getItem())) {
                    Potion current = PotionUtils.getPotion(s);
                    if (current == step.from) {
                        boolean hasIngredient = placed.stream()
                                .anyMatch(ps -> !ps.equals(s) && step.ingredient.test(ps));
                        if (hasIngredient) {
                            craftRecipes.add(PotionUtils.setPotion(new ItemStack(s.getItem()), step.to));
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
            // 精妙背包升级等配方需要从输入复制 NBT，静态 getResultItem 是空物品。
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
        try {
            ItemStack left = container.getItem(0);
            if (left.isEmpty() || currentProductList.isEmpty()) return;

            left.shrink(1);
            if (left.isEmpty()) container.setItem(0, ItemStack.EMPTY);

            for (int i = 1; i <= 9; i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    if (!player.getInventory().add(s.copy())) {
                        player.drop(s.copy(), false);
                    }
                    container.setItem(i, ItemStack.EMPTY);
                }
            }

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
        if (craftRecipes.isEmpty() || craftRecipeIdx >= craftRecipes.size()) return;

        Object obj = craftRecipes.get(craftRecipeIdx);

        if (obj instanceof ItemStack resultItem) {
            if (resultItem.isEmpty()) return;
            Item resultType = resultItem.getItem();

            // 药水箭
            if (resultType == Items.TIPPED_ARROW) {
                Potion potion = PotionUtils.getPotion(resultItem);
                if (potion == Potions.EMPTY) return;
                if (!tryConsumeItem(Items.ARROW)) return;
                if (!tryConsumeAnyLingering(potion)) return;
                container.setItem(10, resultItem.copy());
                container.setChanged();
            }
            // 喷溅药水
            else if (isSplash(resultItem)) {
                Potion rPotion = PotionUtils.getPotion(resultItem);
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
                Potion rPotion = PotionUtils.getPotion(resultItem);
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
            else if (isPotion(resultType)) {
                Potion rPotion = PotionUtils.getPotion(resultItem);
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

            craftRecipes.clear();
            craftRecipeIdx = 0;
            updateCraftingResultByItems();
            broadcastChanges();
            return;
        }

        // 标准配方（含锻造）
        if (!(obj instanceof Recipe<?> recipe)) return;

        // 玩家点击时已经拿走了槽 10 的预览物（displayCurrentCraftResult 用 assemble 生成，
        // 与这里结果一致）。这里只需校验并消耗材料；消耗失败则把产物放回槽 10。
        ItemStack result = assembleRecipeOutput(recipe, player.level().registryAccess());
        if (result.isEmpty()) return;

        List<Ingredient> ings = getIngredientsFromRecipe(recipe).stream()
                .filter(i -> !i.isEmpty())
                .collect(Collectors.toList());

        // 合成确认时不执行"剔除输出物"处理：若剔除自身，玩家可无成本合成，造成物品复制。
        // "自身 + 其他 → 输出"的配方应消耗全部材料（含自身）。

        if (!tryConsumeIngredients(ings)) {
            // 材料在预览后已被改动（极少见）：将产物放回槽 10，以免玩家无消耗获得产物。
            container.setItem(10, result.copy());
            container.setChanged();
            return;
        }

        craftRecipes.clear();
        craftRecipeIdx = 0;
        updateCraftingResultByItems();
        broadcastChanges();
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
            if (s.getItem() == potionItem && PotionUtils.getPotion(s) == potion) {
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
            if (isPotion(s.getItem()) && !isSplash(s) && !isLingering(s) &&
                    PotionUtils.getPotion(s) == potion) {
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
            if (isSplash(s) && PotionUtils.getPotion(s) == potion) {
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
            if (isLingering(s) && PotionUtils.getPotion(s) == potion) {
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
            if (isPotion(s.getItem()) && PotionUtils.getPotion(s) == effect) {
                if (preferAdvancedType && (isSplash(s) || isLingering(s))) return s.getItem();
                if (!preferAdvancedType && !isSplash(s) && !isLingering(s)) return s.getItem();
            }
        }
        for (int i = 1; i <= 9; i++) {
            ItemStack s = container.getItem(i);
            if (isPotion(s.getItem()) && PotionUtils.getPotion(s) == effect) return s.getItem();
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

    private void returnOrDropMiddleItems() {
        for (int i = 1; i <= 9; i++) {
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
        // 只做后台构建，绝不在此同步全量扫描（否则大整合包会冻结服务器线程）。
        if (recipeIndexReady || recipeIndexBuilding || level == null || level.isClientSide) return;
        recipeIndexBuilding = true;
        java.util.concurrent.CompletableFuture.runAsync(() -> buildRecipeIndexAsync(level));
    }

    /**
     * 后台线程分两阶段构建配方索引，带 CPU 节流（低占空比，避免在 4 核老 CPU + 大整合包上饿死服务器）。
     * <p>阶段一：产出→配方（拆解）——只用 getResultItem，快且不碰 AllTheLeaks 锁，拆解先可用。
     * 阶段二：材料→配方（合成）——用 getIngredients，最耗 CPU，用更低的占空比以免持续卡顿。</p>
     */
    private static final long INDEX_WORK_NS = 5_000_000L;     // 阶段一：每次连续处理 ~5ms
    private static final long INDEX_SLEEP_MS = 15L;            // 睡 15ms（~25% 占空比）
    private static final long INGREDIENT_WORK_NS = 2_000_000L; // 阶段二：每次仅 ~2ms
    private static final long INGREDIENT_SLEEP_MS = 25L;       // 睡 25ms（~7% 占空比，整机约 2%，几乎无感）

    private static void buildRecipeIndexAsync(Level level) {
        final int gen = indexGeneration;
        try {
            // 先快照配方列表，避免后台迭代时配方管理器变更导致并发修改
            java.util.List<Recipe<?>> recipes = new java.util.ArrayList<>(level.getRecipeManager().getRecipes());

            // ---- 阶段一：产出→配方（拆解） ----
            RECIPES_BY_OUTPUT.clear();
            RECIPES_BY_OUTPUT_DETAIL.clear();
            recipeIndexProcessed = 0;
            recipeIndexTotal = recipes.size();
            long batchEnd = System.nanoTime() + INDEX_WORK_NS;
            for (Recipe<?> r : recipes) {
                if (gen != indexGeneration) return; // 构建期间被重置，放弃旧快照
                try {
                    ItemStack out = getRecipeOutput(r, level.registryAccess());
                    if (!out.isEmpty()) {
                        RECIPES_BY_OUTPUT.computeIfAbsent(out.getItem(),
                                k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(r);
                        // tacz 枪械等"同物品靠 NBT 区分"的输出：按 GunId 精确索引
                        String nbtKey = out.getTag() != null && out.getTag().contains("GunId")
                                ? out.getTag().getString("GunId") : "";
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

            // ---- 阶段二：材料→配方（合成），极低占空比避免持续卡顿 ----
            RECIPES_BY_INGREDIENT.clear();
            recipeIndexProcessed = 0;
            recipeIndexTotal = recipes.size();
            batchEnd = System.nanoTime() + INGREDIENT_WORK_NS;
            for (Recipe<?> r : recipes) {
                if (gen != indexGeneration) return; // 构建期间被重置，放弃旧快照
                indexRecipeIngredientsSafely(r, level);
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
     * 个别模组配方读取材料时会挂起/死锁，不返回也不抛异常，
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
            // cancel(true) 会中断任务线程，防止其持续占用 CPU。
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
        if (recipeIndexTotal > 0 && !recipeIndexReady && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            int p = recipeIndexProcessed;
            long now = System.currentTimeMillis();
            if (Math.abs(p - this.lastSentIndexProgress) >= 50 && now - this.lastSentIndexTime >= 200L) {
                this.lastSentIndexProgress = p;
                this.lastSentIndexTime = now;
                com.zzq.survival_toolbox.SurvivalToolbox.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                        new com.zzq.survival_toolbox.network.SyncRecipeIndexProgressPacket(p, recipeIndexTotal));
            }
        }
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

    private void recomputeAfterIndexReady() {
        if (isUpdating) return;
        boolean hasLeft = !container.getItem(0).isEmpty();
        if (hasLeft) {
            onLeftSlotChanged();
            return;
        }
        boolean hasGrid = false;
        for (int i = 1; i <= 9; i++) {
            if (!container.getItem(i).isEmpty()) { hasGrid = true; break; }
        }
        if (hasGrid) updateCraftingResultByItems();
    }

    /**
     * 重置全局配方索引。数据包重载（F3+T）或切换世界时调用，
     * 避免沿用旧世界的缓存配方。
     * <p>同时清空 UNREADABLE_RECIPE_TYPES 黑名单：被拉黑的配方类型在下次重建时会重新探测一次。</p>
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