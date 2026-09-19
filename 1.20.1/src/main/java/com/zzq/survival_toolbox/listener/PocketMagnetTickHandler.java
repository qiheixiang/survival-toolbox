package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.PocketMagnet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 磁铁页的"吸收"每 tick 推进（服务端）
 * <p>
 * 和炉子同一套思路：吸收挂在<b>袋子物品</b>上，不挂在界面上 —— 只要袋子在玩家物品栏里
 * （背包 / 快捷栏 / 副手），每个服务端 tick 就吸一次，界面开不开都一样。规则见 {@link PocketMagnet#tick}。
 * </p>
 * <p>
 * 开销：每 tick 扫一遍物品栏，只看"自定义数据里 MagOn 是不是 true"（{@link PocketMagnet#mayBeActive}），
 * 关着的时候几乎零成本；开着时 {@link PocketMagnet#tick} 内部还有 10 tick 节流。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class PocketMagnetTickHandler {

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
        if (!PocketMagnet.mayBeActive(stack)) return;
        PocketMagnet.tick(player, stack);
    }
}