package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * JEI"+"号可转移性查询的<b>回复</b>（服务端 → 客户端）
 * <p>
 * 服务端用真正的库存算完"这套材料现在凑得齐吗"，把答案连同客户端发来的签名一起回给客户端；
 * 客户端把它写进自己的小缓存（{@code client.compat.jei.PocketJeiAvailabilityCache}），
 * 下一次"能不能点 +"的判定就用这个答案（需求：<b>凑不齐时 + 号要变灰/报材料不足</b>）。
 * </p>
 * <p>
 * ⚠️ 这里<b>不能</b>直接引用客户端 / JEI 的类（本类会被专用服务器加载），
 * 所以走 {@code ClientHooks} 这个"公共类 → 客户端"的安全入口
 * （它的方法签名里不出现任何客户端类型，见那个类的说明）。
 * </p>
 *
 * @param signature 客户端算的缓存键（原样回传，用来认出这是哪一次询问的答案）
 * @param available true = 用玩家背包 + 输入格 + 袋子凑得齐（+ 可以点）；false = 确实凑不齐
 */
public record PocketJeiQueryReplyPacket(long signature, boolean available) implements CustomPacketPayload {

    public static final Type<PocketJeiQueryReplyPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_jei_query_reply"));

    public static final StreamCodec<FriendlyByteBuf, PocketJeiQueryReplyPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeLong(packet.signature());
                buf.writeBoolean(packet.available());
            },
            buf -> new PocketJeiQueryReplyPacket(buf.readLong(), buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PocketJeiQueryReplyPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !player.level().isClientSide) return;
            com.zzq.survival_toolbox.client.ClientHooks
                    .onJeiAvailabilityReply(payload.signature(), payload.available());
        });
    }
}
