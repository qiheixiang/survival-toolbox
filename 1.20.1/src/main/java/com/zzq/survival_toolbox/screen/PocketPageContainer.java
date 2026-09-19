package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.util.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 功能页里的普通物品格（数据存在袋子 NBT 里）
 * <p>
 * 铁砧页 / 锻造台页的输入格都用它：和托盘一样走<b>原版堆叠语义</b>（每格一叠、不超过 64），
 * 交互直接交给原版 {@code doClick}。格子内容按槽位号存成袋子 NBT 里的一组键
 * （{@code key + 槽位号}，例如铁砧的 {@code AnvilIn0/AnvilIn1}），
 * 所以东西留在页里不会丢，关掉界面再开还在。
 * </p>
 * <p>
 * 客户端只做显示：内容由原版槽位同步写入（服务端的真实数据在袋子 NBT 与
 * {@link com.zzq.survival_toolbox.util.PocketPageEngine} 里）。
 * </p>
 */
public class PocketPageContainer implements Container {

    private final ItemStack bag;
    private final String key;
    private final int size;
    private boolean clientSide = false;
    private final ItemStack[] display;

    public PocketPageContainer(ItemStack bag, String key, int size, boolean clientSide) {
        this.bag = bag;
        this.key = key;
        this.size = size;
        this.clientSide = clientSide;
        this.display = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            this.display[i] = ItemStack.EMPTY;
        }
        if (!clientSide) {
            reload();
        }
    }

    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    /** 该页用了哪些 NBT 键（保存/读取都用它，别在别处硬写字面量） */
    private String keyOf(int slot) {
        return this.key + slot;
    }

    /** 服务端：从袋子 NBT 读回全部格 */
    public void reload() {
        if (clientSide) return;
        CompoundTag root = this.bag.getTag();
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = ItemStack.EMPTY;
            if (root != null) {
                CompoundTag slotTag = root.getCompound(keyOf(i));
                if (!slotTag.isEmpty()) {
                    stack = ItemStack.of(slotTag);
                }
            }
            this.display[i] = stack;
        }
    }

    /** 服务端：把全部格写回袋子 NBT（只动这一页自己的键） */
    public void save() {
        if (clientSide) return;
        // 复制出来改、最后写回：就地改物品里那份标签会和网络编码线程抢（详见 ItemNbt 注释）
        CompoundTag root = ItemNbt.copyForEdit(this.bag);
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.display[i];
            if (stack.isEmpty()) {
                root.remove(keyOf(i));
            } else {
                root.put(keyOf(i), stack.save(new CompoundTag()));
            }
        }
        ItemNbt.setTag(this.bag, root);
    }

    // ============================================================
    // Container 接口（原版堆叠语义）
    // ============================================================

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.display) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= this.size) return ItemStack.EMPTY;
        return this.display[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (clientSide || slot < 0 || slot >= this.size || amount <= 0) return ItemStack.EMPTY;
        ItemStack current = this.display[slot];
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        if (current.isEmpty()) this.display[slot] = ItemStack.EMPTY;
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.size) return;
        if (clientSide) {
            this.display[slot] = stack.copy(); // 客户端：接受原版槽位同步
            return;
        }
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            int max = Math.min(copy.getMaxStackSize(), getMaxStackSize());
            if (copy.getCount() > max) copy.setCount(max);
        }
        this.display[slot] = copy;
        setChanged();
    }

    @Override
    public void setChanged() {
        if (clientSide) return;
        save();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        if (clientSide) return;
        for (int i = 0; i < this.size; i++) {
            this.display[i] = ItemStack.EMPTY;
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}
