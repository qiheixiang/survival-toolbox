package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.attack.GuardianLanternAttackHandler;
import com.zzq.survival_toolbox.block.GuardianLanternBlock;
import com.zzq.survival_toolbox.item.BlacklistItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.screen.GuardianLanternMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 镇魂灯方块实体
 * <p>
 * 存储 2 个槽位：
 * <ul>
 *   <li>SLOT_WEAPON = 0（武器槽）</li>
 *   <li>SLOT_BLACKLIST = 1（黑白名单槽）</li>
 * </ul>
 * 功能：
 * <ul>
 *   <li>照明：在范围内生成光方块阵列</li>
 *   <li>镇压：阻止敌对/中立/被动生物在范围内生成</li>
 *   <li>自动攻击：使用武器攻击范围内的目标</li>
 * </ul>
 * </p>
 */
public class GuardianLanternBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_WEAPON = 0;
    public static final int SLOT_BLACKLIST = 1;
    private static final int TOTAL_SLOTS = 2;
    private static final int LIGHT_PLACE_PER_TICK = 50;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private long lastWeaponInteractTick = 0;

    private List<BlockPos> lightBlockPositions = new ArrayList<>();
    private int lightPlaceIndex = -1;
    private int lightPlaceEnd = 0;
    private List<BlockPos> lightPlaceBatch = new ArrayList<>();

    private int rangeX = 1;
    private int rangeY = 1;
    private int rangeZ = 1;
    private boolean enabled = false;
    private boolean suppressHostile = false;
    private boolean suppressNeutral = false;
    private boolean suppressPassive = false;
    private boolean autoAttack = false;
    private int attackInterval = 20;
    private int attackCooldown = 0;
    private boolean showRange = false;
    private boolean attackHostile = false;
    private boolean attackNeutral = false;
    private boolean attackPassive = false;
    private boolean lit = false;
    private boolean suppressEnabled = false;

    private UUID ownerUUID;
    private String ownerName = "";

    private static final List<GuardianLanternBlockEntity> CLIENT_INSTANCES = new CopyOnWriteArrayList<>();
    private static final List<GuardianLanternBlockEntity> SERVER_INSTANCES = new CopyOnWriteArrayList<>();

    public GuardianLanternBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUARDIAN_LANTERN.get(), pos, state);
    }

    @Override
    protected net.minecraft.core.NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(net.minecraft.core.NonNullList<ItemStack> items) {
        this.items.clear();
        this.items.addAll(items);
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
        return items.stream().allMatch(ItemStack::isEmpty);
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
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public ItemStack getWeapon() {
        return items.get(SLOT_WEAPON);
    }

    public void setWeapon(ItemStack stack) {
        items.set(SLOT_WEAPON, stack);
        setChanged();
    }

    public ItemStack getBlacklist() {
        return items.get(SLOT_BLACKLIST);
    }

    public void setBlacklist(ItemStack stack) {
        items.set(SLOT_BLACKLIST, stack);
        setChanged();
    }

    /**
     * 将 2 个槽位的物品转换为 {@code CONTAINER} 组件内容。
     * <p>
     * 1.21.1 NeoForge 中 {@link BaseContainerBlockEntity} 通过
     * {@code DataComponents.CONTAINER} 组件管理容器物品：放置方块时
     * {@code BlockItem} 会用物品的 CONTAINER 组件调用
     * {@code applyImplicitComponents} 覆盖方块实体的物品列表。
     * 因此掉落物必须同时携带 CONTAINER 组件，否则放置时会被空组件清空
     * 武器/黑白名单（配置字段不受影响）。
     * </p>
     */
    public net.minecraft.world.item.component.ItemContainerContents getContainerContents() {
        return net.minecraft.world.item.component.ItemContainerContents.fromItems(this.items);
    }

    // ============================================================
    // 菜单
    // ============================================================

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.guardian_lantern");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new GuardianLanternMenu(id, inv, this);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new GuardianLanternMenu(id, inv, this);
    }

    // ============================================================
    // 核心 Tick（攻击 + 光源分帧放置）
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, GuardianLanternBlockEntity be) {
        if (level.isClientSide) return;
        if (!be.enabled) return;

        // 分帧放置光方块
        if (be.lightPlaceIndex >= 0) {
            be.placeLightBatch();
        }

        // 自动攻击
        if (be.autoAttack && !be.items.get(SLOT_WEAPON).isEmpty()) {
            if (be.attackCooldown <= 0) {
                if (level instanceof ServerLevel serverLevel) {
                    GuardianLanternAttackHandler.performAttack(be, serverLevel);
                }
                be.attackCooldown = be.attackInterval;
            } else {
                be.attackCooldown--;
            }
        }
    }

    // ============================================================
    // Getter / Setter
    // ============================================================

    public long getLastWeaponInteractTick() {
        return lastWeaponInteractTick;
    }

    public void setLastWeaponInteractTick(long tick) {
        this.lastWeaponInteractTick = tick;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof GuardianLanternBlock) {
                boolean shouldLit = enabled && lit;
                level.setBlock(worldPosition, state.setValue(GuardianLanternBlock.LIT, shouldLit), 3);
            }
            if (!enabled) {
                clearLightBlocks();
                lightPlaceIndex = -1;
            } else if (lit) {
                startLightPlacement();
            }
        }
        setChanged();
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
        this.rangeX = Math.max(1, Math.min(64, v));
        onRangeChanged();
    }

    public void setRangeY(int v) {
        this.rangeY = Math.max(1, Math.min(64, v));
        onRangeChanged();
    }

    public void setRangeZ(int v) {
        this.rangeZ = Math.max(1, Math.min(64, v));
        onRangeChanged();
    }

    private void onRangeChanged() {
        if (level != null && !level.isClientSide && enabled && lit) startLightPlacement();
        setChanged();
    }

    public boolean isSuppressHostile() {
        return suppressHostile;
    }

    public boolean isSuppressNeutral() {
        return suppressNeutral;
    }

    public boolean isSuppressPassive() {
        return suppressPassive;
    }

    public void setSuppressHostile(boolean v) {
        this.suppressHostile = v;
        setChanged();
    }

    public void setSuppressNeutral(boolean v) {
        this.suppressNeutral = v;
        setChanged();
    }

    public void setSuppressPassive(boolean v) {
        this.suppressPassive = v;
        setChanged();
    }

    public boolean isSuppressEnabled() {
        return suppressEnabled;
    }

    public void setSuppressEnabled(boolean v) {
        this.suppressEnabled = v;
        setChanged();
    }

    public boolean isAutoAttack() {
        return autoAttack;
    }

    public void setAutoAttack(boolean v) {
        this.autoAttack = v;
        setChanged();
    }

    public int getAttackInterval() {
        return attackInterval;
    }

    public void setAttackInterval(int v) {
        this.attackInterval = Math.max(5, Math.min(200, v));
        setChanged();
    }

    public boolean isShowRange() {
        return showRange;
    }

    public void setShowRange(boolean v) {
        this.showRange = v;
        setChanged();
    }

    public boolean isAttackHostile() {
        return attackHostile;
    }

    public void setAttackHostile(boolean v) {
        this.attackHostile = v;
        setChanged();
    }

    public boolean isAttackNeutral() {
        return attackNeutral;
    }

    public void setAttackNeutral(boolean v) {
        this.attackNeutral = v;
        setChanged();
    }

    public boolean isAttackPassive() {
        return attackPassive;
    }

    public void setAttackPassive(boolean v) {
        this.attackPassive = v;
        setChanged();
    }

    public boolean isLit() {
        return lit;
    }

    public void setLit(boolean v) {
        this.lit = v;
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof GuardianLanternBlock) {
                boolean shouldLit = enabled && lit;
                level.setBlock(worldPosition, state.setValue(GuardianLanternBlock.LIT, shouldLit), 3);
            }
            if (!lit || !enabled) {
                clearLightBlocks();
                lightPlaceIndex = -1;
            } else {
                startLightPlacement();
            }
        }
        setChanged();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID u) {
        this.ownerUUID = u;
        setChanged();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String n) {
        this.ownerName = n;
        setChanged();
    }

    // ============================================================
    // 静态实例管理
    // ============================================================

    public static List<GuardianLanternBlockEntity> getServerInstances() {
        return SERVER_INSTANCES;
    }

    public static List<GuardianLanternBlockEntity> getClientInstances() {
        return CLIENT_INSTANCES;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_INSTANCES.remove(this);
            CLIENT_INSTANCES.add(this);
        } else if (this.level != null && !this.level.isClientSide) {
            SERVER_INSTANCES.remove(this);
            SERVER_INSTANCES.add(this);
            if (enabled && lit) {
                List<BlockPos> validPositions = new ArrayList<>();
                for (BlockPos pos : lightBlockPositions) {
                    BlockState state = level.getBlockState(pos);
                    if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
                        validPositions.add(pos);
                    }
                }
                lightBlockPositions = validPositions;
                this.level.getServer().execute(() -> {
                    if (enabled && lit) {
                        startLightPlacement();
                    }
                });
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_INSTANCES.remove(this);
        SERVER_INSTANCES.remove(this);
    }

    public void onRemove() {
        clearLightBlocks();
    }

    // ============================================================
    // 光源分帧放置
    // ============================================================

    private void startLightPlacement() {
        if (level == null || level.isClientSide) return;
        clearLightBlocks();

        List<BlockPos> targets = new ArrayList<>();
        BlockPos center = getBlockPos();

        for (int x = center.getX() - rangeX; x <= center.getX() + rangeX; x++) {
            for (int y = center.getY() - rangeY; y <= center.getY() + rangeY; y++) {
                for (int z = center.getZ() - rangeZ; z <= center.getZ() + rangeZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
                        targets.add(pos);
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            lightPlaceIndex = -1;
            return;
        }

        lightPlaceBatch = targets;
        lightPlaceIndex = 0;
        lightPlaceEnd = targets.size();
    }

    private void placeLightBatch() {
        if (level == null || level.isClientSide || lightPlaceIndex < 0) return;

        int end = Math.min(lightPlaceIndex + LIGHT_PLACE_PER_TICK, lightPlaceEnd);
        for (int i = lightPlaceIndex; i < end; i++) {
            BlockPos pos = lightPlaceBatch.get(i);
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                lightBlockPositions.add(pos);
            }
        }

        lightPlaceIndex = end;
        if (lightPlaceIndex >= lightPlaceEnd) {
            lightPlaceIndex = -1;
            lightPlaceBatch.clear();
        }
    }

    private void clearLightBlocks() {
        if (level == null || level.isClientSide) return;
        for (BlockPos pos : lightBlockPositions) {
            if (level.getBlockState(pos).getBlock() == Blocks.LIGHT) {
                level.removeBlock(pos, false);
            }
        }
        lightBlockPositions.clear();
        lightPlaceIndex = -1;
    }

    // ============================================================
    // 镇压检查
    // ============================================================

    public boolean isInSuppressionRange(BlockPos pos) {
        if (!enabled || !suppressEnabled) return false;
        int dx = Math.abs(pos.getX() - worldPosition.getX());
        int dy = Math.abs(pos.getY() - worldPosition.getY());
        int dz = Math.abs(pos.getZ() - worldPosition.getZ());
        return dx <= rangeX && dy <= rangeY && dz <= rangeZ;
    }

    public boolean shouldSuppressEntity(EntityType<?> type) {
        if (!enabled || !suppressEnabled) return false;
        MobCategory category = type.getCategory();
        if (category == MobCategory.MONSTER && suppressHostile) return true;
        if ((category == MobCategory.CREATURE || category == MobCategory.AMBIENT ||
                category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT)
                && suppressPassive) return true;
        if (category == MobCategory.MISC && suppressNeutral) return true;
        return false;
    }

    // ============================================================
    // 漏斗交互
    // ============================================================

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[]{SLOT_WEAPON, SLOT_BLACKLIST};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == SLOT_WEAPON) return true;
        if (slot == SLOT_BLACKLIST) return stack.getItem() instanceof BlacklistItem;
        return false;
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
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("SuppressHostile", suppressHostile);
        tag.putBoolean("SuppressNeutral", suppressNeutral);
        tag.putBoolean("SuppressPassive", suppressPassive);
        tag.putBoolean("SuppressEnabled", suppressEnabled);
        tag.putBoolean("AutoAttack", autoAttack);
        tag.putBoolean("ShowRange", showRange);
        tag.putBoolean("AttackHostile", attackHostile);
        tag.putBoolean("AttackNeutral", attackNeutral);
        tag.putBoolean("AttackPassive", attackPassive);
        tag.putBoolean("Lit", lit);
        tag.putInt("RangeX", rangeX);
        tag.putInt("RangeY", rangeY);
        tag.putInt("RangeZ", rangeZ);
        tag.putInt("AttackInterval", attackInterval);

        // 注意：LightBlocks 不再持久化/同步。它是运行时放置的光方块坐标，
        // 范围大时可达数万条，会导致 NBT 超过网络 2MB 上限（连接丢失）。
        // 重新加载时 onLoad 会根据 enabled+lit+range 自动重新放置并重建列表。

        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        enabled = tag.getBoolean("Enabled");
        suppressHostile = tag.getBoolean("SuppressHostile");
        suppressNeutral = tag.getBoolean("SuppressNeutral");
        suppressPassive = tag.getBoolean("SuppressPassive");
        suppressEnabled = tag.getBoolean("SuppressEnabled");
        autoAttack = tag.getBoolean("AutoAttack");
        showRange = tag.getBoolean("ShowRange");
        attackHostile = tag.getBoolean("AttackHostile");
        attackNeutral = tag.getBoolean("AttackNeutral");
        attackPassive = tag.getBoolean("AttackPassive");
        lit = tag.getBoolean("Lit");
        rangeX = tag.getInt("RangeX");
        rangeY = tag.getInt("RangeY");
        rangeZ = tag.getInt("RangeZ");
        attackInterval = tag.getInt("AttackInterval");

        // LightBlocks 已不持久化，旧存档中可能残留巨型列表，这里直接忽略，
        // 运行时列表由 onLoad → startLightPlacement 按 range 重新计算。
        lightBlockPositions.clear();

        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        ownerName = tag.getString("OwnerName");
        lightPlaceIndex = -1;
        lightPlaceBatch.clear();
    }

    // ============================================================
    // 网络同步
    // ============================================================

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) loadAdditional(pkt.getTag(), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        saveAdditional(t, registries);
        return t;
    }

    public CompoundTag saveToNbt(HolderLookup.Provider registries) {
        // BLOCK_ENTITY_DATA 组件使用 CustomData.CODEC_WITH_ID，要求 tag 必须含 "id"（方块实体类型）字段，
        // 否则保存该物品时会抛 "Missing id for entity"。saveWithId = saveAdditional + "id"。
        CompoundTag t = this.saveWithId(registries);
        // LightBlocks 是运行时放置的光方块坐标（瞬态数据），范围大时可达数万条，
        // 存进掉落物会让 NBT 超过网络 2MB 上限，导致物品同步到客户端时连接丢失。
        // 放置回来时 onLoad 会根据 enabled+lit 自动重新放置光方块，无需保存这些坐标。
        t.remove("LightBlocks");
        return t;
    }
}