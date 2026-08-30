package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.util.PocketStorageHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 次元袋条目同步包（服务端 → 客户端）
 * <p>
 * 每次变更后全量同步：当前显示的非空条目（槽位索引 + 物品 + long 数量）+
 * 翻页偏移 + 页名列表 + 每页条目数 + 当前页索引。
 * 客户端渲染与页列表均以此为准，保证即时刷新、不依赖本地旧数据。
 * </p>
 */
public record PocketDimensionSyncPacket(int pageOffset, int currentPage,
                                        List<String> pageNames, List<Integer> pageCounts,
                                        List<SlotData> slots) implements CustomPacketPayload {

    /** 显示条目：槽位索引（非搜索 = 页内槽位；搜索 = 平铺索引）+ 条目 */
    public record SlotData(int slot, PocketStorageHelper.Entry entry) {
    }

    public static final Type<PocketDimensionSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_dimension_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PocketDimensionSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.pageOffset());
                buf.writeVarInt(packet.currentPage());
                buf.writeVarInt(packet.pageNames().size());
                for (String name : packet.pageNames()) {
                    buf.writeUtf(name, 32);
                }
                for (int count : packet.pageCounts()) {
                    buf.writeVarInt(count);
                }
                buf.writeVarInt(packet.slots().size());
                for (SlotData sd : packet.slots()) {
                    buf.writeVarInt(sd.slot());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, sd.entry().stack());
                    buf.writeLong(sd.entry().count());
                }
            },
            buf -> {
                int pageOffset = buf.readVarInt();
                int currentPage = buf.readVarInt();
                int nameSize = buf.readVarInt();
                List<String> pageNames = new ArrayList<>(nameSize);
                for (int i = 0; i < nameSize; i++) {
                    pageNames.add(buf.readUtf(32));
                }
                List<Integer> pageCounts = new ArrayList<>(nameSize);
                for (int i = 0; i < nameSize; i++) {
                    pageCounts.add(buf.readVarInt());
                }
                int size = buf.readVarInt();
                List<SlotData> slots = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    int slot = buf.readVarInt();
                    ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    long count = buf.readLong();
                    slots.add(new SlotData(slot, new PocketStorageHelper.Entry(stack, count)));
                }
                return new PocketDimensionSyncPacket(pageOffset, currentPage, pageNames, pageCounts, slots);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
