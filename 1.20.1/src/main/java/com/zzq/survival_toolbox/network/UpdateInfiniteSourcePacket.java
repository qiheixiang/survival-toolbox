package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.block.entity.InfiniteSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 无限之源配置更新包
 * <p>
 * 目前无实际配置项，仅标记方块数据已修改。
 * </p>
 */
public class UpdateInfiniteSourcePacket {

    private final BlockPos pos;
    private final String action;
    private final int value;

    public UpdateInfiniteSourcePacket(BlockPos pos, String action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    public UpdateInfiniteSourcePacket(FriendlyByteBuf buf) {
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
            if (be instanceof InfiniteSourceBlockEntity source) {
                source.setChanged();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}