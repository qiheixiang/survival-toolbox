package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.block.entity.SmartFarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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
public class UpdateSmartFarmPacket {

    private final BlockPos pos;
    private final String action;
    private final int value;

    public UpdateSmartFarmPacket(BlockPos pos, String action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    public UpdateSmartFarmPacket(FriendlyByteBuf buf) {
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
            if (!(be instanceof SmartFarmBlockEntity farm)) return;

            switch (action) {
                case "enabled" -> farm.setEnabled(value == 1);
                case "showRange" -> farm.setShowRange(value == 1);
                case "interval" -> farm.setWorkInterval(value);
                case "rangeX" -> farm.setRangeX(value);
                case "rangeY" -> farm.setRangeY(value);
                case "rangeZ" -> farm.setRangeZ(value);
                default -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}