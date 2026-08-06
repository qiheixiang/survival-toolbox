package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.listener.AdaptationEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 取消受击反馈网络包
 * <p>
 * 客户端收到后标记该玩家应取消受击反馈（视觉/音效）。
 * 目前保留框架，实际取消逻辑由 {@link com.zzq.survival_toolbox.mixin.LivingEntityMixin} 控制。
 * </p>
 */
public class CancelHurtEffectPacket {

    private final UUID playerId;

    public CancelHurtEffectPacket(UUID playerId) {
        this.playerId = playerId;
    }

    public static void encode(CancelHurtEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
    }

    public static CancelHurtEffectPacket decode(FriendlyByteBuf buf) {
        return new CancelHurtEffectPacket(buf.readUUID());
    }

    public static void handle(CancelHurtEffectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AdaptationEventHandler.setCancelHurtEffect(msg.playerId, true);
        });
        ctx.get().setPacketHandled(true);
    }
}