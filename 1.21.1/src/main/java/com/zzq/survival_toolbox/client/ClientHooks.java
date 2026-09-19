package com.zzq.survival_toolbox.client;

import com.zzq.survival_toolbox.client.gui.XraySelectorScreen;
import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端行为入口（供公共类安全调用）
 * <p>
 * 公共类（物品、网络包等）会被专用服务器加载，而专用服务器上不存在
 * {@code net.minecraft.client.*}。因此凡是"只在客户端执行"的行为都集中到这里，
 * 并且<b>这些方法的签名里不能出现客户端类型</b>：服务器在链接公共类时会解析被调用方法的
 * 描述符，一旦描述符里带客户端类（例如 {@code setScreen(Screen)}），就会直接崩服
 * （Attempted to load class ... for invalid dist DEDICATED_SERVER）。
 * 公共类只做 {@code level.isClientSide} 之类的判断后调用这里。
 * </p>
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    /**
     * 打开透视眼镜的方块白名单界面。
     *
     * @param player 使用眼镜的玩家
     * @param hand   手持的那只手（菜单只改这一只手上那副眼镜的列表）
     */
    public static void openXraySelector(Player player, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player) {
            mc.setScreen(new XraySelectorScreen(hand));
        }
    }

    /**
     * 打开原版<b>创造模式物品栏</b>（"创造口袋"右键用；生存也能真取物，见
     * {@code MultiPlayerGameModeMixin} 与 {@code ServerGamePacketListenerImplMixin}）。
     * <p>
     * ⚠️ 只在客户端调用（签名里不出现客户端类型，见类注释）。
     * 界面是原版那一整套（搜索框、分类页、垃圾桶），这里只是把"必须真是创造模式"那道门
     * 对"带着创造口袋的人"打开；取物由服务端按原版创造模式的规则发放。
     * </p>
     */
    public static void openCreativePicker() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        boolean operatorTab = mc.player.isCreative() && mc.options.operatorItemsTab().get();
        mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(
                mc.player, mc.player.level().enabledFeatures(), operatorTab));
    }

    /**
     * 次元袋同步数据到达客户端后写入当前打开的菜单。
     *
     * @param pageOffset  翻页偏移
     * @param currentPage 当前页索引
     * @param pageNames   页名列表
     * @param pageCounts  每页条目数
     * @param slots       显示条目
     * @param fluidMask   存储页哪些格是流体（54 位掩码）
     * @param panel       当前展开的功能页（0 = 全收起，1 = 托盘，2 = 熔炉）
     * @param trayOut     托盘方向（true = 送出）
     * @param trayMask    托盘哪些格是流体（27 位掩码）
     * @param trayAmounts 托盘每格的流体 mB 数量
     * @param trayFluidIcons 托盘流体格的显示栈（桶图标 + 流体本体 + 存量标记；非流体格 = 空栈）
     * @param furnaceBurn 熔炉当前燃料还能烧多少 tick
     * @param furnaceBurnTotal 熔炉当前燃料的总燃烧时长
     * @param furnaceCook 熔炉当前物品的烹饪进度
     * @param furnaceCookTotal 熔炉当前物品需要的总时长
     * @param pageCost    铁砧页原版算出来的经验等级花费
     * @param furnaceToSlot 熔炼页产物去处（true = 放产物格）
     */
    public static void applyPocketDimensionSync(int pageOffset, int currentPage,
                                                java.util.List<String> pageNames,
                                                java.util.List<Integer> pageCounts,
                                                java.util.List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> slots,
                                                boolean shared, long fluidMask,
                                                int panel, boolean trayOut, long trayMask, long[] trayAmounts,
                                                java.util.List<net.minecraft.world.item.ItemStack> trayFluidIcons,
                                                int furnaceBurn, int furnaceBurnTotal,
                                                int furnaceCook, int furnaceCookTotal, int pageCost,
                                                boolean furnaceToSlot) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof PocketDimensionMenu menu) {
            menu.setSyncedData(pageOffset, currentPage, pageNames, pageCounts, slots, shared, fluidMask,
                    panel, trayOut, trayMask, trayAmounts, trayFluidIcons,
                    furnaceBurn, furnaceBurnTotal, furnaceCook, furnaceCookTotal, pageCost, furnaceToSlot);
        }
    }

    /**
     * JEI"+"号可转移性查询的回复到达客户端（见 {@code PocketJeiQueryReplyPacket}）。
     * <p>
     * 只往客户端缓存里写一条答案（键 = 客户端自己算的签名），"能不能点 +"的判定下次就会用上它。
     * 缓存类在 {@code client.compat.jei} 里（依赖 JEI），所以这里整段兜住异常：
     * 万一没装 JEI 之类的情况下走到了这里，也绝不能因为 {@code NoClassDefFoundError}
     * 把客户端带走（那是最难查的一类崩溃）。
     * </p>
     *
     * @param signature 客户端算的缓存键
     * @param available 服务端算出来的答案
     */
    public static void onJeiAvailabilityReply(long signature, boolean available) {
        try {
            com.zzq.survival_toolbox.client.compat.jei.PocketJeiAvailabilityCache.put(signature, available);
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger()
                    .error("[次元袋] 写入 JEI 可转移性缓存失败（忽略这一次答案即可，不影响游戏）", t);
        }
    }
}
