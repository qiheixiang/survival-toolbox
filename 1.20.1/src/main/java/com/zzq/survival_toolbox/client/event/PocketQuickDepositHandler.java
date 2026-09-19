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
 * <p>
 * <b>创造模式的背包界面（E）也要能用</b>：创造模式的界面是 {@code CreativeModeInventoryScreen}，
 * 以前这里只认 {@code InventoryScreen}，所以创造模式下右键收纳完全没反应（实测）。
 * 创造界面是纯客户端界面，服务端那边开着的仍然是 {@code player.inventoryMenu}，
 * 而且它把玩家背包格包成了 {@code SlotWrapper}，槽位号必须取被包住的那一层（见 {@link #inventoryMenuIndex}）。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class PocketQuickDepositHandler {

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return; // 仅右键
        boolean normal = event.getScreen() instanceof InventoryScreen;
        boolean creative = event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        if (!normal && !creative) return;
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
        int menuIndex = inventoryMenuIndex(slot, mc);

        // 手里拿袋 → 右键物品格：收进手里的袋
        if (carriedBag && slot != null && menuIndex >= 0 && !slotBag && slot.hasItem()) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketQuickDepositPacket(
                    PocketQuickDepositPacket.MODE_BAG_IN_HAND, menuIndex));
            event.setCanceled(true);
            return;
        }
        // 手里拿物品 → 右键袋格：收进袋里
        if (!carriedBag && slot != null && menuIndex >= 0 && slotBag && !carried.isEmpty()) {
            SurvivalToolbox.CHANNEL.sendToServer(new PocketQuickDepositPacket(
                    PocketQuickDepositPacket.MODE_BAG_AT_SLOT, menuIndex));
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

    /**
     * 把"鼠标下的格子"换算成服务端 {@code player.inventoryMenu} 的槽位号。
     * <p>
     * 普通背包界面下 {@code slot.index} 就是它；但创造模式的界面（任一创造物品页）会把玩家背包格
     * 包成 {@code SlotWrapper}，NeoForge 自己处理点击时取的也是 {@code ((SlotWrapper) slot).target.index}
     * （见 {@code CreativeModeInventoryScreen#mouseClicked}），所以这里必须解包，
     * 否则右键收纳会打到别的格子上（把不相干的物品收进袋子）。
     * </p>
     *
     * @return 服务端 InventoryMenu 的槽位号；-1 = 该格子不是玩家背包里的格子（创造物品列表等），不要处理
     */
    private static int inventoryMenuIndex(Slot slot, Minecraft mc) {
        if (slot == null || mc.player == null) return -1;
        try {
            java.lang.reflect.Field f = slot.getClass().getDeclaredField("target");
            f.setAccessible(true);
            Object inner = f.get(slot);
            if (inner instanceof Slot target) return target.index;
        } catch (Throwable ignored) {
            // 不是 SlotWrapper，按普通格子继续
        }
        if (slot.container == mc.player.getInventory()) {
            int cs = slot.getContainerSlot();
            if (cs >= 0 && cs < 9) return cs + 36;  // 快捷栏：InventoryMenu 里是 36..44
            if (cs >= 9 && cs < 36) return cs;      // 主背包：两边编号一致
        }
        return -1;
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
