package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 次元袋搜索关键词包（客户端 → 服务端）
 * <p>
 * 搜索框每次输入变化时发送，服务端据此重建条目显示顺序（按物品名/注册名过滤）。
 * </p>
 */
public record PocketDimensionSearchPacket(String keyword) implements CustomPacketPayload {

    public static final Type<PocketDimensionSearchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_dimension_search"));

    public static final StreamCodec<FriendlyByteBuf, PocketDimensionSearchPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> buf.writeUtf(packet.keyword()),
                    buf -> new PocketDimensionSearchPacket(buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
