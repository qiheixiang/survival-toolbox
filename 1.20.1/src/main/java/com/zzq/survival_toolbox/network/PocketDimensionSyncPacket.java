package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 次元袋条目同步包（服务端 → 客户端）
 * <p>
 * 每次变更后全量同步：当前显示的非空条目（槽位索引 + 物品 + long 数量）+
 * 翻页偏移 + 页名列表 + 每页条目数 + 当前页索引。
 * 客户端渲染与页列表均以此为准，保证即时刷新、不依赖本地旧数据。
 * </p>
 */
public class PocketDimensionSyncPacket {

    /** 显示条目：槽位索引（非搜索 = 页内槽位；搜索 = 平铺索引）+ 条目 */
    public static class SlotData {
        public final int slot;
        public final PocketStorageHelper.Entry entry;

        public SlotData(int slot, PocketStorageHelper.Entry entry) {
            this.slot = slot;
            this.entry = entry;
        }
    }

    private final int pageOffset;
    private final int currentPage;
    private final List<String> pageNames;
    private final List<Integer> pageCounts;
    private final List<SlotData> slots;

    public PocketDimensionSyncPacket(int pageOffset, int currentPage,
                                     List<String> pageNames, List<Integer> pageCounts,
                                     List<SlotData> slots) {
        this.pageOffset = pageOffset;
        this.currentPage = currentPage;
        this.pageNames = pageNames;
        this.pageCounts = pageCounts;
        this.slots = slots;
    }

    public static void encode(PocketDimensionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.pageOffset);
        buf.writeVarInt(msg.currentPage);
        buf.writeVarInt(msg.pageNames.size());
        for (String name : msg.pageNames) {
            buf.writeUtf(name, 32);
        }
        for (int count : msg.pageCounts) {
            buf.writeVarInt(count);
        }
        buf.writeVarInt(msg.slots.size());
        for (SlotData sd : msg.slots) {
            buf.writeVarInt(sd.slot);
            buf.writeItem(sd.entry.stack());
            buf.writeLong(sd.entry.count());
        }
    }

    public static PocketDimensionSyncPacket decode(FriendlyByteBuf buf) {
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
            ItemStack stack = buf.readItem();
            long count = buf.readLong();
            slots.add(new SlotData(slot, new PocketStorageHelper.Entry(stack, count)));
        }
        return new PocketDimensionSyncPacket(pageOffset, currentPage, pageNames, pageCounts, slots);
    }

    public static void handle(PocketDimensionSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof PocketDimensionMenu menu) {
                menu.setSyncedData(msg.pageOffset, msg.currentPage, msg.pageNames, msg.pageCounts, msg.slots);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
