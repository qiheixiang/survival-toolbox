package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 交易机：搜索关键词（客户端 → 服务端），服务端过滤报价后用原版报价包刷新列表 */
public record TradeMachineSearchPacket(String keyword) implements CustomPacketPayload {

    public static final Type<TradeMachineSearchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "trade_machine_search"));

    public static final StreamCodec<FriendlyByteBuf, TradeMachineSearchPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.keyword() == null ? "" : packet.keyword(), 32),
            buf -> new TradeMachineSearchPacket(buf.readUtf(32))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TradeMachineSearchPacket packet,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            com.zzq.survival_toolbox.util.TradeMachineMerchant merchant =
                    com.zzq.survival_toolbox.util.TradeMachineMerchant.get(context.player());
            if (merchant != null) merchant.setFilter(packet.keyword());
        });
    }
}