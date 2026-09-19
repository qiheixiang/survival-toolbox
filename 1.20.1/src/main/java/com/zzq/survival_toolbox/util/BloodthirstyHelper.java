package com.zzq.survival_toolbox.util;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嗜血附魔「能附在哪些物品上」的判定。
 * <p>
 * 三类放行：主手带攻击力属性的武器/工具（剑、斧、镐、锹、锄等）、弓弩等弹射武器、
 * 模组枪械。前两类可以用属性与类判断，枪械则没有统一接口，只能用类名启发式识别。
 * </p>
 */
public final class BloodthirstyHelper {

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

    /**
     * 物品能否附上嗜血。
     *
     * @param stack 待附魔的物品
     * @return 主手有攻击力的武器/工具、弓弩、或模组枪械
     */
    public static boolean canBeEnchanted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof ProjectileWeaponItem) return true;
        if (looksLikeGun(item)) return true;
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
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
