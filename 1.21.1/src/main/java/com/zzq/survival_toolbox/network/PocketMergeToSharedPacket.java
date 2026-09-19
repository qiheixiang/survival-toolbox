package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 本地存储并入共享空间包（客户端 → 服务端）
 * <p>
 * 无字段：服务端收到后对当前打开的次元袋执行"本地 → 共享"的<b>合并式</b>转移
 * （同种数量累加、不覆盖共享已有的东西，完成后清空本地并切到共享）。
 * 具体逻辑在 {@code PocketDimensionContainer#mergeLocalToShared()}。
 * </p>
 */
public record PocketMergeToSharedPacket() implements CustomPacketPayload {

    public static final PocketMergeToSharedPacket INSTANCE = new PocketMergeToSharedPacket();

    public static final Type<PocketMergeToSharedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_merge_to_shared"));

    /** 没有字段的包：用 unit 编解码 */
    public static final StreamCodec<FriendlyByteBuf, PocketMergeToSharedPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
