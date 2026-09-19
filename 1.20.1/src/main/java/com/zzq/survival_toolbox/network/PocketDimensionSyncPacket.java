package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.util.PocketStorageHelper;
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
 * 翻页偏移 + 页名列表 + 每页条目数 + 当前页索引 + 存储模式 +
 * 流体格掩码 + 托盘面板状态/方向/流体格掩码与数量 + 熔炉页进度（燃烧/烹饪，界面画火焰与箭头用）。
 * 客户端渲染与页列表均以此为准，保证即时刷新、不依赖本地旧数据。
 * </p>
 * <p>
 * 注意：托盘<b>物品格</b>走原版槽位同步；但<b>流体格不能指望它</b>——
 * 实测：水放进托盘后那一格的图标会消失（客户端只拿到蓝字数量、格子里空的），
 * 所以流体格的显示栈（该流体的桶 + 真实流体 + 存量标记）与存储页流体格一样**走这个包显式同步**；
 * 流体的 mB 数量也必须走这里，因为 1.20.1 的 {@code FriendlyByteBuf#writeItem} 用 byte 写数量，大数值会被截断。
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
    /** true = 共享（末影箱式）存储，false = 本地存储 */
    private final boolean shared;
    /** 存储页哪些格是流体（54 位掩码） */
    private final long fluidMask;
    /** 当前展开的功能页（0 = 全部收起，1 = 托盘，2 = 熔炉） */
    private final int panel;
    /** 托盘方向（true = 送出） */
    private final boolean trayOut;
    /** 托盘哪些格是流体（27 位掩码） */
    private final long trayMask;
    /** 托盘每格的流体 mB 数量 */
    private final long[] trayAmounts;
    /** 托盘流体格的显示栈（桶图标 + 流体本体 + 存量标记；非流体格 = 空栈）—— 见类注释 */
    private final List<ItemStack> trayFluidIcons;
    /** 熔炉：当前这格燃料还能烧多少 tick / 总燃烧时长 */
    private final int furnaceBurn;
    private final int furnaceBurnTotal;
    /** 熔炉：当前物品的烹饪进度 / 需要的总时长 */
    private final int furnaceCook;
    private final int furnaceCookTotal;
    /** 铁砧页：原版算出来的经验等级花费（界面画"花费 N 级"） */
    private final int pageCost;
    /** 熔炼页产物去处（true = 放产物格） */
    private final boolean furnaceToSlot;

    public PocketDimensionSyncPacket(int pageOffset, int currentPage,
                                     List<String> pageNames, List<Integer> pageCounts,
                                     List<SlotData> slots, boolean shared, long fluidMask,
                                     int panel, boolean trayOut, long trayMask, long[] trayAmounts,
                                     List<ItemStack> trayFluidIcons,
                                     int furnaceBurn, int furnaceBurnTotal,
                                     int furnaceCook, int furnaceCookTotal, int pageCost,
                                     boolean furnaceToSlot) {
        this.pageOffset = pageOffset;
        this.currentPage = currentPage;
        this.pageNames = pageNames;
        this.pageCounts = pageCounts;
        this.slots = slots;
        this.shared = shared;
        this.fluidMask = fluidMask;
        this.panel = panel;
        this.trayOut = trayOut;
        this.trayMask = trayMask;
        this.trayAmounts = trayAmounts;
        this.trayFluidIcons = trayFluidIcons;
        this.furnaceBurn = furnaceBurn;
        this.furnaceBurnTotal = furnaceBurnTotal;
        this.furnaceCook = furnaceCook;
        this.furnaceCookTotal = furnaceCookTotal;
        this.pageCost = pageCost;
        this.furnaceToSlot = furnaceToSlot;
    }

    public boolean isShared() {
        return shared;
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
        buf.writeBoolean(msg.shared);
        buf.writeLong(msg.fluidMask);
        buf.writeVarInt(msg.panel);
        buf.writeBoolean(msg.trayOut);
        buf.writeLong(msg.trayMask);
        buf.writeVarInt(msg.trayAmounts.length);
        for (long amount : msg.trayAmounts) {
            buf.writeLong(amount);
        }
        // 托盘流体格的"图标栈"：和存储页流体格一样走同步包（见类注释的说明）
        int iconCount = 0;
        for (ItemStack icon : msg.trayFluidIcons) {
            if (!icon.isEmpty()) iconCount++;
        }
        buf.writeVarInt(iconCount);
        for (int i = 0; i < msg.trayFluidIcons.size(); i++) {
            ItemStack icon = msg.trayFluidIcons.get(i);
            if (icon.isEmpty()) continue;
            buf.writeVarInt(i);
            buf.writeItem(icon);
        }
        buf.writeVarInt(msg.furnaceBurn);
        buf.writeVarInt(msg.furnaceBurnTotal);
        buf.writeVarInt(msg.furnaceCook);
        buf.writeVarInt(msg.furnaceCookTotal);
        buf.writeVarInt(msg.pageCost);
        buf.writeBoolean(msg.furnaceToSlot);
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
        boolean shared = buf.readBoolean();
        long fluidMask = buf.readLong();
        int panel = buf.readVarInt();
        boolean trayOut = buf.readBoolean();
        long trayMask = buf.readLong();
        int amountSize = buf.readVarInt();
        long[] trayAmounts = new long[amountSize];
        for (int i = 0; i < amountSize; i++) {
            trayAmounts[i] = buf.readLong();
        }
        int iconCount = buf.readVarInt();
        List<ItemStack> trayFluidIcons = new ArrayList<>(
                java.util.Collections.nCopies(com.zzq.survival_toolbox.util.PocketTrayStorage.SLOTS, ItemStack.EMPTY));
        for (int i = 0; i < iconCount; i++) {
            int slot = buf.readVarInt();
            ItemStack icon = buf.readItem();
            if (slot >= 0 && slot < trayFluidIcons.size()) trayFluidIcons.set(slot, icon);
        }
        int furnaceBurn = buf.readVarInt();
        int furnaceBurnTotal = buf.readVarInt();
        int furnaceCook = buf.readVarInt();
        int furnaceCookTotal = buf.readVarInt();
        int pageCost = buf.readVarInt();
        boolean furnaceToSlot = buf.readBoolean();
        return new PocketDimensionSyncPacket(pageOffset, currentPage, pageNames, pageCounts, slots,
                shared, fluidMask, panel, trayOut, trayMask, trayAmounts, trayFluidIcons,
                furnaceBurn, furnaceBurnTotal, furnaceCook, furnaceCookTotal, pageCost, furnaceToSlot);
    }

    public static void handle(PocketDimensionSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // 客户端处理逻辑走 client 包入口：本类会被专用服务器加载，这里不能出现客户端类型
        ctx.get().enqueueWork(() -> com.zzq.survival_toolbox.client.ClientHooks.applyPocketDimensionSync(
                msg.pageOffset, msg.currentPage, msg.pageNames, msg.pageCounts, msg.slots, msg.isShared(),
                msg.fluidMask, msg.panel, msg.trayOut, msg.trayMask, msg.trayAmounts, msg.trayFluidIcons,
                msg.furnaceBurn, msg.furnaceBurnTotal, msg.furnaceCook, msg.furnaceCookTotal,
                msg.pageCost, msg.furnaceToSlot));
        ctx.get().setPacketHandled(true);
    }
}
