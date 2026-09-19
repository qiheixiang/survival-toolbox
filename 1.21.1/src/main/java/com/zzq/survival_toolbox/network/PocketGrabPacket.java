package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 次元袋"提起整格"包（客户端 → 服务端）
 * <p>
 * 袋子里一格可能有几千个，**不能**像原版那样把整格放到光标上：1.20.1 同步光标数量用的是 byte，
 * 超过 127 会被截断（几千个会凭空少掉）。所以长按只发一个"提起"信号，服务端记下这一格并高亮，
 * 之后再左键点目标格，才由
 * {@link com.zzq.survival_toolbox.screen.PocketDimensionContainer#swapEntries(int, int)} 整格对调。
 * </p>
 * <p>
 * slotId = 提起的存储格；再长按同一格 = 放下（取消）。点别处 / 按 Esc / 关界面也会取消。
 * </p>
 */
public record PocketGrabPacket(int slotId) implements CustomPacketPayload {

    public static final Type<PocketGrabPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_grab"));

    public static final StreamCodec<FriendlyByteBuf, PocketGrabPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PocketGrabPacket::slotId,
            PocketGrabPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
