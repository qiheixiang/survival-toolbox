package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.List;

/**
 * 磁铁（吸收）—— 袋子自带的地上物品吸引
 * <p>
 * 需求：开关（开=吸、关=不吸）、范围在界面里可调（{@value #MIN_RANGE}~{@value #MAX_RANGE} 格，
 * {@code -}/{@code +} 按钮）、两种模式（只吸"袋子里已经有的"那种 ↔ 有没有都吸）、
 * 以及黑白名单（黑名单 = 名单里的不吸；白名单 = 只吸名单里的）。
 * 名单里放的是"物品信息"（幽灵条目），不是实物 —— 见 {@code PocketFilterContainer}。
 * </p>
 * <p>
 * 状态存在袋子 NBT 里（键前缀 {@code Mag}），服务端每 tick 推进一次（{@code PocketMagnetTickHandler}）。
 * 吸进来的东西一律走 {@link PocketDimensionMenu#depositToOpenMenu}：
 * 界面开着的时候数据在容器的内存缓存里，绕过缓存直接写袋子 NBT 会把东西弄丢。
 * </p>
 */
public final class PocketMagnet {

    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** 开关：true = 吸 */
    public static final String TAG_ON = "MagOn";
    /** 范围（格） */
    public static final String TAG_RANGE = "MagRange";
    /** 只吸"袋子里已经有的" */
    public static final String TAG_ONLY_EXISTING = "MagOnlyExisting";
    /** 名单方向：true = 白名单（只吸名单里的），false = 黑名单（名单里的不吸） */
    public static final String TAG_WHITE_LIST = "MagWhiteList";
    /** 名单条目（每个是"物品信息"的 NBT，数量恒为 1） */
    public static final String TAG_FILTER = "MagFilter";

    public static final int MIN_RANGE = 3;
    public static final int MAX_RANGE = 11;
    public static final int DEFAULT_RANGE = 5;
    /** 名单格数（和界面里的 9 格一致） */
    public static final int FILTER_SIZE = 18;

    private PocketMagnet() {
    }

    /** 磁铁状态 */
    public static final class State {
        public boolean on;
        public int range = DEFAULT_RANGE;
        /** 默认：只吸袋子里已经有的（两种模式都提供，默认取更保守的那个） */
        public boolean onlyExisting = true;
        public boolean whiteList;
        public final ItemStack[] filter = new ItemStack[FILTER_SIZE];

        public State() {
            for (int i = 0; i < FILTER_SIZE; i++) this.filter[i] = ItemStack.EMPTY;
        }

        public State copy() {
            State s = new State();
            s.on = this.on;
            s.range = this.range;
            s.onlyExisting = this.onlyExisting;
            s.whiteList = this.whiteList;
            for (int i = 0; i < FILTER_SIZE; i++) s.filter[i] = this.filter[i].copy();
            return s;
        }
    }

    // ============================================================
    // 读写（袋子 NBT）
    // ============================================================

    public static State read(ItemStack bag, HolderLookup.Provider registries) {
        State st = new State();
        CompoundTag root = ItemNbt.getTag(bag);
        if (root == null) return st;
        st.on = root.getBoolean(TAG_ON);
        st.range = clampRange(root.contains(TAG_RANGE) ? root.getInt(TAG_RANGE) : DEFAULT_RANGE);
        st.onlyExisting = !root.contains(TAG_ONLY_EXISTING) || root.getBoolean(TAG_ONLY_EXISTING);
        st.whiteList = root.getBoolean(TAG_WHITE_LIST);
        ListTag list = root.getList(TAG_FILTER, Tag.TAG_COMPOUND);
        for (int i = 0; i < FILTER_SIZE && i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                ItemStack one = stack.copy();
                one.setCount(1);
                st.filter[i] = one;
            }
        }
        return st;
    }

    /** 写回（复制出来改、再整份写回：绝不就地改物品里那份标签，见 ItemNbt 的线程安全说明） */
    public static void write(ItemStack bag, State st, HolderLookup.Provider registries) {
        ItemNbt.edit(bag, root -> {
            root.putBoolean(TAG_ON, st.on);
            root.putInt(TAG_RANGE, clampRange(st.range));
            root.putBoolean(TAG_ONLY_EXISTING, st.onlyExisting);
            root.putBoolean(TAG_WHITE_LIST, st.whiteList);
            ListTag list = new ListTag();
            for (int i = 0; i < FILTER_SIZE; i++) {
                ItemStack s = st.filter[i];
                if (s == null || s.isEmpty()) continue;
                ItemStack one = s.copy();
                one.setCount(1);
                Tag tag = one.saveOptional(registries);
                if (tag instanceof CompoundTag compound) list.add(compound);
            }
            root.put(TAG_FILTER, list);
        });
    }

    /** 每 tick 的快速判断：只看"有没有开着"这个键，不解析物品（照熔炉那套省开销的做法） */
    public static boolean mayBeActive(ItemStack bag) {
        CompoundTag root = ItemNbt.getTag(bag);
        return root != null && root.getBoolean(TAG_ON);
    }

    public static int clampRange(int range) {
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, range));
    }

    // ============================================================
    // 判定
    // ============================================================

    /** 这件东西在不在名单里（同物品 + 同组件，忽略数量） */
    public static boolean inFilter(State st, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (ItemStack sample : st.filter) {
            if (sample != null && !sample.isEmpty() && PocketStorageHelper.sameItem(sample, stack)) return true;
        }
        return false;
    }

    /** 名单方向判定：黑名单命中 → 不吸；白名单未命中 → 不吸 */
    public static boolean passesFilter(State st, ItemStack stack) {
        boolean listed = inFilter(st, stack);
        return st.whiteList == listed;
    }

    /** 袋子里是不是已经存着这种物品（"只吸袋里有的"用）；pages 由调用方读一次传进来，不要每件物品各读一遍 */
    public static boolean storageHas(List<PocketStorageHelper.Page> pages, ItemStack stack) {
        if (stack.isEmpty() || pages == null) return false;
        for (PocketStorageHelper.Page page : pages) {
            for (PocketStorageHelper.Entry entry : page.entries) {
                if (entry != null && PocketStorageHelper.sameItem(entry.stack(), stack)) return true;
            }
        }
        return false;
    }

    /** 这件东西现在该不该被吸走 */
    public static boolean accepts(State st, List<PocketStorageHelper.Page> pages, ItemStack stack) {
        if (!st.on || stack.isEmpty()) return false;
        if (!passesFilter(st, stack)) return false;
        if (st.onlyExisting && !storageHas(pages, stack)) return false;
        return true;
    }

    // ============================================================
    // 服务端每 tick
    // ============================================================

    /**
     * 吸一次：把范围内符合条件的地上物品收进袋子。
     * <p>
     * 收不进去的（储物空间不收，例如被拉黑的物品）原样留在地上，不会凭空消失。
     * </p>
     */
    /** 照精妙背包的做法节流：最多 10 tick 扫一次；这一轮什么都没吸到就退避到 40 tick 再试 */
    private static final int COOLDOWN_TICKS = 10;
    private static final int FULL_COOLDOWN_TICKS = 40;
    /** 全局共享的下一次扫描时刻（服务端 tick 计数）：避免每个袋子/每个玩家各扫一遍 */
    private static long nextScanTick = 0L;

    public static void tick(ServerPlayer player, ItemStack bag, HolderLookup.Provider registries) {
        long now = player.level().getGameTime();
        if (now < nextScanTick) return;
        State st = read(bag, registries);
        if (!st.on) return;
        double range = st.range;
        AABB box = player.getBoundingBox().inflate(range);
        List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty() && e.distanceToSqr(player) <= range * range);
        // 读页要解析 NBT：只有"真的有东西可吸 + 开了只吸袋里有的"才读这一次
        // ⚠️ 必须按袋子当前模式读（readPagesForMode）：共享袋子直接 readPages 是读"本地页"，永远读空
        List<PocketStorageHelper.Page> pages = (st.onlyExisting && !items.isEmpty())
                ? PocketStorageHelper.readPagesForMode(player, bag, registries) : List.of();
        boolean pickedAny = false;
        long pickedTotal = 0L;
        ItemStack pickedSample = ItemStack.EMPTY;
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem();
            // ⚠️ 没收的一律**不提示**（需求：可以提示吸了什么，
            //    但没吸到不要每次吸都提示）。以前会在"附近有掉落物但一件都没收"时
            //    弹一句"检查磁铁页"，但"只吸袋里有的"模式下跳过本来就是正常行为，
            //    每隔几秒弹一次纯属噪音 —— 现在只在**真的吸到了**的时候说一句。
            if (!accepts(st, pages, stack)) continue;
            // 别的模组/机械假玩家标记过"别远程搬走"的东西不碰（照精妙背包）
            if (entity.getPersistentData().getBoolean("PreventRemoteMovement")) continue;
            int before = stack.getCount();
            ItemStack copy = stack.copy();
            long left = PocketDimensionMenu.depositToOpenMenu(player, bag, copy);
            if (left <= 0) {
                // ⚠️ 入库之后**读回来确认一次**：读不到就宁可不吸（掉落物留在原地），
                //    也绝不让它"被吸走就没了"（历史问题：曾两次出现"吸了以后袋子里什么都没有"）。
                if (!storedInBag(player, bag, copy, registries)) {
                    LOG.warn("[次元袋] 磁铁入库后从存储里读不回来，掉落物留在原地：{} x{}，模式={}",
                            stack.getItem(), before, PocketStorageHelper.isShared(bag) ? "共享" : "本地");
                    continue;
                }
                entity.discard();
                pickedAny = true;
                pickedTotal += before;
                if (pickedSample.isEmpty()) pickedSample = copy;
            } else if (left < before) {
                // 只收进去一部分：剩下的还留在地上
                ItemStack rest = stack.copy();
                rest.setCount((int) Math.min(left, rest.getMaxStackSize()));
                entity.setItem(rest);
                pickedAny = true;
                pickedTotal += before - (int) left;
                if (pickedSample.isEmpty()) pickedSample = copy;
            }
        }
        if (pickedAny) {
            // 只在**真的吸到了**的时候说一句"收到了哪去"：
            // 不然东西并进已有那堆（数量只多几个）时，玩家会以为丢了。
            // ⚠️ 一件都没吸到就**什么都不提示**（需求：没吸到也不要每次吸都提示）：
            //    以前的"检查磁铁页"提示在"只吸袋里有的"模式下每隔几秒就弹一次，纯属噪音，已删除。
            player.displayClientMessage(Component.translatable(
                    "message.zzq_survival_toolbox.pocket.magnet.picked",
                    PocketStorageHelper.isShared(bag)
                            ? Component.translatable("gui.zzq_survival_toolbox.pocket.mode.shared")
                            : Component.translatable("gui.zzq_survival_toolbox.pocket.mode.local"),
                    pickedTotal, pickedSample.getHoverName()), true);
        }
        nextScanTick = now + (pickedAny ? COOLDOWN_TICKS : FULL_COOLDOWN_TICKS);
    }

    /**
     * 袋子里现在有没有这件东西（**按当前模式**读；磁铁入库后用它确认一次）。
     * <p>
     * 只要"能读到"就算成功：磁铁默认只吸袋里已经有的东西，所以正常情况就是并进已有那一堆，
     * 数量对不上不算失败（不要拿数量作为判据，同一件东西可能被合并到任意一格）。
     * </p>
     */
    private static boolean storedInBag(ServerPlayer player, ItemStack bag, ItemStack want,
                                       HolderLookup.Provider registries) {
        for (PocketStorageHelper.Page page : PocketStorageHelper.readPagesForMode(player, bag, registries)) {
            for (PocketStorageHelper.Entry e : page.entries) {
                if (e != null && PocketStorageHelper.sameItem(e.stack(), want)) return true;
            }
        }
        return false;
    }

    /** 名字（界面提示用；只在需要时调用） */
    public static String describeRange(int range) {
        return clampRange(range) + " 格";
    }

}
