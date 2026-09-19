package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋整理包（客户端 → 服务端，1.20.1 的 SimpleChannel 版）
 * <p>
 * scope：0 = 只整理当前页（默认），1 = 把所有页当成一个整体整理。<br>
 * sortBy：0 = 不分 mod 整体按注册名，1 = 同 mod 聚一起各自按注册名，2 = 按数量（相同数量按注册名）。<br>
 * 排序/合并/压实全部在服务端做（服务端权威），完成后由同步包回显。
 * </p>
 */
public class PocketSortPacket {

    /** 只整理当前页 */
    public static final int SCOPE_CURRENT_PAGE = 0;
    /** 所有页当作一个整体整理 */
    public static final int SCOPE_ALL_PAGES = 1;

    /** 不分 mod，整体按注册名（用注册名的路径部分排） */
    public static final int BY_NAME = 0;
    /** 同 mod 的聚在一起，各自按注册名排 */
    public static final int BY_MOD = 1;
    /** 按数量从多到少，数量相同按注册名排 */
    public static final int BY_COUNT = 2;
    /** 按物品/流体的**标签**排（同类的放一起，比如同种木头、同种锭） */
    public static final int BY_TAG = 3;

    private final int scope;
    private final int sortBy;

    public PocketSortPacket(int scope, int sortBy) {
        this.scope = scope;
        this.sortBy = sortBy;
    }

    public static void encode(PocketSortPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.scope);
        buf.writeVarInt(msg.sortBy);
    }

    public static PocketSortPacket decode(FriendlyByteBuf buf) {
        return new PocketSortPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PocketSortPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null
                    && ctx.get().getSender().containerMenu instanceof PocketDimensionMenu menu) {
                menu.sort(msg.scope, msg.sortBy);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
