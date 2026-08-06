package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.data.BlacklistEntry;
import com.zzq.survival_toolbox.item.BlacklistItem;
import com.zzq.survival_toolbox.screen.BlacklistMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * 黑白名单实体交互处理器
 * <p>
 * 当玩家手持黑白名单物品右键实体时：
 * <ul>
 *   <li>普通右键：将该实体记录到名单中</li>
 *   <li>Shift+右键：打开黑白名单管理界面</li>
 * </ul>
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class BlacklistInteractEventHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof BlacklistItem)) return;

        event.setCanceled(true);

        if (player.level().isClientSide()) return;

        if (player.isCrouching()) {
            NetworkHooks.openScreen((ServerPlayer) player, new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.zzq_survival_toolbox.blacklist");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                    return new BlacklistMenu(id, inv);
                }
            });
        } else {
            BlacklistEntry entry = new BlacklistEntry(event.getTarget());
            BlacklistItem.addEntry(stack, entry, player);
            player.sendSystemMessage(Component.translatable(
                    "message.zzq_survival_toolbox.blacklist.recorded",
                    event.getTarget().getName().getString()
            ));
        }
    }
}