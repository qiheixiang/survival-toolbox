package com.zzq.survival_toolbox.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 矿透辅助：手持透视眼镜时激活透视，白名单之外（非矿石）的方块不渲染。
 * <p>
 * <b>白名单按每副眼镜独立保存</b>（存在该物品自己的 NBT 中），互不影响。
 * 存储采用"默认集 + 增删差分"，物品标签很小，且以后模组新增矿石会自动纳入默认集：
 * <ul>
 *   <li>{@code XrayAdded} —— 玩家额外开启的方块</li>
 *   <li>{@code XrayRemoved} —— 玩家额外关闭的方块</li>
 * </ul>
 * 默认集 = 原版矿物 + 注册名以 {@code _ore} 结尾的模组矿石。
 * 玩家可在护目镜 Shift+右键打开的选择菜单中手动开关任意方块。
 * </p>
 * <p>
 * 该类位于 common 包：服务端也会加载 mixin 类，激活状态仅由客户端事件设置，服务端恒为 false。
 * 渲染热路径（{@link #isEnabled(BlockState)}）读取客户端缓存，不逐帧解析 NBT。
 * </p>
 */
public class XrayOreHelper {

    /** 玩家额外开启的方块（ListTag&lt;String&gt;） */
    private static final String TAG_ADDED = "XrayAdded";
    /** 玩家额外关闭的方块（ListTag&lt;String&gt;） */
    private static final String TAG_REMOVED = "XrayRemoved";

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

    /** 默认白名单缓存（首次访问时构建；全注册表扫描一次即可） */
    private static Set<ResourceLocation> cachedDefaults = null;

    /** 客户端当前手持眼镜的有效白名单缓存（渲染热路径用） */
    private static final Set<ResourceLocation> currentEnabled = new HashSet<>();
    /** 缓存对应的物品标签，用于检测变化（变化时才重建缓存并重编译区块） */
    private static CompoundTag currentTag = null;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    // ============================================================
    // 每副眼镜独立的开关数据
    // ============================================================

    /** 默认白名单：原版矿物 + 注册名以 {@code _ore} 结尾的模组矿石 */
    public static Set<ResourceLocation> defaults() {
        if (cachedDefaults == null) {
            Set<ResourceLocation> set = new HashSet<>();
            for (String id : BUILTIN_ORES) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) set.add(rl);
            }
            for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
                // 绝大多数模组矿石注册名形如 xxx_ore（含深层变体 xxx_deep_ore 等）
                if (id.getPath().endsWith("_ore")) {
                    set.add(id);
                }
            }
            cachedDefaults = set;
        }
        return cachedDefaults;
    }

    /** 读取某副眼镜的有效白名单（默认集 ∪ 额外开启 − 额外关闭） */
    public static Set<ResourceLocation> getEnabledBlocks(ItemStack goggles) {
        Set<ResourceLocation> result = new HashSet<>(defaults());
        CompoundTag tag = ItemNbt.getTag(goggles);
        if (tag == null) return result;
        for (ResourceLocation id : readIds(tag, TAG_REMOVED)) result.remove(id);
        for (ResourceLocation id : readIds(tag, TAG_ADDED)) result.add(id);
        return result;
    }

    /** 某副眼镜是否开启该方块 */
    public static boolean isEnabled(ItemStack goggles, ResourceLocation id) {
        if (goggles.isEmpty() || !(goggles.getItem() instanceof com.zzq.survival_toolbox.item.XrayGogglesItem)) {
            return false;
        }
        CompoundTag tag = ItemNbt.getTag(goggles);
        if (tag != null) {
            if (readIds(tag, TAG_REMOVED).contains(id)) return false;
            if (readIds(tag, TAG_ADDED).contains(id)) return true;
        }
        return defaults().contains(id);
    }

    /**
     * 翻转某副眼镜上的某个方块（服务端执行，写回该物品自己的 NBT）。
     *
     * @return 翻转后该方块是否处于开启状态
     */
    public static boolean toggle(ItemStack goggles, ResourceLocation id) {
        Set<ResourceLocation> added = new HashSet<>();
        Set<ResourceLocation> removed = new HashSet<>();
        CompoundTag tag = ItemNbt.getTag(goggles);
        if (tag != null) {
            added.addAll(readIds(tag, TAG_ADDED));
            removed.addAll(readIds(tag, TAG_REMOVED));
        }
        boolean enabled = defaults().contains(id) ? !removed.contains(id) : added.contains(id);
        if (enabled) {
            added.remove(id);
            removed.add(id);
        } else {
            removed.remove(id);
            added.add(id);
        }
        CompoundTag root = tag == null ? new CompoundTag() : tag;
        writeIds(root, TAG_ADDED, added);
        writeIds(root, TAG_REMOVED, removed);
        ItemNbt.setTag(goggles, root);
        return !enabled;
    }

    // ============================================================
    // 客户端渲染缓存
    // ============================================================

    /**
     * 客户端每 tick 调用：把手持眼镜的白名单同步到渲染缓存。
     *
     * @return 白名单是否发生变化（变化时调用方应重编译区块）
     */
    public static boolean updateCurrent(ItemStack goggles) {
        boolean hasGoggles = !goggles.isEmpty()
                && goggles.getItem() instanceof com.zzq.survival_toolbox.item.XrayGogglesItem;
        CompoundTag tag = hasGoggles ? ItemNbt.getTag(goggles) : null;
        if (tag == null && currentTag == null) return false;
        if (tag != null && tag.equals(currentTag)) return false;
        currentTag = tag == null ? null : tag.copy();
        currentEnabled.clear();
        if (hasGoggles) currentEnabled.addAll(getEnabledBlocks(goggles));
        return true;
    }

    /** 矿透白名单判断（渲染热路径）：该方块是否保留渲染 */
    public static boolean isEnabled(BlockState state) {
        return currentEnabled.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
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

    // ============================================================
    // NBT 读写
    // ============================================================

    private static List<ResourceLocation> readIds(CompoundTag tag, String key) {
        List<ResourceLocation> list = new ArrayList<>();
        if (tag == null || !tag.contains(key, 9)) return list;
        ListTag listTag = tag.getList(key, 8);
        for (int i = 0; i < listTag.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(listTag.getString(i));
            if (rl != null) list.add(rl);
        }
        return list;
    }

    private static void writeIds(CompoundTag root, String key, Set<ResourceLocation> ids) {
        if (ids.isEmpty()) {
            root.remove(key);
            return;
        }
        ListTag list = new ListTag();
        ids.stream().map(ResourceLocation::toString).sorted().forEach(s -> list.add(StringTag.valueOf(s)));
        root.put(key, list);
    }
}
