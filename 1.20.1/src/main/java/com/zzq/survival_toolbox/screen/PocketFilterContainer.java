package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.util.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 名单容器（幽灵条目）—— 磁铁页 / 万向漏斗等名单类功能共用
 * <p>
 * 设计规则：名单里放的不是实物，而是"一份物品信息" —— 拿物品往名单格里放会
 * <b>复制一份信息进去，手里的实物退回袋子储物空间</b>；名单里的条目<b>取不出来</b>；
 * <b>右键点条目直接删除</b>。
 * </p>
 * <p>
 * 所以这个容器只当"样板表"用：格子一律<b>只读</b>（原版既不能放也不能拿，连双击收集都挡掉），
 * 放/删都由菜单 {@code clicked()} 拦截后调 {@link #addSample}/{@link #removeSample}；
 * 数据存袋子 NBT 的<b>一个 ListTag 键</b>（例如 {@code MagFilter}），每条数量恒为 1；
 * 客户端只做显示：内容由原版槽位同步写入。
 * </p>
 */
public class PocketFilterContainer implements Container {

    private final ItemStack bag;
    private final String key;
    private final int size;
    private boolean clientSide = false;
    private final ItemStack[] display;

    public PocketFilterContainer(ItemStack bag, String key, int size, boolean clientSide) {
        this.bag = bag;
        this.key = key;
        this.size = size;
        this.clientSide = clientSide;
        this.display = new ItemStack[size];
        for (int i = 0; i < size; i++) this.display[i] = ItemStack.EMPTY;
        if (!clientSide) reload();
    }

    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    /** 服务端：从袋子 NBT 读回名单 */
    public void reload() {
        if (clientSide) return;
        for (int i = 0; i < this.size; i++) this.display[i] = ItemStack.EMPTY;
        CompoundTag root = ItemNbt.getTag(this.bag);
        if (root == null) return;
        ListTag list = root.getList(this.key, Tag.TAG_COMPOUND);
        for (int i = 0; i < this.size && i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                ItemStack one = stack.copy();
                one.setCount(1);
                this.display[i] = one;
            }
        }
    }

    /** 服务端：整份写回（复制出来改、再写回，见 ItemNbt 的线程安全说明） */
    public void save() {
        if (clientSide) return;
        ItemNbt.edit(this.bag, root -> {
            ListTag list = new ListTag();
            for (int i = 0; i < this.size; i++) {
                ItemStack stack = this.display[i];
                if (stack.isEmpty()) continue;
                ItemStack one = stack.copy();
                one.setCount(1);
                list.add(one.save(new CompoundTag()));
            }
            root.put(this.key, list);
        });
    }

    /** 加一条"物品信息"（幽灵条目）；已有同种或格子满了返回 false */
    public boolean addSample(ItemStack stack) {
        if (clientSide || stack.isEmpty()) return false;
        for (ItemStack sample : this.display) {
            if (!sample.isEmpty() && com.zzq.survival_toolbox.util.PocketStorageHelper.sameItem(sample, stack)) return false;
        }
        for (int i = 0; i < this.size; i++) {
            if (this.display[i].isEmpty()) {
                ItemStack one = stack.copy();
                one.setCount(1);
                this.display[i] = one;
                setChanged();
                return true;
            }
        }
        return false;
    }

    /** 删掉第 slot 条（右键点条目） */
    public void removeSample(int slot) {
        if (clientSide || slot < 0 || slot >= this.size) return;
        if (this.display[slot].isEmpty()) return;
        this.display[slot] = ItemStack.EMPTY;
        setChanged();
    }

    /** 条目数（界面提示用） */
    public int sampleCount() {
        int n = 0;
        for (ItemStack stack : this.display) {
            if (!stack.isEmpty()) n++;
        }
        return n;
    }

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
        if (clientSide || slot < 0 || slot >= this.size) return ItemStack.EMPTY;
        ItemStack current = this.display[slot];
        this.display[slot] = ItemStack.EMPTY;
        setChanged();
        return current;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 1);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.size) return;
        ItemStack one = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!one.isEmpty()) one.setCount(1);
        this.display[slot] = one;
        if (clientSide) return;
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
        for (int i = 0; i < this.size; i++) this.display[i] = ItemStack.EMPTY;
        if (!clientSide) setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}