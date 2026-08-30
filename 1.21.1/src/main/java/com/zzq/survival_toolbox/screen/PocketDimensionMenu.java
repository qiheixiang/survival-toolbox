package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.util.PocketStorageHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 随身次元袋菜单（多页版）
 * <p>
 * 槽位：0-53 存储格（54 格 = 当前显示：当前页或搜索结果），54-80 玩家背包，81-89 快捷栏。
 * 左侧 64px 为页列表（新建/删除/重命名/切换），顶部搜索框跨页搜索。
 * 客户端不预测交互（服务端执行后经同步包回显），避免虚拟堆叠数据分叉。
 * </p>
 */
public class PocketDimensionMenu extends AbstractContainerMenu {

    public static final int PAGE_LIST_WIDTH = 64;

    private final PocketDimensionContainer container;
    private final Player player;
    private final ItemStack bag;
    private final List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> lastSyncedSlots =
            new java.util.ArrayList<>();
    private int lastSyncedPage = 0;
    private boolean entriesDirty = true;

    /** 客户端构造：从玩家主手物品读取次元袋数据 */
    public PocketDimensionMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, playerInv.player.getMainHandItem());
    }

    public PocketDimensionMenu(int windowId, Inventory playerInv, ItemStack bag) {
        super(ModMenus.POCKET_DIMENSION.get(), windowId);
        this.player = playerInv.player;
        this.bag = bag;
        this.container = new PocketDimensionContainer(bag, playerInv.player.level().registryAccess());
        if (playerInv.player.level().isClientSide) {
            this.container.setClientSide(true);
        }

        // 存储格（54 格，专用槽位处理虚拟堆叠交互；贴图右移留出左侧页列表）
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new PocketSlot(container, row * 9 + col, PAGE_LIST_WIDTH + 8 + col * 18, 18 + row * 18));
            }
        }
        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, PAGE_LIST_WIDTH + 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, PAGE_LIST_WIDTH + 8 + col * 18, 198));
        }
    }

    public PocketDimensionContainer getPocketContainer() {
        return container;
    }

    /** 客户端：接收服务端同步的显示数据（槽位条目 + 翻页偏移 + 页名 + 每页条目数 + 当前页） */
    public void setSyncedData(int pageOffset, int currentPage, List<String> pageNames,
                              List<Integer> pageCounts,
                              List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> slots) {
        this.container.replaceDisplay(slots, pageOffset, pageNames, pageCounts, currentPage);
        this.lastSyncedSlots.clear();
        this.lastSyncedSlots.addAll(slots);
        this.lastSyncedPage = pageOffset;
        this.entriesDirty = false;
    }

    /** 服务端：设置搜索过滤关键词（跨页搜索） */
    public void setSearch(String keyword) {
        container.setSearch(keyword);
        this.entriesDirty = true;
    }

    /** 服务端：页操作（新建/删除/重命名/切换） */
    public void handlePageAction(int action, int index, String name) {
        if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_ADD) {
            container.addPage();
        } else if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_REMOVE) {
            container.removePage(index);
        } else if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_RENAME) {
            container.renamePage(index, name);
        } else if (action == com.zzq.survival_toolbox.network.PocketDimensionPageActionPacket.ACTION_SET_PAGE) {
            container.setCurrentPage(index);
        }
        this.entriesDirty = true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 100) {
            container.setPageOffset(container.getPageOffset() - PocketDimensionContainer.PAGE_SIZE);
            return true;
        } else if (id == 101) {
            container.setPageOffset(container.getPageOffset() + PocketDimensionContainer.PAGE_SIZE);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide) return ItemStack.EMPTY; // 客户端不预测
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        if (index < PocketDimensionContainer.PAGE_SIZE) {
            // 次元袋 → 背包：取整格（≤64），背包满则丢出
            ItemStack taken = container.removeItem(index, 64);
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
            }
        } else {
            // 背包 → 次元袋：并入（同类合并；新种类放当前页空位；页满返回剩余）
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                ItemStack remaining = container.insert(stack);
                slot.set(remaining);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 接管次元袋格子的点击交互：
     * 点击格子 = 精确操作该格：左键取走/放入该格（同类合并、异类交换）；
     * 搜索模式下只允许取走。客户端直接跳过预测（交互由服务端执行后经同步包回显）。
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (player.level().isClientSide) return;
        if (slotId >= 0 && slotId < PocketDimensionContainer.PAGE_SIZE
                && clickType == net.minecraft.world.inventory.ClickType.PICKUP) {
            ItemStack cursor = getCarried();
            if (button == 0) {
                if (cursor.isEmpty()) {
                    // 取走整格（≤64）
                    ItemStack taken = container.removeItem(slotId, 64);
                    if (!taken.isEmpty()) setCarried(taken);
                } else if (!container.isSearching()) {
                    ItemStack inSlot = this.slots.get(slotId).getItem();
                    if (inSlot.isEmpty() || PocketStorageHelper.sameItem(cursor, inSlot)) {
                        // 放入点击的格子（空 = 插到该位置；同类 = 合并到该格）
                        container.setItemAt(slotId, cursor);
                        setCarried(ItemStack.EMPTY);
                    } else {
                        // 异类交换
                        ItemStack taken = container.removeItem(slotId, 64);
                        container.setItemAt(slotId, cursor);
                        setCarried(taken);
                    }
                }
            } else if (button == 1) {
                if (cursor.isEmpty()) {
                    // 取一半
                    ItemStack inSlot = this.slots.get(slotId).getItem();
                    if (!inSlot.isEmpty()) {
                        int half = Math.max(1, inSlot.getCount() / 2);
                        setCarried(container.removeItem(slotId, half));
                    }
                } else if (!container.isSearching()) {
                    // 放一个到点击的格子（仅空/同类格）
                    ItemStack inSlot = this.slots.get(slotId).getItem();
                    if (inSlot.isEmpty() || PocketStorageHelper.sameItem(cursor, inSlot)) {
                        ItemStack one = cursor.copy();
                        one.setCount(1);
                        container.setItemAt(slotId, one);
                        cursor.shrink(1);
                        setCarried(cursor);
                    }
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        // 袋子必须在玩家物品栏中（主手/背包/快捷栏）；
        // 被丢弃、挪出物品栏后返回 false，MC 会自动关闭界面。
        for (ItemStack stack : player.getInventory().items) {
            if (stack == bag) return true;
        }
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 关闭界面时强制把条目写回物品 NBT，防止最后状态丢失
        this.container.save();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // 条目/翻页/过滤/页操作变化时同步显示数据给客户端
        List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> slots = container.getSyncSlots();
        if (entriesDirty
                || container.getPageOffset() != lastSyncedPage
                || !sameSlots(lastSyncedSlots, slots)) {
            lastSyncedPage = container.getPageOffset();
            lastSyncedSlots.clear();
            lastSyncedSlots.addAll(slots);
            entriesDirty = false;
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new com.zzq.survival_toolbox.network.PocketDimensionSyncPacket(
                                container.getPageOffset(), container.getCurrentPage(),
                                container.getPageNames(), container.getPageCounts(), slots));
            }
        }
    }

    private static boolean sameSlots(List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> a,
                                     List<com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData x = a.get(i);
            com.zzq.survival_toolbox.network.PocketDimensionSyncPacket.SlotData y = b.get(i);
            if (x.slot() != y.slot()) return false;
            if (!PocketStorageHelper.sameItem(x.entry().stack(), y.entry().stack())
                    || x.entry().count() != y.entry().count()) {
                return false;
            }
        }
        return true;
    }
}
