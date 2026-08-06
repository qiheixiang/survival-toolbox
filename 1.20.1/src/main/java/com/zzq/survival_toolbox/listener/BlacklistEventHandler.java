package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.item.BlacklistItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 黑白名单伤害拦截器
 * <p>
 * 检查攻击者物品栏中是否有黑白名单物品，根据名单配置决定是否允许对目标造成伤害。
 * 物品栏中任意位置（包括背包和副手）存在黑白名单即生效。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class BlacklistEventHandler {

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        Entity target = event.getEntity();

        if (attacker == null || target == null) return;

        ItemStack blacklistStack = getBlacklistInInventory(attacker);

        if (!blacklistStack.isEmpty()) {
            if (!BlacklistItem.canHurt(blacklistStack, target)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 获取玩家物品栏中的黑白名单物品
     * <p>
     * 遍历顺序：热键栏 0-8 → 背包 9-35 → 副手
     * 如有多个，取第一个找到的。
     * </p>
     *
     * @param attacker 攻击者实体
     * @return 黑白名单物品栈（若无则返回空栈）
     */
    private static ItemStack getBlacklistInInventory(Entity attacker) {
        if (!(attacker instanceof Player player)) return ItemStack.EMPTY;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlacklistItem) {
                return stack;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof BlacklistItem) {
            return offhand;
        }

        return ItemStack.EMPTY;
    }
}