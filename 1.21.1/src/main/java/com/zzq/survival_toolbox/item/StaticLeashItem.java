package com.zzq.survival_toolbox.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 静态拴绳物品
 * <p>
 * 功能：对生物使用可将其拴在玩家手上，并冻结其 AI 使其静止不动。
 * 对已拴住的生物再次使用可切换 AI 冻结/恢复状态。
 * Shift+右键可将生物拴在栅栏上，使其固定在栅栏位置。
 * </p>
 */
public class StaticLeashItem extends Item {

    public static final String TAG_LEASHED_BY = "StaticLeashOwner";
    public static final String TAG_AI_FROZEN = "StaticLeashAIFrozen";
    public static final String TAG_LEASHED_POS = "StaticLeashPos";

    public StaticLeashItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /**
     * 右键栅栏：将已拴住的生物拴到栅栏上
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) return InteractionResult.SUCCESS;

        BlockState state = level.getBlockState(context.getClickedPos());
        if (state.getBlock() instanceof FenceBlock) {
            Mob leashedMob = findLeashedMob(player, level);
            if (leashedMob == null) {
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.static_leash.no_leashed"),
                        true
                );
                return InteractionResult.PASS;
            }

            LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, context.getClickedPos());
            leashedMob.setLeashedTo(knot, true);
            broadcastLeash(leashedMob, knot);

            BlockPos pos = context.getClickedPos();
            leashedMob.getPersistentData().putIntArray(TAG_LEASHED_POS,
                    new int[]{pos.getX(), pos.getY(), pos.getZ()});

            player.displayClientMessage(
                    Component.translatable("message.zzq_survival_toolbox.static_leash.tied_fence"),
                    true
            );
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private Mob findLeashedMob(Player player, Level level) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20))) {
            if (mob.isLeashed() && mob.getLeashHolder() == player) {
                CompoundTag data = mob.getPersistentData();
                if (data.contains(TAG_LEASHED_BY) &&
                        safeGetUUID(data, TAG_LEASHED_BY).equals(player.getUUID())) {
                    return mob;
                }
            }
        }
        return null;
    }

    /**
     * 核心交互逻辑：处理静态拴绳的绑定、解绑和 AI 切换
     *
     * @param player    玩家
     * @param mob       目标生物
     * @param crouching 是否潜行
     * @return 是否处理成功
     */
    public static boolean handleStaticLeash(Player player, Mob mob, boolean crouching) {
        if (player == null || mob == null || mob.level().isClientSide) return false;

        CompoundTag data = mob.getPersistentData();
        boolean leashedByUs = data.contains(TAG_LEASHED_BY);
        boolean aiFrozen = data.getBoolean(TAG_AI_FROZEN);

        if (crouching) {
            if (leashedByUs) {
                if (data.getBoolean(TAG_AI_FROZEN)) {
                    mob.setNoAi(false);
                    data.remove(TAG_AI_FROZEN);
                    player.displayClientMessage(
                            Component.translatable("message.zzq_survival_toolbox.static_leash.ai_restored",
                                    mob.getName().getString()), true
                    );
                } else {
                    mob.setNoAi(true);
                    data.putBoolean(TAG_AI_FROZEN, true);
                    player.displayClientMessage(
                            Component.translatable("message.zzq_survival_toolbox.static_leash.ai_frozen",
                                    mob.getName().getString()), true
                    );
                }
                syncEntityData(mob);
                return true;
            } else if (aiFrozen) {
                mob.setNoAi(false);
                data.remove(TAG_AI_FROZEN);
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.static_leash.ai_restored",
                                mob.getName().getString()), true
                );
                syncEntityData(mob);
                return true;
            } else {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
                data.putBoolean(TAG_AI_FROZEN, true);
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.static_leash.ai_frozen",
                                mob.getName().getString()), true
                );
                syncEntityData(mob);
                return true;
            }
        }

        // ---- 普通右键 ----
        if (leashedByUs) {
            UUID ownerUUID = safeGetUUID(data, TAG_LEASHED_BY);
            if (!ownerUUID.equals(player.getUUID())) {
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.static_leash.owned_by_other",
                                mob.getName().getString()), true
                );
                return false;
            }

            // 已拴在玩家身上 → 完全解开
            if (mob.isLeashed() && mob.getLeashHolder() == player) {
                mob.setNoAi(false);
                if (mob.isLeashed()) {
                    mob.setLeashedTo(null, true);
                    broadcastLeash(mob, null);
                }
                mob.setDeltaMovement(Vec3.ZERO);
                data.remove(TAG_LEASHED_BY);
                data.remove(TAG_AI_FROZEN);
                data.remove(TAG_LEASHED_POS);
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.static_leash.untied",
                                mob.getName().getString()), true
                );
                syncEntityData(mob);
                return true;
            }

            // 拴在栅栏或未拴 → 重新拴回玩家
            if (mob.isLeashed()) {
                mob.setLeashedTo(null, true);
            }
            mob.setLeashedTo(player, true);
            broadcastLeash(mob, player);
            data.remove(TAG_LEASHED_POS);
            player.displayClientMessage(
                    Component.translatable("message.zzq_survival_toolbox.static_leash.retied",
                            mob.getName().getString()), true
            );
            syncEntityData(mob);
            return true;
        }

        // 未标记，但已被其他拴绳拴住 → 切换成本模组的拴绳
        if (mob.isLeashed()) {
            mob.setLeashedTo(null, true);
            mob.setLeashedTo(player, true);
            broadcastLeash(mob, player);

            mob.setNoAi(true);
            mob.setPersistenceRequired();
            data.putUUID(TAG_LEASHED_BY, player.getUUID());
            data.putBoolean(TAG_AI_FROZEN, true);
            player.displayClientMessage(
                    Component.translatable("message.zzq_survival_toolbox.static_leash.tied",
                            mob.getName().getString()), true
            );
            syncEntityData(mob);
            return true;
        }

        // 完全自由的生物 → 拴到玩家
        mob.setLeashedTo(player, true);
        broadcastLeash(mob, player);

        mob.setNoAi(true);
        mob.setPersistenceRequired();
        data.putUUID(TAG_LEASHED_BY, player.getUUID());
        data.putBoolean(TAG_AI_FROZEN, true);
        player.displayClientMessage(
                Component.translatable("message.zzq_survival_toolbox.static_leash.tied",
                        mob.getName().getString()), true
        );
        syncEntityData(mob);
        return true;
    }

    public static void broadcastLeash(Mob mob, Entity holder) {
        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(mob,
                    new ClientboundSetEntityLinkPacket(mob, holder));
        }
    }

    private static void syncEntityData(Mob mob) {
        if (mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcastAndSend(mob,
                    new ClientboundSetEntityDataPacket(mob.getId(),
                            mob.getEntityData().getNonDefaultValues()));
        }
    }

    public static void cleanupOnDeath(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        CompoundTag data = mob.getPersistentData();
        if (data.contains(TAG_LEASHED_BY)) {
            mob.setNoAi(false);
            if (mob.isLeashed()) {
                mob.setLeashedTo(null, true);
                broadcastLeash(mob, null);
            }
            mob.setDeltaMovement(Vec3.ZERO);
            data.remove(TAG_LEASHED_BY);
            data.remove(TAG_AI_FROZEN);
            data.remove(TAG_LEASHED_POS);
        }
    }

    public static UUID safeGetUUID(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        Tag rawTag = tag.get(key);
        if (rawTag.getId() == Tag.TAG_INT_ARRAY) {
            return tag.getUUID(key);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zzq_survival_toolbox.static_leash.desc"));
    }
}