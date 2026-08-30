package com.zzq.survival_toolbox.client.event;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.network.PocketQuickDepositPacket;
import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 次元袋快捷收纳（仅客户端，类似精妙背包的交互）
 * <p>
 * 在普通背包界面（E）：
 * - 鼠标拿起一件物品，右键点次元袋图标 → 该物品收进这个袋
 * - 鼠标拿起次元袋，右键点背包里的物品 → 该物品收进手里的袋
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class PocketQuickDepositHandler {

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return; // 仅右键
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        net.minecraft.world.inventory.AbstractContainerMenu menu = mc.player.inventoryMenu;

        ItemStack carried = menu.getCarried();
        Slot slot = getSlotUnderMouse(event);
        ItemStack slotItem = slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
        boolean carriedBag = carried.is(ModItems.POCKET_DIMENSION.get());
        boolean slotBag = slotItem.is(ModItems.POCKET_DIMENSION.get());
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean handBag = main.is(ModItems.POCKET_DIMENSION.get()) || off.is(ModItems.POCKET_DIMENSION.get());

        // 手里拿袋 → 右键物品格：收进手里的袋
        if (carriedBag && slot != null && !slotBag && slot.hasItem()) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketQuickDepositPacket(
                    PocketQuickDepositPacket.MODE_BAG_IN_HAND, slot.index));
            event.setCanceled(true);
            return;
        }
        // 手里拿物品 → 右键袋格：收进袋里
        if (!carriedBag && slot != null && slotBag && !carried.isEmpty()) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketQuickDepositPacket(
                    PocketQuickDepositPacket.MODE_BAG_AT_SLOT, slot.index));
            event.setCanceled(true);
            return;
        }
        // 手里拿袋/手持袋 → 右键背包界面空白处：一键收纳背包物品（快捷栏除外）
        if ((carriedBag || handBag) && slot == null) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketQuickDepositPacket(
                    PocketQuickDepositPacket.MODE_DEPOSIT_ALL, -1));
            event.setCanceled(true);
        }
    }

    private static Slot getSlotUnderMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        try {
            java.lang.reflect.Method m = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class
                    .getDeclaredMethod("getSlotUnderMouse");
            m.setAccessible(true);
            return (Slot) m.invoke(event.getScreen());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
