package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.util.TradeMachineData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 交易机：把记录下来的报价列表同步给客户端（界面画列表用） */
public record SyncTradeMachinePacket(List<TradeMachineData.Trade> trades) implements CustomPacketPayload {

    public static final Type<SyncTradeMachinePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "sync_trade_machine"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SyncTradeMachinePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.trades().size());
                for (TradeMachineData.Trade t : packet.trades()) {
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, t.costA);
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, t.costB);
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, t.result);
                    buf.writeUtf(t.npc == null ? "" : t.npc, 64);
                    buf.writeVarInt(t.uses);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<TradeMachineData.Trade> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    TradeMachineData.Trade t = new TradeMachineData.Trade();
                    t.costA = net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    t.costB = net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    t.result = net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    t.npc = buf.readUtf(64);
                    t.uses = buf.readVarInt();
                    list.add(t);
                }
                return new SyncTradeMachinePacket(list);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncTradeMachinePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TradeMachineData.setClientTrades(packet.trades()));
    }
}