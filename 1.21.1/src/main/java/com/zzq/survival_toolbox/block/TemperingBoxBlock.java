package com.zzq.survival_toolbox.block;

import com.zzq.survival_toolbox.block.entity.TemperingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 锤炼箱方块
 * <p>
 * 放置后打开界面：上方 8 格放自适应附魔装备，下方 4 格放被捕捉实体。
 * 每秒由被捕捉实体"攻击"装备，每件自适应装备 +0.001 × 实体数量 层。
 * </p>
 */
public class TemperingBoxBlock extends BaseEntityBlock {

    public TemperingBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(TemperingBoxBlock::new);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TemperingBoxBlockEntity box) {
                ((ServerPlayer) player).openMenu(box, buf -> buf.writeBlockPos(pos));
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TemperingBoxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type,
                com.zzq.survival_toolbox.registry.ModBlockEntities.TEMPERING_BOX.get(),
                TemperingBoxBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
