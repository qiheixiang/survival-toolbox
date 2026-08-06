package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.DisassembleMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 拆解台配方索引构建进度同步包（服务端 → 客户端）
 * <p>
 * 阶段二（材料→配方索引）在大整合包里有数万条配方，后台构建耗时很长。
 * 通过本包把「已处理 / 总数」实时推给客户端，屏幕上显示 "X/Y" 进度。
 * 配方数量可能超过 DataSlot 的 short 上限，故用 int 走网络包。
 * </p>
 */
public class SyncRecipeIndexProgressPacket {

    private final int processed;
    private final int total;

    public SyncRecipeIndexProgressPacket(int processed, int total) {
        this.processed = processed;
        this.total = total;
    }

    public static void encode(SyncRecipeIndexProgressPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.processed);
        buf.writeInt(msg.total);
    }

    public static SyncRecipeIndexProgressPacket decode(FriendlyByteBuf buf) {
        return new SyncRecipeIndexProgressPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(SyncRecipeIndexProgressPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DisassembleMenu.setClientIndexProgress(msg.processed, msg.total));
        ctx.get().setPacketHandled(true);
    }
}
