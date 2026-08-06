package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.listener.AdaptationEventHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 取消受击反馈网络包
 * <p>
 * 客户端收到后标记该玩家应取消受击反馈（视觉/音效）。
 * 目前保留框架，实际取消逻辑由 {@link com.zzq.survival_toolbox.mixin.LivingEntityMixin} 控制。
 * </p>
 */
public class CancelHurtEffectPacket implements CustomPacketPayload {

    public static final Type<CancelHurtEffectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "cancel_hurt_effect"));

    public static final StreamCodec<FriendlyByteBuf, CancelHurtEffectPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUUID(msg.playerId),
            buf -> new CancelHurtEffectPacket(buf.readUUID())
    );

    private final UUID playerId;

    public CancelHurtEffectPacket(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelHurtEffectPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AdaptationEventHandler.setCancelHurtEffect(msg.playerId, true));
    }
}
