package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.item.XrayGogglesItem;
import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 透视眼镜方块开关包（客户端 → 服务端）
 * <p>
 * 选择菜单点击某一行时发送：服务端把该方块的开/关写进<b>对应那只手上的眼镜物品</b>
 * 自己的 NBT（每副眼镜独立），随后广播背包变化让客户端立即看到新状态。
 * 客户端不自行改 NBT，避免被服务端同步覆盖。
 * </p>
 */
public class XrayToggleBlockPacket {

    /** 0 = 主手，1 = 副手 */
    private final int hand;
    private final ResourceLocation blockId;

    public XrayToggleBlockPacket(int hand, ResourceLocation blockId) {
        this.hand = hand;
        this.blockId = blockId;
    }

    public XrayToggleBlockPacket(FriendlyByteBuf buf) {
        this.hand = buf.readVarInt();
        this.blockId = buf.readResourceLocation();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(hand);
        buf.writeResourceLocation(blockId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            InteractionHand handEnum = this.hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack goggles = player.getItemInHand(handEnum);
            if (!(goggles.getItem() instanceof XrayGogglesItem)) return;
            // 方块必须在注册表中，避免非法 id 写进物品 NBT
            if (!BuiltInRegistries.BLOCK.containsKey(this.blockId)) return;
            XrayOreHelper.toggle(goggles, this.blockId);
            // 立即把改过的物品同步给客户端（选择菜单每帧读取手持物品，能马上反映新状态）
            player.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
