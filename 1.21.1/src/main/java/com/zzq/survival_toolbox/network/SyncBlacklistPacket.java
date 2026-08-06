package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.item.BlacklistItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 黑白名单数据同步包
 * <p>
 * 双向同步：客户端↔服务端
 * <ul>
 *   <li>客户端发往服务端：玩家修改了名单数据</li>
 *   <li>服务端发往客户端：服务端修改了名单数据，更新客户端显示</li>
 * </ul>
 * </p>
 */
public class SyncBlacklistPacket implements CustomPacketPayload {

    public static final Type<SyncBlacklistPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "sync_blacklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBlacklistPacket> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, SyncBlacklistPacket::getStack,
            SyncBlacklistPacket::new
    );

    private final ItemStack stack;

    public SyncBlacklistPacket(ItemStack stack) {
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncBlacklistPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) return;

            ItemStack newStack = msg.stack;
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();

            if (mainHand.getItem() instanceof BlacklistItem) {
                player.getInventory().setItem(player.getInventory().selected, newStack);
            } else if (offHand.getItem() instanceof BlacklistItem) {
                player.getInventory().offhand.set(0, newStack);
            }
        });
    }
}
