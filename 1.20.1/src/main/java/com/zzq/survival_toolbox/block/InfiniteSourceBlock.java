package com.zzq.survival_toolbox.block;

import com.zzq.survival_toolbox.block.entity.InfiniteSourceBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 无限之源方块
 * <p>
 * 功能：万能无限流体源。放入液体容器后记录其流体类型，随后可无限量供应同种流体。
 * 交互方式：右键使用空桶或兼容容器取液，Shift+右键打开 GUI 查看或更换标记物。
 * </p>
 */
public class InfiniteSourceBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_ITEM = BooleanProperty.create("has_item");

    public InfiniteSourceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(HAS_ITEM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_ITEM);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HAS_ITEM, false);
    }

    /**
     * 处理方块的右键交互
     * <p>
     * - Shift+右键：打开 GUI。
     * - 普通右键：尝试从无限之源中取液到玩家手中的容器。
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
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InfiniteSourceBlockEntity sourceBE) {
                if (player.isCrouching()) {
                    NetworkHooks.openScreen((ServerPlayer) player, sourceBE, pos);
                    return InteractionResult.SUCCESS;
                }
                if (sourceBE.onRightClick(player, hand)) {
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfiniteSourceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.INFINITE_SOURCE.get(),
                InfiniteSourceBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 方块被破坏时的回调
     * <p>
     * 掉落方块自身以及标记物物品。
     * </p>
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InfiniteSourceBlockEntity sourceBE) {
                sourceBE.dropContents();
            }
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(this));
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}