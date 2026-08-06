package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.client.renderer.AnvilOrbItemRenderer;
import com.zzq.survival_toolbox.entity.AnvilOrbProjectile;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * 铁砧球物品
 * <p>
 * 右键投掷后，命中实体时尝试捕获该实体。
 * 捕获条件：目标血量低于配置阈值且不在黑名单中。
 * 捕获成功后生成 {@link CapturedEntityItem} 物品。
 * </p>
 */
public class AnvilOrbItem extends Item {

    public AnvilOrbItem() {
        super(new Properties().stacksTo(64));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return AnvilOrbItemRenderer.INSTANCE;
            }
        });
    }

    /**
     * 右键使用：投掷铁砧球
     *
     * @param level  世界
     * @param player 玩家
     * @param hand   交互手
     * @return 交互结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            throwOrb(level, player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * 执行铁砧球的投掷
     *
     * @param level  世界
     * @param player 玩家
     * @param stack  物品栈
     */
    private void throwOrb(Level level, Player player, ItemStack stack) {
        AnvilOrbProjectile projectile = new AnvilOrbProjectile(level, player);
        projectile.setItem(stack);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
        level.addFreshEntity(projectile);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zzq_survival_toolbox.anvil_orb.use_hint"));
    }
}