package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.util.TradeMachineMerchant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 交易机：搜索关键词（客户端 → 服务端），服务端过滤报价后用原版报价包刷新列表 */
public class TradeMachineSearchPacket {

    private final String keyword;

    public TradeMachineSearchPacket(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
    }

    public static void encode(TradeMachineSearchPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.keyword, 32);
    }

    public static TradeMachineSearchPacket decode(FriendlyByteBuf buf) {
        return new TradeMachineSearchPacket(buf.readUtf(32));
    }

    public static void handle(TradeMachineSearchPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            TradeMachineMerchant merchant = TradeMachineMerchant.get(player);
            if (merchant != null) merchant.setFilter(msg.keyword);
        });
        ctx.get().setPacketHandled(true);
    }
}