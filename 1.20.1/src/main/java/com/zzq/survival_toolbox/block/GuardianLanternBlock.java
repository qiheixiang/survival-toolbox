package com.zzq.survival_toolbox.block;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.item.BlacklistItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.Containers;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 镇魂灯方块
 * <p>
 * 功能：照明（光方块阵列）、镇压（阻止怪物/幻翼生成）、自动攻击（使用武器自动攻击范围内的目标）。
 * 交互方式：Shift+右键打开 GUI；手持武器/黑白名单右键放入或交换对应槽位；
 * 空手右键取出武器或黑白名单。
 * </p>
 */
public class GuardianLanternBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public GuardianLanternBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 15 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LIT, true);
    }

    /**
     * 方块放置时记录放置者信息
     *
     * @param level   世界
     * @param pos     位置
     * @param state   方块状态
     * @param placer  放置者
     * @param stack   物品栈
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GuardianLanternBlockEntity lantern) {
                lantern.setOwnerUUID(player.getUUID());
                lantern.setOwnerName(player.getName().getString());
            }
        }
    }

    // ============================================================
    // 右键交互
    // ============================================================

    /**
     * 处理方块的右键交互
     * <p>
     * - Shift+右键：打开 GUI。
     * - 手持黑白名单或武器：与对应槽位进行放入或交换操作。
     * - 空手：取出武器或黑白名单（优先武器）。
     * </p>
     *
     * @param state   方块状态
     * @param level   世界
     * @param pos     方块位置
     * @param player  玩家
     * @param hand    交互手
     * @param hit     碰撞结果
     * @return 交互结果
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof GuardianLanternBlockEntity lantern)) {
            return InteractionResult.PASS;
        }

        // Shift+右键 → 打开GUI
        if (player.isCrouching()) {
            NetworkHooks.openScreen((ServerPlayer) player, lantern, pos);
            return InteractionResult.SUCCESS;
        }

        // 防连点
        if (lantern.getLastWeaponInteractTick() + 2 > level.getGameTime()) {
            return InteractionResult.PASS;
        }
        lantern.setLastWeaponInteractTick(level.getGameTime());

        ItemStack handStack = player.getItemInHand(hand);
        ItemStack weaponSlot = lantern.getWeapon();
        ItemStack blacklistSlot = lantern.getBlacklist();

        boolean isBlacklist = !handStack.isEmpty() && handStack.getItem() instanceof BlacklistItem;

        if (isBlacklist) {
            return handleSlotInteraction(player, handStack, blacklistSlot, hand,
                    lantern::setBlacklist, lantern);
        } else if (!handStack.isEmpty() && (handStack.isDamageableItem() || handStack.getMaxStackSize() == 1)) {
            return handleSlotInteraction(player, handStack, weaponSlot, hand,
                    lantern::setWeapon, lantern);
        } else {
            // 空手或非武器/非名单：取出武器或名单
            if (!weaponSlot.isEmpty()) {
                return takeItem(player, weaponSlot, lantern::setWeapon,
                        Component.translatable("message.zzq_survival_toolbox.guardian_lantern.weapon_taken"),
                        lantern, level, pos);
            } else if (!blacklistSlot.isEmpty()) {
                return takeItem(player, blacklistSlot, lantern::setBlacklist,
                        Component.translatable("message.zzq_survival_toolbox.guardian_lantern.blacklist_taken"),
                        lantern, level, pos);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.guardian_lantern.no_item"),
                        true
                );
                return InteractionResult.PASS;
            }
        }
    }

    // ----- 槽位交互通用方法 -----

    /** 槽位设置函数式接口 */
    @FunctionalInterface
    private interface SlotSetter {
        void set(ItemStack stack);
    }

    /**
     * 处理手持物品与槽位的放入/交换
     */
    private InteractionResult handleSlotInteraction(Player player, ItemStack handStack, ItemStack slotStack,
                                                    InteractionHand hand, SlotSetter setter,
                                                    GuardianLanternBlockEntity lantern) {
        if (slotStack.isEmpty()) {
            setter.set(handStack.copy());
            handStack.shrink(1);
            playPickupSound(lantern.getLevel(), lantern.getBlockPos());
            player.displayClientMessage(
                    Component.translatable("message.zzq_survival_toolbox.guardian_lantern.inserted"),
                    true
            );
            return InteractionResult.SUCCESS;
        } else {
            ItemStack temp = slotStack.copy();
            setter.set(handStack.copy());
            handStack.shrink(1);
            if (!player.getInventory().add(temp)) {
                player.drop(temp, false);
            }
            playPickupSound(lantern.getLevel(), lantern.getBlockPos());
            player.displayClientMessage(
                    Component.translatable("message.zzq_survival_toolbox.guardian_lantern.swapped"),
                    true
            );
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * 从槽位取出物品给玩家
     */
    private InteractionResult takeItem(Player player, ItemStack slotStack, SlotSetter setter,
                                       Component message, GuardianLanternBlockEntity lantern,
                                       Level level, BlockPos pos) {
        ItemStack item = slotStack.copy();
        if (player.getInventory().add(item)) {
            setter.set(ItemStack.EMPTY);
            playPickupSound(level, pos);
            player.displayClientMessage(message, true);
            return InteractionResult.SUCCESS;
        } else {
            player.displayClientMessage(
                    Component.translatable("message.zzq_survival_toolbox.inventory_full"),
                    true
            );
            return InteractionResult.PASS;
        }
    }

    /** 播放拾取音效 */
    private void playPickupSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    // ============================================================
    // 方块实体管理
    // ============================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuardianLanternBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.GUARDIAN_LANTERN.get(),
                GuardianLanternBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 方块被破坏时的回调
     * <p>
     * 将方块实体数据保存到掉落物中（保留配置），并清除光方块。
     * </p>
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                ItemStack stack = new ItemStack(this);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof GuardianLanternBlockEntity lantern) {
                    stack.addTagElement("BlockEntityTag", lantern.saveToNbt());
                }
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GuardianLanternBlockEntity lantern) {
                lantern.onRemove();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }
}