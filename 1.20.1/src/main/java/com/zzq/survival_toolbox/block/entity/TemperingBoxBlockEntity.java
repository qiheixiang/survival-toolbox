package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.item.CapturedEntityItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import com.zzq.survival_toolbox.screen.TemperingBoxMenu;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 锤炼箱方块实体
 * <p>
 * 12 槽：0-7 自适应装备（仅限带自适应附魔的装备），8-11 被捕捉实体（每格 64）。
 * 每秒：实体总数为 N 时，每件自适应装备 +0.001 × N 层（模拟被捕捉实体攻击装备）。
 * </p>
 */
public class TemperingBoxBlockEntity extends BaseContainerBlockEntity {

    public static final int EQUIP_SIZE = 8;
    public static final int ENTITY_START = 8;
    public static final int ENTITY_SIZE = 4;
    public static final int TOTAL_SLOTS = 12;

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int tickAccum = 0;

    public TemperingBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEMPERING_BOX.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.tempering_box");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new TemperingBoxMenu(id, inventory, this);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TemperingBoxMenu(id, inventory, this);
    }

    // ============================================================
    // Tick：每秒模拟被捕捉实体攻击，+0.001 × 实体数 层
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, TemperingBoxBlockEntity be) {
        if (level.isClientSide || be.isRemoved()) return;
        if (++be.tickAccum < 20) return;
        be.tickAccum = 0;

        // 统计被捕捉实体总数（4 格 × 每格最多 64）
        int entityCount = 0;
        for (int i = ENTITY_START; i < ENTITY_START + ENTITY_SIZE; i++) {
            entityCount += be.items.get(i).getCount();
        }
        if (entityCount <= 0) return;

        // 每件自适应装备 +0.001 × 实体数
        double gain = 0.001 * entityCount;
        boolean changed = false;
        for (int i = 0; i < EQUIP_SIZE; i++) {
            ItemStack armor = be.items.get(i);
            if (armor.isEmpty()) continue;
            if (armor.getEnchantmentLevel(ModEnchantments.ADAPTATION.get()) > 0) {
                AdaptationHelper.addArmorLayers(armor, gain);
                changed = true;
            }
        }
        if (changed) be.setChanged();
    }

    // ============================================================
    // 容器
    // ============================================================

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index >= 0 && index < this.items.size()) {
            return this.items.get(index);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.items.addAll(NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY));
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index < EQUIP_SIZE) {
            // 装备格：仅限带自适应附魔的装备
            return stack.getEnchantmentLevel(ModEnchantments.ADAPTATION.get()) > 0;
        }
        if (index < ENTITY_START + ENTITY_SIZE) {
            // 实体格：仅限被捕捉实体
            return stack.getItem() instanceof CapturedEntityItem;
        }
        return false;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index >= 0 && index < this.items.size()) {
            this.items.set(index, stack);
            this.setChanged();
        }
    }

    @Override
    public ItemStack removeItem(int index, int amount) {
        if (index >= 0 && index < this.items.size()) {
            ItemStack stack = this.items.get(index);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            if (amount >= stack.getCount()) {
                this.items.set(index, ItemStack.EMPTY);
                this.setChanged();
                return stack;
            }
            ItemStack split = stack.split(amount);
            this.setChanged();
            return split;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index >= 0 && index < this.items.size()) {
            ItemStack stack = this.items.get(index);
            this.items.set(index, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.worldPosition == null) return false;
        return player.distanceToSqr(this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }
}
