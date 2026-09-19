package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.PocketFurnace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 熔炉页的"炉子"每 tick 推进（服务端）
 * <p>
 * 需求：<b>关掉界面也能继续烧</b>。所以炉子不是挂在界面上的，而是挂在袋子上的：
 * 只要袋子在玩家物品栏里（背包/快捷栏/副手），每个服务端 tick 就推进一次，
 * 进度存在袋子 NBT 里，界面开不开都一样。规则见 {@link PocketFurnace#tick}。
 * </p>
 * <p>
 * 开销：每 tick 要扫一遍物品栏，所以只看"自定义数据里有没有炉子的键"
 * （{@link PocketFurnace#mayBeActive}，不复制 NBT、不解析物品），
 * 空炉子（没燃料、输入燃料没齐）连读都不读。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox")
public class PocketFurnaceTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 主背包 + 快捷栏
        for (ItemStack stack : player.getInventory().items) {
            tickOne(player, stack);
        }
        // 副手
        tickOne(player, player.getInventory().offhand.get(0));
    }

    private static void tickOne(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModItems.POCKET_DIMENSION.get())) return;
        if (!PocketFurnace.mayBeActive(stack)) return;
        PocketFurnace.tick(player, stack);
    }
}
