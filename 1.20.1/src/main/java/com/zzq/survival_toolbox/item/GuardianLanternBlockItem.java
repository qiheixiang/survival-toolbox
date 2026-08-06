package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.block.GuardianLanternBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 镇魂灯方块物品
 * <p>
 * 重写 {@link #useOn} 方法，以支持直接与已放置的镇魂灯方块交互。
 * 当玩家手持此物品右键已放置的镇魂灯时，会触发镇魂灯方块的交互逻辑，
 * 而非尝试重新放置方块。
 * </p>
 */
public class GuardianLanternBlockItem extends BlockItem {

    public GuardianLanternBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof GuardianLanternBlock) {
            return state.getBlock().use(state, level, pos, context.getPlayer(), context.getHand(),
                    new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, context.isInside()));
        }
        return super.useOn(context);
    }
}