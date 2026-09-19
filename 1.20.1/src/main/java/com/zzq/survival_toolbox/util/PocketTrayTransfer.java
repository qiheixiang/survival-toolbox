package com.zzq.survival_toolbox.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 托盘与容器方块的交互（手持袋子 + Shift + 右键方块）
 * <p>
 * <b>送出</b>（托盘 → 容器）：从托盘第 1 格起逐个判断，容器放得下就放并从托盘扣掉，
 * 放不下的<b>跳过保留</b>；只动托盘，不动存储页。
 * 物品走 {@code IItemHandler}，流体先走方块的 {@code IFluidHandler}；
 * 普通箱子没有流体接口，就遍历箱内格子里的桶/流体罐来灌装（容器形态变化写回原格）。
 * </p>
 * <p>
 * <b>纳入</b>（容器 → 存储）：方块的流体接口抽干、箱内桶/罐倒空，再逐格把物品收走；
 * 一律走 {@link PocketStorageHelper#quickDeposit(Player, ItemStack, ItemStack)}
 * / {@link PocketStorageHelper#quickDepositFluid(Player, ItemStack, FluidStack)}，
 * 因此<b>严格按袋子当前的共享/本地模式</b>写入，绝不进托盘。
 * </p>
 * <p>
 * 只操作容器与袋子 NBT，不碰玩家背包（除非容器替换失败需要把容器丢给玩家，以免物品消失）。
 * 公共类，不得引用任何客户端类型（专用服务器会上这个类）。
 * </p>
 * <p>
 * <b>1.20.1 的能力查询差异</b>：Forge 没有"方块级能力"通用 API（那是 1.21 的
 * {@code level.getCapability(...)}），只能先取 {@link BlockEntity} 再问它要能力；
 * 普通箱子的 {@code IItemHandler} 由 Forge 给容器类方块实体打的补丁提供，
 * 所以箱子的物品接口照常可用。流体仍优先用 {@link FluidUtil#getFluidHandler}，
 * 它能额外处理流体方块等"非方块实体"的情况。
 * </p>
 */
public final class PocketTrayTransfer {

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** 抽干流体接口时的循环上限（防某些实现"抽了但没少"导致死循环） */
    private static final int DRAIN_LOOP_LIMIT = 4096;

    private PocketTrayTransfer() {
    }

    /**
     * 这一格是不是"能被吸走的流体源"：必须是**整格流体方块**且是静止的源。
     * <p>
     * 水/岩浆在世界里没有物品/流体接口（{@link #hasTarget} 为 false），所以这条单开一个判断。
     * 只认 {@link LiquidBlock}：水logged 的台阶/栅栏/海草之类不动它 ——
     * 原版桶也只在方块实现了 {@code BucketPickup}（= LiquidBlock）时才舀得起来，
     * 否则会连方块一起销毁、造成物品丢失。流动的流体（{@code isSource() == false}）同样不吸。
     * </p>
     */
    public static boolean isFluidSource(Level level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) return false;
        FluidState state = level.getFluidState(pos);
        return !state.isEmpty() && state.isSource();
    }

    /**
     * 玩家准星看到的流体**源**方块（没看到就 null）。
     * <p>
     * 为什么要自己再打一条射线：**方块射线不吃流体**（{@code ClipContext.Fluid.NONE}），
     * 玩家对着水面时 {@code event.getPos()} 拿到的是"水后面那个方块"（湖底/墙），
     * 开阔水面更是直接什么都没打到（MISS）。这里照原版桶的做法用"认流体"的射线再找一次
     * （{@code ClipContext.Fluid.SOURCE_ONLY}，**不是** ANY —— 见下面方法里的说明），
     * 命中后再过一遍 {@link #isFluidSource}：流动的水/岩浆不算，只认静止的源（这条判定是双保险）。
     * </p>
     * <p>
     * 取距离用的是 Forge 的 {@code Player#getBlockReach()}（1.20.1 没有
     * {@code BLOCK_INTERACTION_RANGE} 属性，那是 1.20.5 才有的东西）。
     * </p>
     */
    public static BlockPos fluidSourceInSight(Player player) {
        // ⚠️ 射线必须用 **ClipContext.Fluid.SOURCE_ONLY**（原版桶就是这么干的：
        //    Item#getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY)）。
        //    用 Fluid.ANY 的话射线会先撞到"流动的水"，于是"隔着流水对着源头"就吸不到 ——
        //    实测：同样位置桶能舀、本模组不行，差别就在这一个参数：
        //    SOURCE_ONLY 会**无视流动流体直接穿过去**打到源头方块。
        double reach = player.getBlockReach();
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = player.level().clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        return isFluidSource(player.level(), pos) ? pos : null;
    }

    /**
     * 把这一格流体源吸进袋子（手持袋子 + Shift + 右键水/岩浆源）。
     * <p>
     * 一格 = {@link FluidType#BUCKET_VOLUME}（1000 mB），和用桶舀一样：**先写进袋子、成了再清方块**，
     * 顺序反了万一写失败就凭空少掉一格。和托盘方向无关（这是"取"，不是送出/纳入）。
     * 只在服务端调用（客户端只负责取消事件）。
     * </p>
     *
     * @return 是否真的吸走了一格
     */
    public static boolean scoopFluidSource(Player player, ItemStack bag, Level level, BlockPos pos) {
        if (level.isClientSide() || !isFluidSource(level, pos)) return false;
        FluidState state = level.getFluidState(pos);
        FluidStack stack = new FluidStack(state.getType(), FluidType.BUCKET_VOLUME);
        // 袋子容量无限，这一写本该必成功；但**回读确认**之后才敢清方块——
        // 万一写入没落地就把水源方块删了，等于凭空少掉一格（老写法就是这个顺序，且完全不看结果）。
        long before = PocketStorageHelper.fluidAmountInStorage(player, bag, stack);
        PocketStorageHelper.quickDepositFluid(player, bag, stack);
        long stored = PocketStorageHelper.fluidAmountInStorage(player, bag, stack) - before;
        if (stored < FluidType.BUCKET_VOLUME) {
            LOG.warn("[次元袋] 吸水回读确认失败（{} 只进去 {} mB），方块保持原样，模式={}",
                    stack.getFluid(), stored, PocketStorageHelper.isShared(bag) ? "共享" : "本地");
            player.displayClientMessage(Component.translatable(
                    "message.zzq_survival_toolbox.pocket.store_failed"), true);
            return false;
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        // 原版桶舀水的音效/粒子就是这个游戏事件
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        player.displayClientMessage(Component.translatable("message.zzq_survival_toolbox.pocket.scoop",
                stack.getDisplayName(), PocketStorageHelper.formatFluidAmount(stack.getAmount())), true);
        return true;
    }

    /** 目标方块的物品接口（没有就 null；face 可能为 null = 无方向查询） */
    private static IItemHandler itemHandlerAt(Level level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
    }

    /** 目标方块的流体接口（没有就 null） */
    private static IFluidHandler fluidHandlerAt(Level level, BlockPos pos, Direction face) {
        return FluidUtil.getFluidHandler(level, pos, face).orElse(null);
    }

    /** 目标方块有没有可交互的接口（没有就不接管这次右键，保持原版行为） */
    public static boolean hasTarget(Level level, BlockPos pos, Direction face) {
        return itemHandlerAt(level, pos, face) != null || fluidHandlerAt(level, pos, face) != null;
    }

    /**
     * 按袋子里的托盘方向执行一次交互。
     *
     * @param player 玩家（服务端）
     * @param bag    手上的次元袋
     * @param level  世界（服务端）
     * @param pos    目标方块
     * @param face   点击面
     */
    public static void run(Player player, ItemStack bag, Level level, BlockPos pos, Direction face) {
        if (PocketTrayStorage.isOut(bag)) {
            sendOut(player, bag, level, pos, face);
        } else {
            takeIn(player, bag, level, pos, face);
        }
    }

    // ============================================================
    // 送出方向 + 对着"既不是容器、也不是流体源"的东西：往世界里放一格液体
    // ============================================================

    /** 托盘里"唯一的那种液体"（没有液体、或者有两种以上 → null）；数量 = 该种液体总量 */
    private static FluidStack singleTrayFluid(PocketTrayStorage.Tray tray) {
        FluidStack found = null;
        long total = 0L;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            PocketStorageHelper.FluidEntry entry = tray.fluids.get(i);
            if (entry == null) continue;
            FluidStack one = entry.toStack(1);
            if (one.isEmpty()) continue;
            if (found == null) {
                found = one;
            } else if (!found.isFluidEqual(one)) {
                return null;                 // 托盘里不止一种液体 → 不知道该放哪种，本次不接管
            }
            total += entry.amount();
        }
        if (found == null || total < FluidType.BUCKET_VOLUME) return null;
        found.setAmount((int) Math.min(total, Integer.MAX_VALUE));
        return found;
    }

    /**
     * 这个位置能不能被这种流体占掉（纯读世界，两端调用结论一致），照原版桶 {@code BucketItem#emptyContents} 的规则：
     * <ul>
     *   <li>只认 {@code FlowingFluid}（原版桶也只认它）；</li>
     *   <li>这种流体必须**有方块形态**：很多模组流体（机械动力那些）根本没有，
     *       {@code createLegacyBlock()} 给的是空气 —— 那种流体只能待在袋子里，不许往世界倒；</li>
     *   <li>目标格必须是空气或**可被替换**的方块（草/雪/流体都行，石头这类不行）——
     *       ⚠️ 空气要单独判：{@code canBeReplaced(Fluid)} 对空气**不一定**是 true，原版也是分开写的；</li>
     *   <li>这一格已经是同种流体的源就不再放（原版桶同规则，放了也是浪费）。</li>
     * </ul>
     */
    private static boolean canPutFluidAt(Level level, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        if (!level.isLoaded(pos)) return false;
        if (!(fluid instanceof FlowingFluid)) return false;
        if (fluid.defaultFluidState().createLegacyBlock().isAir()) return false;
        BlockState target = level.getBlockState(pos);
        if (!target.isAir() && !target.canBeReplaced(fluid)) return false;
        return !level.getFluidState(pos).isSourceOfType(fluid);
    }

    /**
     * <b>纯判断</b>：这次 Shift+右键该不该由"往世界放一格液体"接管。
     * <p>
     * 客户端与服务端都会调用它来决定要不要取消事件，所以这里<b>只能读、不能改</b>，
     * 而且两端必须得出同一个结论（读的是袋子 NBT + 方块状态，两端都有）。
     * 条件：
     * <ol>
     *   <li>托盘方向 = <b>送出</b>；</li>
     *   <li>托盘里<b>只有一种液体</b>，且总量够一格（1000 mB）；</li>
     *   <li>那种液体<b>真能在世界里放下</b>（有方块形态）；</li>
     *   <li>瞄着的方块旁边那个位置可以放（不是石头、不是已经有的同种水源）。</li>
     * </ol>
     * </p>
     */
    public static boolean canPlaceTrayFluid(Level level, ItemStack bag, BlockPos clicked, Direction face) {
        if (face == null) return false;
        FluidStack fluid = singleTrayFluid(PocketTrayStorage.read(bag));
        return fluid != null && canPutFluidAt(level, clicked.relative(face), fluid.getFluid());
    }

    /**
     * 托盘里确实只有一种液体、够一格，但**这种液体没有方块形态**（放不到世界里）。
     * 用来给玩家一句明确提示，而不是"右键没反应"。
     */
    public static boolean trayFluidUnplaceable(Level level, ItemStack bag) {
        FluidStack fluid = singleTrayFluid(PocketTrayStorage.read(bag));
        return fluid != null && fluid.getFluid().defaultFluidState().createLegacyBlock().isAir();
    }

    /**
     * 真正把托盘里的液体放一格到世界里（只在服务端调用）。
     * <p>
     * 顺序：<b>先确认能放，再动方块，最后扣托盘</b> —— 反过来的话，放失败就等于凭空扣掉一格。
     * 一格 = {@link FluidType#BUCKET_VOLUME}（1000 mB），和倒桶一模一样（水源方块 + 音效 + 游戏事件）。
     * </p>
     *
     * @return 是否真的放出去了一格
     */
    public static boolean placeTrayFluid(Player player, ItemStack bag, Level level, BlockPos clicked, Direction face) {
        if (level.isClientSide() || face == null) return false;
        PocketTrayStorage.Tray tray = PocketTrayStorage.read(bag);
        FluidStack fluid = singleTrayFluid(tray);
        if (fluid == null) return false;
        BlockPos target = clicked.relative(face);
        if (!canPutFluidAt(level, target, fluid.getFluid())) return false;
        if (!putFluidBlock(player, level, target, fluid.getFluid())) return false;
        consumeTrayFluid(tray, fluid, FluidType.BUCKET_VOLUME);
        PocketTrayStorage.write(bag, tray);
        player.displayClientMessage(Component.translatable("message.zzq_survival_toolbox.pocket.tray.placed",
                fluid.getDisplayName(), PocketStorageHelper.formatFluidAmount(FluidType.BUCKET_VOLUME)), true);
        return true;
    }

    /**
     * 把一格流体写成世界里的源方块（照原版桶 {@code BucketItem#emptyContents} 的简化实现）。
     * <p>
     * 能走到这里说明 {@link #canPutFluidAt} 已经过了，所以这里只做"真的放"：
     * 顶掉可替换方块（草/雪按原版掉落）→ 写水源方块（flag 11，和原版桶一致）→ 音效 + 游戏事件。
     * 下界那样的"超热维度"里的水按原版处理：滋一声蒸发，不落方块（但这一格确实消耗掉了）。
     * </p>
     */
    private static boolean putFluidBlock(Player player, Level level, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        if (!canPutFluidAt(level, pos, fluid)) return false;
        BlockState target = level.getBlockState(pos);
        boolean replaceable = target.canBeReplaced(fluid);
        // 下界这类超热维度里的水：原版是"滋"一声蒸发（烟 + 火熄灭音效），不落方块
        if (level.dimensionType().ultraWarm() && fluid.is(FluidTags.WATER)) {
            level.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.5D, 0.5D, 0.5D, 0.0D);
            }
            return true;
        }
        if (replaceable && !target.liquid()) {
            level.destroyBlock(pos, true);          // 草/雪这类被顶掉的方块照原版掉落
        }
        level.setBlock(pos, fluid.defaultFluidState().createLegacyBlock(), 11);
        level.playSound(player, pos, fluid.is(FluidTags.LAVA)
                ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        return true;
    }

    /** 从托盘里扣掉某种液体的若干 mB（从第一格有这种液体的格子起扣） */
    private static void consumeTrayFluid(PocketTrayStorage.Tray tray, FluidStack fluid, long amount) {
        long left = amount;
        for (int i = 0; i < PocketTrayStorage.SLOTS && left > 0; i++) {
            PocketStorageHelper.FluidEntry entry = tray.fluids.get(i);
            if (entry == null) continue;
            FluidStack one = entry.toStack(1);
            if (one.isEmpty() || !one.isFluidEqual(fluid)) continue;
            long take = Math.min(left, entry.amount());
            long rest = entry.amount() - take;
            tray.fluids.set(i, rest <= 0 ? null : new PocketStorageHelper.FluidEntry(entry.stackTag(), rest));
            left -= take;
        }
    }

    // ============================================================
    // 送出：托盘 → 容器
    // ============================================================

    private static void sendOut(Player player, ItemStack bag, Level level, BlockPos pos, Direction face) {
        IItemHandler items = itemHandlerAt(level, pos, face);
        IFluidHandler fluids = fluidHandlerAt(level, pos, face);
        PocketTrayStorage.Tray tray = PocketTrayStorage.read(bag);

        int movedItems = 0;
        long movedFluid = 0L;

        // 1) 物品：从第 1 格起，容器放得下就放（部分装下也只扣装下的部分），放不下的留在托盘
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            ItemStack stack = tray.items.get(i);
            if (stack.isEmpty()) continue;
            if (items == null) continue;
            int before = stack.getCount();
            ItemStack rest = ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
            int inserted = before - rest.getCount();
            if (inserted <= 0) continue;
            movedItems += inserted;
            if (inserted >= before) {
                tray.items.set(i, ItemStack.EMPTY);
            } else {
                ItemStack left = stack.copy();
                left.setCount(before - inserted);
                tray.items.set(i, left);
            }
        }

        // 2) 流体：先给方块自己的流体接口，还有剩就灌进箱子里的桶/罐
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            PocketStorageHelper.FluidEntry entry = tray.fluids.get(i);
            if (entry == null) continue;
            long left = entry.amount();
            if (fluids != null) {
                int accepted = fluids.fill(entry.toStack((int) Math.min(left, Integer.MAX_VALUE)),
                        IFluidHandler.FluidAction.EXECUTE);
                if (accepted > 0) {
                    left -= accepted;
                    movedFluid += accepted;
                }
            }
            // ⚠️ 对面没有流体接口时**不再**灌进箱子里的空桶/空罐（只送进流体接口、不灌桶）：
            //    送不出去的液体就留在托盘里，不碰箱子里的任何物品。
            tray.fluids.set(i, left <= 0 ? null : new PocketStorageHelper.FluidEntry(entry.stackTag(), left));
        }

        PocketTrayStorage.write(bag, tray);

        if (movedItems == 0 && movedFluid == 0L) {
            player.displayClientMessage(Component.translatable("message.zzq_survival_toolbox.pocket.tray.nothing_out"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.zzq_survival_toolbox.pocket.tray.sent",
                    movedItems, fluidText(movedFluid)), true);
        }
    }


    // ============================================================
    // 纳入：容器 → 存储（严格按当前共享/本地模式）
    // ============================================================

    private static void takeIn(Player player, ItemStack bag, Level level, BlockPos pos, Direction face) {
        IItemHandler items = itemHandlerAt(level, pos, face);
        IFluidHandler fluids = fluidHandlerAt(level, pos, face);

        int movedItems = 0;
        long movedFluid = 0L;
        boolean refunded = false;

        // 1) 方块自己的流体接口：抽干（袋子容量无限，永远收得下）
        //    ⚠️ 抽出来之后同样要**回读确认**：确认真的进了袋子才算数，没进就把差额倒回方块自己的接口。
        //       老写法是无条件 movedFluid += drained（连 quickDepositFluid 的返回值都不看），
        //       一旦写入没落地，玩家看到的就是"容器里的液体没了、袋子两边都没有"。
        if (fluids != null) {
            for (int guard = 0; guard < DRAIN_LOOP_LIMIT; guard++) {
                FluidStack drained = fluids.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                if (drained.isEmpty()) break;
                long want = drained.getAmount();
                long before = PocketStorageHelper.fluidAmountInStorage(player, bag, drained);
                PocketStorageHelper.quickDepositFluid(player, bag, drained);
                long stored = PocketStorageHelper.fluidAmountInStorage(player, bag, drained) - before;
                stored = Math.max(0L, Math.min(stored, want));
                if (stored < want) {
                    long missing = want - stored;
                    FluidStack give = drained.copy();
                    give.setAmount((int) Math.min(missing, Integer.MAX_VALUE));
                    int accepted = fluids.fill(give, IFluidHandler.FluidAction.EXECUTE);
                    if (accepted < missing) {
                        LOG.warn("[次元袋] 托盘纳入：流体回读确认失败且倒不回去，可能丢 {} mB（{}），模式={}",
                                missing - Math.max(0, accepted), drained.getFluid(),
                                PocketStorageHelper.isShared(bag) ? "共享" : "本地");
                        refunded = true;
                    }
                }
                movedFluid += stored;
                // 一点都没进袋子（也倒不回去的话更糟）：别再循环抽了，避免把容器抽干却什么都没存下
                if (stored <= 0) break;
            }
        }

        // 2) 容器格子里的桶/罐不再倒空（否则机械动力的巧克力桶/蜂蜜桶被纳入之后会变成袋子里的
        //    巧克力液体/蜂蜜液体）。新规则：桶拿过来还是桶，只有真正的流体才算流体——
        //    流体只认方块自己的流体接口（上面第 1 步），格子里的桶/罐一律当物品收走（下面第 3 步）。

        // 3) 剩下的物品逐格收进袋子存储（含上一步倒空的桶）
        //    ⚠️ 这里的"到底进没进袋子"**只认回读确认**（入库前后各数一次数量，差值才是真进去的）。
        //       quickDeposit 的返回值恒为 0（装不下会自动新建一页），拿它判断等于完全信任写入：
        //       只要有一条路径没写进去，玩家看到的就是"箱子取走了、袋子两边都看不到"（实测）。
        //       没进去的部分按 原格 → 玩家背包 → 丢出 的顺序还给玩家，绝不静默丢弃。
        if (items != null) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack inSlot = items.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                ItemStack extracted = items.extractItem(slot, inSlot.getCount(), false);
                if (extracted.isEmpty()) continue;
                ItemStack want = extracted.copy();
                long before = PocketStorageHelper.countInStorage(player, bag, want);
                PocketStorageHelper.quickDeposit(player, bag, want.copy());
                long stored = PocketStorageHelper.countInStorage(player, bag, want) - before;
                int storedInt = (int) Math.max(0L, Math.min(stored, want.getCount()));
                movedItems += storedInt;
                int missing = want.getCount() - storedInt;
                if (missing > 0) {
                    LOG.warn("[次元袋] 托盘纳入：入库后回读确认少了 {} 个（{}），已退回原容器/背包，模式={}",
                            missing, want.getItem(),
                            PocketStorageHelper.isShared(bag) ? "共享" : "本地");
                    ItemStack back = want.copy();
                    back.setCount(missing);
                    ItemStack rest = items.insertItem(slot, back, false);
                    if (!rest.isEmpty() && !player.getInventory().add(rest)) {
                        player.drop(rest, false);
                    }
                    refunded = true;
                }
            }
        }

        if (movedItems == 0 && movedFluid == 0L) {
            player.displayClientMessage(Component.translatable("message.zzq_survival_toolbox.pocket.tray.nothing_in"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.zzq_survival_toolbox.pocket.tray.taken",
                    movedItems, fluidText(movedFluid)), true);
        }
        // 有东西没能进袋子（已经退回原处/背包了）：明确说一句，别让玩家以为"取走了却没进袋子"
        if (refunded) {
            player.displayClientMessage(Component.translatable(
                    "message.zzq_survival_toolbox.pocket.store_failed"), true);
        }
    }

    /** 流体数量文案（0 = 显示成"—"，避免出现"流体 0 mB"） */
    private static String fluidText(long amount) {
        return amount <= 0 ? "-" : PocketStorageHelper.formatFluidAmount(amount);
    }
}
