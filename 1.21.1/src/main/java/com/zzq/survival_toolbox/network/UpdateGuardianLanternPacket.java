package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 镇魂灯配置更新包（客户端 → 服务端）
 * <p>
 * 支持更新以下配置项：
 * <ul>
 *   <li>{@code lit}</li>
 *   <li>{@code attackHostile} / {@code attackNeutral} / {@code attackPassive}</li>
 *   <li>{@code rangeX} / {@code rangeY} / {@code rangeZ}</li>
 *   <li>{@code suppressHostile} / {@code suppressNeutral} / {@code suppressPassive}</li>
 *   <li>{@code suppressEnabled}</li>
 *   <li>{@code autoAttack}</li>
 *   <li>{@code attackInterval}</li>
 *   <li>{@code enabled}</li>
 *   <li>{@code showRange}</li>
 * </ul>
 * </p>
 */
public class UpdateGuardianLanternPacket implements CustomPacketPayload {

    public static final Type<UpdateGuardianLanternPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "update_guardian_lantern"));

    public static final StreamCodec<FriendlyByteBuf, UpdateGuardianLanternPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeUtf(packet.action);
                buf.writeInt(packet.value);
            },
            buf -> new UpdateGuardianLanternPacket(buf.readBlockPos(), buf.readUtf(), buf.readInt())
    );

    private final BlockPos pos;
    private final String action;
    private final int value;

    public UpdateGuardianLanternPacket(BlockPos pos, String action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateGuardianLanternPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof GuardianLanternBlockEntity lantern)) return;

            switch (packet.action) {
                case "lit" -> lantern.setLit(packet.value == 1);
                case "attackHostile" -> lantern.setAttackHostile(packet.value == 1);
                case "attackNeutral" -> lantern.setAttackNeutral(packet.value == 1);
                case "attackPassive" -> lantern.setAttackPassive(packet.value == 1);
                case "rangeX" -> lantern.setRangeX(packet.value);
                case "rangeY" -> lantern.setRangeY(packet.value);
                case "rangeZ" -> lantern.setRangeZ(packet.value);
                case "suppressHostile" -> lantern.setSuppressHostile(packet.value == 1);
                case "suppressNeutral" -> lantern.setSuppressNeutral(packet.value == 1);
                case "suppressPassive" -> lantern.setSuppressPassive(packet.value == 1);
                case "suppressEnabled" -> lantern.setSuppressEnabled(packet.value == 1);
                case "autoAttack" -> lantern.setAutoAttack(packet.value == 1);
                case "attackInterval" -> lantern.setAttackInterval(packet.value);
                case "enabled" -> lantern.setEnabled(packet.value == 1);
                case "showRange" -> lantern.setShowRange(packet.value == 1);
                default -> {
                }
            }
        });
    }
}
