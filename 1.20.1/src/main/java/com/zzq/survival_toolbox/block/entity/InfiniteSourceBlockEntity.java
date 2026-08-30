package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.block.InfiniteSourceBlock;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.screen.InfiniteSourceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 无限之源方块实体
 * <p>
 * 存储 1 个槽位（标记物），用于记录流体类型。
 * 功能：从标记物容器中读取流体类型，然后提供无限量的流体供应。
 * 返回的流体量截断到 Integer.MAX_VALUE，保证不会因数值溢出而异常。
 * </p>
 */
public class InfiniteSourceBlockEntity extends BaseContainerBlockEntity
        implements WorldlyContainer, MenuProvider, IFluidHandler {

    public static final int SLOT_MARKER = 0;
    public static final int TOTAL_SLOTS = 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private FluidStack storedFluid = FluidStack.EMPTY;
    private FluidStack cachedFluidStack = null;
    private int capacity = Integer.MAX_VALUE;

    private static final List<InfiniteSourceBlockEntity> CLIENT_INSTANCES = new ArrayList<>();
    private static final List<InfiniteSourceBlockEntity> SERVER_INSTANCES = new ArrayList<>();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return storedFluid.isEmpty() ? 0 : 1;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    private final LazyOptional<? extends IItemHandler>[] handlers =
            SidedInvWrapper.create(this, Direction.values());

    public InfiniteSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFINITE_SOURCE.get(), pos, state);
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
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        if (slot == SLOT_MARKER && !level.isClientSide) {
            updateFluidFromMarker(stack);
            updateAppearance();
        }
    }

    private void updateFluidFromMarker(ItemStack stack) {
        if (stack.isEmpty()) {
            storedFluid = FluidStack.EMPTY;
            updateAppearance();
            setChanged();
            return;
        }

        IFluidHandlerItem fluidHandler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .resolve().orElse(null);
        if (fluidHandler == null) {
            storedFluid = FluidStack.EMPTY;
            updateAppearance();
            setChanged();
            return;
        }

        FluidStack fluid = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
        if (fluid == null || fluid.isEmpty()) {
            storedFluid = FluidStack.EMPTY;
            updateAppearance();
            setChanged();
            return;
        }

        storedFluid = new FluidStack(fluid.getFluid(), Integer.MAX_VALUE);
        updateAppearance();
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
    // 菜单
    // ============================================================

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.infinite_source");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new InfiniteSourceMenu(id, inventory, this, dataAccess);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new InfiniteSourceMenu(id, inventory, this, dataAccess);
    }

    // ============================================================
    // 漏斗交互
    // ============================================================

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{SLOT_MARKER};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_MARKER;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    // ============================================================
    // Capability
    // ============================================================

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) return LazyOptional.of(() -> new SidedInvWrapper(this, null)).cast();
            return handlers[side.ordinal()].cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return LazyOptional.of(() -> this).cast();
        }
        return super.getCapability(cap, side);
    }

    // ============================================================
    // IFluidHandler 实现（无限供应）
    // ============================================================

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        if (cachedFluidStack == null || cachedFluidStack.getFluid() != storedFluid.getFluid()) {
            cachedFluidStack = new FluidStack(storedFluid.getFluid(), Integer.MAX_VALUE);
        }
        return cachedFluidStack;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return 0;
        if (storedFluid.isEmpty() || storedFluid.getFluid() == resource.getFluid()) {
            if (action.execute()) {
                storedFluid = new FluidStack(resource.getFluid(), Integer.MAX_VALUE);
                cachedFluidStack = null;
                updateAppearance();
                setChanged();
            }
            return resource.getAmount();
        }
        return 0;
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
        if (storedFluid.isEmpty()) return FluidStack.EMPTY;
        if (!storedFluid.isFluidEqual(resource)) return FluidStack.EMPTY;
        return new FluidStack(resource.getFluid(), resource.getAmount());
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        if (storedFluid.isEmpty()) return FluidStack.EMPTY;
        if (maxDrain <= 0) return FluidStack.EMPTY;
        int amount = Math.min(maxDrain, Integer.MAX_VALUE);
        return new FluidStack(storedFluid.getFluid(), amount);
    }

    // ============================================================
    // 玩家右键取液
    // ============================================================

    public boolean onRightClick(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) return false;

        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.isEmpty()) return false;

        // 1. 先尝试模组容器（IFluidHandlerItem）——每次只取 1 个容器（支持堆叠桶）
        // capability 必须从"单个容器副本"获取：堆叠桶共享 NBT，直接操作整叠会将整叠改为装液状态。
        ItemStack oneBucket = heldItem.copy();
        oneBucket.setCount(1);
        IFluidHandlerItem fluidHandler = oneBucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .resolve().orElse(null);
        if (fluidHandler != null) {
            if (storedFluid.isEmpty()) return false;

            int tankCapacity = fluidHandler.getTankCapacity(0);
            if (tankCapacity <= 0) return false;

            FluidStack toFill = new FluidStack(storedFluid.getFluid(), tankCapacity);
            int fillable = fluidHandler.fill(toFill, FluidAction.SIMULATE);
            if (fillable <= 0) return false;

            fluidHandler.fill(toFill, FluidAction.EXECUTE);
            ItemStack filledContainer = fluidHandler.getContainer();
            heldItem.shrink(1);
            if (heldItem.isEmpty()) {
                player.setItemInHand(hand, filledContainer);
            } else {
                if (!player.getInventory().add(filledContainer)) {
                    player.drop(filledContainer, false);
                }
            }
            setChanged();
            return true;
        }

        // 2. 原版玻璃瓶（没有 IFluidHandlerItem）——支持堆叠
        if (heldItem.getItem() == Items.GLASS_BOTTLE) {
            if (storedFluid.getFluid() == Fluids.WATER) {
                heldItem.shrink(1);
                ItemStack waterBottle = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
                if (heldItem.isEmpty()) {
                    player.setItemInHand(hand, waterBottle);
                } else {
                    if (!player.getInventory().add(waterBottle)) {
                        player.drop(waterBottle, false);
                    }
                }
                setChanged();
                return true;
            }
            return false;
        }

        return false;
    }

    // ============================================================
    // 外观更新
    // ============================================================

    private void updateAppearance() {
        if (level == null || level.isClientSide) return;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof InfiniteSourceBlock) {
            boolean hasItem = !items.get(SLOT_MARKER).isEmpty();
            BlockState newState = state.setValue(InfiniteSourceBlock.HAS_ITEM, hasItem);
            level.setBlock(worldPosition, newState, 3);
            level.sendBlockUpdated(worldPosition, state, newState, 3);
            setChanged();
        }
    }

    public FluidStack getStoredFluid() {
        return storedFluid;
    }

    public boolean hasFluid() {
        return !storedFluid.isEmpty();
    }

    public String getFluidName() {
        if (storedFluid.isEmpty()) return "empty";
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(storedFluid.getFluid());
        return id != null ? id.getPath() : "empty";
    }

    // ============================================================
    // 客户端/服务端实例管理
    // ============================================================

    public static List<InfiniteSourceBlockEntity> getClientInstances() {
        return CLIENT_INSTANCES;
    }

    public static List<InfiniteSourceBlockEntity> getServerInstances() {
        return SERVER_INSTANCES;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) CLIENT_INSTANCES.add(this);
        else if (this.level != null && !this.level.isClientSide) SERVER_INSTANCES.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) CLIENT_INSTANCES.remove(this);
        else if (this.level != null && !this.level.isClientSide) SERVER_INSTANCES.remove(this);
    }

    // ============================================================
    // NBT 持久化
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (!storedFluid.isEmpty()) {
            CompoundTag fluidTag = new CompoundTag();
            storedFluid.writeToNBT(fluidTag);
            tag.put("StoredFluid", fluidTag);
        }
        tag.putInt("Capacity", capacity);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
        if (tag.contains("StoredFluid")) {
            storedFluid = FluidStack.loadFluidStackFromNBT(tag.getCompound("StoredFluid"));
            if (storedFluid != null && !storedFluid.isEmpty()) storedFluid.setAmount(Integer.MAX_VALUE);
        }
        capacity = tag.getInt("Capacity");
        if (capacity <= 0) capacity = Integer.MAX_VALUE;
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

    public static void tick(Level level, BlockPos pos, BlockState state, InfiniteSourceBlockEntity be) {
        // 无周期性逻辑
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, stack));
            }
        }
        items.clear();
        setChanged();
    }
}