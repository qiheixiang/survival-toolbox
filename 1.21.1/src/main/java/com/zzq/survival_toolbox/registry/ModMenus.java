package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.item.TerrainEditorItem;
import com.zzq.survival_toolbox.screen.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组菜单类型注册类
 * 所有 GUI 对应的菜单容器在此注册
 */
public class ModMenus {

    /** 菜单类型延迟注册器 */
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SurvivalToolbox.MODID);


    /** 交易机菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<net.minecraft.world.inventory.MerchantMenu>> TRADE_MACHINE =
            MENUS.register("trade_machine",
                    () -> IMenuTypeExtension.create((windowId, inv, data) ->
                            new TradeMachineMenu(windowId, inv, new TradeMachineMenu.DummyMerchant(inv.player))));

    /** 拆解台菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<DisassembleMenu>> DISASSEMBLE =
            MENUS.register("disassemble",
                    () -> IMenuTypeExtension.create((windowId, inv, data) ->
                            new DisassembleMenu(windowId, inv)));

    /** 微型牧场菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<MicroFarmMenu>> MICRO_FARM =
            MENUS.register("micro_farm",
                    () -> IMenuTypeExtension.create((windowId, inv, data) ->
                            new MicroFarmMenu(windowId, inv, data)));

    /** 万物转化炉菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<TransmutationFurnaceMenu>> TRANSMUTATION_FURNACE =
            MENUS.register("transmutation_furnace",
                    () -> IMenuTypeExtension.create(TransmutationFurnaceMenu::new));

    /** 镇魂灯菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<GuardianLanternMenu>> GUARDIAN_LANTERN =
            MENUS.register("guardian_lantern",
                    () -> IMenuTypeExtension.create(GuardianLanternMenu::new));

    /** 智慧农场菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<SmartFarmMenu>> SMART_FARM =
            MENUS.register("smart_farm",
                    () -> IMenuTypeExtension.create(SmartFarmMenu::new));

    /** 无限之源菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<InfiniteSourceMenu>> INFINITE_SOURCE =
            MENUS.register("infinite_source",
                    () -> IMenuTypeExtension.create(InfiniteSourceMenu::new));

    /** 地形编辑器菜单（从玩家主手或副手获取编辑器物品） */
    public static final DeferredHolder<MenuType<?>, MenuType<TerrainEditorMenu>> TERRAIN_EDITOR =
            MENUS.register("terrain_editor", () -> IMenuTypeExtension.create((id, inv, data) -> {
                ItemStack stack = inv.player.getMainHandItem();
                if (!(stack.getItem() instanceof TerrainEditorItem)) {
                    stack = inv.player.getOffhandItem();
                }
                return new TerrainEditorMenu(id, inv, stack);
            }));

    /** 万向漏斗主菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<OmniHopperMenu>> OMNI_HOPPER =
            MENUS.register("omni_hopper",
                    () -> IMenuTypeExtension.create(OmniHopperMenu::new));

    /** 万向漏斗方向配置子菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<DirectionConfigMenu>> DIRECTION_CONFIG =
            MENUS.register("direction_config",
                    () -> IMenuTypeExtension.create(DirectionConfigMenu::new));

    /** 附魔数据交换菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<EnchantmentTransferMenu>> ENCHANTMENT_TRANSFER =
            MENUS.register("enchantment_transfer",
                    () -> IMenuTypeExtension.create(EnchantmentTransferMenu::new));

    /** 黑白名单菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<BlacklistMenu>> BLACKLIST =
            MENUS.register("blacklist",
                    () -> IMenuTypeExtension.create(BlacklistMenu::new));

    /** 混沌篝火菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<FeastMenu>> FEAST =
            MENUS.register("feast",
                    () -> IMenuTypeExtension.create(FeastMenu::new));

    /** 随身次元袋菜单（从玩家主手获取次元袋物品） */
    public static final DeferredHolder<MenuType<?>, MenuType<PocketDimensionMenu>> POCKET_DIMENSION =
            MENUS.register("pocket_dimension",
                    () -> IMenuTypeExtension.create(PocketDimensionMenu::new));

    /** 锤炼箱菜单 */
    public static final DeferredHolder<MenuType<?>, MenuType<TemperingBoxMenu>> TEMPERING_BOX =
            MENUS.register("tempering_box",
                    () -> IMenuTypeExtension.create(TemperingBoxMenu::new));

    /**
     * 空方法，用于触发类加载和注册
     */
    public static void register() {}
}
