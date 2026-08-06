package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组物品注册类
 * 负责注册所有独立物品（非方块物品），方块物品在 {@link ModBlocks} 中统一注册
 */
public class ModItems {

    /** 物品延迟注册器 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SurvivalToolbox.MODID);

    /** 铁砧球物品 */
    public static final DeferredHolder<Item, AnvilOrbItem> ANVIL_ORB =
            ITEMS.register("anvil_orb", AnvilOrbItem::new);

    /** 被捕获实体物品（容器型物品，存储实体数据） */
    public static final DeferredHolder<Item, CapturedEntityItem> CAPTURED_ENTITY =
            ITEMS.register("captured_entity", CapturedEntityItem::new);

    /** 除你装备（卸除怪物装备的法杖） */
    public static final DeferredHolder<Item, DisarmStaffItem> DISARM_STAFF =
            ITEMS.register("disarm_staff", DisarmStaffItem::new);

    /** 地形编辑器（多功能范围操作工具） */
    public static final DeferredHolder<Item, TerrainEditorItem> TERRAIN_EDITOR =
            ITEMS.register("terrain_editor", TerrainEditorItem::new);

    /** 静态拴绳（生物拴绳增强版，支持AI冻结与栅栏绑定） */
    public static final DeferredHolder<Item, StaticLeashItem> STATIC_LEASH =
            ITEMS.register("static_leash", StaticLeashItem::new);

    /** 黑白名单物品（用于伤害过滤与实体记录） */
    public static final DeferredHolder<Item, BlacklistItem> BLACKLIST =
            ITEMS.register("blacklist", BlacklistItem::new);

    /** 附魔包（一次性生成两本自定义附魔书） */
    public static final DeferredHolder<Item, EnchantmentBagItem> ENCHANTMENT_BAG =
            ITEMS.register("enchantment_bag", EnchantmentBagItem::new);

    /**
     * 空方法，仅用于触发类加载和注册
     */
    public static void register() {}
}
