package com.zzq.survival_toolbox.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 透视眼镜
 * <p>
 * 手持时激活透视：白名单之外（非矿石）方块不渲染（墙变透明），白名单方块正常显示；
 * 丢出或切换主手物品后自动恢复正常渲染。
 * Shift+右键打开方块选择菜单，可手动开关任意方块的显示。
 * 效果由 {@link com.zzq.survival_toolbox.util.XrayOreHelper} 与
 * {@link com.zzq.survival_toolbox.client.event.XrayClientHandler} 实现。
 * </p>
 */
public class XrayGogglesItem extends Item {

    public XrayGogglesItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Shift+右键：打开矿透方块选择菜单（纯客户端界面，服务端不执行该分支）
        if (level.isClientSide && player.isSecondaryUseActive()) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == player) {
                mc.setScreen(new com.zzq.survival_toolbox.client.gui.XraySelectorScreen());
            }
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.xray_goggles"));
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.xray_goggles.menu"));
    }
}
