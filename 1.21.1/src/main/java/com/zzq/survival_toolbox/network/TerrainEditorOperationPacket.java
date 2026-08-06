package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.item.TerrainEditorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 地形编辑器操作包（客户端 → 服务端）
 * <p>
 * 携带视线目标坐标，服务端用该坐标执行范围破坏/替换/填充操作。
 * </p>
 */
public class TerrainEditorOperationPacket implements CustomPacketPayload {

    public static final Type<TerrainEditorOperationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "terrain_editor_operation"));

    public static final StreamCodec<FriendlyByteBuf, TerrainEditorOperationPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.target);
                buf.writeEnum(packet.hand);
            },
            buf -> new TerrainEditorOperationPacket(buf.readBlockPos(), buf.readEnum(InteractionHand.class))
    );

    private final BlockPos target;
    private final InteractionHand hand;

    public TerrainEditorOperationPacket(BlockPos target, InteractionHand hand) {
        this.target = target;
        this.hand = hand;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerrainEditorOperationPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getItemInHand(packet.hand);
            if (!(stack.getItem() instanceof TerrainEditorItem)) return;
            TerrainEditorItem.executeOperationStatic(player.level(), packet.target, stack, player);
        });
    }
}
