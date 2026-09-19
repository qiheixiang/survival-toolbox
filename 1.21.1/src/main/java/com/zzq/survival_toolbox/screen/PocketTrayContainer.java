package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.util.PocketStorageHelper;
import com.zzq.survival_toolbox.util.PocketTrayStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 次元袋托盘容器（27 格：物品 + 流体）
 * <p>
 * 和存储页（{@link PocketDimensionContainer}）不同，托盘走**原版堆叠语义**：每格一叠，
 * 最多到物品自己的堆叠上限，因此物品交互直接交给原版 {@code AbstractContainerMenu#doClick}
 * （左键整叠、右键放1/取半、Shift 转、拖拽分配、双击收集全部免费且行为标准）。
 * 需要单独接管的只有两类：
 * <ul>
 *   <li><b>流体格</b>：真实数据是 {@link PocketStorageHelper.FluidEntry}（数量 mB，long）；
 *       格子上的物品只是显示用的桶图标，所以点击必须在菜单里拦截，绝不能让原版把桶当真实物品拿走。</li>
 *   <li><b>光标上的流体</b>：见 {@code PocketFluidCarry}。</li>
 * </ul>
 * 数据只写袋子自己的 NBT（{@link PocketTrayStorage}），不随共享/本地模式切换。
 * 显示数据：物品格与流体格的桶图标走原版槽位同步；流体的种类掩码与 mB 数量走次元袋同步包
 * （原版同步在 1.20.1 用 byte 写数量，大数值会被截断，不能用来传 mB）。
 * </p>
 */
public class PocketTrayContainer implements Container {

    private final ItemStack bag;
    private final HolderLookup.Provider registries;
    private boolean clientSide = false;

    /** 格子显示内容（服务端：物品格是真实数据，流体格是显示用的桶；客户端：原版槽位同步写入） */
    private final List<ItemStack> display = new ArrayList<>();
    /** 服务端：流体格真实数据（null = 该格不是流体） */
    private final List<PocketStorageHelper.FluidEntry> fluids = new ArrayList<>();
    /** 方向：true = 送出（托盘 → 容器），false = 纳入（容器 → 存储） */
    private boolean out = true;

    /** 客户端：同步来的流体掩码与 mB 数量（只用于显示） */
    private long syncedFluidMask = 0L;
    private final long[] syncedFluidAmounts = new long[PocketTrayStorage.SLOTS];

    public PocketTrayContainer(ItemStack bag, HolderLookup.Provider registries, boolean clientSide) {
        this.bag = bag;
        this.registries = registries;
        this.clientSide = clientSide;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            display.add(ItemStack.EMPTY);
            fluids.add(null);
        }
        if (!clientSide) {
            load();
        }
    }

    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    // ============================================================
    // 读写袋子 NBT
    // ============================================================

    /** 服务端：从袋子 NBT 载入托盘 */
    public void load() {
        PocketTrayStorage.Tray tray = PocketTrayStorage.read(bag, registries);
        this.out = tray.out;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            PocketStorageHelper.FluidEntry fluid = tray.fluids.get(i);
            fluids.set(i, fluid);
            if (fluid != null) {
                // 流体格：显示栈 = 该流体的桶当图标、数量恒为 1，存量与流体本身放在自定义数据里
                display.set(i, PocketStorageHelper.fluidDisplayStack(fluid));
            } else {
                display.set(i, tray.items.get(i));
            }
        }
    }

    /** 服务端：把托盘写回袋子 NBT（只替换托盘的三个键） */
    public void save() {
        if (clientSide) return;
        PocketTrayStorage.Tray tray = new PocketTrayStorage.Tray();
        tray.out = this.out;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            PocketStorageHelper.FluidEntry fluid = fluids.get(i);
            if (fluid != null) {
                tray.fluids.set(i, fluid);
            } else {
                tray.items.set(i, display.get(i));
            }
        }
        PocketTrayStorage.write(bag, tray, registries);
    }

    // ============================================================
    // 方向与统计
    // ============================================================

    /** 当前方向：true = 送出，false = 纳入 */
    public boolean isOut() {
        return out;
    }

    /** 切换方向（服务端）：立刻写进袋子 NBT，切界面后依旧有效 */
    public void setOut(boolean out) {
        this.out = out;
        if (!clientSide) {
            PocketTrayStorage.setOut(bag, out);
        }
    }

    /** 客户端：用同步包里的方向/流体数据刷新显示（不写存档） */
    public void applySynced(boolean out, long fluidMask, long[] fluidAmounts) {
        this.out = out;
        this.syncedFluidMask = fluidMask;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            this.syncedFluidAmounts[i] = i < fluidAmounts.length ? fluidAmounts[i] : 0L;
        }
    }

    /**
     * 所有<b>流体格</b>的显示栈（非流体格 = 空栈）——同步包用。
     * <p>
     * 为什么流体格要显式同步：原版槽位同步本来该把这件"桶"发下来，但实测时
     * "水放进托盘后那一格图标就没了"（客户端只拿到掩码 + 数量，格子里是空的、只剩蓝字）。
     * 存储页的流体格一直是走同步包发的（所以那边没问题），托盘这边照同一套做。
     * </p>
     */
    public java.util.List<ItemStack> fluidIcons() {
        java.util.List<ItemStack> out = new ArrayList<>(PocketTrayStorage.SLOTS);
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            out.add(fluids.get(i) != null ? display.get(i) : ItemStack.EMPTY);
        }
        return out;
    }

    /** 客户端：把同步包里的流体格显示栈填进格子里（图标/流体本体/存量标记都在它身上） */
    public void applySyncedFluidIcons(java.util.List<ItemStack> icons) {
        if (!clientSide || icons == null) return;
        for (int i = 0; i < PocketTrayStorage.SLOTS && i < icons.size(); i++) {
            ItemStack icon = icons.get(i);
            if (icon != null && !icon.isEmpty()) display.set(i, icon.copy());
        }
    }

    /** 装了物品的格数（提示用；两端都能算：客户端靠同步掩码判断哪些格是流体） */
    public int itemSlotCount() {
        int n = 0;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            if (!isFluidAt(i) && !display.get(i).isEmpty()) n++;
        }
        return n;
    }

    /** 装了流体的格数（提示用） */
    public int fluidSlotCount() {
        int n = 0;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            if (isFluidAt(i)) n++;
        }
        return n;
    }

    // ============================================================
    // 流体格（服务端）
    // ============================================================

    /** 该格是不是流体格（客户端用同步掩码判断） */
    public boolean isFluidAt(int slot) {
        if (slot < 0 || slot >= PocketTrayStorage.SLOTS) return false;
        if (clientSide) return ((syncedFluidMask >>> slot) & 1L) != 0L;
        return fluids.get(slot) != null;
    }

    /** 该格的流体条目（服务端：真实数据；客户端：null，显示数据走掩码 + 数量） */
    public PocketStorageHelper.FluidEntry fluidAt(int slot) {
        if (clientSide || slot < 0 || slot >= PocketTrayStorage.SLOTS) return null;
        return fluids.get(slot);
    }

    /** 该格流体的 mB 数量（两端都能用：客户端用同步值） */
    public long fluidAmount(int slot) {
        if (slot < 0 || slot >= PocketTrayStorage.SLOTS) return 0L;
        if (clientSide) return ((syncedFluidMask >>> slot) & 1L) != 0L ? syncedFluidAmounts[slot] : 0L;
        PocketStorageHelper.FluidEntry entry = fluids.get(slot);
        return entry == null ? 0L : entry.amount();
    }

    /** 54（27）位掩码：哪些格是流体（同步包用） */
    public long buildFluidMask() {
        long mask = 0L;
        if (clientSide) return syncedFluidMask;
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            if (fluids.get(i) != null) mask |= 1L << i;
        }
        return mask;
    }

    /** 每格流体数量（同步包用；非流体格为 0） */
    public long[] fluidAmounts() {
        long[] out = new long[PocketTrayStorage.SLOTS];
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            out[i] = fluidAmount(i);
        }
        return out;
    }

    /** 该格能不能放这种流体（空格或同种流体） */
    public boolean canAcceptFluid(int slot, net.neoforged.neoforge.fluids.FluidStack stack) {
        if (clientSide || slot < 0 || slot >= PocketTrayStorage.SLOTS || stack.isEmpty()) return false;
        PocketStorageHelper.FluidEntry existing = fluids.get(slot);
        if (existing != null) return existing.sameFluid(stack);
        return display.get(slot).isEmpty();
    }

    /**
     * 往指定格灌流体（服务端）：同种合并 / 空格放入；装不下时返回剩余量。
     *
     * @return 没放进去的量（0 = 全部放下）
     */
    public long insertFluid(int slot, net.neoforged.neoforge.fluids.FluidStack stack) {
        long amount = stack.isEmpty() ? 0L : stack.getAmount();
        if (clientSide || amount <= 0) return amount;
        if (slot < 0 || slot >= PocketTrayStorage.SLOTS) return amount;
        PocketStorageHelper.FluidEntry existing = fluids.get(slot);
        if (existing != null) {
            if (!existing.sameFluid(stack)) return amount;
            setFluidEntry(slot, new PocketStorageHelper.FluidEntry(existing.stackTag(), existing.amount() + amount));
            return 0L;
        }
        if (!display.get(slot).isEmpty()) return amount;
        setFluidEntry(slot, new PocketStorageHelper.FluidEntry(
                PocketStorageHelper.encodeFluid(stack), amount));
        return 0L;
    }

    /**
     * 从指定格取走流体（服务端）：最多 maxAmount mB，取完就清空该格。
     *
     * @return 实际取出的流体（空 = 该格不是流体）
     */
    public net.neoforged.neoforge.fluids.FluidStack extractFluid(int slot, long maxAmount) {
        PocketStorageHelper.FluidEntry entry = fluidAt(slot);
        if (entry == null || maxAmount <= 0) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        // FluidStack 数量是 int：取走量也要按 int 上限夹一次，
        // 否则会出现"扣了但没给出"（toStack 内部截断）导致流体凭空消失
        long take = Math.min(Math.min(maxAmount, entry.amount()), Integer.MAX_VALUE);
        if (take <= 0) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        net.neoforged.neoforge.fluids.FluidStack outStack = entry.toStack(take);
        if (outStack.isEmpty()) return outStack;
        long left = entry.amount() - take;
        setFluidEntry(slot, left <= 0 ? null
                : new PocketStorageHelper.FluidEntry(entry.stackTag(), left));
        return outStack;
    }

    /** 整条拿走（"拿起"到光标上）：返回条目并清空该格 */
    public PocketStorageHelper.FluidEntry takeFluidEntry(int slot) {
        PocketStorageHelper.FluidEntry entry = fluidAt(slot);
        if (entry == null) return null;
        setFluidEntry(slot, null);
        return entry;
    }

    /** 写入某格的流体条目（服务端；null = 清空该格），同时维护格子上的桶图标 */
    private void setFluidEntry(int slot, PocketStorageHelper.FluidEntry entry) {
        if (clientSide) return;
        fluids.set(slot, entry);
        if (entry == null) {
            display.set(slot, ItemStack.EMPTY);
        } else {
            display.set(slot, PocketStorageHelper.fluidDisplayStack(entry));
        }
        setChanged();
    }

    // ============================================================
    // Container 接口（原版堆叠语义）
    // ============================================================

    @Override
    public int getContainerSize() {
        return PocketTrayStorage.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        return itemSlotCount() == 0 && fluidSlotCount() == 0;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= PocketTrayStorage.SLOTS) return ItemStack.EMPTY;
        // 与原版容器一致：返回格内实时堆叠（原版 doClick 会直接改它再调用 setChanged）
        return display.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (clientSide || slot < 0 || slot >= PocketTrayStorage.SLOTS || amount <= 0) return ItemStack.EMPTY;
        if (fluids.get(slot) != null) return ItemStack.EMPTY; // 流体格不走物品接口
        ItemStack current = display.get(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        if (current.isEmpty()) display.set(slot, ItemStack.EMPTY);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= PocketTrayStorage.SLOTS) return;
        if (clientSide) {
            // 客户端：接受原版槽位同步（物品格与流体格的桶图标都靠它显示）
            display.set(slot, stack.copy());
            return;
        }
        if (fluids.get(slot) != null) return; // 流体格不接受物品（防御：正常点击已在菜单里拦截）
        ItemStack copy = stack.copy();
        if (!copy.isEmpty()) {
            // 托盘按"原版箱子"处理：每格一叠，且不超过 64（与 getMaxStackSize 保持一致）
            int max = Math.min(copy.getMaxStackSize(), getMaxStackSize());
            if (copy.getCount() > max) copy.setCount(max);
        }
        display.set(slot, copy);
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
        for (int i = 0; i < PocketTrayStorage.SLOTS; i++) {
            fluids.set(i, null);
            display.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}
