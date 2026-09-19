package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 功能页里的文本输入框（客户端 → 服务端）
 * <p>
 * 目前只有铁砧页的<b>改名框</b>用它：改名必须由服务端算（产物、经验花费全是原版
 * {@code AnvilMenu} 算的），所以框里的文字要传上去。空字符串 = 不改名（用物品自己的名字）。
 * </p>
 */
public class PocketPageTextPacket {

    /** 名字长度上限（原版铁砧是 50） */
    public static final int MAX_LENGTH = 50;

    private final String text;

    public PocketPageTextPacket(String text) {
        String t = text == null ? "" : text;
        this.text = t.length() > MAX_LENGTH ? t.substring(0, MAX_LENGTH) : t;
    }

    public String text() {
        return text;
    }

    public static void encode(PocketPageTextPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.text, MAX_LENGTH);
    }

    public static PocketPageTextPacket decode(FriendlyByteBuf buf) {
        return new PocketPageTextPacket(buf.readUtf(MAX_LENGTH));
    }

    public static void handle(PocketPageTextPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu
                    instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                menu.setAnvilName(msg.text);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
