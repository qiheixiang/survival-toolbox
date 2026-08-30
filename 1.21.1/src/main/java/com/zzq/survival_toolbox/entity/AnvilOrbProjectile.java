package com.zzq.survival_toolbox.entity;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.item.CapturedEntityItem;
import com.zzq.survival_toolbox.registry.ModEntities;
import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.entity.PartEntity;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 铁砧球投射物实体
 * <p>
 * 由 {@link com.zzq.survival_toolbox.item.AnvilOrbItem} 投掷生成。
 * 命中实体后尝试捕获目标生物，捕获条件：
 * <ul>
 *   <li>目标不是玩家</li>
 *   <li>目标血量 ≤ 最大血量 × 配置阈值</li>
 *   <li>目标不在硬编码黑名单或配置黑名单中</li>
 * </ul>
 * 捕获成功后生成 {@link CapturedEntityItem} 并保存实体的战利品表和装备数据。
 * </p>
 */
public class AnvilOrbProjectile extends ThrowableItemProjectile implements ItemSupplier {

    private static final Set<String> HARDCODED_BLACKLIST = new HashSet<>(Arrays.asList(
            "minecraft:player",
            "minecraft:ender_dragon_part"
    ));

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK =
            SynchedEntityData.defineId(AnvilOrbProjectile.class, EntityDataSerializers.ITEM_STACK);

    public AnvilOrbProjectile(EntityType<? extends AnvilOrbProjectile> type, Level level) {
        super(type, level);
    }

    /** 发射器/无射手投掷用：按坐标生成弹射物 */
    public AnvilOrbProjectile(Level level, double x, double y, double z) {
        super(ModEntities.ANVIL_ORB.get(), x, y, z, level);
    }

    public AnvilOrbProjectile(Level level, LivingEntity owner) {
        super(ModEntities.ANVIL_ORB.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.GLASS;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM_STACK, stack.copy());
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM_STACK);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity hitEntity = result.getEntity();

        if (hitEntity == this.getOwner()) return;

        Entity target = getRootTarget(hitEntity);
        if (target == null || target == this.getOwner()) return;

        String targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();

        if (isBlacklisted(targetId)) {
            dropOrbItem();
            this.discard();
            return;
        }

        if (canCapture(target)) {
            captureEntity(target);
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5F, 1.0F);
            dropOrbItem();
        }
        this.discard();
    }

    /**
     * 获取实体的根目标（处理部件实体和父实体链）
     *
     * @param entity 原始实体
     * @return 根实体
     */
    private Entity getRootTarget(Entity entity) {
        if (entity instanceof PartEntity part) {
            return part.getParent();
        }
        try {
            var method = entity.getClass().getMethod("getParentMob");
            Object result = method.invoke(entity);
            if (result instanceof Entity parent) return parent;
        } catch (Exception ignored) {
        }
        try {
            var method = entity.getClass().getMethod("getOwner");
            Object result = method.invoke(entity);
            if (result instanceof Entity parent) return parent;
        } catch (Exception ignored) {
        }
        try {
            var method = entity.getClass().getMethod("getParent");
            Object result = method.invoke(entity);
            if (result instanceof Entity parent) return parent;
        } catch (Exception ignored) {
        }
        return entity;
    }

    private boolean isBlacklisted(String entityId) {
        if (HARDCODED_BLACKLIST.contains(entityId)) return true;
        var configList = ModConfig.COMMON.anvilOrbBlacklist.get();
        if (configList != null) {
            for (String id : configList) {
                if (id.equals(entityId)) return true;
            }
        }
        return false;
    }

    private boolean canCapture(Entity target) {
        if (target instanceof Player) return false;
        if (target.isRemoved()) return false;
        if (!(target instanceof LivingEntity)) return false;
        LivingEntity living = (LivingEntity) target;
        if (living.isDeadOrDying()) return false;

        double threshold = ModConfig.COMMON.anvilOrbHealthThreshold.get();
        if (threshold >= 1.0) return true;
        return living.getHealth() / living.getMaxHealth() <= threshold;
    }

    private void captureEntity(Entity target) {
        try {
            // 保存实体 NBT
            CompoundTag entityNBT = new CompoundTag();
            target.saveWithoutId(entityNBT);
            entityNBT.remove("UUID");

            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
            String entityName = target.getName().getString();
            String typeName = target.getType().getDescription().getString();

            // 预保存装备数据（死亡前）
            List<ItemStack> preDeathHandItems = new ArrayList<>();
            List<ItemStack> preDeathArmorItems = new ArrayList<>();
            List<Float> preDeathHandChances = new ArrayList<>();
            List<Float> preDeathArmorChances = new ArrayList<>();

            if (target instanceof LivingEntity living) {
                for (ItemStack stack : living.getHandSlots()) {
                    preDeathHandItems.add(stack.copy());
                }
                for (ItemStack stack : living.getArmorSlots()) {
                    preDeathArmorItems.add(stack.copy());
                }
                CompoundTag livingNBT = new CompoundTag();
                living.saveWithoutId(livingNBT);
                if (livingNBT.contains("HandDropChances", 9)) {
                    ListTag list = livingNBT.getList("HandDropChances", 5);
                    for (int i = 0; i < list.size(); i++) {
                        preDeathHandChances.add(list.getFloat(i));
                    }
                }
                if (livingNBT.contains("ArmorDropChances", 9)) {
                    ListTag list = livingNBT.getList("ArmorDropChances", 5);
                    for (int i = 0; i < list.size(); i++) {
                        preDeathArmorChances.add(list.getFloat(i));
                    }
                }
            }

            // 生成战利品掉落（死亡前，使用可靠的 LootParams 方式）
            List<ItemStack> lootDrops = new ArrayList<>();
            if (target instanceof LivingEntity living && level() instanceof ServerLevel serverLevel) {
                try {
                    lootDrops = generateLootDrops(serverLevel, living);
                } catch (Exception ex) {
                    // 吞掉异常防止中断流程
                }
            }

            // 触发死亡
            if (target instanceof LivingEntity living) {
                try {
                    Holder.Reference<net.minecraft.world.damagesource.DamageType> holder =
                            this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                    .getHolderOrThrow(DamageTypes.GENERIC);
                    DamageSource damageSource = new DamageSource(holder, this, this.getOwner());
                    // 确保死亡：先走完整死亡流程（掉落/死亡事件），再以 remove(KILLED) 回退直接抹除（无倒地动画）
                    if (!living.isRemoved()) {
                        living.setHealth(0);
                        if (!living.hurt(damageSource, Float.MAX_VALUE)) {
                            living.die(damageSource);
                        }
                        if (!living.isRemoved()) {
                            living.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                        }
                    }
                } catch (Exception ignored) {
                    // 死亡触发失败不影响捕获
                }
            }

            // 创建被捕获物品
            ItemStack capturedStack = CapturedEntityItem.create(entityId, entityName, typeName, entityNBT);

            // 写入掉落列表（含装备 + 战利品）
            if (target instanceof LivingEntity living) {
                try {
                    CompoundTag dropListNBT = buildDropListNBTWithEquipment(
                            this.level().registryAccess(),
                            living,
                            preDeathHandItems, preDeathArmorItems,
                            preDeathHandChances, preDeathArmorChances,
                            lootDrops
                    );
                    if (dropListNBT != null && !dropListNBT.isEmpty()) {
                        ItemNbt.getOrCreateTag(capturedStack).put(CapturedEntityItem.TAG_DROP_LIST, dropListNBT);
                    }
                } catch (Exception ex) {
                    // 掉落列表构建失败不影响捕获本身
                }
            }

            // 交给玩家或掉落
            Entity owner = this.getOwner();
            if (owner instanceof Player player) {
                if (!player.getInventory().add(capturedStack)) {
                    player.drop(capturedStack, false);
                }
                player.playSound(SoundEvents.ANVIL_LAND, 0.8F, 1.0F);
            } else {
                this.level().addFreshEntity(new ItemEntity(
                        this.level(), this.getX(), this.getY(), this.getZ(), capturedStack));
            }

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.0F, 1.0F);

        } catch (Exception e) {
            if (this.getOwner() instanceof Player player) {
                player.sendSystemMessage(Component.literal("捕获失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
            dropOrbItem();
        }
    }

    /**
     * 提取实体的所有可能战利品掉落（用于显示，不依赖反射）
     */
    private static List<ItemStack> generateLootDrops(ServerLevel serverLevel, LivingEntity living) {
        ResourceKey<LootTable> lootTableId = living.getLootTable();
        if (lootTableId == null || lootTableId.equals(BuiltInLootTables.EMPTY)) {
            return new ArrayList<>();
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

        net.minecraft.world.level.storage.loot.LootContext context =
                new net.minecraft.world.level.storage.loot.LootContext.Builder(params)
                        .create(java.util.Optional.of(lootTableId.location()));

        LootTable lootTable = context.getResolver()
                .lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(lootTableId).value();

        List<ItemStack> result = new ArrayList<>();
        try {
            // 将战利品表序列化为 JSON，遍历所有条目，收集所有可能的物品
            net.minecraft.resources.RegistryOps<com.google.gson.JsonElement> ops =
                    net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                            serverLevel.registryAccess());
            com.google.gson.JsonElement json =
                    LootTable.DIRECT_CODEC.encodeStart(ops, lootTable).getOrThrow();
            if (json.isJsonObject() && json.getAsJsonObject().has("pools")) {
                for (com.google.gson.JsonElement pool : json.getAsJsonObject().getAsJsonArray("pools")) {
                    if (pool.isJsonObject() && pool.getAsJsonObject().has("entries")) {
                        for (com.google.gson.JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                            collectEntryItems(entry, result);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败时退回单次随机掉落
            try {
                result = lootTable.getRandomItems(params);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 递归收集战利品条目中所有可能的物品
     */
    private static void collectEntryItems(com.google.gson.JsonElement entry, List<ItemStack> result) {
        if (!entry.isJsonObject()) return;
        com.google.gson.JsonObject obj = entry.getAsJsonObject();
        String type = obj.has("type") ? obj.get("type").getAsString() : "";

        if ("minecraft:item".equals(type)) {
            if (obj.has("name")) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(obj.get("name").getAsString()));
                if (item != null && item != Items.AIR) {
                    result.add(new ItemStack(item));
                }
            }
        } else if ("minecraft:tag".equals(type) && obj.has("name")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM,
                    ResourceLocation.parse(obj.get("name").getAsString()));
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders -> {
                for (Holder<Item> holder : holders) {
                    if (holder.value() != Items.AIR) {
                        result.add(new ItemStack(holder.value()));
                    }
                }
            });
        } else if (obj.has("children") && obj.get("children").isJsonArray()) {
            for (com.google.gson.JsonElement child : obj.getAsJsonArray("children")) {
                collectEntryItems(child, result);
            }
        }
    }

    private CompoundTag buildDropListNBTWithEquipment(
            net.minecraft.core.HolderLookup.Provider registries,
            LivingEntity living,
            List<ItemStack> handItems,
            List<ItemStack> armorItems,
            List<Float> handChances,
            List<Float> armorChances,
            List<ItemStack> lootDrops) {

        List<CapturedEntityItem.DropEntry> dropEntries = new ArrayList<>();

        // 战利品掉落
        for (ItemStack drop : lootDrops) {
            if (drop != null && !drop.isEmpty()) {
                dropEntries.add(new CapturedEntityItem.DropEntry(drop, 1, 1, drop.getCount()));
            }
        }

        // 手持物品
        for (int i = 0; i < handItems.size() && i < handChances.size(); i++) {
            ItemStack stack = handItems.get(i);
            if (!stack.isEmpty()) {
                float chance = handChances.get(i) > 0 ? handChances.get(i) : 0.085F;
                int weight = (int) (chance * 100);
                if (weight > 0) {
                    dropEntries.add(new CapturedEntityItem.DropEntry(stack, weight, 1, 1));
                }
            }
        }

        // 盔甲
        for (int i = 0; i < armorItems.size() && i < armorChances.size(); i++) {
            ItemStack stack = armorItems.get(i);
            if (!stack.isEmpty()) {
                float chance = armorChances.get(i) > 0 ? armorChances.get(i) : 0.085F;
                int weight = (int) (chance * 100);
                if (weight > 0) {
                    dropEntries.add(new CapturedEntityItem.DropEntry(stack, weight, 1, 1));
                }
            }
        }

        CompoundTag dropListTag = new CompoundTag();
        ListTag entriesTag = new ListTag();
        for (CapturedEntityItem.DropEntry entry : dropEntries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("ItemStack", entry.stack.save(registries, new CompoundTag()));
            entryTag.putInt(CapturedEntityItem.TAG_DROP_WEIGHT, entry.weight);
            entryTag.putInt(CapturedEntityItem.TAG_DROP_MIN, entry.minCount);
            entryTag.putInt(CapturedEntityItem.TAG_DROP_MAX, entry.maxCount);
            entriesTag.add(entryTag);
        }
        dropListTag.put(CapturedEntityItem.TAG_DROP_ENTRIES, entriesTag);
        return dropListTag;
    }

    private List<CapturedEntityItem.DropEntry> parseLootTableFallback(LootTable lootTable) {
        List<CapturedEntityItem.DropEntry> result = new ArrayList<>();
        try {
            List<LootPool> pools = null;
            for (Field field : LootTable.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(lootTable);
                if (value instanceof List) {
                    List<?> list = (List<?>) value;
                    if (!list.isEmpty() && list.get(0) instanceof LootPool) {
                        pools = (List<LootPool>) list;
                        break;
                    }
                }
            }
            if (pools == null) return result;

            for (LootPool pool : pools) {
                List<?> entriesList = null;
                for (Field field : LootPool.class.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(pool);
                    if (value instanceof List) {
                        entriesList = (List<?>) value;
                        break;
                    } else if (value instanceof LootPoolEntryContainer[]) {
                        entriesList = Arrays.asList((LootPoolEntryContainer[]) value);
                        break;
                    }
                }
                if (entriesList == null) continue;

                for (Object entryObj : entriesList) {
                    CapturedEntityItem.DropEntry entry = parseEntryFallback(entryObj);
                    if (entry != null) result.add(entry);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private CapturedEntityItem.DropEntry parseEntryFallback(Object entryObj) {
        if (entryObj == null) return null;
        if (entryObj instanceof LootItem) {
            return parseLootItemFallback((LootItem) entryObj);
        }
        // TagEntry 处理
        if (entryObj.getClass().getSimpleName().equals("TagEntry")) {
            try {
                Field tagField = entryObj.getClass().getDeclaredField("tag");
                tagField.setAccessible(true);
                Object tagObj = tagField.get(entryObj);
                if (tagObj instanceof TagKey) {
                    TagKey<Item> tag = (TagKey<Item>) tagObj;
                    var holders = BuiltInRegistries.ITEM.getTag(tag);
                    if (holders.isPresent()) {
                        int weight = 1;
                        try {
                            Field weightField = entryObj.getClass().getDeclaredField("weight");
                            weightField.setAccessible(true);
                            weight = weightField.getInt(entryObj);
                        } catch (Exception ignored) {
                        }
                        for (Holder<Item> holder : holders.get()) {
                            Item item = holder.value();
                            if (item != null && item != Items.AIR) {
                                return new CapturedEntityItem.DropEntry(new ItemStack(item), weight, 1, 1);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        // 反射提取
        try {
            for (Field field : entryObj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(entryObj);
                if (value instanceof Item) {
                    return new CapturedEntityItem.DropEntry((Item) value, 1, 1, 1);
                }
                if (value instanceof LootItem) {
                    return parseEntryFallback(value);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private CapturedEntityItem.DropEntry parseLootItemFallback(LootItem lootItem) {
        try {
            Item item = null;
            int weight = 1;
            int minCount = 1;
            int maxCount = 1;

            for (Field field : lootItem.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(lootItem);
                if (value instanceof Item) {
                    item = (Item) value;
                } else if (field.getType() == int.class && field.getName().toLowerCase().contains("weight")) {
                    weight = field.getInt(lootItem);
                } else if (value instanceof IntProvider) {
                    IntProvider provider = (IntProvider) value;
                    minCount = provider.getMinValue();
                    maxCount = provider.getMaxValue();
                }
            }

            if (item != null && item != Items.AIR) {
                return new CapturedEntityItem.DropEntry(new ItemStack(item), weight, minCount, maxCount);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void dropOrbItem() {
        ItemStack stack = new ItemStack(ModItems.ANVIL_ORB.get());
        this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), stack));
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        dropOrbItem();
        this.discard();
    }
}