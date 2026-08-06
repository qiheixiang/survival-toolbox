package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.screen.DisassembleMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 拆解台配方索引构建进度同步包（服务端 → 客户端）
 * <p>
 * 阶段二（材料→配方索引）在 ATM10 这类大整合包里有数万条配方，后台构建耗时很长。
 * 通过本包把「已处理 / 总数」实时推给客户端，屏幕上显示 "X/Y" 进度。
 * 配方数量可能超过 DataSlot 的 short 上限，故用 int 走网络包。
 * </p>
 */
public class SyncRecipeIndexProgressPacket implements CustomPacketPayload {

    public static final Type<SyncRecipeIndexProgressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, "sync_recipe_index_progress"));

    public static final StreamCodec<FriendlyByteBuf, SyncRecipeIndexProgressPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeInt(msg.processed);
                buf.writeInt(msg.total);
            },
            buf -> new SyncRecipeIndexProgressPacket(buf.readInt(), buf.readInt())
    );

    private final int processed;
    private final int total;

    public SyncRecipeIndexProgressPacket(int processed, int total) {
        this.processed = processed;
        this.total = total;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncRecipeIndexProgressPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DisassembleMenu.setClientIndexProgress(msg.processed, msg.total));
    }
}
