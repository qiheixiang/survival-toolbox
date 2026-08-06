package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.entity.InfiniteSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 无限之源配置更新包
 * <p>
 * 目前无实际配置项，仅标记方块数据已修改。
 * </p>
 */
public class UpdateInfiniteSourcePacket implements CustomPacketPayload {

    public static final Type<UpdateInfiniteSourcePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "update_infinite_source"));

    public static final StreamCodec<FriendlyByteBuf, UpdateInfiniteSourcePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos);
                buf.writeUtf(packet.action);
                buf.writeInt(packet.value);
            },
            buf -> new UpdateInfiniteSourcePacket(buf.readBlockPos(), buf.readUtf(), buf.readInt())
    );

    private final BlockPos pos;
    private final String action;
    private final int value;

    public UpdateInfiniteSourcePacket(BlockPos pos, String action, int value) {
        this.pos = pos;
        this.action = action;
        this.value = value;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateInfiniteSourcePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof InfiniteSourceBlockEntity source) {
                source.setChanged();
            }
        });
    }
}
