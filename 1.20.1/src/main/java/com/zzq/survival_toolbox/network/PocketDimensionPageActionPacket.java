package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋页操作包（客户端 → 服务端）
 * <p>
 * action: 0=新建页, 1=删除页(仅空页), 2=重命名页, 3=切换当前页。
 * index: 目标页索引；name: 重命名时的新页名。
 * </p>
 */
public class PocketDimensionPageActionPacket {

    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_RENAME = 2;
    public static final int ACTION_SET_PAGE = 3;

    private final int action;
    private final int index;
    private final String name;

    public PocketDimensionPageActionPacket(int action, int index, String name) {
        this.action = action;
        this.index = index;
        this.name = name;
    }

    public static void encode(PocketDimensionPageActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.index);
        buf.writeUtf(msg.name == null ? "" : msg.name, 32);
    }

    public static PocketDimensionPageActionPacket decode(FriendlyByteBuf buf) {
        return new PocketDimensionPageActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
    }

    public static void handle(PocketDimensionPageActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null
                    && ctx.get().getSender().containerMenu instanceof PocketDimensionMenu menu) {
                menu.handlePageAction(msg.action, msg.index, msg.name);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
