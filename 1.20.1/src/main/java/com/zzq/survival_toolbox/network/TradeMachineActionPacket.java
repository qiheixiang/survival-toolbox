package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.screen.TradeMachineMenu;
import com.zzq.survival_toolbox.util.TradeMachineData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** 交易机：客户端点某一条报价（index >= 0 成交；< 0 只重新同步列表） */
public class TradeMachineActionPacket {

    private final int index;

    public TradeMachineActionPacket(int index) {
        this.index = index;
    }

    public static void encode(TradeMachineActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
    }

    public static TradeMachineActionPacket decode(FriendlyByteBuf buf) {
        return new TradeMachineActionPacket(buf.readVarInt());
    }

    public static void handle(TradeMachineActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof TradeMachineMenu)) return;
            ItemStack machine = ItemStack.EMPTY;
            if (player.getMainHandItem().is(ModItems.TRADE_MACHINE.get())) machine = player.getMainHandItem();
            else if (player.getOffhandItem().is(ModItems.TRADE_MACHINE.get())) machine = player.getOffhandItem();
            else {
                for (ItemStack s : player.getInventory().items) {
                    if (!s.isEmpty() && s.is(ModItems.TRADE_MACHINE.get())) {
                        machine = s;
                        break;
                    }
                }
            }
            if (machine.isEmpty()) return;
            List<TradeMachineData.Trade> trades = TradeMachineData.read(machine);
            if (msg.index >= 0 && msg.index < trades.size()) {
                TradeMachineData.Trade t = trades.get(msg.index);
                if (!TradeMachineData.execute(player, t)) {
                    player.displayClientMessage(Component.translatable(
                            "gui.zzq_survival_toolbox.trade_machine.not_enough"), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "gui.zzq_survival_toolbox.trade_machine.done", t.result.getHoverName()), true);
                }
            }
            TradeMachineMenu.syncTo(player, machine);
        });
        ctx.get().setPacketHandled(true);
    }
}