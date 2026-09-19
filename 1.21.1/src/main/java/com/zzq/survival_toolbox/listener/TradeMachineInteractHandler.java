package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.TradeMachineData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 交易机：shift+右键有交易菜单的 NPC（村民 / 流浪商人）把对方报价记进手里那台交易机。
 * <p>
 * 记录规则见 {@link TradeMachineData#record}：同一交易项里更实惠的覆盖不实惠的。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox")
public class TradeMachineInteractHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        ItemStack tool = player.getItemInHand(event.getHand());
        if (tool.isEmpty() || !tool.is(ModItems.TRADE_MACHINE.get())) return;
        Entity target = event.getTarget();
        if (!(target instanceof AbstractVillager villager)) return;

        event.setCanceled(true);
        int added = 0;
        int replaced = 0;
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer == null || offer.getResult().isEmpty()) continue;
            TradeMachineData.Trade t = TradeMachineData.fromOffer(offer, villager.getName().getString());
            int r = TradeMachineData.record(tool, t, player.level().registryAccess());
            if (r == 1) added++;
            else if (r == 2) replaced++;
        }
        player.displayClientMessage(Component.translatable(
                "gui.zzq_survival_toolbox.trade_machine.recorded", added, replaced), true);
        com.zzq.survival_toolbox.screen.TradeMachineMenu.syncTo(player, tool);
    }
}