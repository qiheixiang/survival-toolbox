package com.zzq.survival_toolbox.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嗜血附魔「能附在哪些物品上」的判定。
 * <p>
 * 原版物品靠数据包标签 {@code #zzq_survival_toolbox:enchantable/bloodthirsty} 覆盖，
 * 但远程武器写不进静态标签：原版 {@code #minecraft:enchantable/bow} 只列了原版弓，
 * 模组弓不在其中；模组枪械更是在运行时按枪包动态注册，物品 id 事先无法枚举。
 * 因此这部分改用代码判定，并由 {@link com.zzq.survival_toolbox.mixin.ItemExtensionMixin}
 * 接进附魔系统——铁砧、{@code /enchant}、战利品生成等所有走
 * {@code IItemExtension#supportsEnchantment} 的路径都会生效。
 * </p>
 */
public final class BloodthirstyHelper {

    /** 本附魔使用的可附魔标签；只有以该标签作为 supported_items 的附魔才会应用下面的扩展判定 */
    public static final TagKey<Item> ENCHANTABLE_TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "enchantable/bloodthirsty"));

    /**
     * 枪械类物品的类名关键字（小写，逐个在物品类及其父类、接口的简单名中查找）。
     * <p>
     * 只匹配类/接口名而不匹配包名，否则 {@code com.tacz.guns.item.AmmoItem} 这类
     * 包名含 guns 的弹药、配件也会被判成枪械。
     * </p>
     */
    private static final String[] GUN_KEYWORDS = {
            "gun", "firearm", "pistol", "rifle", "shotgun", "sniper", "revolver", "musket",
            "flintlock", "blaster", "cannon", "gatling", "carbine", "launcher", "submachine", "smg"
    };

    /** 名字里带这些词的属于弹药、配件、工作台，虽然含枪械关键字但不算枪械本体 */
    private static final String[] GUN_EXCLUDES = {"ammo", "table", "attachment", "magazine"};

    /** 物品类 → 是否枪械 的缓存（类结构不会变，判定结果可长期复用） */
    private static final Map<Class<?>, Boolean> GUN_CACHE = new ConcurrentHashMap<>();

    private BloodthirstyHelper() {
    }

    /** 该附魔定义是否使用本模组的可附魔标签 */
    public static boolean usesBloodthirstyTag(Holder<Enchantment> enchantment) {
        return enchantment.value().definition().supportedItems().unwrapKey()
                .map(ENCHANTABLE_TAG::equals)
                .orElse(false);
    }

    /**
     * 物品能否附上嗜血。
     * <p>
     * 三类放行：主手带攻击力属性的武器/工具（与 1.20.1 版判定一致）、弓弩等弹射武器、
     * 模组枪械。
     * </p>
     */
    public static boolean canBeEnchanted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof ProjectileWeaponItem) return true;
        if (looksLikeGun(item)) return true;
        return hasMainhandAttackDamage(stack);
    }

    /** 物品在主手是否提供攻击力属性（剑、斧、镐、锹、锄等） */
    private static boolean hasMainhandAttackDamage(ItemStack stack) {
        boolean[] found = {false};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_DAMAGE.value()) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean looksLikeGun(Item item) {
        return GUN_CACHE.computeIfAbsent(item.getClass(), BloodthirstyHelper::classLooksLikeGun);
    }

    /** 沿父类链与接口链查找类名关键字 */
    private static boolean classLooksLikeGun(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (nameMatches(current.getSimpleName())) return true;
            for (Class<?> itf : current.getInterfaces()) {
                if (interfaceLooksLikeGun(itf)) return true;
            }
        }
        return false;
    }

    private static boolean interfaceLooksLikeGun(Class<?> type) {
        if (nameMatches(type.getSimpleName())) return true;
        for (Class<?> parent : type.getInterfaces()) {
            if (interfaceLooksLikeGun(parent)) return true;
        }
        return false;
    }

    private static boolean nameMatches(String simpleName) {
        String lower = simpleName.toLowerCase(Locale.ROOT);
        for (String exclude : GUN_EXCLUDES) {
            if (lower.contains(exclude)) return false;
        }
        for (String keyword : GUN_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
}
