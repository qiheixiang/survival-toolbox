package com.zzq.survival_toolbox.network;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 次元袋快捷收纳包（客户端 → 服务端）
 * <p>
 * 背包界面内（类似精妙背包）：
 * mode=1：鼠标拿着次元袋，右键点物品格 → 该物品收进手里的袋（slotIndex = 物品格）
 * mode=2：鼠标拿着物品，右键点次元袋格 → 手里物品收进该袋（slotIndex = 袋格）
 * </p>
 */
public record PocketQuickDepositPacket(int mode, int slotIndex) implements CustomPacketPayload {

    /** 鼠标携带的是次元袋，右键目标 = 物品格 */
    public static final int MODE_BAG_IN_HAND = 1;
    /** 鼠标携带的是物品，右键目标 = 次元袋格 */
    public static final int MODE_BAG_AT_SLOT = 2;
    /** 鼠标携带的是次元袋，右键背包界面空白处：一键收纳背包物品（不含快捷栏） */
    public static final int MODE_DEPOSIT_ALL = 3;

    public static final Type<PocketQuickDepositPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "pocket_quick_deposit"));

    public static final StreamCodec<FriendlyByteBuf, PocketQuickDepositPacket> STREAM_CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    PocketQuickDepositPacket::mode,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    PocketQuickDepositPacket::slotIndex,
                    PocketQuickDepositPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PocketQuickDepositPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) return;

            try {
                if (payload.mode() == MODE_DEPOSIT_ALL) {
                    // 一键收纳：把手里的袋/手持袋 ← 背包物品（快捷栏除外）
                    ItemStack bag = menu.getCarried();
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) bag = findBag(player);
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) return;
                    net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                    // items 0-8 快捷栏，9-35 背包，只收背包部分
                    for (int i = 9; i < inv.items.size(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;
                        int moved = depositVerified(player, bag, stack);
                        if (moved <= 0) continue;                       // 一个都没进去：原样留着，绝不凭空删玩家的东西
                        if (moved >= stack.getCount()) {
                            inv.setItem(i, ItemStack.EMPTY);
                        } else {
                            ItemStack remain = stack.copy();
                            remain.setCount(stack.getCount() - moved);
                            inv.setItem(i, remain);
                        }
                    }
                    return;
                }

                int idx = payload.slotIndex();
                if (idx < 0 || idx >= menu.slots.size()) return;
                Slot slot = menu.getSlot(idx);
                if (slot == null) return;

                if (payload.mode() == MODE_BAG_IN_HAND) {
                    // 鼠标上的袋 ← 物品格
                    ItemStack bag = menu.getCarried();
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) return;
                    if (!slot.hasItem()) return;
                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) return;
                    int moved = depositVerified(player, bag, stack);
                    if (moved > 0) applyRemainder(slot, stack, stack.getCount() - moved);
                } else if (payload.mode() == MODE_BAG_AT_SLOT) {
                    // 物品格上的袋 ← 鼠标上的物品
                    if (!slot.hasItem()) return;
                    ItemStack bag = slot.getItem();
                    if (!bag.is(ModItems.POCKET_DIMENSION.get())) return;
                    ItemStack target = menu.getCarried();
                    if (target.isEmpty()) return;
                    int moved = depositVerified(player, bag, target);
                    if (moved > 0) {
                        if (moved >= target.getCount()) {
                            menu.setCarried(ItemStack.EMPTY);
                        } else {
                            ItemStack remain = target.copy();
                            remain.setCount(target.getCount() - moved);
                            menu.setCarried(remain);
                        }
                    }
                    slot.set(bag); // bag NBT 已更新，写回格子
                }
            } catch (Throwable t) {
                // 吞掉异常防止中断流程
            }
        });
    }

    /**
     * 把一件东西收进袋子，并<b>回读确认真的进去了</b>。
     * <p>
     * ⚠️⚠️ <b>绝不能拿 {@code quickDeposit} 的返回值当"成功"判据</b>（必须遵守的约束）：
     * 它返回的是"还剩多少没装下"，而袋子容量无限、装不下会自动新建页，所以**恒为 0** ——
     * 写入因为任何原因没落盘时它也照样返回 0。旧代码就是"返回 0 就把来源那一格清掉"，
     * 一旦写入没落盘，玩家看到的就是<b>"右键一收，东西直接没了"</b>
     * （"创造模式下右键收纳，创造口袋直接消失了"就是这一类）。
     * 所以这里入库前后各数一次袋子里的数量，<b>只有真的多出来的那些</b>才从玩家身上扣。
     * </p>
     *
     * @return 真正进了袋子的数量（0 = 一个都没进去，调用方必须原样留下）
     */
    private static int depositVerified(ServerPlayer player, ItemStack bag, ItemStack stack) {
        long before = PocketStorageHelper.countInStorage(player, bag, stack, player.level().registryAccess());
        PocketStorageHelper.quickDeposit(player, bag, stack.copy(), player.level().registryAccess());
        long after = PocketStorageHelper.countInStorage(player, bag, stack, player.level().registryAccess());
        long moved = after - before;
        return moved <= 0 ? 0 : (int) Math.min(moved, stack.getCount());
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
