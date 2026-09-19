package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.util.PocketJeiTransfer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * JEI 的"+"号转移包（客户端 → 服务端）
 * <p>
 * <b>为什么要自己搬，而不是交给 JEI</b>（实测出现过的问题）：
 * JEI 的 {@code BasicRecipeTransferHandler} 只跑在<b>客户端</b>——它先按"玩家背包 + 输入槽"算能不能转
 * （算不出来就报 {@code jei.tooltip.error.recipe.transfer.missing} = "材料不足"），
 * 能转就发一个 {@code PacketRecipeTransfer} 到服务端，由 {@code BasicRecipeTransferHandlerServer} **直接摆格**。
 * 也就是说"从袋子里取料"这件事<b>根本没有机会发生</b>：
 * 客户端判定时说袋子为空（它只看背包），服务端那一段又是 JEI 自己的静态代码、不经过本模组的处理器。
 * 实测反馈的"袋里有 1.8 千云杉木板，点 + 却提示材料不足"就是这么来的。
 * </p>
 * <p>
 * 因此这里改成：客户端把"配方每一格可以用哪些物品"（按优先级排好的候选表）发上来，
 * <b>服务端</b>负责取料并摆格——取料顺序是 玩家背包/副手 → 袋子（严格按当前共享/本地模式，
 * 且<b>只看"打开着的那一页"</b>，见 {@code PocketJeiTransfer#effectivePage}），<b>绝不凭空造物品</b>。
 * </p>
 */
public class PocketJeiTransferPacket {

    /** 单格候选物品数上限（标签可能很大，需要避免数据包体积过大） */
    private static final int MAX_CANDIDATES = 32;

    /** 配方每一格的候选物品（按配方格顺序；空列表 = 这一格配方是空的） */
    private final List<List<ItemStack>> plan;
    /** 配方第一格对应的容器槽位下标 */
    private final int inputSlotStart;
    /** 配方格数（合成 9、锻造 3） */
    private final int inputSlotCount;
    /**
     * 客户端看到的"打开的袋子页"下标（{@code PocketStorageHelper.PAGE_ALL} = 没有页面开着/看所有页）。
     * 服务端开着次元袋界面时会用<b>服务端自己的当前页</b>覆盖它（那边才是权威）。
     */
    private final int pageIndex;

    public PocketJeiTransferPacket(List<List<ItemStack>> plan, int inputSlotStart, int inputSlotCount,
                                   int pageIndex) {
        this.plan = plan;
        this.inputSlotStart = inputSlotStart;
        this.inputSlotCount = inputSlotCount;
        this.pageIndex = pageIndex;
    }

    public static void encode(PocketJeiTransferPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.plan.size());
        for (List<ItemStack> candidates : msg.plan) {
            int n = Math.min(candidates.size(), MAX_CANDIDATES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                buf.writeItem(candidates.get(i));
            }
        }
        buf.writeVarInt(msg.inputSlotStart);
        buf.writeVarInt(msg.inputSlotCount);
        buf.writeVarInt(msg.pageIndex);
    }

    public static PocketJeiTransferPacket decode(FriendlyByteBuf buf) {
        int slots = buf.readVarInt();
        List<List<ItemStack>> plan = new ArrayList<>(slots);
        for (int s = 0; s < slots; s++) {
            int n = buf.readVarInt();
            List<ItemStack> candidates = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                candidates.add(buf.readItem());
            }
            plan.add(candidates);
        }
        return new PocketJeiTransferPacket(plan, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PocketJeiTransferPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                PocketJeiTransfer.apply(ctx.get().getSender(), msg.plan, msg.inputSlotStart, msg.inputSlotCount,
                        msg.pageIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
