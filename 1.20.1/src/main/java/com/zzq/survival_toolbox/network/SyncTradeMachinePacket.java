package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.util.TradeMachineData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 交易机：把记录下来的报价列表同步给客户端（服务端 → 客户端） */
public class SyncTradeMachinePacket {

    private final List<TradeMachineData.Trade> trades;

    public SyncTradeMachinePacket(List<TradeMachineData.Trade> trades) {
        this.trades = trades == null ? List.of() : trades;
    }

    public static void encode(SyncTradeMachinePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.trades.size());
        for (TradeMachineData.Trade t : msg.trades) {
            buf.writeItem(t.costA);
            buf.writeItem(t.costB);
            buf.writeItem(t.result);
            buf.writeUtf(t.npc == null ? "" : t.npc, 64);
            buf.writeVarInt(t.uses);
        }
    }

    public static SyncTradeMachinePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<TradeMachineData.Trade> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            TradeMachineData.Trade t = new TradeMachineData.Trade();
            t.costA = buf.readItem();
            t.costB = buf.readItem();
            t.result = buf.readItem();
            t.npc = buf.readUtf(64);
            t.uses = buf.readVarInt();
            list.add(t);
        }
        return new SyncTradeMachinePacket(list);
    }

    public static void handle(SyncTradeMachinePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TradeMachineData.setClientTrades(msg.trades));
        ctx.get().setPacketHandled(true);
    }
}