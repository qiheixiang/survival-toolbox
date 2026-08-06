package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.block.FeastBlock;
import com.zzq.survival_toolbox.data.BlacklistEntry;
import com.zzq.survival_toolbox.item.BlacklistItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.screen.FeastMenu;
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
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 混沌篝火方块实体
 * <p>
 * 存储 28 个槽位：
 * <ul>
 *   <li>0-26：物品存储区（食物、药水、牛奶、蜂蜜等）</li>
 *   <li>27：黑白名单槽</li>
 * </ul>
 * 功能：定时对范围内的目标实体直接喂食物品副本（不消耗原物品），
 * 支持黑白名单联动控制目标筛选。
 * </p>
 */
public class FeastBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, MenuProvider {

    public static final int STORAGE_START = 0;
    public static final int SLOT_BLACKLIST = 27;
    public static final int TOTAL_SLOTS = 28;

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    // ----- 配置参数 -----
    private boolean enabled = false;
    private int workInterval = 20;
    private int workCooldown = 20;
    private int rangeX = 9;
    private int rangeY = 9;
    private int rangeZ = 9;
    private boolean showRange = true;
    private boolean targetPlayer = false;
    private boolean targetNeutral = false;
    private boolean targetPassive = false;
    private boolean targetHostile = false;

    // 客户端/服务端实例列表
    private static final List<FeastBlockEntity> CLIENT_INSTANCES = new ArrayList<>();
    private static final List<FeastBlockEntity> SERVER_INSTANCES = new ArrayList<>();

    private final LazyOptional<? extends IItemHandler>[] handlers =
            SidedInvWrapper.create(this, Direction.values());

    public FeastBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FEAST.get(), pos, state);
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
        for (ItemStack s : items) if (!s.isEmpty()) return false;
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
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public ItemStack getBlacklist() {
        return items.get(SLOT_BLACKLIST);
    }

    public void setBlacklist(ItemStack stack) {
        items.set(SLOT_BLACKLIST, stack);
        setChanged();
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    // ============================================================
    // 菜单
    // ============================================================

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.feast");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new FeastMenu(id, inventory, this);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FeastMenu(id, inventory, this);
    }

    // ============================================================
    // 漏斗交互
    // ============================================================

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[27];
        for (int i = 0; i < 27; i++) slots[i] = i;
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == SLOT_BLACKLIST) return stack.getItem() instanceof BlacklistItem;
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
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
    // 客户端/服务端实例管理
    // ============================================================

    public static List<FeastBlockEntity> getClientInstances() {
        return CLIENT_INSTANCES;
    }

    public static List<FeastBlockEntity> getServerInstances() {
        return SERVER_INSTANCES;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_INSTANCES.add(this);
        } else if (this.level != null && !this.level.isClientSide) {
            SERVER_INSTANCES.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_INSTANCES.remove(this);
        } else if (this.level != null && !this.level.isClientSide) {
            SERVER_INSTANCES.remove(this);
        }
    }

    // ============================================================
    // Getter / Setter
    // ============================================================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
        setChanged();
        sync();
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof FeastBlock) {
                level.setBlock(worldPosition, state.setValue(FeastBlock.LIT, v), 3);
            }
        }
    }

    public int getWorkInterval() {
        return workInterval;
    }

    public void setWorkInterval(int v) {
        this.workInterval = Math.max(1, Math.min(600, v));
        setChanged();
        sync();
    }

    public int getWorkCooldown() {
        return workCooldown;
    }

    public void setWorkCooldown(int v) {
        this.workCooldown = v;
        setChanged();
        sync();
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
        setChanged();
        sync();
    }

    public void setRangeY(int v) {
        this.rangeY = Math.max(1, Math.min(64, v));
        setChanged();
        sync();
    }

    public void setRangeZ(int v) {
        this.rangeZ = Math.max(1, Math.min(64, v));
        setChanged();
        sync();
    }

    public boolean isShowRange() {
        return showRange;
    }

    public void setShowRange(boolean v) {
        this.showRange = v;
        setChanged();
        sync();
    }

    public boolean isTargetPlayer() {
        return targetPlayer;
    }

    public void setTargetPlayer(boolean v) {
        this.targetPlayer = v;
        setChanged();
        sync();
    }

    public boolean isTargetNeutral() {
        return targetNeutral;
    }

    public void setTargetNeutral(boolean v) {
        this.targetNeutral = v;
        setChanged();
        sync();
    }

    public boolean isTargetPassive() {
        return targetPassive;
    }

    public void setTargetPassive(boolean v) {
        this.targetPassive = v;
        setChanged();
        sync();
    }

    public boolean isTargetHostile() {
        return targetHostile;
    }

    public void setTargetHostile(boolean v) {
        this.targetHostile = v;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
        tag.putInt("WorkInterval", workInterval);
        tag.putInt("WorkCooldown", workCooldown);
        tag.putInt("RangeX", rangeX);
        tag.putInt("RangeY", rangeY);
        tag.putInt("RangeZ", rangeZ);
        tag.putBoolean("ShowRange", showRange);
        tag.putBoolean("TargetPlayer", targetPlayer);
        tag.putBoolean("TargetNeutral", targetNeutral);
        tag.putBoolean("TargetPassive", targetPassive);
        tag.putBoolean("TargetHostile", targetHostile);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
        enabled = tag.getBoolean("Enabled");
        workInterval = tag.getInt("WorkInterval");
        workCooldown = tag.getInt("WorkCooldown");
        rangeX = tag.getInt("RangeX");
        rangeY = tag.getInt("RangeY");
        rangeZ = tag.getInt("RangeZ");
        showRange = tag.getBoolean("ShowRange");
        targetPlayer = tag.getBoolean("TargetPlayer");
        targetNeutral = tag.getBoolean("TargetNeutral");
        targetPassive = tag.getBoolean("TargetPassive");
        targetHostile = tag.getBoolean("TargetHostile");
        if (workCooldown <= 0) workCooldown = workInterval;
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

    // ============================================================
    // 核心 Tick
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, FeastBlockEntity be) {
        if (level.isClientSide || be.isRemoved() || !be.enabled) return;

        be.workCooldown--;
        if (be.workCooldown <= 0) {
            be.workCooldown = be.workInterval;
            be.applyEffects((ServerLevel) level);
        }
    }

    // ============================================================
    // 应用效果（直接喂食副本，不消耗原物品）
    // ============================================================

    private void applyEffects(ServerLevel level) {
        List<LivingEntity> targets = collectTargets(level);
        if (targets.isEmpty()) return;

        for (LivingEntity target : targets) {
            for (int i = 0; i < 27; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;

                // 药水处理
                if (stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION ||
                        stack.getItem() == Items.LINGERING_POTION) {
                    var potion = PotionUtils.getPotion(stack);
                    if (potion != Potions.EMPTY && potion != Potions.WATER) {
                        for (MobEffectInstance effect : potion.getEffects()) {
                            target.addEffect(new MobEffectInstance(effect));
                        }
                    }
                    continue;
                }

                // 其他物品：使用副本喂食，不消耗原物品，静音处理
                ItemStack food = stack.copy();
                food.setCount(1);
                boolean wasSilent = target.isSilent();
                target.setSilent(true);
                try {
                    food.finishUsingItem(level, target);
                } finally {
                    target.setSilent(wasSilent);
                }
            }
        }
    }

    // ============================================================
    // 收集目标（含黑白名单修正）
    // ============================================================

    private List<LivingEntity> collectTargets(ServerLevel level) {
        BlockPos center = getBlockPos();
        AABB bounds = new AABB(
                center.getX() - rangeX, center.getY() - rangeY, center.getZ() - rangeZ,
                center.getX() + rangeX + 1, center.getY() + rangeY + 1, center.getZ() + rangeZ + 1
        );

        ItemStack blacklistStack = getBlacklist();
        boolean hasBlacklist = !blacklistStack.isEmpty() && blacklistStack.getItem() instanceof BlacklistItem;
        List<BlacklistEntry> whitelist = new ArrayList<>();
        List<BlacklistEntry> blacklist = new ArrayList<>();

        if (hasBlacklist) {
            List<BlacklistEntry> entries = BlacklistItem.getEntries(blacklistStack);
            for (BlacklistEntry entry : entries) {
                if (entry.action == BlacklistEntry.Action.WHITELIST) whitelist.add(entry);
                else if (entry.action == BlacklistEntry.Action.BLACKLIST) blacklist.add(entry);
            }
        }

        List<LivingEntity> allTargets = level.getEntitiesOfClass(LivingEntity.class, bounds,
                e -> e != null && e.isAlive());

        List<LivingEntity> result = new ArrayList<>();

        for (LivingEntity target : allTargets) {
            if (matchesAny(target, whitelist)) continue;
            if (matchesAny(target, blacklist)) {
                result.add(target);
                continue;
            }

            if (target instanceof Player) {
                if (targetPlayer) result.add(target);
                continue;
            }

            MobCategory category = target.getType().getCategory();
            if (category == MobCategory.MONSTER && targetHostile) {
                result.add(target);
            } else if ((category == MobCategory.CREATURE || category == MobCategory.AMBIENT ||
                    category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT)
                    && targetPassive) {
                result.add(target);
            } else if (category == MobCategory.MISC && targetNeutral) {
                result.add(target);
            }
        }

        return result;
    }

    private boolean matchesAny(LivingEntity target, List<BlacklistEntry> entries) {
        for (BlacklistEntry entry : entries) {
            if (entry.matches(target)) return true;
        }
        return false;
    }
}