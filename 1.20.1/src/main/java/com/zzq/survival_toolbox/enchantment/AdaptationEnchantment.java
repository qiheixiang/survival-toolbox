package com.zzq.survival_toolbox.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 自适应附魔
 * <p>
 * 可应用于所有盔甲部件（头盔、胸甲、护腿、靴子）。
 * 该附魔为宝藏附魔（只能通过战利品箱或村民交易获得），固定为 1 级。
 * <p>
 * 效果：
 * <ul>
 *   <li>受到伤害时累积层数（层数 = 盔甲吸收的伤害 × 增益倍数）</li>
 *   <li>层数提供伤害减伤、护盾、飞行能力、原地复活等效果</li>
 *   <li>可适应负面效果、火焰、夜间环境和迷雾环境</li>
 * </ul>
 * </p>
 */
public class AdaptationEnchantment extends Enchantment {

    public AdaptationEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET});
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }
}