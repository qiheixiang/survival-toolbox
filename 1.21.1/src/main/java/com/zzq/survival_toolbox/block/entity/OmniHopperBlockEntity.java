package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.screen.OmniHopperMenu;
import com.zzq.survival_toolbox.util.DirectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * 万向漏斗方块实体
 * <p>
 * 存储 27 格缓存区，每个方向独立配置传输模式、速度和过滤规则。
 * 方向映射根据玩家放置时的朝向动态计算（前后左右相对于玩家视角）。
 * </p>
 */
public class OmniHopperBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final Map<Direction, DirectionConfig> configs = new EnumMap<>(Direction.class);

    /** 玩家放置时的朝向，用于映射前后左右 */
    private Direction playerFacing = Direction.NORTH;

    private static final List<Direction> DIRECTION_ORDER = Arrays.asList(
            Direction.UP, Direction.DOWN, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST
    );

    public OmniHopperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMNI_HOPPER.get(), pos, state);
        for (Direction d : Direction.values()) {
            configs.put(d, new DirectionConfig());
        }
        // 默认配置：上方流入，下方流出
        configs.get(Direction.UP).setMode(DirectionConfig.Mode.IN);
        configs.get(Direction.DOWN).setMode(DirectionConfig.Mode.OUT);
    }

    // ============================================================
    // Getter / Setter
    // ============================================================

    public Direction getPlayerFacing() {
        return playerFacing;
    }

    public void setPlayerFacing(Direction facing) {
        this.playerFacing = facing;
        setChanged();
    }

    public DirectionConfig getConfig(Direction dir) {
        return configs.get(dir);
    }

    public Map<Direction, DirectionConfig> getAllConfigs() {
        return configs;
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    private boolean isCacheEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    // ============================================================
    // 菜单
    // ============================================================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.zzq_survival_toolbox.omni_hopper");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new OmniHopperMenu(id, inv, this);
    }

    // ============================================================
    // 核心 Tick
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, OmniHopperBlockEntity be) {
        if (level.isClientSide) return;

        // 冷却递减
        for (Direction d : Direction.values()) {
            be.configs.get(d).tickCooldown();
        }

        int maxPullPerTick = 1;
        int pulledThisTick = 0;

        // ---- 流入方向 ----
        for (Direction dir : DIRECTION_ORDER) {
            DirectionConfig cfg = be.configs.get(dir);
            if (cfg.getMode() != DirectionConfig.Mode.IN) continue;
            if (cfg.getCooldown() > 0) continue;
            if (pulledThisTick >= maxPullPerTick) break;

            if (be.pullFromDirection(dir)) {
                cfg.setCooldown(cfg.getSpeed());
                pulledThisTick++;
            } else {
                cfg.setCooldown(1);
            }
        }

        // ---- 流出方向（平均分配） ----
        List<Direction> outDirections = new ArrayList<>();
        for (Direction dir : DIRECTION_ORDER) {
            DirectionConfig cfg = be.configs.get(dir);
            if (cfg.getMode() == DirectionConfig.Mode.OUT && cfg.getCooldown() <= 0) {
                outDirections.add(dir);
            }
        }

        if (!outDirections.isEmpty() && !be.isCacheEmpty()) {
            for (Direction dir : outDirections) {
                if (be.isCacheEmpty()) break;
                if (be.pushToDirection(dir)) {
                    be.configs.get(dir).setCooldown(be.configs.get(dir).getSpeed());
                } else {
                    be.configs.get(dir).setCooldown(1);
                }
            }
        }
    }

    // ============================================================
    // 拉取 / 推送
    // ============================================================

    /**
     * 从指定方向拉取一个物品
     *
     * @param dir 方向
     * @return 是否成功拉取
     */
    private boolean pullFromDirection(Direction dir) {
        if (level == null) return false;

        BlockPos neighborPos = worldPosition.relative(dir);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, dir.getOpposite());
        if (handler == null) return false;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.extractItem(i, 1, true);
            if (stack.isEmpty()) continue;

            ItemStack remaining = addItemToCache(stack);
            if (remaining.getCount() < stack.getCount()) {
                handler.extractItem(i, 1, false);
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * 向指定方向推送一个物品
     *
     * @param dir 方向
     * @return 是否成功推送
     */
    private boolean pushToDirection(Direction dir) {
        if (level == null || isCacheEmpty()) return false;

        BlockPos neighborPos = worldPosition.relative(dir);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, dir.getOpposite());
        if (handler == null) return false;

        DirectionConfig cfg = configs.get(dir);

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            if (!cfg.isItemAllowed(stack)) continue;

            ItemStack copy = stack.copy();
            copy.setCount(1);

            for (int slotIdx = 0; slotIdx < handler.getSlots(); slotIdx++) {
                ItemStack remaining = handler.insertItem(slotIdx, copy, false);
                if (remaining.isEmpty()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        items.set(i, ItemStack.EMPTY);
                    }
                    setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================================
    // 缓存管理
    // ============================================================

    /**
     * 将物品存入缓存区（合并到已有堆叠或放入空槽位）
     *
     * @param stack 待存入的物品
     * @return 未能存入的剩余物品
     */
    private ItemStack addItemToCache(ItemStack stack) {
        if (stack.isEmpty()) return stack;

        int totalAdded = 0;
        int targetCount = stack.getCount();

        // 先合并到已有堆叠
        for (int i = 0; i < items.size(); i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < slot.getMaxStackSize()) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int transfer = Math.min(space, targetCount - totalAdded);
                if (transfer > 0) {
                    slot.grow(transfer);
                    totalAdded += transfer;
                    setChanged();
                    if (totalAdded >= targetCount) return ItemStack.EMPTY;
                }
            }
        }

        // 放到空槽位
        for (int i = 0; i < items.size(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                int remaining = targetCount - totalAdded;
                items.set(i, stack.copyWithCount(remaining));
                setChanged();
                return ItemStack.EMPTY;
            }
        }

        return totalAdded == 0 ? stack : stack.copyWithCount(targetCount - totalAdded);
    }

    // ============================================================
    // 容器接口
    // ============================================================

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
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
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[27];
        for (int i = 0; i < 27; i++) slots[i] = i;
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (side == null) return false;
        DirectionConfig cfg = configs.get(side);
        return cfg.getMode() == DirectionConfig.Mode.IN;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    // ============================================================
    // NBT 持久化
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);

        CompoundTag configsTag = new CompoundTag();
        for (Direction d : Direction.values()) {
            configsTag.put(d.getName(), configs.get(d).serializeNBT(registries));
        }
        tag.put("Configs", configsTag);
        tag.putInt("PlayerFacing", playerFacing.ordinal());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);

        CompoundTag configsTag = tag.getCompound("Configs");
        for (Direction d : Direction.values()) {
            if (configsTag.contains(d.getName())) {
                configs.get(d).deserializeNBT(registries, configsTag.getCompound(d.getName()));
            }
        }

        if (tag.contains("PlayerFacing")) {
            playerFacing = Direction.values()[tag.getInt("PlayerFacing")];
        }
    }

    // ============================================================
    // 网络同步
    // ============================================================

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    // ============================================================
    // 掉落
    // ============================================================

    public void dropContents() {
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