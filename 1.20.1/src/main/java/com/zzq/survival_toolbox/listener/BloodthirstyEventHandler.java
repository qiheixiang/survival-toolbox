package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * 嗜血附魔事件处理器
 * <p>
 * 功能：
 * <ul>
 *   <li>击杀怪物：吸收攻击力加成</li>
 *   <li>攻击怪物：附加伤害、恢复生命、修复武器耐久</li>
 *   <li>Tooltip：显示当前攻击力加成</li>
 * </ul>
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class BloodthirstyEventHandler {

    /**
     * 返回带指定颜色的嗜血附魔名组件，供消息/tooltip 中 {@code [附魔名]} 整体着色使用。
     */
    private static Component bloodthirstyName(ChatFormatting color) {
        return Component.translatable("enchantment.zzq_survival_toolbox.bloodthirsty").withStyle(color);
    }

    /**
     * 从伤害来源解析出实际攻击者。
     * <p>
     * 近战与箭矢的来源实体本就是攻击者；但部分模组枪械把子弹本身当作来源实体、
     * 把射手挂在子弹的 owner 上，这里补一层兜底，保证枪械命中同样能吃到嗜血效果。
     * </p>
     *
     * @param source 伤害来源
     * @return 攻击者，无法确定时返回 {@code null}
     */
    private static LivingEntity attackerOf(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof LivingEntity living) return living;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        return null;
    }

    // ============================================================
    // 击杀：吸收攻击力
    // ============================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity killer = attackerOf(event.getSource());
        if (killer == null) return;

        ItemStack weapon = killer.getMainHandItem();
        if (weapon.isEmpty()) return;
        if (weapon.getEnchantmentLevel(ModEnchantments.BLOODTHIRSTY.get()) <= 0) return;

        double maxBonus = ModConfig.CLIENT.maxBonus.get();
        double multiplier = ModConfig.CLIENT.killAbsorbMultiplier.get();

        CompoundTag modData = weapon.getOrCreateTag().getCompound("zzq_survival_toolbox_data");
        float currentBonus = modData.getFloat("bloodthirsty_bonus");

        if (currentBonus >= maxBonus) return;

        float healthToAdd = (float) (event.getEntity().getMaxHealth() * multiplier);
        float newBonus = (float) Math.min(currentBonus + healthToAdd, maxBonus);
        modData.putFloat("bloodthirsty_bonus", newBonus);
        ItemNbt.edit(weapon, t -> t.put("zzq_survival_toolbox_data", modData));

        if (ModConfig.CLIENT.enableKillMessage.get() && killer instanceof Player player) {
            player.sendSystemMessage(Component.translatable(
                    "message.zzq_survival_toolbox.bloodthirsty.absorb",
                    bloodthirstyName(ChatFormatting.GREEN),
                    healthToAdd,
                    newBonus
            ));
        }
    }

    // ============================================================
    // 攻击：附加伤害 + 回血 + 修耐久
    // ============================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity attacker = attackerOf(event.getSource());
        if (attacker == null) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return;

        CompoundTag modData = weapon.getOrCreateTag().getCompound("zzq_survival_toolbox_data");
        float bonus = modData.getFloat("bloodthirsty_bonus");
        if (bonus <= 0) return;

        // 附加伤害
        event.setAmount(event.getAmount() + bonus);

        // 回血
        double healMultiplier = ModConfig.CLIENT.healMultiplier.get();
        float healAmount = (float) (bonus * healMultiplier);
        attacker.heal(healAmount);

        // 修耐久
        double repairMultiplier = ModConfig.CLIENT.repairMultiplier.get();
        int repairAmount = 0;
        if (weapon.isDamageableItem()) {
            int currentDamage = weapon.getDamageValue();
            if (currentDamage > 0) {
                repairAmount = Math.max(1, (int) (bonus * repairMultiplier));
                weapon.setDamageValue(Math.max(0, currentDamage - repairAmount));
            }
        }

        if (ModConfig.CLIENT.enableAttackMessage.get() && attacker instanceof Player player) {
            player.sendSystemMessage(Component.translatable(
                    "message.zzq_survival_toolbox.bloodthirsty.on_attack",
                    bloodthirstyName(ChatFormatting.GREEN),
                    String.format("%.1f", healAmount),
                    repairAmount
            ));
        }
    }

    // ============================================================
    // Tooltip 显示
    // ============================================================

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getEnchantmentLevel(ModEnchantments.BLOODTHIRSTY.get()) <= 0) return;

        CompoundTag modData = stack.getOrCreateTag().getCompound("zzq_survival_toolbox_data");
        float bonus = modData.getFloat("bloodthirsty_bonus");

        if (bonus > 0) {
            event.getToolTip().add(Component.translatable(
                    "message.zzq_survival_toolbox.bloodthirsty.tooltip",
                    bloodthirstyName(ChatFormatting.GOLD),
                    bonus
            ));
            event.getToolTip().add(Component.translatable(
                    "message.zzq_survival_toolbox.bloodthirsty.tooltip_heal",
                    bonus * 0.1f
            ));
            event.getToolTip().add(Component.translatable(
                    "message.zzq_survival_toolbox.bloodthirsty.tooltip_repair",
                    (int) Math.max(1, bonus)
            ));
        } else {
            event.getToolTip().add(Component.translatable(
                    "message.zzq_survival_toolbox.bloodthirsty.tooltip_empty",
                    bloodthirstyName(ChatFormatting.GRAY)
            ));
        }
    }
}