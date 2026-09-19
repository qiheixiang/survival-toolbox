package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 本地存储并入共享空间包（客户端 → 服务端，1.20.1 的 SimpleChannel 版）
 * <p>
 * 无字段：服务端收到后对当前打开的次元袋执行"本地 → 共享"的<b>合并式</b>转移
 * （同种数量累加、不覆盖共享已有的东西，完成后清空本地并切到共享）。
 * 具体逻辑在 {@code PocketDimensionContainer#mergeLocalToShared()}。
 * </p>
 */
public class PocketMergeToSharedPacket {

    public static void encode(PocketMergeToSharedPacket msg, FriendlyByteBuf buf) {
        // 无字段
    }

    public static PocketMergeToSharedPacket decode(FriendlyByteBuf buf) {
        return new PocketMergeToSharedPacket();
    }

    public static void handle(PocketMergeToSharedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null
                    && ctx.get().getSender().containerMenu instanceof PocketDimensionMenu menu) {
                menu.mergeLocalToShared();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
