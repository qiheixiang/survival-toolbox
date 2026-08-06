package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.block.TransmutationFurnaceBlock;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.screen.TransmutationFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 万物转化炉方块实体
 * <p>
 * 存储 3 个槽位：
 * <ul>
 *   <li>0：输入槽</li>
 *   <li>1：燃料槽</li>
 *   <li>2：输出槽</li>
 * </ul>
 * 功能：消耗 1 单位燃料后，每 40 tick 将输入槽中的物品复制 1 份到输出槽。
 * 输入物品不会被消耗，可无限复制。
 * </p>
 */
public class TransmutationFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_OUTPUT = 2;
    private static final int TOTAL_SLOTS = 3;
    private static final int TOTAL_BURN_TIME = 40;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == 0) return burnTime;
            if (index == 1) return TOTAL_BURN_TIME;
            return 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                burnTime = value;
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int burnTime = 0;

    private final LazyOptional<? extends IItemHandler>[] handlers =
            SidedInvWrapper.create(this, Direction.values());

    public TransmutationFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSMUTATION_FURNACE.get(), pos, state);
    }

    // ============================================================
    // 菜单
    // ============================================================

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.transmutation_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new TransmutationFurnaceMenu(id, inventory, this, data);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TransmutationFurnaceMenu(id, inventory, this, data);
    }

    // ============================================================
    // 核心 Tick
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, TransmutationFurnaceBlockEntity be) {
        if (level.isClientSide) return;

        ItemStack fuelStack = be.items.get(SLOT_FUEL);
        ItemStack inputStack = be.items.get(SLOT_INPUT);
        ItemStack outputStack = be.items.get(SLOT_OUTPUT);

        // 检查输出槽能否接收产物
        boolean canProduce = false;
        if (!inputStack.isEmpty()) {
            ItemStack copy = inputStack.copy();
            copy.setCount(1);
            if (outputStack.isEmpty()) {
                canProduce = true;
            } else if (ItemStack.isSameItemSameTags(outputStack, copy) &&
                    outputStack.getCount() < outputStack.getMaxStackSize()) {
                canProduce = true;
            }
        } else {
            canProduce = true; // 空烧
        }

        if (!canProduce) {
            be.burnTime = 0;
            be.setChanged();
            updateLit(level, pos, state, false);
            return;
        }

        if (be.burnTime > 0) {
            be.burnTime--;
            if (be.burnTime == 0) {
                if (!inputStack.isEmpty()) {
                    ItemStack result = inputStack.copy();
                    result.setCount(1);
                    if (outputStack.isEmpty()) {
                        be.items.set(SLOT_OUTPUT, result);
                    } else {
                        outputStack.grow(1);
                    }
                }
                be.setChanged();
            }
            updateLit(level, pos, state, true);
            return;
        }

        // 开始新燃烧
        if (fuelStack.isEmpty()) {
            be.burnTime = 0;
            be.setChanged();
            updateLit(level, pos, state, false);
            return;
        }

        fuelStack.shrink(1);
        if (fuelStack.isEmpty()) {
            be.items.set(SLOT_FUEL, ItemStack.EMPTY);
        }
        be.burnTime = TOTAL_BURN_TIME;
        be.setChanged();
        updateLit(level, pos, state, true);
    }

    private static void updateLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(TransmutationFurnaceBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(TransmutationFurnaceBlock.LIT, lit), 3);
        }
    }

    // ============================================================
    // 容器接口
    // ============================================================

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return stack.split(amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ============================================================
    // 漏斗交互
    // ============================================================

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{SLOT_INPUT, SLOT_FUEL, SLOT_OUTPUT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == SLOT_INPUT) return false;
        return slot == SLOT_FUEL;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == SLOT_INPUT) return false;
        return slot == SLOT_OUTPUT;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) return LazyOptional.of(() -> new SidedInvWrapper(this, null)).cast();
            return handlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }

    // ============================================================
    // NBT 持久化
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("BurnTime", burnTime);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
        burnTime = tag.getInt("BurnTime");
    }

    // ============================================================
    // 网络同步
    // ============================================================

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) load(pkt.getTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getTotalBurnTime() {
        return TOTAL_BURN_TIME;
    }
}