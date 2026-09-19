package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 次元袋整理包（客户端 → 服务端）
 * <p>
 * scope：0 = 只整理当前页（默认），1 = 把所有页当成一个整体整理。<br>
 * sortBy：0 = 不分 mod 整体按注册名，1 = 同 mod 聚一起各自按注册名，2 = 按数量（相同数量按注册名）。<br>
 * 排序/合并/压实全部在服务端做（服务端权威），完成后由同步包回显。
 * </p>
 */
public record PocketSortPacket(int scope, int sortBy) implements CustomPacketPayload {

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

    public static final Type<PocketSortPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_sort"));

    public static final StreamCodec<FriendlyByteBuf, PocketSortPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PocketSortPacket::scope,
            ByteBufCodecs.VAR_INT, PocketSortPacket::sortBy,
            PocketSortPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
