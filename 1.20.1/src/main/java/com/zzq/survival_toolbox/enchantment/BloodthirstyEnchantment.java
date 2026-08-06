package com.zzq.survival_toolbox.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 嗜血附魔
 * <p>
 * 可应用于武器类物品（主手）。
 * 该附魔为宝藏附魔（只能通过战利品箱或村民交易获得），固定为 1 级。
 * <p>
 * 效果：
 * <ul>
 *   <li>击杀怪物时吸收其部分生命值转化为攻击力加成</li>
 *   <li>攻击时附加攻击力加成作为额外伤害</li>
 *   <li>攻击时恢复自身生命值（基于攻击力加成）</li>
 *   <li>攻击时修复武器耐久（基于攻击力加成）</li>
 * </ul>
 * </p>
 */
public class BloodthirstyEnchantment extends Enchantment {

    public BloodthirstyEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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