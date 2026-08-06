package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.block.FeastBlock;
import com.zzq.survival_toolbox.block.GuardianLanternBlock;
import com.zzq.survival_toolbox.item.GuardianLanternBlockItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.screen.SmartFarmMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.FarmlandWaterManager;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.ticket.AABBTicket;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 智慧农场方块实体
 * <p>
 * 全自动耕地、播种、收获、浇水、吸收掉落物。
 * 工作范围由配置的 X/Y/Z 范围决定，工作周期由间隔时间控制。
 * 使用分帧机制处理大量方块操作，避免单 Tick 卡顿。
 * </p>
 */
public class SmartFarmBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_WATER = 0;
    public static final int SLOT_HOE = 1;
    public static final int SLOT_TOOLBAR_START = 2;
    public static final int SLOT_TOOLBAR_END = 10;
    public static final int STORAGE_START = 11;
    public static final int TOTAL_SLOTS = 38;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    // ---- 配置参数 ----
    private boolean enabled = false;
    private int rangeX = 9, rangeY = 9, rangeZ = 9;
    private boolean showRange = true;
    private int workInterval = 60;
    private int workCooldown = 60;
    private boolean harvestEnabled = true, sowEnabled = true;

    // ---- 分帧工作状态 ----
    private boolean workInProgress = false;
    private int currentWorkIndex = 0, totalBlocks = 0, blocksPerTick = 0;
    private boolean harvestInProgress = true, sowInProgress = true, hoeInProgress = true;

    private static final int ABSORB_PER_TICK = 20;

    private AABBTicket waterTicket = null;
    private boolean hasWaterCached = false;

    // ---- 客户端实例列表（用于渲染遍历） ----
    private static final List<SmartFarmBlockEntity> CLIENT_INSTANCES = new CopyOnWriteArrayList<>();

    public static List<SmartFarmBlockEntity> getClientInstances() {
        return CLIENT_INSTANCES;
    }

    private static FakePlayer reusableFakePlayer = null;

    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    private static final Map<Block, ToolType> TOOL_REQUIREMENTS = new HashMap<>();

    static {
        TOOL_REQUIREMENTS.put(Blocks.MELON, ToolType.AXE);
        TOOL_REQUIREMENTS.put(Blocks.PUMPKIN, ToolType.AXE);
    }

    public SmartFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_FARM.get(), pos, state);
    }

    // ============================================================
    // 容器基础
    // ============================================================

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
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
        if (slot == SLOT_WATER) {
            hasWaterCached = !stack.isEmpty() && stack.getItem() == Items.WATER_BUCKET;
        }
    }

    @Override
    public boolean stillValid(Player p) {
        return Container.stillValidBlockEntity(this, p);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.smart_farm");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new SmartFarmMenu(id, inv, this);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new SmartFarmMenu(id, inv, this);
    }

    // ============================================================
    // 漏斗交互
    // ============================================================

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] s = new int[27];
        for (int i = 0; i < 27; i++) s[i] = STORAGE_START + i;
        return s;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= STORAGE_START && slot < TOTAL_SLOTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= STORAGE_START && slot < TOTAL_SLOTS;
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
        return super.getCapability(cap, side);
    }

    // ============================================================
    // 生命周期（客户端实例管理）
    // ============================================================

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_INSTANCES.add(this);
        } else if (this.level != null && !this.level.isClientSide) {
            if (enabled && hasWaterCached) updateVirtualWater();
            else clearVirtualWater();
        }
    }

    @Override
    public void setRemoved() {
        clearVirtualWater();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_INSTANCES.remove(this);
        }
        super.setRemoved();
    }

    // ============================================================
    // 公共 Getter / Setter
    // ============================================================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        if (enabled == v) return;
        enabled = v;
        setChanged();
        sync();
        if (!level.isClientSide) {
            resetWorkProgress();
            if (enabled && hasWaterCached) updateVirtualWater();
            else clearVirtualWater();
        }
    }

    public int getRangeX() {
        return rangeX;
    }

    public int getRangeY() {
        return rangeY;
    }

    public int getRangeZ() {
        return rangeZ;
    }

    public void setRangeX(int v) {
        int n = clampRange(v);
        if (rangeX == n) return;
        rangeX = n;
        onRangeChanged();
    }

    public void setRangeY(int v) {
        int n = clampRange(v);
        if (rangeY == n) return;
        rangeY = n;
        onRangeChanged();
    }

    public void setRangeZ(int v) {
        int n = clampRange(v);
        if (rangeZ == n) return;
        rangeZ = n;
        onRangeChanged();
    }

    private int clampRange(int v) {
        return Math.max(1, Math.min(64, v));
    }

    private void onRangeChanged() {
        setChanged();
        sync();
        if (!level.isClientSide) {
            resetWorkProgress();
            if (enabled && hasWaterCached) updateVirtualWater();
        }
    }

    public boolean isShowRange() {
        return showRange;
    }

    public void setShowRange(boolean v) {
        showRange = v;
        setChanged();
        sync();
    }

    public int getWorkInterval() {
        return workInterval;
    }

    public void setWorkInterval(int v) {
        int n = Math.max(1, Math.min(600, v));
        if (workInterval != n) {
            workInterval = n;
            setChanged();
            sync();
            recalcBlocksPerTick();
        }
    }

    public int getWorkCooldown() {
        return workCooldown;
    }

    public void setWorkCooldown(int v) {
        workCooldown = v;
        setChanged();
        sync();
    }

    public boolean hasWater() {
        return hasWaterCached;
    }

    public boolean isHarvestEnabled() {
        return harvestEnabled;
    }

    public void setHarvestEnabled(boolean v) {
        if (harvestEnabled == v) return;
        harvestEnabled = v;
        setChanged();
        sync();
        if (!v) harvestInProgress = false;
        else harvestInProgress = true;
    }

    public boolean isSowEnabled() {
        return sowEnabled;
    }

    public void setSowEnabled(boolean v) {
        if (sowEnabled == v) return;
        sowEnabled = v;
        setChanged();
        sync();
        if (!v) sowInProgress = false;
        else sowInProgress = true;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isInRange(BlockPos pos) {
        BlockPos c = getBlockPos();
        return Math.abs(pos.getX() - c.getX()) <= rangeX &&
                Math.abs(pos.getY() - c.getY()) <= rangeY &&
                Math.abs(pos.getZ() - c.getZ()) <= rangeZ;
    }

    // ============================================================
    // 虚拟水源
    // ============================================================

    private void updateVirtualWater() {
        if (level == null || level.isClientSide) return;
        clearVirtualWater();
        BlockPos c = getBlockPos();
        AABB aabb = new AABB(
                c.getX() - rangeX, c.getY() - rangeY, c.getZ() - rangeZ,
                c.getX() + rangeX + 1, c.getY() + rangeY + 1, c.getZ() + rangeZ + 1
        );
        try {
            waterTicket = FarmlandWaterManager.addAABBTicket((ServerLevel) level, aabb);
            if (waterTicket != null) waterTicket.validate();
        } catch (Exception ignored) {
        }
    }

    private void clearVirtualWater() {
        if (level == null || level.isClientSide) return;
        if (waterTicket != null) {
            try {
                waterTicket.invalidate();
            } catch (Exception ignored) {
            }
            waterTicket = null;
        }
    }

    // ============================================================
    // NBT 持久化
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("ShowRange", showRange);
        tag.putInt("RangeX", rangeX);
        tag.putInt("RangeY", rangeY);
        tag.putInt("RangeZ", rangeZ);
        tag.putInt("WorkInterval", workInterval);
        tag.putInt("WorkCooldown", workCooldown);
        tag.putBoolean("HasWater", hasWaterCached);
        tag.putBoolean("HarvestEnabled", harvestEnabled);
        tag.putBoolean("SowEnabled", sowEnabled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
        enabled = tag.getBoolean("Enabled");
        showRange = tag.getBoolean("ShowRange");
        rangeX = tag.getInt("RangeX");
        rangeY = tag.getInt("RangeY");
        rangeZ = tag.getInt("RangeZ");
        workInterval = tag.getInt("WorkInterval");
        workCooldown = tag.getInt("WorkCooldown");
        hasWaterCached = tag.getBoolean("HasWater");
        harvestEnabled = tag.getBoolean("HarvestEnabled");
        sowEnabled = tag.getBoolean("SowEnabled");
        if (workCooldown <= 0) workCooldown = workInterval;
        resetWorkProgress();
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

    // ============================================================
    // 核心 Tick（吸收并入工作冷却周期）
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, SmartFarmBlockEntity be) {
        if (level.isClientSide || be.isRemoved() || !be.enabled) return;
        ServerLevel srv = (ServerLevel) level;

        // 更新水桶状态
        ItemStack ws = be.items.get(SLOT_WATER);
        boolean nw = !ws.isEmpty() && ws.getItem() == Items.WATER_BUCKET;
        if (nw != be.hasWaterCached) {
            be.hasWaterCached = nw;
            if (nw && be.enabled) be.updateVirtualWater();
            else be.clearVirtualWater();
        }

        be.workCooldown--;

        // 分帧工作进行中
        if (be.workInProgress) {
            be.processWorkStep(srv);
            be.setChanged();
            return;
        }

        // 开始新工作周期
        if (be.workCooldown <= 0) {
            be.workCooldown = be.workInterval;
            be.startNewWorkCycle();
            be.processWorkStep(srv);
            be.setChanged();
        }
    }

    private void startNewWorkCycle() {
        totalBlocks = (2 * rangeX + 1) * (2 * rangeY + 1) * (2 * rangeZ + 1);
        currentWorkIndex = 0;
        workInProgress = true;
        hoeInProgress = true;
        harvestInProgress = harvestEnabled;
        sowInProgress = sowEnabled;
        recalcBlocksPerTick();
    }

    private void recalcBlocksPerTick() {
        blocksPerTick = Math.max(1, totalBlocks / Math.max(1, workInterval));
    }

    private void resetWorkProgress() {
        workInProgress = false;
        currentWorkIndex = 0;
        totalBlocks = 0;
        blocksPerTick = 0;
        hoeInProgress = harvestInProgress = sowInProgress = true;
    }

    // ============================================================
    // 分帧工作处理
    // ============================================================

    private void processWorkStep(ServerLevel level) {
        if (!workInProgress) return;

        int end = Math.min(currentWorkIndex + blocksPerTick, totalBlocks);
        BlockPos center = getBlockPos();
        FakePlayer fake = getFakePlayer(level);

        try {
            for (int i = currentWorkIndex; i < end; i++) {
                int sx = 2 * rangeX + 1, sz = 2 * rangeZ + 1;
                int yOff = i / (sx * sz) - rangeY;
                int rem = i % (sx * sz);
                int xOff = rem / sz - rangeX;
                int zOff = rem % sz - rangeZ;
                BlockPos pos = center.offset(xOff, yOff, zOff);

                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                BlockState above = level.getBlockState(pos.above());

                // 跳过镇魂灯和混沌篝火
                if (state.getBlock() instanceof GuardianLanternBlock || state.getBlock() instanceof FeastBlock) continue;

                // 耕地
                if (hoeInProgress && isTillable(state, above, level, pos)) {
                    hoeBlock(level, pos);
                }

                // 收获
                if (harvestInProgress && isHarvestable(state)) {
                    if (isStorageFull()) {
                        harvestInProgress = false;
                    } else {
                        performHarvest(level, pos, state, fake);
                    }
                }

                // 播种
                if (sowInProgress) {
                    if (isStorageFull()) {
                        sowInProgress = false;
                    } else {
                        tryPlantAt(level, pos, fake);
                    }
                }
            }
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            fake.getInventory().clearContent();
        }

        currentWorkIndex = end;
        if (currentWorkIndex >= totalBlocks) {
            resetWorkProgress();
            processAbsorb(level);
        }
    }

    // ============================================================
    // 耕田
    // ============================================================

    private boolean isTillable(BlockState state, BlockState above, Level l, BlockPos p) {
        Block b = state.getBlock();
        if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.DIRT_PATH ||
                b == Blocks.ROOTED_DIRT || b == Blocks.COARSE_DIRT) {
            if (!above.isAir() && above.getBlock() != Blocks.LIGHT) return false;
            ItemStack hoe = items.get(SLOT_HOE);
            return !hoe.isEmpty() && hoe.canPerformAction(ToolActions.HOE_DIG);
        }
        return false;
    }

    private void hoeBlock(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        Block b = s.getBlock();
        if (b == Blocks.COARSE_DIRT || b == Blocks.ROOTED_DIRT) {
            l.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
        }
        if (l.getBlockState(p.above()).isAir() || l.getBlockState(p.above()).getBlock() == Blocks.LIGHT) {
            l.setBlock(p, Blocks.FARMLAND.defaultBlockState(), 3);
        }
    }

    // ============================================================
    // 收获
    // ============================================================

    private boolean isHarvestable(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof CropBlock crop) {
            IntegerProperty age = getAgeProperty(s);
            return age != null && s.getValue(age) >= crop.getMaxAge();
        }
        if (b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO || b == Blocks.KELP || b == Blocks.KELP_PLANT) {
            return true;
        }
        if (b == Blocks.MELON || b == Blocks.PUMPKIN) {
            return true;
        }
        if (b == Blocks.COCOA) {
            return s.getValue(BlockStateProperties.AGE_2) >= 2;
        }
        return true;
    }

    private IntegerProperty getAgeProperty(BlockState s) {
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals("age") && p instanceof IntegerProperty) {
                return (IntegerProperty) p;
            }
        }
        return null;
    }

    private void performHarvest(ServerLevel l, BlockPos p, BlockState state, FakePlayer fake) {
        Block b = state.getBlock();

        if (b instanceof CropBlock crop) {
            IntegerProperty age = getAgeProperty(state);
            if (age != null && state.getValue(age) >= crop.getMaxAge()) {
                List<ItemStack> drops = Block.getDrops(state, l, p, null, fake, ItemStack.EMPTY);
                if (tryStoreDrops(drops)) {
                    l.setBlock(p, state.setValue(age, 0), 3);
                }
            }
            return;
        }

        if (b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO || b == Blocks.KELP || b == Blocks.KELP_PLANT) {
            harvestTallPlantKeepingOne(l, p, b);
            return;
        }

        if (b == Blocks.MELON || b == Blocks.PUMPKIN) {
            List<ItemStack> drops = Block.getDrops(state, l, p, null, fake, ItemStack.EMPTY);
            if (tryStoreDrops(drops)) {
                l.removeBlock(p, false);
            }
            return;
        }

        if (b == Blocks.COCOA) {
            if (state.getValue(BlockStateProperties.AGE_2) >= 2) {
                List<ItemStack> drops = Block.getDrops(state, l, p, null, fake, ItemStack.EMPTY);
                if (tryStoreDrops(drops)) {
                    l.setBlock(p, state.setValue(BlockStateProperties.AGE_2, 0), 3);
                }
            }
            return;
        }

        // 模组作物右键收获
        if (l.getBlockEntity(p) instanceof BaseContainerBlockEntity) return;
        if (b == Blocks.CRAFTING_TABLE || b == Blocks.ENCHANTING_TABLE || b == Blocks.ANVIL) return;
        if (b instanceof DoorBlock || b instanceof LeverBlock || b instanceof ButtonBlock) return;

        try {
            ItemStack tool = selectToolForBlock(b);
            fake.setItemInHand(InteractionHand.MAIN_HAND, tool);
            fake.getInventory().clearContent();

            List<ItemStack> before = captureInventory(fake);
            InteractionResult result = state.use(l, fake, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));

            if (result.consumesAction()) {
                List<ItemStack> after = captureInventory(fake);
                for (ItemStack gained : getGainedItems(before, after)) {
                    addToStorage(gained);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean tryStoreDrops(List<ItemStack> drops) {
        for (ItemStack s : drops) {
            if (!canStoreItemStack(s)) return false;
        }
        for (ItemStack s : drops) {
            addToStorage(s);
        }
        return true;
    }

    /**
     * 收获多节植物（甘蔗、竹子、海带等），保留根部一节
     *
     * @param level     世界
     * @param start     收获起始位置
     * @param blockType 植物方块类型
     */
    private void harvestTallPlantKeepingOne(ServerLevel level, BlockPos start, Block blockType) {
        // 找到根部
        BlockPos root = start;
        while (true) {
            BlockState below = level.getBlockState(root.below());
            if (isSamePlantFamily(below.getBlock(), blockType)) {
                root = root.below();
            } else {
                break;
            }
        }

        // 计算高度
        BlockPos check = root.above();
        int height = 1;
        while (level.getBlockState(check).getBlock() == blockType) {
            height++;
            check = check.above();
        }
        if (height < 2) return;

        // 从根部上方第二格开始收获（保留根部一节）
        BlockPos current = root.above();
        while (true) {
            BlockState curState = level.getBlockState(current);
            if (curState.getBlock() != blockType) break;

            List<ItemStack> drops = Block.getDrops(curState, level, current, null, null, ItemStack.EMPTY);
            for (ItemStack d : drops) {
                addToStorage(d);
            }
            level.removeBlock(current, false);
            current = current.above();
        }
    }

    private boolean isSamePlantFamily(Block b1, Block b2) {
        if (b1 == b2) return true;
        if ((b1 == Blocks.KELP || b1 == Blocks.KELP_PLANT) &&
                (b2 == Blocks.KELP || b2 == Blocks.KELP_PLANT)) {
            return true;
        }
        return false;
    }

    // ============================================================
    // 播种
    // ============================================================

    private void tryPlantAt(ServerLevel level, BlockPos pos, FakePlayer fake) {
        BlockState here = level.getBlockState(pos);
        if (!here.canBeReplaced()) return;

        for (int i = STORAGE_START; i < TOTAL_SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;

            BlockItem seedItem = (BlockItem) stack.getItem();
            Block plantBlock = seedItem.getBlock();

            // 防止在已有垂直作物正上方或正下方重复种植
            if (level.getBlockState(pos.below()).getBlock() == plantBlock ||
                    level.getBlockState(pos.above()).getBlock() == plantBlock) {
                continue;
            }

            ItemStack seed = stack.copy();
            seed.setCount(1);

            if (tryPlantOnAllFaces(level, pos, seedItem, seed, fake)) {
                stack.shrink(1);
                if (stack.isEmpty()) items.set(i, ItemStack.EMPTY);
                setChanged();
                return;
            }
        }
    }

    private boolean tryPlantOnAllFaces(ServerLevel level, BlockPos pos,
                                       BlockItem seedItem, ItemStack seedStack, FakePlayer fake) {
        fake.setItemInHand(InteractionHand.MAIN_HAND, seedStack);

        for (Direction face : Direction.values()) {
            BlockPos targetPos = pos.relative(face);
            BlockState targetState = level.getBlockState(targetPos);

            // 跳过容器或不可替换方块
            if (!targetState.canBeReplaced() && !targetState.isAir()) {
                continue;
            }

            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
            UseOnContext ctx = new UseOnContext(level, fake, InteractionHand.MAIN_HAND, seedStack, hit);

            try {
                if (seedItem.useOn(ctx).consumesAction() && seedStack.isEmpty()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    // ============================================================
    // FakePlayer 工具
    // ============================================================

    private FakePlayer getFakePlayer(ServerLevel l) {
        if (reusableFakePlayer == null || reusableFakePlayer.level() != l) {
            reusableFakePlayer = FakePlayerFactory.getMinecraft(l);
        }
        reusableFakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        reusableFakePlayer.getInventory().clearContent();
        return reusableFakePlayer;
    }

    private List<ItemStack> captureInventory(FakePlayer fake) {
        List<ItemStack> l = new ArrayList<>();
        l.add(fake.getMainHandItem().copy());
        for (int i = 0; i < fake.getInventory().getContainerSize(); i++) {
            l.add(fake.getInventory().getItem(i).copy());
        }
        return l;
    }

    private List<ItemStack> getGainedItems(List<ItemStack> before, List<ItemStack> after) {
        Map<Item, Integer> bm = new HashMap<>();
        for (ItemStack s : before) {
            if (!s.isEmpty()) bm.merge(s.getItem(), s.getCount(), Integer::sum);
        }

        Map<Item, Integer> am = new HashMap<>();
        for (ItemStack s : after) {
            if (!s.isEmpty()) am.merge(s.getItem(), s.getCount(), Integer::sum);
        }

        List<ItemStack> g = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : am.entrySet()) {
            int diff = e.getValue() - bm.getOrDefault(e.getKey(), 0);
            if (diff > 0) g.add(new ItemStack(e.getKey(), diff));
        }
        return g;
    }

    // ============================================================
    // 工具需求
    // ============================================================

    private enum ToolType {
        HOE, AXE, SHOVEL, PICKAXE, SHEARS, SWORD, ANY
    }

    private ItemStack selectToolForBlock(Block b) {
        ToolType t = TOOL_REQUIREMENTS.getOrDefault(b, null);
        if (t == null) return ItemStack.EMPTY;

        for (int i = SLOT_TOOLBAR_START; i <= SLOT_TOOLBAR_END; i++) {
            ItemStack tool = items.get(i);
            if (!tool.isEmpty() && matchesToolType(tool, t)) {
                return tool;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean matchesToolType(ItemStack tool, ToolType type) {
        return switch (type) {
            case HOE -> tool.canPerformAction(ToolActions.HOE_DIG);
            case AXE -> tool.canPerformAction(ToolActions.AXE_DIG);
            case SHOVEL -> tool.canPerformAction(ToolActions.SHOVEL_DIG);
            case PICKAXE -> tool.canPerformAction(ToolActions.PICKAXE_DIG);
            case SHEARS -> tool.getItem() == Items.SHEARS;
            case SWORD -> tool.canPerformAction(ToolActions.SWORD_DIG);
            default -> true;
        };
    }

    // ============================================================
    // 存储
    // ============================================================

    private boolean isStorageFull() {
        for (int i = STORAGE_START; i < TOTAL_SLOTS; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return false;
        }
        return true;
    }

    private boolean canStoreItemStack(ItemStack stack) {
        if (stack.isEmpty()) return true;
        for (int i = STORAGE_START; i < TOTAL_SLOTS; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(s, stack) && s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean addToStorage(ItemStack stack) {
        if (stack.isEmpty()) return true;
        int rem = tryAddToStorage(stack);
        if (rem > 0) {
            if (level != null) {
                ItemStack drop = stack.copy();
                drop.setCount(rem);
                level.addFreshEntity(new ItemEntity(level,
                        getBlockPos().getX() + 0.5,
                        getBlockPos().getY() + 1,
                        getBlockPos().getZ() + 0.5,
                        drop));
            }
            return false;
        }
        return true;
    }

    private int tryAddToStorage(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int remaining = stack.getCount();

        for (int i = STORAGE_START; i < TOTAL_SLOTS; i++) {
            ItemStack existing = items.get(i);
            if (existing.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(remaining);
                items.set(i, copy);
                setChanged();
                return 0;
            }
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, remaining);
                    existing.grow(move);
                    remaining -= move;
                    setChanged();
                    if (remaining <= 0) return 0;
                }
            }
        }
        return remaining;
    }

    // ============================================================
    // 吸收掉落物（工作周期结束后调用）
    // ============================================================

    private void processAbsorb(ServerLevel level) {
        if (!enabled || isStorageFull()) return;

        BlockPos c = getBlockPos();
        AABB bounds = new AABB(
                c.getX() - rangeX, c.getY() - rangeY, c.getZ() - rangeZ,
                c.getX() + rangeX + 1, c.getY() + rangeY + 1, c.getZ() + rangeZ + 1
        );

        List<ItemEntity> list = level.getEntitiesOfClass(ItemEntity.class, bounds, e -> !e.isRemoved());
        int proc = 0;

        for (ItemEntity e : list) {
            if (proc >= ABSORB_PER_TICK) break;
            if (e.getItem().getItem() instanceof GuardianLanternBlockItem) continue;

            ItemStack stack = e.getItem();
            int rem = tryAddToStorage(stack);

            if (rem <= 0) {
                e.discard();
                proc++;
            } else {
                stack.setCount(rem);
                e.setItem(stack);
                proc++;
                if (isStorageFull()) break;
            }
        }
    }
}