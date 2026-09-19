package com.zzq.survival_toolbox.enchantment;

import com.zzq.survival_toolbox.util.BloodthirstyHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 嗜血附魔
 * <p>
 * 可应用于带攻击力的武器/工具、弓弩、以及模组枪械（主手），不限于剑/斧。
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
     * 可附魔判定：主手带攻击力的武器/工具、弓弩、以及模组枪械都可附上嗜血。
     * <p>
     * Forge 下 {@link #canEnchant(ItemStack)} 会转调本方法，因此附魔台、铁砧、
     * {@code /enchant} 等所有路径共用这一份判定。
     * </p>
     *
     * @param stack 待附魔的物品
     * @return 是否允许附上嗜血
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return BloodthirstyHelper.canBeEnchanted(stack);
    }
}