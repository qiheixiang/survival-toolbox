package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.entity.SmartFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 智慧农场配置更新包（客户端 → 服务端）
 * <p>
 * 支持更新以下配置项：
 * <ul>
 *   <li>{@code enabled}</li>
 *   <li>{@code showRange}</li>
 *   <li>{@code interval}</li>
 *   <li>{@code rangeX} / {@code rangeY} / {@code rangeZ}</li>
 * </ul>
 * </p>
 */
public class UpdateSmartFarmPacket implements CustomPacketPayload {

    public static final Type<UpdateSmartFarmPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "update_smart_farm"));

    public static final StreamCodec<FriendlyByteBuf, UpdateSmartFarmPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeUtf(packet.action);
                buf.writeInt(packet.value);
            },
            buf -> new UpdateSmartFarmPacket(buf.readBlockPos(), buf.readUtf(), buf.readInt())
    );

    private final BlockPos pos;
    private final String action;
    private final int value;

    public UpdateSmartFarmPacket(BlockPos pos, String action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateSmartFarmPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof SmartFarmBlockEntity farm)) return;

            switch (packet.action) {
                case "enabled" -> farm.setEnabled(packet.value == 1);
                case "showRange" -> farm.setShowRange(packet.value == 1);
                case "interval" -> farm.setWorkInterval(packet.value);
                case "rangeX" -> farm.setRangeX(packet.value);
                case "rangeY" -> farm.setRangeY(packet.value);
                case "rangeZ" -> farm.setRangeZ(packet.value);
                default -> {
                }
            }
        });
    }
}
