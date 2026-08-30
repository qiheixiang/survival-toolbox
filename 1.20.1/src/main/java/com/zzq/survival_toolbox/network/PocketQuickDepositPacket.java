package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 次元袋快捷收纳包（客户端 → 服务端）
 * <p>
 * 背包界面内（类似精妙背包）：
 * mode=1：鼠标拿着次元袋，右键点物品格 → 该物品收进手里的袋（slotIndex = 物品格）
 * mode=2：鼠标拿着物品，右键点次元袋格 → 手里物品收进该袋（slotIndex = 袋格）
 * </p>
 */
public class PocketQuickDepositPacket {

    /** 鼠标携带的是次元袋，右键目标 = 物品格 */
    public static final int MODE_BAG_IN_HAND = 1;
    /** 鼠标携带的是物品，右键目标 = 次元袋格 */
    public static final int MODE_BAG_AT_SLOT = 2;
    /** 鼠标携带的是次元袋，右键背包界面空白处：一键收纳背包物品（不含快捷栏） */
    public static final int MODE_DEPOSIT_ALL = 3;

    private final int mode;
    private final int slotIndex;

    public PocketQuickDepositPacket(int mode, int slotIndex) {
        this.mode = mode;
        this.slotIndex = slotIndex;
    }

    public static void encode(PocketQuickDepositPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
        buf.writeVarInt(msg.slotIndex);
    }

    public static PocketQuickDepositPacket decode(FriendlyByteBuf buf) {
        return new PocketQuickDepositPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PocketQuickDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) return;

            try {
                if (msg.mode == MODE_DEPOSIT_ALL) {
                    // 一键收纳：把手里的袋/手持袋 ← 背包物品（快捷栏除外）
                    ItemStack bag = menu.getCarried();
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) bag = findBag(player);
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) return;
                    net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                    int stored = 0;
                    for (int i = 9; i < inv.items.size(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;
                        long after = PocketStorageHelper.quickDeposit(bag, stack.copy());
                        stored++;
                        if (after <= 0) {
                            inv.setItem(i, ItemStack.EMPTY);
                        } else {
                            ItemStack remain = stack.copy();
                            remain.setCount((int) after);
                            inv.setItem(i, remain);
                        }
                    }
                    return;
                }

                int idx = msg.slotIndex;
                if (idx < 0 || idx >= menu.slots.size()) return;
                Slot slot = menu.getSlot(idx);
                if (slot == null) return;

                if (msg.mode == MODE_BAG_IN_HAND) {
                    // 鼠标上的袋 ← 物品格
                    ItemStack bag = menu.getCarried();
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) return;
                    if (!slot.hasItem()) return;
                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) return;
                    long after = PocketStorageHelper.quickDeposit(bag, stack.copy());
                    applyRemainder(slot, stack, after);
                } else if (msg.mode == MODE_BAG_AT_SLOT) {
                    // 物品格上的袋 ← 鼠标上的物品
                    if (!slot.hasItem()) return;
                    ItemStack bag = slot.getItem();
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) return;
                    ItemStack target = menu.getCarried();
                    if (target.isEmpty()) return;
                    long after = PocketStorageHelper.quickDeposit(bag, target.copy());
                    if (after <= 0) {
                        menu.setCarried(ItemStack.EMPTY);
                    } else {
                        ItemStack remain = target.copy();
                        remain.setCount((int) after);
                        menu.setCarried(remain);
                    }
                    slot.set(bag); // bag NBT 已更新，写回格子
                }
            } catch (Throwable t) {
                // 捕获并吞掉异常，防止中断后续处理流程。
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void applyRemainder(Slot slot, ItemStack stack, long after) {
        if (after <= 0) {
            slot.set(ItemStack.EMPTY);
        } else {
            ItemStack remain = stack.copy();
            remain.setCount((int) after);
            slot.set(remain);
        }
    }

    /** 主手/副手中的次元袋 */
    private static ItemStack findBag(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.POCKET_DIMENSION.get())) return main;
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.POCKET_DIMENSION.get())) return off;
        return ItemStack.EMPTY;
    }
}
