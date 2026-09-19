package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋存储模式切换包（客户端 → 服务端）
 * <p>
 * 切换"共享（末影箱式，数据在服务器存档按玩家存）"与"本地（数据存在袋子里）"。
 * 两边数据各自保留，切换只换看哪一份。
 * </p>
 */
public class PocketModeTogglePacket {

    private final boolean shared;

    public PocketModeTogglePacket(boolean shared) {
        this.shared = shared;
    }

    public PocketModeTogglePacket(FriendlyByteBuf buf) {
        this.shared = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(shared);
    }

    /** 服务端处理：只在玩家当前打开的正是次元袋界面时生效 */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                menu.setSharedMode(shared);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
