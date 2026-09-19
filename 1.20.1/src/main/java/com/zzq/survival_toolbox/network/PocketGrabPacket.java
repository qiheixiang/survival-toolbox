package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋"提起整格"包（客户端 → 服务端，1.20.1 的 SimpleChannel 版）
 * <p>
 * 袋子里一格可能有几千个，**不能**像原版那样把整格放到光标上：1.20.1 同步光标数量用的是 byte，
 * 超过 127 会被截断（几千个会凭空少掉）。所以长按只发一个"提起"信号，服务端记下这一格并高亮，
 * 之后再左键点目标格，才由
 * {@link com.zzq.survival_toolbox.screen.PocketDimensionContainer#swapEntries(int, int)} 整格对调。
 * </p>
 * <p>
 * slotId = 提起的存储格；再长按同一格 = 放下（取消）。点别处 / 按 Esc / 关界面也会取消。
 * </p>
 */
public class PocketGrabPacket {

    private final int slotId;

    public PocketGrabPacket(int slotId) {
        this.slotId = slotId;
    }

    public static void encode(PocketGrabPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slotId);
    }

    public static PocketGrabPacket decode(FriendlyByteBuf buf) {
        return new PocketGrabPacket(buf.readVarInt());
    }

    public static void handle(PocketGrabPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null
                    && ctx.get().getSender().containerMenu instanceof PocketDimensionMenu menu) {
                menu.toggleGrab(msg.slotId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
