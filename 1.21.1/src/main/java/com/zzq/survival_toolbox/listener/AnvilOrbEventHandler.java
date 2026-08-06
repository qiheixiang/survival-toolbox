package com.zzq.survival_toolbox.listener;

import net.neoforged.fml.common.EventBusSubscriber;
import com.zzq.survival_toolbox.entity.AnvilOrbProjectile;
import com.zzq.survival_toolbox.item.AnvilOrbItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 铁砧球交互拦截器
 * <p>
 * 当玩家手持铁砧球右键实体时，取消原交互（骑乘、交易等），改为投掷铁砧球。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox")
public class AnvilOrbEventHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof AnvilOrbItem)) return;

        event.setCanceled(true);

        if (!player.level().isClientSide()) {
            AnvilOrbProjectile projectile = new AnvilOrbProjectile(player.level(), player);
            projectile.setItem(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            player.level().addFreshEntity(projectile);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                    0.5F, 0.4F / (player.level().getRandom().nextFloat() * 0.4F + 0.8F));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
    }
}