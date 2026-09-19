package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 次元袋存储模式切换包（客户端 → 服务端）
 * <p>
 * 切换"共享（末影箱式，数据在服务器存档按玩家存）"与"本地（数据存在袋子里）"。
 * 两边数据各自保留，切换只换看哪一份。
 * </p>
 *
 * @param shared true = 切到共享，false = 切到本地
 */
public record PocketModeTogglePacket(boolean shared) implements CustomPacketPayload {

    public static final Type<PocketModeTogglePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "pocket_mode_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketModeTogglePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeBoolean(packet.shared()),
            buf -> new PocketModeTogglePacket(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端处理：只在玩家当前打开的正是次元袋界面时生效 */
    public static void handle(PocketModeTogglePacket payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                menu.setSharedMode(payload.shared());
            }
        });
    }
}
