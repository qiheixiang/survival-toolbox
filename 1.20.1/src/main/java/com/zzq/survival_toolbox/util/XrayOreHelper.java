package com.zzq.survival_toolbox.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 矿透辅助：手持透视眼镜时激活透视，白名单之外的方块不渲染。
 * <p>
 * 白名单默认 = 原版全部矿物 + 自动识别的模组矿石（注册名以 {@code _ore} 结尾）。
 * 玩家可在护目镜 Shift+右键打开的选择菜单中手动开关任意方块（会话内生效）。
 * 该类位于 common 包：服务端也会加载 mixin 类，激活状态仅由客户端事件设置，服务端恒为 false。
 * </p>
 */
public class XrayOreHelper {

    /** 原版矿物（含深板岩/下界变种、远古残骸、粗矿块），不依赖命名规则，作为回退保证识别 */
    private static final String[] BUILTIN_ORES = {
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris",
            "minecraft:raw_iron_block", "minecraft:raw_gold_block", "minecraft:raw_copper_block"
    };

    private static boolean active = false;
    /** 白名单（矿透时保留渲染的方块），默认 = 原版矿物 + 自动识别的矿石 */
    private static final Set<ResourceLocation> enabledBlocks = new HashSet<>();
    private static boolean defaultsLoaded = false;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    /** 矿透白名单判断：该方块是否保留渲染 */
    public static boolean isEnabled(BlockState state) {
        ensureDefaults();
        return enabledBlocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    // ============================================================
    // 选择菜单支持
    // ============================================================

    /** 当前注册表全部方块 id（按注册名排序），供选择菜单展示 */
    public static List<ResourceLocation> getAllBlockIds() {
        List<ResourceLocation> list = new ArrayList<>(BuiltInRegistries.BLOCK.keySet());
        list.sort(Comparator.comparing(ResourceLocation::toString));
        return list;
    }

    public static boolean isEnabled(ResourceLocation id) {
        ensureDefaults();
        return enabledBlocks.contains(id);
    }

    /** 开关某个方块（菜单点击） */
    public static void toggle(ResourceLocation id) {
        ensureDefaults();
        if (!enabledBlocks.remove(id)) {
            enabledBlocks.add(id);
        }
    }

    /** 当前已开启的方块数量（菜单统计显示） */
    public static int enabledCount() {
        ensureDefaults();
        return enabledBlocks.size();
    }

    /** 首次使用时构建默认白名单：原版矿物 + 注册名以 _ore 结尾的模组矿石 */
    private static void ensureDefaults() {
        if (defaultsLoaded) return;
        defaultsLoaded = true;
        for (String id : BUILTIN_ORES) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) enabledBlocks.add(rl);
        }
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            // 绝大多数模组矿石注册名形如 xxx_ore（含深层变体 xxx_deep_ore 等）
            if (id.getPath().endsWith("_ore")) {
                enabledBlocks.add(id);
            }
        }
    }
}
