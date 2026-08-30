package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋搜索关键词包（客户端 → 服务端）
 * <p>
 * 搜索框每次输入变化时发送，服务端据此重建条目显示顺序（按物品名/注册名过滤）。
 * </p>
 */
public class PocketDimensionSearchPacket {

    private final String keyword;

    public PocketDimensionSearchPacket(String keyword) {
        this.keyword = keyword;
    }

    public static void encode(PocketDimensionSearchPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.keyword);
    }

    public static PocketDimensionSearchPacket decode(FriendlyByteBuf buf) {
        return new PocketDimensionSearchPacket(buf.readUtf(64));
    }

    public static void handle(PocketDimensionSearchPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null
                    && ctx.get().getSender().containerMenu instanceof PocketDimensionMenu menu) {
                menu.setSearch(msg.keyword);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
