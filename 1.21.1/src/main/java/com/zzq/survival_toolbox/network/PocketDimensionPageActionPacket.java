package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 次元袋页操作包（客户端 → 服务端）
 * <p>
 * action: 0=新建页, 1=删除页(仅空页), 2=重命名页, 3=切换当前页。
 * index: 目标页索引（新建/删除/重命名/切换）；name: 重命名时的新页名。
 * </p>
 */
public record PocketDimensionPageActionPacket(int action, int index, String name) implements CustomPacketPayload {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_RENAME = 2;
    public static final int ACTION_SET_PAGE = 3;
    /** 切石机页：选中第 N 条配方（index）——客户端服务端都按"配方 id 排序"算同一份列表，所以传下标就够 */
    public static final int ACTION_STONE_SELECT = 4;

    public static final Type<PocketDimensionPageActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_page_action"));

    public static final StreamCodec<FriendlyByteBuf, PocketDimensionPageActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.action());
                buf.writeVarInt(packet.index());
                buf.writeUtf(packet.name() == null ? "" : packet.name(), 32);
            },
            buf -> new PocketDimensionPageActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(32))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
