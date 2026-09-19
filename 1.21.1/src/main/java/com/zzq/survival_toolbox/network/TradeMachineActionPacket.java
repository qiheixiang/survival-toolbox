package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 交易机：客户端点某一条报价（index >= 0 成交；index < 0 表示只要重新同步列表） */
public record TradeMachineActionPacket(int index) implements CustomPacketPayload {

    public static final Type<TradeMachineActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "trade_machine_action"));

    public static final StreamCodec<FriendlyByteBuf, TradeMachineActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.index()),
            buf -> new TradeMachineActionPacket(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TradeMachineActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof com.zzq.survival_toolbox.screen.TradeMachineMenu menu)) return;
            net.minecraft.world.item.ItemStack machine = player.getMainHandItem().is(
                    com.zzq.survival_toolbox.registry.ModItems.TRADE_MACHINE.get())
                    ? player.getMainHandItem() : player.getOffhandItem();
            if (machine.isEmpty() || !machine.is(
                    com.zzq.survival_toolbox.registry.ModItems.TRADE_MACHINE.get())) {
                // 手里没拿着交易机：退回物品栏里找一台（界面开着时被挪走的情况）
                for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
                    if (!s.isEmpty() && s.is(com.zzq.survival_toolbox.registry.ModItems.TRADE_MACHINE.get())) {
                        machine = s;
                        break;
                    }
                }
            }
            if (machine.isEmpty()) return;
            java.util.List<com.zzq.survival_toolbox.util.TradeMachineData.Trade> trades =
                    com.zzq.survival_toolbox.util.TradeMachineData.read(machine, player.level().registryAccess());
            if (packet.index() >= 0 && packet.index() < trades.size()) {
                com.zzq.survival_toolbox.util.TradeMachineData.Trade t = trades.get(packet.index());
                if (!com.zzq.survival_toolbox.util.TradeMachineData.execute(player, t)) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "gui.zzq_survival_toolbox.trade_machine.not_enough"), true);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "gui.zzq_survival_toolbox.trade_machine.done",
                            t.result.getHoverName()), true);
                }
            }
            com.zzq.survival_toolbox.screen.TradeMachineMenu.syncTo(player, machine);
        });
    }
}