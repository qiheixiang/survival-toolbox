package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.screen.PocketDimensionMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋托盘面板操作包（客户端 → 服务端）
 * <p>
 * action: 0=展开托盘页, 1=收起面板, 2=方向设为"送出", 3=方向设为"纳入",
 * 4=展开熔炉页, 5=展开铁砧页, 6=展开锻造台页。
 * 托盘/熔炉本身的内容不在这里传（物品走原版槽位同步、流体走同步包），这里只同步
 * "面板是否展开"和"送出/纳入"这两个状态——它们必须是服务端权威的：
 * 面板状态决定 Shift+点击背包格的去向，方向则存在袋子 NBT 里、关掉界面后右键容器还要用。
 * </p>
 */
public class PocketTrayActionPacket {

    /** 展开托盘页 */
    public static final int ACTION_OPEN = 0;
    /** 收起当前功能页 */
    public static final int ACTION_CLOSE = 1;
    /** 方向 = 送出（托盘 → 容器） */
    public static final int ACTION_SET_OUT = 2;
    /** 方向 = 纳入（容器 → 存储） */
    public static final int ACTION_SET_IN = 3;
    /** 展开熔炉页 */
    public static final int ACTION_OPEN_FURNACE = 4;
    /** 展开铁砧页 */
    public static final int ACTION_OPEN_ANVIL = 5;
    /** 展开锻造台页 */
    public static final int ACTION_OPEN_SMITHING = 6;
    /** 熔炼页：产物放产物格（默认，原版熔炉那样） */
    public static final int ACTION_FURNACE_TO_SLOT = 7;
    /** 熔炼页：产物直接进储物空间 */
    public static final int ACTION_FURNACE_TO_STORAGE = 8;
    /** 展开合成页 */
    public static final int ACTION_OPEN_CRAFTING = 9;
    /** 展开拆解台页 */
    public static final int ACTION_OPEN_DISASSEMBLE = 10;
    /** 拆解页：上一个变体（合成模式下 = 上一条配方） */
    public static final int ACTION_DIS_PREV = 11;
    /** 拆解页：下一个变体（合成模式下 = 下一条配方） */
    public static final int ACTION_DIS_NEXT = 12;
    /** 拆解页：把输入槽里那一叠全部拆掉（原版"批量拆解"按钮） */
    public static final int ACTION_DIS_BULK = 13;
    /** 展开磁铁页（吸收） */
    public static final int ACTION_OPEN_MAGNET = 14;
    /** 磁铁：开/关 */
    public static final int ACTION_MAG_TOGGLE = 15;
    /** 磁铁：范围 -1 */
    public static final int ACTION_MAG_RANGE_DOWN = 16;
    /** 磁铁：范围 +1 */
    public static final int ACTION_MAG_RANGE_UP = 17;
    /** 磁铁：只吸袋里有的 ↔ 什么都吸 */
    public static final int ACTION_MAG_MODE = 18;
    /** 磁铁：黑名单 ↔ 白名单 */
    public static final int ACTION_MAG_LIST_MODE = 19;
    /** 展开补货页 */
    public static final int ACTION_OPEN_RESTOCK = 20;
    /** 补货：开/关 */
    public static final int ACTION_RES_TOGGLE = 21;
    /** 补货范围：只补快捷栏 ↔ 快捷栏 + 整个背包 */
    public static final int ACTION_RES_SCOPE = 22;
    /** 展开切石机页 */
    public static final int ACTION_OPEN_STONECUTTER = 23;
    /** 合成页："自动补充"开/关（取走产物后是否自动从储物空间把材料补回九宫格） */
    public static final int ACTION_CRAFT_REFILL_TOGGLE = 24;

    private final int action;

    public PocketTrayActionPacket(int action) {
        this.action = action;
    }

    public static void encode(PocketTrayActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
    }

    public static PocketTrayActionPacket decode(FriendlyByteBuf buf) {
        return new PocketTrayActionPacket(buf.readVarInt());
    }

    public static void handle(PocketTrayActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null
                    && ctx.get().getSender().containerMenu instanceof PocketDimensionMenu menu) {
                menu.handleTrayAction(msg.action);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
