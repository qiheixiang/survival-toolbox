package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.enchantment.AdaptationEnchantment;
import com.zzq.survival_toolbox.enchantment.BloodthirstyEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组附魔注册类
 * 注册嗜血（武器）和自适应（盔甲）两种自定义附魔
 */
public class ModEnchantments {

    /** 附魔延迟注册器 */
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, "zzq_survival_toolbox");

    /**
     * 嗜血附魔（武器）
     * 通过击杀怪物累积攻击力加成，攻击时附加伤害、恢复生命、修复耐久
     */
    public static final RegistryObject<Enchantment> BLOODTHIRSTY =
            ENCHANTMENTS.register("bloodthirsty", BloodthirstyEnchantment::new);

    /**
     * 自适应附魔（盔甲）
     * 受到伤害时累积层数，提供减伤、护盾、飞行、复活等能力
     */
    public static final RegistryObject<Enchantment> ADAPTATION =
            ENCHANTMENTS.register("adaptation", AdaptationEnchantment::new);
}