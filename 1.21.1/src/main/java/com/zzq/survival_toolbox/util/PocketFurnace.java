package com.zzq.survival_toolbox.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * 次元袋里的熔炉（"熔炉页"的数据与逻辑）
 * <p>
 * <b>规则全部取自原版，不自己编</b>（需求："只做熔炉、调用原版逻辑"）：
 * <ul>
 *   <li>配方：原版 {@link RecipeType#SMELTING}，用 {@code RecipeManager} 查表，不写死任何配方；</li>
 *   <li>燃料：原版燃烧时间（{@code ItemStack#getBurnTime(SMELTING)}，就是原版燃料表）；</li>
 *   <li>每个耗时：<b>配方自带</b>的 {@code getCookingTime()}（原版熔炉 200 tick = 10 秒；
 *       高炉快一倍是高炉配方写 100，不是炉子特殊——这也正是"高炉不能烧沙子"的原因：玻璃只有熔炼配方）；</li>
 *   <li>经验：配方自带的 {@code getExperience()}；</li>
 *   <li>进度推进、以及"没燃料时进度每 tick 回退 2"：照着原版
 *       {@code AbstractFurnaceBlockEntity#serverTick} 的规则做。</li>
 * </ul>
 * </p>
 * <p>
 * <b>为什么不能像铁砧/锻造台那样直接开原版界面</b>：熔炉的烧炼逻辑在方块实体
 * （{@code AbstractFurnaceBlockEntity#serverTick}）里，菜单 {@code AbstractFurnaceMenu} 只负责显示进度，
 * 凭空开一个没有方块实体的熔炉界面只会得到永远不烧的空壳。这里把"炉子"搬进袋子，
 * 由服务端每 tick 推进（袋子在玩家物品栏里就烧，<b>关掉界面也继续烧</b>），进度存在袋子 NBT 里。
 * </p>
 * <p>
 * <b>状态为什么存"时刻"而不是"进度"</b>（照精妙背包 {@code CookingLogic} 的做法）：
 * 进度每 tick 都在变，如果每 tick 都写袋子 NBT，原版会认为"这个物品变了"→ 每 tick 把它重新同步给客户端
 * → 玩家手里那个袋子会一直抖（实测："拿着袋子反复同步"）。所以 NBT 里存的是<b>结束时刻</b>
 * （{@code FurnaceBurnEnd} / {@code FurnaceCookStart} 等，取 {@code level.getGameTime()}），
 * 当前进度用"现在 - 开始时刻"现算；只有<b>状态切换</b>时（点火、烧完一件、进度重置、暂停/恢复）才写盘。
 * 好处：拿着袋子不抖，而且中途退出游戏再进来还能接着烧。
 * </p>
 */
public final class PocketFurnace {

    /** 输入格 */
    public static final int SLOT_INPUT = 0;
    /** 燃料格 */
    public static final int SLOT_FUEL = 1;
    /** 产物格（需求：默认产物放这个格子里，和原版熔炉一样；按钮可切成直接进袋子） */
    public static final int SLOT_OUTPUT = 2;
    /** 槽位数（输入 + 燃料 + 产物） */
    public static final int SLOTS = 3;

    private static final String TAG_INPUT = "FurnaceInput";
    private static final String TAG_FUEL = "FurnaceFuel";
    private static final String TAG_OUTPUT = "FurnaceOutput";
    /** 产物去处：true = 放产物格（默认，原版熔炉那样），false = 直接进储物空间 */
    private static final String TAG_TO_SLOT = "FurnaceToSlot";
    /** 这格燃料烧到哪个游戏时刻（0 = 没在烧） */
    private static final String TAG_BURN_END = "FurnaceBurnEnd";
    /** 这格燃料总共能烧多久（只用来画火焰的高矮） */
    private static final String TAG_BURN_TOTAL = "FurnaceBurnTotal";
    /** 已经攒下的烧炼 tick（不含当前这一段） */
    private static final String TAG_COOK_DONE = "FurnaceCookDone";
    /** 当前这段从哪个游戏时刻开始烧（0 = 没在烧） */
    private static final String TAG_COOK_START = "FurnaceCookStart";
    /** 没火之后从哪个游戏时刻开始回退（0 = 没在回退）；原版规则是每 tick 回退 2 */
    private static final String TAG_COOK_DECAY = "FurnaceCookDecay";
    /** 当前物品需要的总时长（配方 getCookingTime） */
    private static final String TAG_COOK_TOTAL = "FurnaceCookTotal";
    /** 正在烧的东西（换物品要重置进度，和原版一致） */
    private static final String TAG_COOK_TARGET = "FurnaceCookTarget";
    /** 攒着的经验（"产物放格子"模式下等玩家取产物时再给；不足 1 点先留着） */
    private static final String TAG_XP = "FurnaceXp";

    private PocketFurnace() {
    }

    /**
     * 炉子状态。
     * <p>
     * 落盘的是"时刻"（{@code burnEnd}/{@code cookStart}/{@code cookDecayStart}）与总量，
     * 当前进度（{@link #remainingBurn}/{@link #cookProgress}）都是<b>现算</b>的，不落盘。
     * </p>
     */
    public static final class State {
        public ItemStack input = ItemStack.EMPTY;
        public ItemStack fuel = ItemStack.EMPTY;
        /** 产物格里的东西（只在"产物放格子"模式下用；"直接进袋子"模式下这里永远是空的） */
        public ItemStack output = ItemStack.EMPTY;
        /** 产物去处：true = 放产物格（默认），false = 直接进储物空间 */
        public boolean toSlot = true;
        /** 这格燃料烧到哪个游戏时刻（0 = 没在烧） */
        public long burnEnd;
        /** 这格燃料总共能烧多久 */
        public int burnTotal;
        /** 已经攒下的烧炼 tick（不含当前这一段） */
        public long cookDone;
        /** 当前这段从哪个游戏时刻开始烧（0 = 没在烧） */
        public long cookStart;
        /** 没火之后从哪个游戏时刻开始回退（0 = 没在回退） */
        public long cookDecayStart;
        /** 当前物品需要的总时长 */
        public int cookTotal;
        /** 正在烧的东西 */
        public ItemStack cookTarget = ItemStack.EMPTY;
        /** 攒着的经验 */
        public float xp;
    }

    // ============================================================
    // 进度换算（都不落盘，谁需要谁现算）
    // ============================================================

    /** 当前这格燃料还剩多少 tick（0 = 没在烧） */
    public static int remainingBurn(State st, long now) {
        if (st.burnEnd <= now) return 0;
        long left = st.burnEnd - now;
        return (int) Math.min(left, Integer.MAX_VALUE);
    }

    /** 正在烧吗 */
    public static boolean isBurning(State st, long now) {
        return st.burnEnd > now;
    }

    /**
     * 当前物品已经烧了多少 tick：{@code 已攒下的 + 这一段已经烧的 - 没火期间回退的}（原版回退速度 2/tick）。
     * 夹在 {@code [0, cookTotal]}。
     */
    public static long cookProgress(State st, long now) {
        long p = st.cookDone;
        if (st.cookStart > 0 && now > st.cookStart) {
            p += now - st.cookStart;
        }
        if (st.cookDecayStart > 0 && now > st.cookDecayStart) {
            p -= 2L * (now - st.cookDecayStart);
        }
        if (p < 0) p = 0;
        if (st.cookTotal > 0 && p > st.cookTotal) p = st.cookTotal;
        return p;
    }

    /** 清空"这一件"的进度 */
    private static void resetCook(State st) {
        st.cookDone = 0;
        st.cookStart = 0;
        st.cookDecayStart = 0;
        st.cookTotal = 0;
        st.cookTarget = ItemStack.EMPTY;
    }

    // ============================================================
    // 袋子 NBT 读写
    // ============================================================

    /**
     * 只读地看一眼自定义数据。
     * <p>
     * 刻意不复制标签：本地模式的袋子 NBT 里装着全部页，每 tick 复制一次太浪费；
     * 而 {@code getUnsafe()} 拿到的实时标签在这里只读不改。
     * </p>
     */
    private static CompoundTag peek(ItemStack bag) {
        if (bag == null || bag.isEmpty()) return null;
        CustomData data = bag.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return null;
        return data.getUnsafe();
    }

    /** 读炉子状态（没有炉子数据时返回空状态） */
    public static State read(ItemStack bag, HolderLookup.Provider registries) {
        State st = new State();
        CompoundTag root = peek(bag);
        if (root == null) return st;
        st.input = readStack(root.getCompound(TAG_INPUT), registries);
        st.fuel = readStack(root.getCompound(TAG_FUEL), registries);
        st.output = readStack(root.getCompound(TAG_OUTPUT), registries);
        // 缺省 = 放产物格（原版熔炉那样），只有明确写成 false 才是"直接进袋子"
        st.toSlot = !root.contains(TAG_TO_SLOT, 1) || root.getBoolean(TAG_TO_SLOT);
        st.burnEnd = root.getLong(TAG_BURN_END);
        st.burnTotal = root.getInt(TAG_BURN_TOTAL);
        st.cookDone = root.getLong(TAG_COOK_DONE);
        st.cookStart = root.getLong(TAG_COOK_START);
        st.cookDecayStart = root.getLong(TAG_COOK_DECAY);
        st.cookTotal = root.getInt(TAG_COOK_TOTAL);
        st.cookTarget = readStack(root.getCompound(TAG_COOK_TARGET), registries);
        st.xp = root.getFloat(TAG_XP);
        return st;
    }

    /** 写炉子状态（只替换炉子自己的键，Pages/FluidPages/Tray* 都不动） */
    public static void write(ItemStack bag, State st, HolderLookup.Provider registries) {
        CompoundTag root = ItemNbt.copyForEdit(bag);
        root.put(TAG_INPUT, writeStack(st.input, registries));
        root.put(TAG_FUEL, writeStack(st.fuel, registries));
        root.put(TAG_OUTPUT, writeStack(st.output, registries));
        root.putBoolean(TAG_TO_SLOT, st.toSlot);
        root.putLong(TAG_BURN_END, Math.max(0, st.burnEnd));
        root.putInt(TAG_BURN_TOTAL, Math.max(0, st.burnTotal));
        root.putLong(TAG_COOK_DONE, Math.max(0, st.cookDone));
        root.putLong(TAG_COOK_START, Math.max(0, st.cookStart));
        root.putLong(TAG_COOK_DECAY, Math.max(0, st.cookDecayStart));
        root.putInt(TAG_COOK_TOTAL, Math.max(0, st.cookTotal));
        root.put(TAG_COOK_TARGET, writeStack(st.cookTarget, registries));
        root.putFloat(TAG_XP, st.xp);
        ItemNbt.setTag(bag, root);
    }

    public static ItemStack getSlot(State st, int slot) {
        if (slot == SLOT_FUEL) return st.fuel;
        if (slot == SLOT_OUTPUT) return st.output;
        return st.input;
    }

    public static void setSlot(State st, int slot, ItemStack stack) {
        ItemStack s = stack == null ? ItemStack.EMPTY : stack;
        if (slot == SLOT_FUEL) {
            st.fuel = s;
        } else if (slot == SLOT_OUTPUT) {
            st.output = s;
        } else {
            st.input = s;
        }
    }

    private static ItemStack readStack(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.parseOptional(registries, tag);
    }

    private static CompoundTag writeStack(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        Tag tag = stack.saveOptional(registries);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /**
     * 切换产物去处（服务端）。true = 放产物格（默认），false = 直接进储物空间。
     * <p>
     * 切成"直接进袋子"时，格子里已有的产物会先收进储物空间（免得卡在格子里不出来）。
     * </p>
     */
    public static void setToSlot(ServerPlayer player, ItemStack bag, boolean toSlot,
                                HolderLookup.Provider registries) {
        State st = read(bag, registries);
        if (st.toSlot == toSlot) return;
        if (!toSlot && !st.output.isEmpty()) {
            com.zzq.survival_toolbox.screen.PocketDimensionMenu
                    .depositToOpenMenu(player, bag, st.output.copy());
            st.output = ItemStack.EMPTY;
        }
        st.toSlot = toSlot;
        write(bag, st, registries);
    }

    /** 取走产物格里的东西后：把攒着的经验给玩家（原版熔炉也是取产物时才给经验） */
    public static void awardXp(ServerPlayer player, ItemStack bag, HolderLookup.Provider registries) {
        State st = read(bag, registries);
        int whole = (int) st.xp;
        if (whole <= 0) return;
        player.giveExperiencePoints(whole);
        st.xp -= whole;
        write(bag, st, registries);
    }

    /**
     * 袋子需不需要每 tick 推进：只有"有燃料在烧"或"输入格与燃料格都有东西"时才需要。
     * <p>
     * 这个判断每 tick、每个物品栏格子都会被问一次，所以只看键、不解析物品。
     * </p>
     */
    public static boolean mayBeActive(ItemStack bag) {
        CompoundTag root = peek(bag);
        if (root == null) return false;
        if (root.getLong(TAG_BURN_END) > 0) return true;
        return !root.getCompound(TAG_INPUT).isEmpty() && !root.getCompound(TAG_FUEL).isEmpty();
    }

    /**
     * 推进一次炉子（仅服务端，每 tick 调一次）。规则照着原版 {@code AbstractFurnaceBlockEntity#serverTick}：
     * 先烧燃料、再推进烹饪；没燃料时进度每 tick 回退 2；烧好按产物去处处理并给经验。
     * <p>
     * 只有"状态切换"才写袋子 NBT（点火、烧完一件、进度重置、开始/结束烧炼），进度本身不写 —— 见类注释。
     * </p>
     *
     * @param player 袋子所在的玩家（给经验、判定共享/本地存储都用他）
     * @param bag    袋子
     * @return 这次有没有写盘（调用方基本不关心）
     */
    public static boolean tick(ServerPlayer player, ItemStack bag) {
        if (player == null || player.level().isClientSide) return false;
        net.minecraft.world.level.Level level = player.level();
        long now = level.getGameTime();
        HolderLookup.Provider registries = level.registryAccess();
        State st = read(bag, registries);
        if (st.input.isEmpty() && st.fuel.isEmpty() && st.burnEnd <= now && st.cookStart == 0
                && st.cookDone <= 0) {
            return false;
        }

        boolean persist = false;

        // 0) 换物品就重置进度（原版每 tick 比对配方，换了就重来）
        if ((st.cookTotal > 0 || st.cookDone > 0 || st.cookStart > 0)
                && (st.input.isEmpty() || !PocketStorageHelper.sameItem(st.input, st.cookTarget))) {
            resetCook(st);
            persist = true;
        }

        SmeltingRecipe recipe = findRecipe(level, st.input);
        boolean canSmelt = recipe != null;
        if (canSmelt && st.cookTotal <= 0) {
            st.cookTotal = recipe.getCookingTime();
            ItemStack target = st.input.copy();
            target.setCount(1);
            st.cookTarget = target;
            persist = true;
        } else if (!canSmelt && st.cookTotal > 0) {
            resetCook(st);
            persist = true;
        }

        // 1) 燃料：先看这格烧完没有，没在烧就试着点着新的一格
        if (st.burnEnd != 0 && st.burnEnd <= now) {
            st.burnEnd = 0;
            persist = true;
        }
        boolean burning = st.burnEnd > now;
        if (!burning && canSmelt && !st.fuel.isEmpty()) {
            int burnTime = st.fuel.getBurnTime(RecipeType.SMELTING);
            if (burnTime > 0) {
                ItemStack fuelLeft = st.fuel.copy();
                fuelLeft.shrink(1);
                st.fuel = fuelLeft.isEmpty() ? ItemStack.EMPTY : fuelLeft;
                st.burnEnd = now + burnTime;
                st.burnTotal = burnTime;
                burning = true;
                persist = true; // 燃料已经扣掉了 → 必须落盘
            }
        }

        // 2) 烹饪
        if (burning && canSmelt) {
            if (st.cookStart == 0) {
                // 从"暂停/回退"切回"在烧"：先把已经攒的进度结算下来，再从这一刻开始计时
                st.cookDone = cookProgress(st, now);
                st.cookDecayStart = 0;
                st.cookStart = now;
                persist = true;
            }
            if (cookProgress(st, now) >= st.cookTotal) {
                if (finishOne(player, bag, st, recipe, registries)) {
                    // 烧完一件：下一件从头开始（火还在、输入还有的话就从这一刻接着烧）
                    st.cookDone = 0;
                    st.cookDecayStart = 0;
                    if (st.input.isEmpty()) {
                        st.cookStart = 0;
                        st.cookTotal = 0;
                        st.cookTarget = ItemStack.EMPTY;
                    } else {
                        st.cookStart = now;
                    }
                    persist = true;
                } else {
                    // 产物格满了/是别的东西：停在这一件"已完成"的状态，等玩家取走再继续
                    st.cookDone = st.cookTotal;
                    st.cookStart = 0;
                    persist = true;
                }
            }
        } else if (st.cookStart > 0) {
            // 没在烧了（燃料烧尽 / 输入没了）：结算进度，并按原版规则开始"每 tick 回退 2"
            st.cookDone = cookProgress(st, now);
            st.cookStart = 0;
            st.cookDecayStart = now;
            persist = true;
        }

        if (persist) write(bag, st, registries);
        return persist;
    }

    /** 查原版熔炼配方（输入为空 / 没配方时返回 null）；两端都能用，界面预览也走它 */
    public static SmeltingRecipe findRecipe(net.minecraft.world.level.Level level, ItemStack input) {
        if (level == null || input == null || input.isEmpty()) return null;
        // 1.21.1 的 SmeltingRecipe 是 Recipe<SingleRecipeInput>，原版熔炉自己查表用的也是
        // SingleRecipeInput（只喂输入格那一件），这里照做
        SingleRecipeInput recipeInput = new SingleRecipeInput(input.copyWithCount(1));
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, recipeInput, level)
                .map(RecipeHolder::value)
                .orElse(null);
    }

    /**
     * 完成一个物品：产物按当前模式处理（放产物格 / 直接进储物空间）、消耗 1 个输入、给经验。
     * <p>
     * "放格子"时如果格子里已有别的东西、或者同种已经堆满 64 → 这一件先不做完（下 tick 再试，东西不会丢），
     * 和原版熔炉产物格满了就停火是一个道理。
     * </p>
     *
     * @return 是否真的完成了
     */
    private static boolean finishOne(ServerPlayer player, ItemStack bag, State st,
                                     SmeltingRecipe recipe, HolderLookup.Provider registries) {
        ItemStack result = recipe.getResultItem(registries).copy();
        if (result.isEmpty()) return true; // 配方没产物（理论上不会）：当作完成，避免卡死

        if (st.toSlot) {
            // 产物放格子里（默认，和原版熔炉一样）
            if (st.output.isEmpty()) {
                st.output = result;
            } else if (PocketStorageHelper.sameItem(st.output, result)
                    && st.output.getCount() + result.getCount() <= st.output.getMaxStackSize()) {
                st.output = st.output.copy();
                st.output.grow(result.getCount());
            } else {
                return false; // 格子满了/是别的东西：等玩家取走再继续
            }
        } else {
            // 直接进储物空间：必须走"当时开着的次元袋界面"的入库口，
            // 界面里存着一份页缓存，直接写袋子 NBT 会被下一次保存整份覆盖（东西就没了）
            long left = com.zzq.survival_toolbox.screen.PocketDimensionMenu
                    .depositToOpenMenu(player, bag, result);
            if (left > 0) return false;
        }

        ItemStack inputLeft = st.input.copy();
        inputLeft.shrink(1);
        st.input = inputLeft.isEmpty() ? ItemStack.EMPTY : inputLeft;
        // 经验：照原版按配方给；"放格子"模式下攒着，等玩家取产物时再给（原版也是取的时候给）
        st.xp += recipe.getExperience();
        if (!st.toSlot) {
            int whole = (int) st.xp;
            if (whole > 0) {
                player.giveExperiencePoints(whole);
                st.xp -= whole;
            }
        }
        return true;
    }
}
