package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.PocketRestock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/**
 * 补货页的"补货"每 tick 判断（服务端）
 * <p>
 * 和磁铁/炉子同一套：挂在<b>袋子物品</b>上，袋子在物品栏里就一直生效，界面开不开都一样。
 * 规则见 {@link PocketRestock#tick}（内部有节流，开关关着时几乎零成本）。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class PocketRestockTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        for (ItemStack stack : player.getInventory().items) {
            tickOne(player, stack);
        }
        tickOne(player, player.getInventory().offhand.get(0));
    }

    private static void tickOne(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.POCKET_DIMENSION.get())) return;
        if (!PocketRestock.mayBeActive(stack)) return;
        PocketRestock.tick(player, stack);
    }
}
