package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.block.entity.EnchantmentTransferBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlocks;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 附魔数据交换菜单容器
 * <p>
 * 两个槽位（A 和 B），点击交换按钮可互换两件装备的嗜血或自适应附魔数据。
 * 要求两件装备类型相同（武器 ↔ 武器，护甲 ↔ 护甲）。
 * </p>
 */
public class EnchantmentTransferMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;
    private final Player player;

    public EnchantmentTransferMenu(int id, Inventory playerInventory, FriendlyByteBuf data) {
        this(id, playerInventory, getBlockEntity(playerInventory, data));
    }

    private static EnchantmentTransferBlockEntity getBlockEntity(Inventory playerInventory, FriendlyByteBuf data) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof EnchantmentTransferBlockEntity) {
            return (EnchantmentTransferBlockEntity) be;
        }
        throw new IllegalStateException("Invalid block entity");
    }

    public EnchantmentTransferMenu(int id, Inventory playerInventory,
                                   EnchantmentTransferBlockEntity blockEntity) {
        super(ModMenus.ENCHANTMENT_TRANSFER.get(), id);
        this.container = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.player = playerInventory.player;

        // ---- 槽位 A 和 B ----
        this.addSlot(new Slot(container, EnchantmentTransferBlockEntity.SLOT_A, 44, 35));
        this.addSlot(new Slot(container, EnchantmentTransferBlockEntity.SLOT_B, 116, 35));

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.ENCHANTMENT_TRANSFER.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index < EnchantmentTransferBlockEntity.TOTAL_SLOTS) {
                if (!this.moveItemStackTo(slotStack, EnchantmentTransferBlockEntity.TOTAL_SLOTS,
                        this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, EnchantmentTransferBlockEntity.TOTAL_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return stack;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            performSwap();
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    // ============================================================
    // 核心交换逻辑
    // ============================================================

    /**
     * 执行嗜血或自适应数据的交换
     */
    private void performSwap() {
        ItemStack stackA = container.getItem(EnchantmentTransferBlockEntity.SLOT_A);
        ItemStack stackB = container.getItem(EnchantmentTransferBlockEntity.SLOT_B);

        if (stackA.isEmpty() || stackB.isEmpty()) {
            sendMessage("message.zzq_survival_toolbox.enchantment_transfer.need_two_items");
            return;
        }

        boolean isAWeapon = isWeapon(stackA);
        boolean isBWeapon = isWeapon(stackB);
        boolean isAArmor = isArmor(stackA);
        boolean isBArmor = isArmor(stackB);

        if ((isAWeapon && isBArmor) || (isAArmor && isBWeapon)) {
            sendMessage("message.zzq_survival_toolbox.enchantment_transfer.different_types");
            return;
        }

        if (isAWeapon && isBWeapon) {
            float bonusA = getBloodthirstyBonus(stackA);
            float bonusB = getBloodthirstyBonus(stackB);
            if (bonusA <= 0 && bonusB <= 0) {
                sendMessage("message.zzq_survival_toolbox.enchantment_transfer.not_applicable");
                return;
            }
            swapBloodthirstyData(stackA, stackB);
            sendMessage("message.zzq_survival_toolbox.enchantment_transfer.bloodthirsty_swapped");
        } else if (isAArmor && isBArmor) {
            boolean hasA = hasAdaptationData(stackA);
            boolean hasB = hasAdaptationData(stackB);
            if (!hasA && !hasB) {
                sendMessage("message.zzq_survival_toolbox.enchantment_transfer.not_applicable");
                return;
            }
            swapAdaptationData(stackA, stackB);
            sendMessage("message.zzq_survival_toolbox.enchantment_transfer.adaptation_swapped");
        } else {
            sendMessage("message.zzq_survival_toolbox.enchantment_transfer.not_applicable");
        }

        container.setChanged();
        broadcastChanges();
    }

    // ============================================================
    // 类型判断
    // ============================================================

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean[] hasAttack = {false};
        stack.getAttributeModifiers().forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                hasAttack[0] = true;
            }
        });
        if (hasAttack[0]) {
            return true;
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.contains("sword") || path.contains("axe") || path.contains("trident") ||
                path.contains("mace") || path.contains("dagger") || path.contains("scythe") ||
                stack.getItem() instanceof net.minecraft.world.item.SwordItem ||
                stack.getItem() instanceof net.minecraft.world.item.TridentItem ||
                stack.getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    private boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean[] hasArmor = {false};
        stack.getAttributeModifiers().forEach(
                net.minecraft.world.entity.EquipmentSlot.HEAD, (attr, mod) -> {
                    if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) {
                        hasArmor[0] = true;
                    }
                });
        if (hasArmor[0]) {
            return true;
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") ||
                path.contains("boots") || path.contains("head") || path.contains("body") ||
                path.contains("legs") || path.contains("feet") ||
                stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
    }

    // ============================================================
    // 嗜血数据交换
    // ============================================================

    private void swapBloodthirstyData(ItemStack a, ItemStack b) {
        float bonusA = getBloodthirstyBonus(a);
        float bonusB = getBloodthirstyBonus(b);
        setBloodthirstyBonus(a, bonusB);
        setBloodthirstyBonus(b, bonusA);
    }

    private float getBloodthirstyBonus(ItemStack stack) {
        CompoundTag modData = ItemNbt.getOrCreateTag(stack).getCompound("zzq_survival_toolbox_data");
        return modData.getFloat("bloodthirsty_bonus");
    }

    private void setBloodthirstyBonus(ItemStack stack, float bonus) {
        if (bonus <= 0) {
            CompoundTag modData = ItemNbt.getOrCreateTag(stack).getCompound("zzq_survival_toolbox_data");
            modData.remove("bloodthirsty_bonus");
            if (modData.isEmpty()) {
                ItemNbt.getOrCreateTag(stack).remove("zzq_survival_toolbox_data");
            } else {
                ItemNbt.getOrCreateTag(stack).put("zzq_survival_toolbox_data", modData);
            }
        } else {
            CompoundTag modData = ItemNbt.getOrCreateTag(stack).getCompound("zzq_survival_toolbox_data");
            modData.putFloat("bloodthirsty_bonus", bonus);
            ItemNbt.getOrCreateTag(stack).put("zzq_survival_toolbox_data", modData);
        }
    }

    // ============================================================
    // 自适应数据交换
    // ============================================================

    private void swapAdaptationData(ItemStack a, ItemStack b) {
        float layersA = getAdaptationLayers(a);
        float layersB = getAdaptationLayers(b);
        float shieldA = getAdaptationShield(a);
        float shieldB = getAdaptationShield(b);
        float maxShieldA = getAdaptationMaxShield(a);
        float maxShieldB = getAdaptationMaxShield(b);
        CompoundTag adaptDataA = getAdaptationData(a);
        CompoundTag adaptDataB = getAdaptationData(b);

        setAdaptationLayers(a, layersB);
        setAdaptationShield(a, shieldB);
        setAdaptationMaxShield(a, maxShieldB);
        setAdaptationData(a, adaptDataB);

        setAdaptationLayers(b, layersA);
        setAdaptationShield(b, shieldA);
        setAdaptationMaxShield(b, maxShieldA);
        setAdaptationData(b, adaptDataA);
    }

    private boolean hasAdaptationData(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CompoundTag tag = ItemNbt.getOrCreateTag(stack);
        if (tag.contains("adapt_layers") && tag.getFloat("adapt_layers") > 0) return true;
        if (tag.contains("adapt_shield_current") && tag.getFloat("adapt_shield_current") > 0) return true;
        if (tag.contains("adapt_shield_max") && tag.getFloat("adapt_shield_max") > 0) return true;
        if (tag.contains("adapt_data") && !tag.getCompound("adapt_data").isEmpty()) return true;
        return false;
    }

    private float getAdaptationLayers(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getFloat("adapt_layers");
    }

    private void setAdaptationLayers(ItemStack stack, float layers) {
        if (layers <= 0) {
            ItemNbt.getOrCreateTag(stack).remove("adapt_layers");
        } else {
            ItemNbt.getOrCreateTag(stack).putFloat("adapt_layers", layers);
        }
    }

    private float getAdaptationShield(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getFloat("adapt_shield_current");
    }

    private void setAdaptationShield(ItemStack stack, float shield) {
        if (shield <= 0) {
            ItemNbt.getOrCreateTag(stack).remove("adapt_shield_current");
        } else {
            ItemNbt.getOrCreateTag(stack).putFloat("adapt_shield_current", shield);
        }
    }

    private float getAdaptationMaxShield(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getFloat("adapt_shield_max");
    }

    private void setAdaptationMaxShield(ItemStack stack, float max) {
        if (max <= 0) {
            ItemNbt.getOrCreateTag(stack).remove("adapt_shield_max");
        } else {
            ItemNbt.getOrCreateTag(stack).putFloat("adapt_shield_max", max);
        }
    }

    private CompoundTag getAdaptationData(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getCompound("adapt_data");
    }

    private void setAdaptationData(ItemStack stack, CompoundTag data) {
        if (data.isEmpty()) {
            ItemNbt.getOrCreateTag(stack).remove("adapt_data");
        } else {
            ItemNbt.getOrCreateTag(stack).put("adapt_data", data);
        }
    }

    private void sendMessage(String key) {
        if (player != null && !player.level().isClientSide) {
            player.sendSystemMessage(Component.translatable(key));
        }
    }
}