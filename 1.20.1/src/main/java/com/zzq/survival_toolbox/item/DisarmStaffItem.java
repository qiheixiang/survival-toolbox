package com.zzq.survival_toolbox.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 除你装备（缴械法杖）
 * <p>
 * 右键怪物可卸下其所有装备（手持物品和全部盔甲）。
 * 潜行时使用，装备掉落至玩家脚下；否则掉落至怪物脚下。
 * </p>
 */
public class DisarmStaffItem extends Item {

    public DisarmStaffItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 不能对玩家使用
        if (target instanceof Player) {
            return InteractionResult.PASS;
        }

        double distance = player.distanceToSqr(target);
        if (distance > 6.0 * 6.0) {
            return InteractionResult.PASS;
        }

        ServerLevel level = (ServerLevel) player.level();
        boolean hasDisarmed = false;

        // 判断掉落位置
        Vec3 dropPos = player.isCrouching() ? player.position() : target.position();

        // 遍历所有装备槽位
        EquipmentSlot[] slots = {
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : slots) {
            ItemStack slotStack = target.getItemBySlot(slot);
            if (slotStack.isEmpty()) continue;

            ItemStack disarmedItem = slotStack.copy();
            target.setItemSlot(slot, ItemStack.EMPTY);
            dropToPosition(level, dropPos, disarmedItem);
            hasDisarmed = true;
        }

        if (hasDisarmed) {
            level.playSound(null, target.blockPosition(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.8f, 1.0f + level.random.nextFloat() * 0.4f);
            spawnDisarmParticles(level, target);
        }

        return hasDisarmed ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private void dropToPosition(ServerLevel level, Vec3 pos, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(
                level,
                pos.x + (level.random.nextDouble() - 0.5) * 0.3,
                pos.y + 0.3,
                pos.z + (level.random.nextDouble() - 0.5) * 0.3,
                stack
        );
        itemEntity.setDeltaMovement(
                (level.random.nextDouble() - 0.5) * 0.1,
                0.1,
                (level.random.nextDouble() - 0.5) * 0.1
        );
        itemEntity.setPickUpDelay(10);
        level.addFreshEntity(itemEntity);
    }

    private void spawnDisarmParticles(ServerLevel level, LivingEntity target) {
        Vec3 center = target.position().add(0, target.getBbHeight() / 2, 0);
        double radius = 0.8;

        for (int i = 0; i < 30; i++) {
            double angle = (i / 30.0) * 2 * Math.PI;
            double yOffset = (level.random.nextDouble() - 0.5) * target.getBbHeight() * 0.8;

            level.sendParticles(
                    ParticleTypes.ENCHANT,
                    center.x + Math.cos(angle) * radius,
                    center.y + yOffset,
                    center.z + Math.sin(angle) * radius,
                    1,
                    Math.cos(angle) * 0.3,
                    0.1 + level.random.nextDouble() * 0.2,
                    Math.sin(angle) * 0.3,
                    0.5
            );

            if (i % 3 == 0) {
                level.sendParticles(
                        ParticleTypes.END_ROD,
                        center.x + Math.cos(angle) * radius,
                        center.y + yOffset + 0.2,
                        center.z + Math.sin(angle) * radius,
                        1,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        0.1 + level.random.nextDouble() * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        0.1
                );
            }
        }

        level.sendParticles(
                ParticleTypes.ENCHANT,
                center.x, center.y + 0.5, center.z,
                15,
                0.3, 0.3, 0.3,
                0.3
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}