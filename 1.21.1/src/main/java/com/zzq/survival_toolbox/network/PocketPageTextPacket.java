package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 功能页里的文本输入框（客户端 → 服务端）
 * <p>
 * 目前只有铁砧页的<b>改名框</b>用它：改名必须由服务端算（产物、经验花费全是原版
 * {@code AnvilMenu} 算的），所以框里的文字要传上去。空字符串 = 不改名（用物品自己的名字）。
 * </p>
 */
public record PocketPageTextPacket(String text) implements CustomPacketPayload {

    /** 名字长度上限（原版铁砧是 50） */
    public static final int MAX_LENGTH = 50;

    public static final Type<PocketPageTextPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_page_text"));

    public static final StreamCodec<FriendlyByteBuf, PocketPageTextPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.text(), MAX_LENGTH),
            buf -> new PocketPageTextPacket(buf.readUtf(MAX_LENGTH))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
