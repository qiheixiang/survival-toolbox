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
     * 绝不在服务器线程上全量扫描配方——否则 ATM 这种上万配方的大整合包会卡死服务器几十秒。
     */
    private static final java.util.concurrent.ConcurrentMap<Item, List<Recipe<?>>> RECIPES_BY_OUTPUT =
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
            if (recipeOrData instanceof BrewingRecipe br) return br.getOutput();
            if (recipeOrData instanceof Recipe<?> r) return r.getResultItem(access).copy();
        } catch (Exception ignored) {
            // 部分模组配方读取结果时可能被 AllTheLeaks 等性能模组拦截抛异常，优雅跳过。
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

        if (recipe instanceof BrewingRecipe br) {
            return Arrays.asList(br.getInput(), br.getIngredient());
        }

        // 锻造配方暴力提取
        if (recipe instanceof SmithingTransformRecipe || recipe instanceof SmithingTrimRecipe) {
            List<Ingredient> result = new ArrayList<>();
            String[] fieldNames = {"template", "base", "addition", "left", "right", "middle", "input1", "input2", "input3"};
            for (String n : fieldNames) {
                try {
                    java.lang.reflect.Field f = recipe.getClass().getDeclaredField(n);
                    f.setAccessible(true);
                    Object val = f.get(recipe);
                    if (val instanceof Ingredient ing) result.add(ing);
                    else if (val instanceof ItemStack s && !s.isEmpty()) result.add(Ingredient.of(s));
                } catch (NoSuchFieldException | IllegalAccessException ignored) {
                }
            }
            if (result.isEmpty()) {
                try {
                    for (java.lang.reflect.Field f : recipe.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object val = f.get(recipe);
                        if (val instanceof Ingredient ing) result.add(ing);
                        else if (val instanceof ItemStack s && !s.isEmpty()) result.add(Ingredient.of(s));
                    }
                } catch (Exception ignored) {
                }
            }
            return result;
        }

        // 其他配方不再做全字段反射扫描：对复杂模组配方（如现代工业化）反射会深度递归、
        // 宽幅爆炸烧 CPU 且停不下来。这类配方材料本就无法可靠提取，返回空让其跳过即可。
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

    private boolean containsOutputItem(Object recipeOrData, RegistryAccess access) {
        ItemStack out = getRecipeOutput(recipeOrData, access);
        if (out.isEmpty()) return false;
        for (Ingredient ing : getIngredientsFromRecipe(recipeOrData)) {
            for (ItemStack s : ing.getItems()) {
                if (s.getItem() == out.getItem()) return true;
            }
        }
        return false;
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
        List<Recipe<?>> candidates = recipeIndexOutputReady
                ? RECIPES_BY_OUTPUT.getOrDefault(input.getItem(), Collections.emptyList())
                : Collections.emptyList();

        disassembleRecipes = new ArrayList<>(candidates);
        disassembleRecipes = disassembleRecipes.stream()
                .filter(r -> {
                    try {
                        List<Ingredient> ings = getIngredientsFromRecipe(r);
                        if (ings.isEmpty()) return false;
                        for (Ingredient ing : ings) {
                            for (ItemStack s : ing.getItems()) {
                                if (s.getItem() != input.getItem()) return true;
                            }
                        }
                    } catch (Exception e) {
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
     * 判断物品是否属于“药水”。不写死原版物品类，而是优先看有没有标准的
     * {@code POTION_CONTENTS} 数据组件（1.20.5+ 所有药水的统一数据载体），
     * 再兜底检查是否继承 {@link PotionItem}，从而兼容其他 mod 的自定义药水。
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
        for (Ingredient ing : getIngredientsFromRecipe(recipe)) {
            ItemStack[] stacks = ing.getItems();
            if (stacks.length > 0) {
                ItemStack mat = stacks[materialVariantIdx % stacks.length].copy();
                if (mat.getItem() != input.getItem()) currentProductList.add(mat);
            }
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
        // 绝不在此全量遍历所有配方，否则大整合包会卡死服务器线程。
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

                boolean isSmithing = (r instanceof SmithingTransformRecipe || r instanceof SmithingTrimRecipe);
                if (isSmithing) {
                    ItemStack smithingOutput = getRecipeOutput(r, level.registryAccess());
                    if (!smithingOutput.isEmpty()) {
                        ings = ings.stream()
                                .filter(ing -> Arrays.stream(ing.getItems())
                                        .noneMatch(s -> s.getItem() == smithingOutput.getItem()))
                                .collect(Collectors.toList());
                    }
                    if (ings.isEmpty()) continue;
                }

                if (!isSmithing && containsOutputItem(r, level.registryAccess())) continue;

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
            container.setItem(10, getRecipeOutput(r, player.level().registryAccess()).copy());
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

            craftRecipes.clear();
            craftRecipeIdx = 0;
            updateCraftingResultByItems();
            broadcastChanges();
            return;
        }

        // 标准配方（含锻造）
        if (!(obj instanceof Recipe<?> recipe)) return;
        ItemStack result = getRecipeOutput(recipe, player.level().registryAccess());
        if (result.isEmpty()) return;

        List<Ingredient> ings = getIngredientsFromRecipe(recipe).stream()
                .filter(i -> !i.isEmpty())
                .collect(Collectors.toList());

        boolean isSmithing = (recipe instanceof SmithingTransformRecipe || recipe instanceof SmithingTrimRecipe);
        if (isSmithing) {
            ItemStack smithingOutput = result;
            ings = ings.stream()
                    .filter(ing -> Arrays.stream(ing.getItems())
                            .noneMatch(s -> s.getItem() == smithingOutput.getItem()))
                    .collect(Collectors.toList());
        }

        if (!tryConsumeIngredients(ings)) return;
        container.setItem(10, result.copy());
        container.setChanged();

        boolean canContinue = checkIngredientsAvailable(ings);
        if (!canContinue) {
            craftRecipes.clear();
            craftRecipeIdx = 0;
            updateCraftingResultByItems();
        }
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
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes =
                    new java.util.ArrayList<>(level.getRecipeManager().getRecipes());

            // ---- 阶段一：产出→配方（拆解） ----
            RECIPES_BY_OUTPUT.clear();
            recipeIndexProcessed = 0;
            recipeIndexTotal = recipes.size();
            long batchEnd = System.nanoTime() + INDEX_WORK_NS;
            for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : recipes) {
                if (gen != indexGeneration) return; // 构建期间被重置，放弃旧快照
                Recipe<?> r = holder.value();
                try {
                    ItemStack out = getRecipeOutput(r, level.registryAccess());
                    if (!out.isEmpty()) {
                        RECIPES_BY_OUTPUT.computeIfAbsent(out.getItem(),
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
            // cancel(true) 会中断任务线程，防止它一直烧 CPU（getIngredients 里的代码
            // 若不响应中断则线程仍会漏，但至少尽力停止）。
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
            onLeftSlotChanged();
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