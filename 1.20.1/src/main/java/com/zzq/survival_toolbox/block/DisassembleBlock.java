package com.zzq.survival_toolbox.block;

import com.zzq.survival_toolbox.block.entity.DisassembleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.Containers;
import org.jetbrains.annotations.Nullable;

/**
 * 拆解台方块
 * <p>
 * 功能：逆向合成，支持工作台、熔炉、锻造台、酿造台、附魔台等配方的反向拆解。
 * 交互方式：右键打开 GUI，自动根据左侧输入物品切换拆解模式或合成模式。
 * </p>
 */
public class DisassembleBlock extends Block implements EntityBlock {

    /** GUI 标题 */
    private static final Component CONTAINER_TITLE =
            Component.translatable("container.zzq_survival_toolbox.disassemble");

    public DisassembleBlock(Properties properties) {
        super(properties);
    }

    /**
     * 处理方块的右键交互
     * <p>
     * 在服务端打开拆解台 GUI。
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
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DisassembleBlockEntity disBE) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new com.zzq.survival_toolbox.screen.DisassembleMenu(id, inv, disBE),
                    CONTAINER_TITLE
            );
            NetworkHooks.openScreen((ServerPlayer) player, provider, pos);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 创建新的方块实体实例
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 方块实体实例
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisassembleBlockEntity(pos, state);
    }

    /**
     * 方块被破坏时的回调
     * <p>
     * 掉落方块自身以及方块实体中存储的所有物品。
     * </p>
     *
     * @param state    旧方块状态
     * @param level    世界
     * @param pos      位置
     * @param newState 新方块状态
     * @param isMoving 是否正在移动
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DisassembleBlockEntity disBE) {
                disBE.dropContents(level, pos);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(this));
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}