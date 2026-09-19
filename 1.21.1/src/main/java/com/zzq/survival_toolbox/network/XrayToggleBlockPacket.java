package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.item.XrayGogglesItem;
import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 透视眼镜方块开关包（客户端 → 服务端）
 * <p>
 * 选择菜单点击某一行时发送：服务端把该方块的开/关写进<b>对应那只手上的眼镜物品</b>
 * 自己的 NBT（每副眼镜独立），随后广播背包变化让客户端立即看到新状态。
 * 客户端不自行改 NBT，避免被服务端同步覆盖。
 * </p>
 *
 * @param hand    手持的手（0 = 主手，1 = 副手）
 * @param blockId 目标方块注册名
 */
public record XrayToggleBlockPacket(int hand, ResourceLocation blockId) implements CustomPacketPayload {

    public static final Type<XrayToggleBlockPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "xray_toggle_block"));

    public static final StreamCodec<FriendlyByteBuf, XrayToggleBlockPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.hand());
                        buf.writeResourceLocation(packet.blockId());
                    },
                    buf -> new XrayToggleBlockPacket(buf.readVarInt(), buf.readResourceLocation()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(XrayToggleBlockPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            InteractionHand hand = payload.hand() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack goggles = player.getItemInHand(hand);
            if (!(goggles.getItem() instanceof XrayGogglesItem)) return;
            // 方块必须在注册表中，避免非法 id 写进物品 NBT
            if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(payload.blockId())) return;
            XrayOreHelper.toggle(goggles, payload.blockId());
            // 立即把改过的物品同步给客户端（选择菜单每帧读取手持物品，能马上反映新状态）
            player.inventoryMenu.broadcastChanges();
        });
    }
}
