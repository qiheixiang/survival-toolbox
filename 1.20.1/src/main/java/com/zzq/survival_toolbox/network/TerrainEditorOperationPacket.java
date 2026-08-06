package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.item.TerrainEditorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 地形编辑器操作包（客户端 → 服务端）
 * <p>
 * 携带视线目标坐标，服务端用该坐标执行范围破坏/替换/填充操作。
 * </p>
 */
public class TerrainEditorOperationPacket {

    private final BlockPos target;
    private final InteractionHand hand;

    public TerrainEditorOperationPacket(BlockPos target, InteractionHand hand) {
        this.target = target;
        this.hand = hand;
    }

    public static void encode(TerrainEditorOperationPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.target);
        buf.writeEnum(packet.hand);
    }

    public static TerrainEditorOperationPacket decode(FriendlyByteBuf buf) {
        return new TerrainEditorOperationPacket(buf.readBlockPos(), buf.readEnum(InteractionHand.class));
    }

    public static void handle(TerrainEditorOperationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack stack = player.getItemInHand(packet.hand);
            if (!(stack.getItem() instanceof TerrainEditorItem)) return;
            TerrainEditorItem.executeOperationStatic(player.level(), packet.target, stack, player);
        });
        ctx.get().setPacketHandled(true);
    }
}