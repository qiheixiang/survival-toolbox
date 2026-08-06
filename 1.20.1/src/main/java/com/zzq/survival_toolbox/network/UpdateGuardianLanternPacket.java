package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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
public class UpdateGuardianLanternPacket {

    private final BlockPos pos;
    private final String action;
    private final int value;

    public UpdateGuardianLanternPacket(BlockPos pos, String action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    public UpdateGuardianLanternPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readUtf();
        this.value = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(action);
        buf.writeInt(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof GuardianLanternBlockEntity lantern)) return;

            switch (action) {
                case "lit" -> lantern.setLit(value == 1);
                case "attackHostile" -> lantern.setAttackHostile(value == 1);
                case "attackNeutral" -> lantern.setAttackNeutral(value == 1);
                case "attackPassive" -> lantern.setAttackPassive(value == 1);
                case "rangeX" -> lantern.setRangeX(value);
                case "rangeY" -> lantern.setRangeY(value);
                case "rangeZ" -> lantern.setRangeZ(value);
                case "suppressHostile" -> lantern.setSuppressHostile(value == 1);
                case "suppressNeutral" -> lantern.setSuppressNeutral(value == 1);
                case "suppressPassive" -> lantern.setSuppressPassive(value == 1);
                case "suppressEnabled" -> lantern.setSuppressEnabled(value == 1);
                case "autoAttack" -> lantern.setAutoAttack(value == 1);
                case "attackInterval" -> lantern.setAttackInterval(value);
                case "enabled" -> lantern.setEnabled(value == 1);
                case "showRange" -> lantern.setShowRange(value == 1);
                default -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}