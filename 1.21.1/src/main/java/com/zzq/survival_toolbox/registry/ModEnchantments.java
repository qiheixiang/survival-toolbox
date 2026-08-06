package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.SurvivalToolbox;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 模组附魔引用类
 * <p>
 * 1.21.1 中附魔为数据驱动注册（位于 data/zzq_survival_toolbox/enchantment/*.json），
 * 本类仅提供 {@link ResourceKey} 常量与从注册表解析 {@link Holder} 的辅助方法。
 * </p>
 */
public class ModEnchantments {

    /** 嗜血附魔（武器） */
    public static final ResourceKey<Enchantment> BLOODTHIRSTY = key("bloodthirsty");

    /** 自适应附魔（盔甲） */
    public static final ResourceKey<Enchantment> ADAPTATION = key("adaptation");

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(SurvivalToolbox.MODID, name));
    }

    /**
     * 从注册表获取嗜血附魔的 {@link Holder}
     *
     * @param lookup 注册表查找（通常来自 world/player 的 registryAccess）
     */
    public static Holder<Enchantment> bloodthirsty(HolderLookup.Provider lookup) {
        return lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(BLOODTHIRSTY);
    }

    /**
     * 从注册表获取自适应附魔的 {@link Holder}
     *
     * @param lookup 注册表查找（通常来自 world/player 的 registryAccess）
     */
    public static Holder<Enchantment> adaptation(HolderLookup.Provider lookup) {
        return lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ADAPTATION);
    }
}
