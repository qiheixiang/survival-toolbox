package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.PocketTrayStorage;
import com.zzq.survival_toolbox.util.PocketTrayTransfer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 次元袋托盘与容器的交互入口：手持袋子 + Shift + 右键容器方块
 * <p>
 * 方向由袋子里的托盘方向决定（界面右上"送出/纳入"切换，存在袋子 NBT 里）：
 * <b>送出</b> = 把托盘里的东西塞进容器（放不下的留在托盘）；
 * <b>纳入</b> = 把容器里的东西收进袋子存储（严格按当前共享/本地模式，不进托盘）。
 * 具体逻辑在 {@link PocketTrayTransfer}。
 * </p>
 * <p>
 * 为什么两端都要取消事件：原版 {@code useItemOn} 里潜行会跳过方块自身的交互，
 * 随后调用物品的 {@code useOn}/{@code use}——服务端不取消的话，袋子界面会被打开；
 * 客户端不取消的话，客户端会认为"点击没被消费"，再补一个"使用物品"包上来，
 * 服务端照样把界面弹出来。两端一起取消（并给出 SUCCESS 结果）才干净：
 * 箱子不开、袋子界面也不弹。真正干活只在服务端。
 * </p>
 * <p>
 * 只在<b>事件那只手</b>拿着袋子时接管：主手拿袋子 ✓；副手拿袋子而主手空着 ✓；
 * 副手拿袋子但主手拿着方块时不抢（否则"往箱子上放方块"会失灵）。
 * </p>
 * <p>
 * 目标方块没有任何物品/流体接口时，剩下的行为**跟托盘方向走**（需求）：
 * <ul>
 *   <li><b>纳入</b>：瞄到的方块是流体源（水/岩浆）就吸进袋子（1 格 = 1000 mB、方块变空气，和用桶舀一样）。</li>
 *   <li><b>送出</b>：托盘里只有一种液体、而且那种液体真能在世界里放下时，朝瞄着的那个面**放出一格**
 *       （和倒桶一样：水源方块 + 音效）。这是"袋子里的液体倒回世界"的出口。</li>
 *   <li>两种都不成立就完全不接管，保持原版行为（Shift+右键打开袋子界面）。</li>
 * </ul>
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox")
public class PocketTrayInteractHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || !player.isShiftKeyDown()) return;
        ItemStack bag = event.getItemStack();
        if (bag.isEmpty() || !bag.is(ModItems.POCKET_DIMENSION.get())) return;

        // ① 有接口的方块（箱子/储罐/机器）：按袋子里的托盘方向送出或纳入
        if (PocketTrayTransfer.hasTarget(event.getLevel(), event.getPos(), event.getFace())) {
            // 两端都取消（客户端请只做取消，不做实际转移）
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getLevel().isClientSide()) return;
            PocketTrayTransfer.run(player, bag, event.getLevel(), event.getPos(), event.getFace());
            return;
        }

        boolean out = PocketTrayStorage.isOut(bag);

        // ② **纳入**方向的流体源方块：直接吸进袋子（一格 = 1000 mB，方块变空气，和用桶舀一样）。
        //    ⚠️ 只有"纳入"才吸（需求："装液体跟托盘状态走"）：送出方向下瞄准水面不该偷水。
        //    注意不能用 event.getPos()：方块射线不吃流体，那个位置是"水后面那个方块"；
        //    这里用认流体的射线重新找。
        if (!out) {
            net.minecraft.core.BlockPos fluidPos = PocketTrayTransfer.fluidSourceInSight(player);
            if (fluidPos != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (event.getLevel().isClientSide()) return;
                PocketTrayTransfer.scoopFluidSource(player, bag, event.getLevel(), fluidPos);
                return;
            }
        }

        // ③ **送出**方向：托盘里只有一种液体、且那种液体能在世界里放下 → 朝瞄着的面放一格。
        //    判定是纯读操作，两端都会算出同样的结论，所以可以放心地两端一起取消事件。
        if (out) {
            if (PocketTrayTransfer.canPlaceTrayFluid(event.getLevel(), bag, event.getPos(), event.getFace())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (event.getLevel().isClientSide()) return;
                PocketTrayTransfer.placeTrayFluid(player, bag, event.getLevel(), event.getPos(), event.getFace());
                return;
            }
            // 只有一种液体但它是"没有方块形态"的（机械动力那类）：给出提示，不要让玩家对着空气右键
            if (PocketTrayTransfer.trayFluidUnplaceable(event.getLevel(), bag)) {
                if (event.getLevel().isClientSide()) return;
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.zzq_survival_toolbox.pocket.tray.unplaceable"), true);
            }
        }
    }
}
