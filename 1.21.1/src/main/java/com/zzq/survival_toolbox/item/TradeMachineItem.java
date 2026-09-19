package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.screen.TradeMachineMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 交易机（手机外观）
 * <p>
 * 右键：打开交易机界面（一开始是空的）；shift+右键村民/流浪商人：把对方的报价记下来
 * （更实惠的会覆盖同一项里不实惠的那条，规则见 {@code util/TradeMachineData}）。
 * </p>
 */
public class TradeMachineItem extends Item {

    public TradeMachineItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !stack.isEmpty()) {
            // 开的**就是原版村民交易界面**（列表 + 两个输入格 + 箭头 + 产物 + 物品栏），
            // 只把标题换成"交易机"；报价来自记录（见 TradeMachineMerchant）。
            com.zzq.survival_toolbox.util.TradeMachineMerchant merchant =
                    new com.zzq.survival_toolbox.util.TradeMachineMerchant(serverPlayer, stack);
            // ⚠️ 必须先把"正在交易的玩家"设上：原版 MerchantMenu#stillValid 判的就是
            // merchant.getTradingPlayer() == player；不设的话界面会开一下立刻自己关掉（实测出现过）。
            merchant.setTradingPlayer(serverPlayer);
            merchant.openTradingScreen(serverPlayer,
                    Component.translatable("container.zzq_survival_toolbox.trade_machine"),
                    0);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 悬浮提示（需求："除了显示名字再加个提示"）：
     * 把两件事写清楚 —— 右键打开、Shift+右键村民/流浪商人复制交易列表。
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.trade_machine"));
    }
}