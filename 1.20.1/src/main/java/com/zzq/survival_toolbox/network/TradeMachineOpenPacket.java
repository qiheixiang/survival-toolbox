package com.zzq.survival_toolbox.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 交易机：界面打开"标记包"（服务端 → 客户端）
 * <p>
 * 交易机的界面**就是原版村民交易界面**：{@code MerchantMenu} 的构造把菜单类型写死成
 * {@code MenuType.MERCHANT}，客户端拿到的永远是原版 {@code MerchantScreen}，
 * 本模组继承出来的界面类根本不会被实例化。
 * 所以搜索框改成"在原版界面上叠画"，前提是客户端得知道**现在这个村民界面是交易机**。
 * </p>
 * <p>
 * 这个包就是那个标记：带上服务端菜单的容器 id，客户端只认"当前界面是
 * {@code MerchantScreen} 且容器 id 相符"。关界面 / 打开别的界面时客户端自己把标记清掉
 * （见 {@code client/gui/TradeMachineSearchOverlay}），所以不需要额外的解除包。
 * </p>
 */
public class TradeMachineOpenPacket {

    private final int containerId;

    public TradeMachineOpenPacket(int containerId) {
        this.containerId = containerId;
    }

    public static void encode(TradeMachineOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
    }

    public static TradeMachineOpenPacket decode(FriendlyByteBuf buf) {
        return new TradeMachineOpenPacket(buf.readVarInt());
    }

    public static void handle(TradeMachineOpenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        // 只认服务端→客户端这个方向：万一有客户端把这个包发上来（改过的客户端/串包），
        // 这里必须提前退出，绝不能让专用服务器去碰客户端类。
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }
        // 客户端专属逻辑：这里只是**执行时**才解析客户端类，专用服务器不会加载它
        context.enqueueWork(() -> com.zzq.survival_toolbox.client.gui.TradeMachineSearchOverlay
                .mark(msg.containerId));
        context.setPacketHandled(true);
    }
}
