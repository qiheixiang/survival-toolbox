package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.block.MicroFarmBlock;
import com.zzq.survival_toolbox.item.CapturedEntityItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.screen.MicroFarmMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 微型牧场方块实体
 * <p>
 * 存储 37 个槽位：
 * <ul>
 *   <li>0-8：食物区</li>
 *   <li>9：实体槽（被捕获实体物品）</li>
 *   <li>10-36：存储区（产出物）</li>
 * </ul>
 * 功能：放入被捕获实体和食物，实体按自身生命值周期（生命值 × 20 tick）产出战利品。
 * </p>
 */
public class MicroFarmBlockEntity extends BaseContainerBlockEntity implements MenuProvider, WorldlyContainer {

    // ============================================================
    // 槽位常量
    // ============================================================

    public static final int FOOD_START = 0;
    public static final int FOOD_END = 8;
    public static final int SLOT_ENTITY = 9;
    public static final int STORAGE_START = 10;
    public static final int STORAGE_SIZE = 27;
    public static final int TOTAL_SLOTS = 37;

    private NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    private long remainingTicks = 0;
    private float entityMaxHealth = 20.0F;
    private int entityCount = 0;

    public MicroFarmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICRO_FARM.get(), pos, state);
        syncData();
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
    // 菜单
    // ============================================================

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.zzq_survival_toolbox.micro_farm");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new MicroFarmMenu(id, inventory, this);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MicroFarmMenu(id, inventory, this);
    }

    // ============================================================
    // Getter
    // ============================================================

    public long getRemainingTicks() {
        return remainingTicks;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public float getEntityMaxHealth() {
        return entityMaxHealth;
    }

    // ============================================================
    // 核心 Tick
    // ============================================================

    public static void tick(Level level, BlockPos pos, BlockState state, MicroFarmBlockEntity be) {
        if (level.isClientSide || be.isRemoved()) return;

        ItemStack entityStack = be.items.get(SLOT_ENTITY);
        if (entityStack.isEmpty()) {
            be.resetTimer();
            be.syncData();
            return;
        }

        // 检查是否有食物
        boolean hasFood = false;
        for (int i = FOOD_START; i <= FOOD_END; i++) {
            if (!be.items.get(i).isEmpty() && be.items.get(i).has(net.minecraft.core.component.DataComponents.FOOD)) {
                hasFood = true;
                break;
            }
        }
        if (!hasFood) {
            be.resetTimer();
            be.syncData();
            return;
        }

        float singleHealth = CapturedEntityItem.getMaxHealth(entityStack);
        int entityCount = entityStack.getCount();
        if (singleHealth <= 0 || entityCount <= 0) {
            be.resetTimer();
            be.syncData();
            return;
        }

        be.entityCount = entityCount;
        be.entityMaxHealth = singleHealth;

        long intervalTicks = (long) (singleHealth * 20);

        if (be.remainingTicks <= 0) {
            be.remainingTicks = intervalTicks;
            be.syncData();
            return;
        }

        be.remainingTicks--;
        if (be.remainingTicks > 0) {
            be.syncData();
            return;
        }

        // 时间到，产出
        be.tryProduce(level);
    }

    // ============================================================
    // 产出逻辑
    // ============================================================

    private void tryProduce(Level level) {
        ItemStack entityStack = items.get(SLOT_ENTITY);
        if (entityStack.isEmpty()) {
            resetTimer();
            syncData();
            return;
        }

        // 消耗食物
        if (!consumeFood()) {
            resetTimer();
            syncData();
            return;
        }

        // 收集掉落物
        List<ItemStack> allDrops = new ArrayList<>();
        int entityCount = entityStack.getCount();

        // 战利品表
        for (int i = 0; i < entityCount; i++) {
            allDrops.addAll(getRandomDropsForEntity(level, entityStack));
        }

        // 装备（按权重随机）
        List<CapturedEntityItem.DropEntry> dropEntries = CapturedEntityItem.getDropList(entityStack, level.registryAccess());
        if (!dropEntries.isEmpty()) {
            List<CapturedEntityItem.DropEntry> equipmentEntries = new ArrayList<>();
            int totalEquipmentWeight = 0;
            for (CapturedEntityItem.DropEntry entry : dropEntries) {
                if (entry.stack.getItem() == ModItems.CAPTURED_ENTITY.get()) continue;
                if (entry.weight > 1) {
                    equipmentEntries.add(entry);
                    totalEquipmentWeight += entry.weight;
                }
            }
            if (!equipmentEntries.isEmpty()) {
                int rand = level.random.nextInt(totalEquipmentWeight);
                int cumulative = 0;
                for (CapturedEntityItem.DropEntry entry : equipmentEntries) {
                    cumulative += entry.weight;
                    if (rand < cumulative) {
                        ItemStack drop = entry.stack.copy();
                        drop.setCount(1);
                        allDrops.add(drop);
                        break;
                    }
                }
            }
        }

        // 复制一个实体
        ItemStack newEntity = entityStack.copy();
        newEntity.setCount(1);
        allDrops.add(newEntity);

        // 存入存储区
        for (ItemStack drop : allDrops) {
            if (!drop.isEmpty()) {
                storeItemInStorage(drop);
            }
        }

        resetTimer();
        syncData();
    }

    // ============================================================
    // 食物消耗
    // ============================================================

    private boolean consumeFood() {
        double totalHealth = (double) entityCount * entityMaxHealth;
        double needed = totalHealth;

        for (int i = FOOD_START; i <= FOOD_END; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            if (!stack.has(net.minecraft.core.component.DataComponents.FOOD)) continue;

            var foodProps = stack.getFoodProperties((net.minecraft.world.entity.LivingEntity) null);
            if (foodProps == null) continue;
            int nutrition = foodProps.nutrition();
            if (nutrition <= 0) continue;

            int available = stack.getCount();
            int toConsume;

            if (needed > 0) {
                toConsume = Math.min(available, (int) Math.ceil(needed / nutrition));
                if (toConsume == 0) toConsume = 1;
            } else {
                toConsume = 1;
            }

            if (toConsume > 0) {
                int consumed = Math.min(toConsume, available);
                stack.shrink(consumed);
                if (stack.isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
                if (needed > 0) {
                    needed -= consumed * nutrition;
                    if (needed < 0) needed = 0;
                }
                setChanged();
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 存储辅助
    // ============================================================

    private int storeItemInStorage(ItemStack stack) {
        int leftover = stack.getCount();
        for (int i = STORAGE_START; i < TOTAL_SLOTS; i++) {
            ItemStack slotStack = items.get(i);
            if (slotStack.isEmpty()) {
                items.set(i, stack.copy());
                setChanged();
                return 0;
            } else if (ItemStack.isSameItemSameComponents(stack, slotStack)) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, leftover);
                    slotStack.grow(transfer);
                    leftover -= transfer;
                    if (leftover <= 0) {
                        setChanged();
                        return 0;
                    }
                }
            }
        }
        return leftover;
    }

    // ============================================================
    // 战利品表解析
    // ============================================================

    private List<ItemStack> getRandomDropsForEntity(Level level, ItemStack entityStack) {
        CompoundTag nbt = CapturedEntityItem.getEntityNBT(entityStack);
        if (nbt == null) return Collections.emptyList();

        String typeId = CapturedEntityItem.getEntityTypeId(entityStack);
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(typeId));
        if (entityType == null) return Collections.emptyList();

        Entity entity = entityType.create(level);
        if (entity == null) return Collections.emptyList();
        entity.load(nbt);

        if (!(entity instanceof LivingEntity living)) {
            return Collections.emptyList();
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return Collections.emptyList();
        }

        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(serverLevel);
        DamageSource damageSource = serverLevel.damageSources().playerAttack(fakePlayer);

        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, living)
                .withParameter(LootContextParams.ORIGIN, living.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withParameter(LootContextParams.ATTACKING_ENTITY, fakePlayer)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, fakePlayer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, fakePlayer)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.ENTITY);

        ResourceKey<LootTable> lootTableId = living.getLootTable();
        if (lootTableId == null || lootTableId.equals(BuiltInLootTables.EMPTY)) {
            return Collections.emptyList();
        }

        net.minecraft.world.level.storage.loot.LootContext context =
                new net.minecraft.world.level.storage.loot.LootContext.Builder(params)
                        .create(java.util.Optional.of(lootTableId.location()));

        // 安全取表：模组实体可能声明数据包中不存在的战利品表（如 Connector 环境下的
        // MCA 村民 mca:entities/female_villager），此处缺失时跳过掉落产出，
        // 绝不能抛出异常中断方块实体 tick（会导致服务器崩溃）。
        return com.zzq.survival_toolbox.util.LootTableHelper.rollDropsOrEmpty(context, lootTableId, params);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private void resetTimer() {
        this.remainingTicks = 0;
    }

    private void syncData() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public boolean hasFood() {
        for (int i = FOOD_START; i <= FOOD_END; i++) {
            if (!items.get(i).isEmpty() && items.get(i).has(net.minecraft.core.component.DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    public Direction getFacing() {
        if (level == null) return Direction.NORTH;
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof MicroFarmBlock) {
            return state.getValue(MicroFarmBlock.FACING);
        }
        return Direction.NORTH;
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
        int[] slots = new int[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) slots[i] = i;
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot >= FOOD_START && slot <= FOOD_END) {
            return !stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD);
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= STORAGE_START && slot < TOTAL_SLOTS;
    }

    // ============================================================
    // NBT 持久化
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag itemsTag = new CompoundTag();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                itemsTag.put("Slot" + i, items.get(i).save(registries, new CompoundTag()));
            }
        }
        tag.put("Items", itemsTag);
        tag.putLong("RemainingTicks", remainingTicks);
        tag.putFloat("EntityMaxHealth", entityMaxHealth);
        tag.putInt("EntityCount", entityCount);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag itemsTag = tag.getCompound("Items");
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (itemsTag.contains("Slot" + i)) {
                items.set(i, ItemStack.parseOptional(registries, itemsTag.getCompound("Slot" + i)));
            } else {
                items.set(i, ItemStack.EMPTY);
            }
        }
        remainingTicks = tag.getLong("RemainingTicks");
        entityMaxHealth = tag.getFloat("EntityMaxHealth");
        entityCount = tag.getInt("EntityCount");
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
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
    }
}