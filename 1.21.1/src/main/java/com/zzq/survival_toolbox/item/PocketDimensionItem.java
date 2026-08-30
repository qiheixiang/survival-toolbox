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
        if (!level.isClientSide) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.zzq.survival_toolbox.screen.PocketDimensionMenu(id, inv, stack),
                    Component.translatable("container.zzq_survival_toolbox.pocket_dimension")));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.pocket_dimension"));
    }
}
