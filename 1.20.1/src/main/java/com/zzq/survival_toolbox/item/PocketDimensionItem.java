package com.zzq.survival_toolbox.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 随身次元袋
 * <p>
 * 占背包一格，右键打开界面。内部为"按类型合并"的无限存储：
 * 同种物品（组件完全相同）自动合并为一条，数量用 long 计数无上限，
 * 槽位数 = 物品种类数（天然封顶），存档数据始终可控。
 * </p>
 */
public class PocketDimensionItem extends Item {

    public PocketDimensionItem() {
        // fireResistant：掉进火/岩浆里不会燃烧销毁（配合 ItemEntityMixin 免疫环境伤害）
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 潜行 + 准星对着水/岩浆源：直接吸进袋子。
        // 走这里是因为"对着开阔水面"时方块射线（不吃流体）什么都没打到，客户端发的是"使用物品"包，
        // 不会走 PocketTrayInteractHandler 的 RightClickBlock；两条路都调同一个 scoopFluidSource。
        // ⚠️ 只有托盘方向是**纳入**才吸水（规则：装液体跟随托盘方向状态）。
        //    这条是"对着开阔水面、方块射线打空"时客户端发过来的使用物品包，之前漏了这个判断，
        //    所以曾出现过"不管托盘什么方向都能吸水"（历史问题）。
        if (player.isShiftKeyDown() && !com.zzq.survival_toolbox.util.PocketTrayStorage.isOut(stack)) {
            net.minecraft.core.BlockPos fluidPos =
                    com.zzq.survival_toolbox.util.PocketTrayTransfer.fluidSourceInSight(player);
            if (fluidPos != null) {
                if (!level.isClientSide) {
                    com.zzq.survival_toolbox.util.PocketTrayTransfer.scoopFluidSource(player, stack, level, fluidPos);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }
        if (!level.isClientSide) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.zzq.survival_toolbox.screen.PocketDimensionMenu(id, inv, stack),
                    Component.translatable("container.zzq_survival_toolbox.pocket_dimension")));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.pocket_dimension"));
    }
}
