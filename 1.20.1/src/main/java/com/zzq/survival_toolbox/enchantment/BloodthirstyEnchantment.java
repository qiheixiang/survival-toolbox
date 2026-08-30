package com.zzq.survival_toolbox.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 嗜血附魔
 * <p>
 * 可应用于任何带有攻击力的物品（主手），不限于剑/斧等武器。
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

    /**
     * 可附魔判定：只要物品主手带有攻击力属性（ATTACK_DAMAGE）即可附魔。
     * 覆盖武器类目的限制，使镐/锹/锄等工具及模组武器也能附上嗜血。
     *
     * @param stack 待附魔的物品
     * @return 物品主手是否有攻击力
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }
}