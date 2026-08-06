package com.zzq.survival_toolbox.block;

import com.zzq.survival_toolbox.block.entity.EnchantmentTransferBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Containers;

import javax.annotation.Nullable;

/**
 * 附魔数据交换台方块
 * <p>
 * 功能：在两件装备之间互换嗜血附魔的累计攻击力加成数据，或自适应附魔的层数、护盾等数据。
 * 要求交换的装备类型必须匹配（武器与武器，护甲与护甲）。
 * 交互方式：右键打开 GUI，点击交换按钮执行数据互换。
 * </p>
 */
public class EnchantmentTransferBlock extends BaseEntityBlock {

    private static final Component TITLE = Component.translatable("container.zzq_survival_toolbox.enchantment_transfer");

    public EnchantmentTransferBlock(Properties properties) {
        super(properties);
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(EnchantmentTransferBlock::new);
    }

    /**
     * 处理方块的右键交互
     * <p>
     * 在服务端打开附魔数据交换 GUI。
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
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof EnchantmentTransferBlockEntity transferBE) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new com.zzq.survival_toolbox.screen.EnchantmentTransferMenu(id, inv, transferBE),
                    TITLE
            );
            ((ServerPlayer) player).openMenu(provider, buf -> buf.writeBlockPos(pos));
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
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
        return new EnchantmentTransferBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 方块被破坏时的回调
     * <p>
     * 掉落方块自身以及容器中存储的物品。
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
            if (be instanceof EnchantmentTransferBlockEntity transferBE) {
                Containers.dropContents(level, pos, transferBE);
            }
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(this));
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}