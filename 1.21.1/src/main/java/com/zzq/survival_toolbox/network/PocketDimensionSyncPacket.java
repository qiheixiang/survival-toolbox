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
 * 翻页偏移 + 页名列表 + 每页条目数 + 当前页索引 + 存储模式 +
 * 流体格掩码 + 托盘面板状态/方向/流体格掩码与数量 + 熔炉页进度（燃烧/烹饪，界面画火焰与箭头用）。
 * 客户端渲染与页列表均以此为准，保证即时刷新、不依赖本地旧数据。
 * </p>
 * <p>
 * 注意：托盘<b>物品格</b>走原版槽位同步；但<b>流体格不能指望它</b>——
 * 实测"水放进托盘后那一格的图标就没了"（客户端只拿到蓝字数量、格子里空的），
 * 所以流体格的显示栈（该流体的桶 + 真实流体 + 存量标记）与存储页流体格一样**走这个包显式同步**；
 * 流体的 mB 数量也必须走这里，因为原版 1.20.1 的物品同步用 byte 写数量，大数值会被截断。
 * </p>
 */
public record PocketDimensionSyncPacket(int pageOffset, int currentPage,
                                        List<String> pageNames, List<Integer> pageCounts,
                                        List<SlotData> slots, boolean shared, long fluidMask,
                                        int panel, boolean trayOut, long trayMask, long[] trayAmounts,
                                        List<ItemStack> trayFluidIcons,
                                        int furnaceBurn, int furnaceBurnTotal,
                                        int furnaceCook, int furnaceCookTotal,
                                        int pageCost, boolean furnaceToSlot)
        implements CustomPacketPayload {

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
                buf.writeBoolean(packet.shared());
                buf.writeLong(packet.fluidMask());
                buf.writeVarInt(packet.panel());
                buf.writeBoolean(packet.trayOut());
                buf.writeLong(packet.trayMask());
                buf.writeVarInt(packet.trayAmounts().length);
                for (long amount : packet.trayAmounts()) {
                    buf.writeLong(amount);
                }
                // 托盘流体格的"图标栈"：和存储页流体格一样走同步包（见 record 注释的说明）
                List<ItemStack> icons = packet.trayFluidIcons();
                int iconCount = 0;
                for (ItemStack icon : icons) {
                    if (!icon.isEmpty()) iconCount++;
                }
                buf.writeVarInt(iconCount);
                for (int i = 0; i < icons.size(); i++) {
                    ItemStack icon = icons.get(i);
                    if (icon.isEmpty()) continue;
                    buf.writeVarInt(i);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon);
                }
                buf.writeVarInt(packet.furnaceBurn());
                buf.writeVarInt(packet.furnaceBurnTotal());
                buf.writeVarInt(packet.furnaceCook());
                buf.writeVarInt(packet.furnaceCookTotal());
                buf.writeVarInt(packet.pageCost());
                buf.writeBoolean(packet.furnaceToSlot());
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
                List<ItemStack> trayFluidIcons = new ArrayList<>(java.util.Collections.nCopies(
                        com.zzq.survival_toolbox.util.PocketTrayStorage.SLOTS, ItemStack.EMPTY));
                for (int i = 0; i < iconCount; i++) {
                    int slot = buf.readVarInt();
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
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
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端收到同步数据后写回当前打开的次元袋菜单。
     * <p>
     * 本包只发往客户端（{@code playToClient}），此方法不会被专用服务器执行，
     * 因此这里的客户端类型引用安全；写在包类里可避免主类出现客户端引用。
     * </p>
     *
     * @param payload 同步数据
     * @param context 网络上下文
     */
    public static void handle(PocketDimensionSyncPacket payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.zzq.survival_toolbox.client.ClientHooks.applyPocketDimensionSync(
                payload.pageOffset(), payload.currentPage(),
                payload.pageNames(), payload.pageCounts(), payload.slots(), payload.shared(),
                payload.fluidMask(), payload.panel(), payload.trayOut(), payload.trayMask(),
                payload.trayAmounts(), payload.trayFluidIcons(),
                payload.furnaceBurn(), payload.furnaceBurnTotal(),
                payload.furnaceCook(), payload.furnaceCookTotal(), payload.pageCost(),
                payload.furnaceToSlot()));
    }
}
