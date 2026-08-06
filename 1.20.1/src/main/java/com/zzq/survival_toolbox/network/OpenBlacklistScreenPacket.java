package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.BlacklistMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * 打开黑白名单界面的网络包
 * <p>
 * 客户端请求服务端打开黑白名单管理界面。
 * </p>
 */
public class OpenBlacklistScreenPacket {

    public static void encode(OpenBlacklistScreenPacket msg, FriendlyByteBuf buf) {
        // 无数据需要编码
    }

    public static OpenBlacklistScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenBlacklistScreenPacket();
    }

    public static void handle(OpenBlacklistScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            NetworkHooks.openScreen(player, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.zzq_survival_toolbox.blacklist");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BlacklistMenu(id, inv);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}