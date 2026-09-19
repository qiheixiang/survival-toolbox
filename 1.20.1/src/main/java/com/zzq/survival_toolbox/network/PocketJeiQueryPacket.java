package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.util.PocketJeiTransfer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * JEI"+"号的可转移性查询包（客户端 → 服务端）
 * <p>
 * <b>为什么需要它</b>（实测："1.20.1 就算物品不足，+ 号也能点"）：
 * JEI 的判定跑在客户端，而<b>共享模式袋子的数据在服务器存档里（按玩家 UUID 存），客户端根本看不见</b>。
 * 于是客户端只能说"我不知道袋子里有什么"，老代码为了不误报"材料不足"就干脆<b>一律放行</b>——
 * 结果就是环境不对时 + 号照样亮着，点了才发现摆不上（新建的袋子默认就是共享模式，所以所有玩家都会遇到）。
 * 光看客户端是解决不了的：客户端能看到的永远只有"这一页显示出来的那 54 格"，
 * 而原版工作台界面下连这个都看不到（没有袋子的页开着）。
 * </p>
 * <p>
 * 所以这里开一条"问服务端"的路：客户端把 <b>配方每格的候选表 + 输入区间 + 它看到的页号 + 一个签名</b> 发上来，
 * 服务端用<b>真正的库存</b>（背包 + 副手 + 输入格 + 袋子，页号以服务端自己开着的界面为准）算一遍，
 * 再用 {@link PocketJeiQueryReplyPacket} 把答案回给客户端。
 * </p>
 * <p>
 * <b>签名（signature）</b>是客户端按"袋子 + 配方 + 输入格内容 + 页号"算出来的一个 long：
 * 客户端缓存答案时用它当键，回复里原样带回来，客户端就知道这条回复说的是哪一次询问。
 * 这样"同一个配方反复悬停"只发一次包，而材料一变（签名就变）会立刻重新问。
 * </p>
 * <p>
 * 包体大小沿用 {@code MAX_CANDIDATES = 32} 的约定（和转移包一致），避免标签把包撑爆。
 * </p>
 */
public class PocketJeiQueryPacket {

    /** 单格候选物品数上限（必须和 {@link PocketJeiTransferPacket} 保持一致） */
    private static final int MAX_CANDIDATES = 32;

    /** 配方每一格的候选物品（按配方格顺序；空列表 = 这一格配方是空的） */
    private final List<List<ItemStack>> plan;
    /** 配方第一格对应的容器槽位下标 */
    private final int inputSlotStart;
    /** 配方格数（合成 9、锻造 3） */
    private final int inputSlotCount;
    /** 客户端看到的"打开的袋子页"下标（{@code PocketStorageHelper.PAGE_ALL} = 所有页） */
    private final int pageIndex;
    /** 客户端算的缓存键（原样回传） */
    private final long signature;

    public PocketJeiQueryPacket(List<List<ItemStack>> plan, int inputSlotStart, int inputSlotCount,
                                int pageIndex, long signature) {
        this.plan = plan;
        this.inputSlotStart = inputSlotStart;
        this.inputSlotCount = inputSlotCount;
        this.pageIndex = pageIndex;
        this.signature = signature;
    }

    public static void encode(PocketJeiQueryPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.signature);
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

    public static PocketJeiQueryPacket decode(FriendlyByteBuf buf) {
        long signature = buf.readLong();
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
        return new PocketJeiQueryPacket(plan, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), signature);
    }

    public static void handle(PocketJeiQueryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // ⚠️ 包处理里抛异常 = 单人模式直接崩游戏：算不出来就当作"凑不齐"，绝不能把异常抛出去
            boolean ok = false;
            try {
                ok = PocketJeiTransfer.canFill(player, msg.plan, msg.inputSlotStart, msg.inputSlotCount,
                        msg.pageIndex);
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger()
                        .error("[次元袋] JEI 可转移性查询失败（一律按「凑不齐」回复，宁可点不动也不要复制物品）", t);
            }
            SurvivalToolbox.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PocketJeiQueryReplyPacket(msg.signature, ok));
        });
        ctx.get().setPacketHandled(true);
    }
}
