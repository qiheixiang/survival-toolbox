package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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
 */
public class PocketJeiQueryReplyPacket {

    /** 客户端算的缓存键（原样回传，用来认出这是哪一次询问的答案） */
    private final long signature;
    /** true = 用玩家背包 + 输入格 + 袋子凑得齐（+ 可以点）；false = 确实凑不齐 */
    private final boolean available;

    public PocketJeiQueryReplyPacket(long signature, boolean available) {
        this.signature = signature;
        this.available = available;
    }

    public static void encode(PocketJeiQueryReplyPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.signature);
        buf.writeBoolean(msg.available);
    }

    public static PocketJeiQueryReplyPacket decode(FriendlyByteBuf buf) {
        return new PocketJeiQueryReplyPacket(buf.readLong(), buf.readBoolean());
    }

    public static void handle(PocketJeiQueryReplyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.zzq.survival_toolbox.client.ClientHooks
                .onJeiAvailabilityReply(msg.signature, msg.available));
        ctx.get().setPacketHandled(true);
    }
}
