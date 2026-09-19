package com.zzq.survival_toolbox.util;

import com.zzq.survival_toolbox.item.CreativePickerItem;
import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * "创造口袋"的判定（公共类，两端都要用，所以<b>不引用任何客户端类型</b>）
 * <p>
 * 服务端只在"玩家背包里真的带着这个物品"时才放行创造物品栏的取物请求；
 * 客户端则用它决定 {@code hasInfiniteItems()} 要不要按创造算、要不要发包。
 * 注意：判定一律以<b>服务端看到的背包</b>为准（见 {@code ServerGamePacketListenerImplMixin}），
 * 客户端的判定只影响界面能不能用。
 * </p>
 */
public final class PocketCreativeAccess {

    private PocketCreativeAccess() {
    }

    /** 背包（含副手、光标）里有没有创造口袋 */
    public static boolean hasCreativePicker(Player player) {
        if (player == null) return false;
        Inventory inv = player.getInventory();
        for (ItemStack stack : inv.items) {
            if (isPicker(stack)) return true;
        }
        for (ItemStack stack : inv.offhand) {
            if (isPicker(stack)) return true;
        }
        // 光标上的也算：创造物品栏开着时光标物品在容器菜单里，不在 Inventory 里
        if (player.containerMenu != null && isPicker(player.containerMenu.getCarried())) return true;
        return false;
    }

    public static boolean isPicker(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CreativePickerItem;
    }

    /** 物品本身（注册用；给 mixin 之外的地方少写点字） */
    public static boolean isPickerItem(net.minecraft.world.item.Item item) {
        return item == ModItems.CREATIVE_PICKER.get();
    }
}
