package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.client.renderer.CapturedEntityRenderer;
import com.zzq.survival_toolbox.entity.CapturedEntityProjectile;
import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 被捕获实体物品
 * <p>
 * 存储捕获的实体数据（类型、NBT、名称、生命值、掉落列表等）。
 * 右键投掷后释放实体到世界中。
 * 在物品栏中会渲染捕获实体的模型。
 * </p>
 */
public class CapturedEntityItem extends Item {

    private static final String TAG_ENTITY_TYPE = "EntityType";
    private static final String TAG_ENTITY_NAME = "EntityName";
    private static final String TAG_ENTITY_TYPE_NAME = "EntityTypeName";
    private static final String TAG_ENTITY_NBT = "EntityNBT";
    private static final String TAG_MAX_HEALTH = "MaxHealth";

    public static final String TAG_DROP_LIST = "DropList";
    public static final String TAG_DROP_ENTRIES = "Entries";
    public static final String TAG_DROP_WEIGHT = "Weight";
    public static final String TAG_DROP_MIN = "MinCount";
    public static final String TAG_DROP_MAX = "MaxCount";
    public static final String TAG_POSSIBLE_DROPS = "PossibleDrops"; // 旧版兼容

    /**
     * 掉落条目：物品 + 权重 + 数量范围
     */
    public static class DropEntry {
        public final ItemStack stack;
        public final int weight;
        public final int minCount;
        public final int maxCount;

        public DropEntry(ItemStack stack, int weight, int minCount, int maxCount) {
            this.stack = stack.copy();
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        public DropEntry(Item item, int weight, int minCount, int maxCount) {
            this(new ItemStack(item), weight, minCount, maxCount);
        }
    }

    public CapturedEntityItem() {
        super(new Properties().stacksTo(64));
    }

    // ============================================================
    // 创建物品
    // ============================================================

    public static ItemStack create(String entityTypeId, String entityName,
                                   String typeName, CompoundTag entityNBT) {
        ItemStack stack = new ItemStack(ModItems.CAPTURED_ENTITY.get());
        CompoundTag tag = ItemNbt.getOrCreateTag(stack);
        tag.putString(TAG_ENTITY_TYPE, entityTypeId);
        tag.putString(TAG_ENTITY_NAME, entityName);
        tag.putString(TAG_ENTITY_TYPE_NAME, typeName);
        tag.put(TAG_ENTITY_NBT, entityNBT);
        tag.putFloat(TAG_MAX_HEALTH, getMaxHealthFromNBT(entityNBT));
        return stack;
    }

    private static float getMaxHealthFromNBT(CompoundTag nbt) {
        if (nbt == null) return 20.0F;
        if (nbt.contains("Attributes", 9)) {
            ListTag attrs = nbt.getList("Attributes", 10);
            for (int i = 0; i < attrs.size(); i++) {
                CompoundTag attr = attrs.getCompound(i);
                if (attr.getString("Name").equals("minecraft:generic.max_health")) {
                    return (float) attr.getDouble("Base");
                }
            }
        }
        if (nbt.contains("Health")) return nbt.getFloat("Health");
        return 20.0F;
    }

    // ============================================================
    // 掉落列表读写
    // ============================================================

    public static void setDropList(ItemStack stack, List<DropEntry> dropEntries,
                                   net.minecraft.core.HolderLookup.Provider registries) {
        if (dropEntries == null || dropEntries.isEmpty()) return;
        CompoundTag dropListTag = new CompoundTag();
        ListTag entriesTag = new ListTag();
        for (DropEntry entry : dropEntries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("ItemStack", entry.stack.save(registries, new CompoundTag()));
            entryTag.putInt(TAG_DROP_WEIGHT, entry.weight);
            entryTag.putInt(TAG_DROP_MIN, entry.minCount);
            entryTag.putInt(TAG_DROP_MAX, entry.maxCount);
            entriesTag.add(entryTag);
        }
        dropListTag.put(TAG_DROP_ENTRIES, entriesTag);
        ItemNbt.getOrCreateTag(stack).put(TAG_DROP_LIST, dropListTag);
    }

    public static List<DropEntry> getDropList(ItemStack stack,
                                              net.minecraft.core.HolderLookup.Provider registries) {
        List<DropEntry> result = new ArrayList<>();
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) return result;
        CompoundTag dropListTag = tag.getCompound(TAG_DROP_LIST);
        if (dropListTag.isEmpty()) return result;
        ListTag entriesTag = dropListTag.getList(TAG_DROP_ENTRIES, 10);
        for (int i = 0; i < entriesTag.size(); i++) {
            CompoundTag entryTag = entriesTag.getCompound(i);
            CompoundTag stackTag = entryTag.getCompound("ItemStack");
            ItemStack itemStack = ItemStack.parseOptional(registries, stackTag);
            if (!itemStack.isEmpty()) {
                result.add(new DropEntry(itemStack,
                        entryTag.getInt(TAG_DROP_WEIGHT),
                        entryTag.getInt(TAG_DROP_MIN),
                        entryTag.getInt(TAG_DROP_MAX)));
            }
        }
        return result;
    }

    // ============================================================
    // Getter
    // ============================================================

    public static float getMaxHealth(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag == null ? 20.0F : tag.getFloat(TAG_MAX_HEALTH);
    }

    public static String getEntityTypeId(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag == null ? "" : tag.getString(TAG_ENTITY_TYPE);
    }

    public static CompoundTag getEntityNBT(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag == null ? null : tag.getCompound(TAG_ENTITY_NBT);
    }

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CapturedEntityRenderer.INSTANCE;
            }
        });
    }

    // ============================================================
    // 释放实体
    // ============================================================

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (ItemNbt.getTag(stack) == null || !ItemNbt.getTag(stack).contains(TAG_ENTITY_TYPE)) {
                return InteractionResultHolder.fail(stack);
            }
            CapturedEntityProjectile projectile = new CapturedEntityProjectile(level, player);
            projectile.setCapturedData(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(projectile);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.SNOWBALL_THROW,
                    net.minecraft.sounds.SoundSource.NEUTRAL,
                    0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ============================================================
    // 悬浮提示
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) return;

        String entityName = tag.getString(TAG_ENTITY_NAME);
        String typeName = tag.getString(TAG_ENTITY_TYPE_NAME);
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.entity", entityName));
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.type", typeName));

        float maxHealth = tag.getFloat(TAG_MAX_HEALTH);
        if (maxHealth > 0) {
            tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.health",
                    String.format("%.1f", maxHealth)));
        }

        // 显示掉落列表（通过 TooltipComponent 渲染）
        List<DropEntry> dropEntries = getDropList(stack, level.registries());
        if (!dropEntries.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.drops"));
            tooltip.add(Component.literal("\u0000").withStyle(Style.EMPTY));
            tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.release"));
            return;
        }

        // 兼容旧版 PossibleDrops
        CompoundTag dropsTag = tag.getCompound(TAG_POSSIBLE_DROPS);
        if (dropsTag != null && !dropsTag.isEmpty()) {
            int count = dropsTag.getInt("Count");
            if (count > 0) {
                tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.drops"));
                tooltip.add(Component.literal("\u0000").withStyle(Style.EMPTY));
                tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.release"));
                return;
            }
        }

        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.no_drops_hint"));
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.release"));
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag != null && tag.contains(TAG_ENTITY_NAME)) {
            return Component.translatable("tooltip.zzq_survival_toolbox.captured_entity.name",
                    tag.getString(TAG_ENTITY_NAME));
        }
        return super.getName(stack);
    }
}