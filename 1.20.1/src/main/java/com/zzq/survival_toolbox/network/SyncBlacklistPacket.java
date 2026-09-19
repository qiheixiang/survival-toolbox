package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.item.BlacklistItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 黑白名单数据同步包
 * <p>
 * 双向同步：客户端↔服务端
 * <ul>
 *   <li>客户端发往服务端：玩家修改了名单数据</li>
 *   <li>服务端发往客户端：服务端修改了名单数据，更新客户端显示</li>
 * </ul>
 * </p>
 */
public class SyncBlacklistPacket {

    private final ItemStack stack;

    public SyncBlacklistPacket(ItemStack stack) {
        this.stack = stack;
    }

    public static void encode(SyncBlacklistPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static SyncBlacklistPacket decode(FriendlyByteBuf buf) {
        return new SyncBlacklistPacket(buf.readItem());
    }

    public static void handle(SyncBlacklistPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 服务端收到（来自客户端的同步）
            if (ctx.get().getDirection().getReceptionSide().isServer()) {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ItemStack newStack = msg.stack;
                    ItemStack mainHand = player.getMainHandItem();
                    ItemStack offHand = player.getOffhandItem();

                    if (mainHand.getItem() instanceof BlacklistItem) {
                        player.getInventory().setItem(player.getInventory().selected, newStack);
                    } else if (offHand.getItem() instanceof BlacklistItem) {
                        player.getInventory().offhand.set(0, newStack);
                    }
                }
            }
            // 客户端收到（来自服务端的同步）
            else {
                // 走 client 包入口：本类会被专用服务器加载，这里不能出现客户端类型
                com.zzq.survival_toolbox.client.ClientHooks.applyBlacklistSync(msg.stack);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}