package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.screen.BlacklistMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 打开黑白名单界面的网络包
 * <p>
 * 客户端请求服务端打开黑白名单管理界面。
 * </p>
 */
public class OpenBlacklistScreenPacket implements CustomPacketPayload {

    public static final Type<OpenBlacklistScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "open_blacklist_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenBlacklistScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
            },
            buf -> new OpenBlacklistScreenPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBlacklistScreenPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.zzq_survival_toolbox.blacklist");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BlacklistMenu(id, inv);
                }
            });
        });
    }
}
