package com.zzq.survival_toolbox.block;

import com.zzq.survival_toolbox.block.entity.FeastBlockEntity;
import com.zzq.survival_toolbox.item.BlacklistItem;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.Containers;

import javax.annotation.Nullable;

/**
 * 混沌篝火方块
 * <p>
 * 功能：定时对范围内的生物施加食物效果（直接喂食副本）、药水效果或牛奶效果。
 * 支持黑白名单联动控制目标筛选。交互方式：Shift+右键打开 GUI，
 * 手持黑白名单右键放入/交换名单槽，普通右键切换点燃/熄灭状态。
 * </p>
 */
public class FeastBlock extends BaseEntityBlock {

    /** 点燃状态属性 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** 方块碰撞箱（篝火模型） */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(2, 4, 2, 14, 14, 14),
            Block.box(0, 4, 0, 2, 14, 2),
            Block.box(14, 4, 0, 16, 14, 2),
            Block.box(0, 4, 14, 2, 14, 16),
            Block.box(14, 4, 14, 16, 14, 16)
    );

    public FeastBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(FeastBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LIT, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * 处理方块的右键交互
     * <p>
     * - FakePlayer 跳过所有交互（防止智慧农场误触发）。
     * - Shift+右键：打开 GUI。
     * - 手持黑白名单：与名单槽进行放入/交换操作。
     * - 普通右键：切换点燃/熄灭状态。
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
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof FakePlayer) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FeastBlockEntity feastBE)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }

        if (player.isCrouching()) {
            ((ServerPlayer) player).openMenu(feastBE, buf -> buf.writeBlockPos(pos));
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }

        // ---- 黑白名单槽交互 ----
        ItemStack handStack = player.getItemInHand(hand);
        boolean isBlacklist = !handStack.isEmpty() && handStack.getItem() instanceof BlacklistItem;

        if (isBlacklist) {
            ItemStack slotStack = feastBE.getBlacklist();
            if (slotStack.isEmpty()) {
                feastBE.setBlacklist(handStack.copy());
                handStack.shrink(1);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.feast.blacklist_inserted"),
                        true
                );
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            } else {
                ItemStack temp = slotStack.copy();
                feastBE.setBlacklist(handStack.copy());
                handStack.shrink(1);
                if (!player.getInventory().add(temp)) {
                    player.drop(temp, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                player.displayClientMessage(
                        Component.translatable("message.zzq_survival_toolbox.feast.blacklist_swapped"),
                        true
                );
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            }
        }

        // ---- 切换点燃/熄灭 ----
        boolean newLit = !state.getValue(LIT);
        level.setBlock(pos, state.setValue(LIT, newLit), 3);
        feastBE.setEnabled(newLit);
        level.playSound(null, pos,
                newLit ? SoundEvents.CAMPFIRE_CRACKLE : SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 0.5F, 1.0F);

        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    // ============================================================
    // 粒子效果
    // ============================================================

    /**
     * 客户端粒子动画
     * <p>
     * 点燃状态下生成灵魂火焰和灵魂粒子。
     * </p>
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;

        if (random.nextInt(10) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
            double y = pos.getY() + 0.8 + random.nextDouble() * 0.4;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.02, 0);
        }
        if (random.nextInt(5) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double y = pos.getY() + 1.0 + random.nextDouble() * 0.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0.02, 0);
        }
    }

    // ============================================================
    // 方块实体管理
    // ============================================================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeastBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.FEAST.get(), FeastBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 方块被破坏时的回调
     * <p>
     * 掉落方块自身以及方块实体中存储的所有物品。
     * </p>
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FeastBlockEntity feastBE) {
                feastBE.dropContents();
            }
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(this));
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}