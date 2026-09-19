package com.zzq.survival_toolbox.client.compat.jei;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.screen.DisassembleMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Optional;

/**
 * JEI 兼容插件
 * <p>
 * 让 JEI 配方界面里的"+"号能把材料直接放进拆解台：JEI 原先只认识原版工作台/熔炉等
 * 自带容器，拆解台是自定义菜单，必须由模组自己声明"配方的第几个格子对应容器的第几个槽"。
 * </p>
 * <p>
 * 拆解台的槽位排布：0 = 拆解输入，1-9 = 九宫格（合成材料放这里），10 = 产物预览，
 * 11-46 = 玩家背包（27 格背包 + 9 格快捷栏）。
 * </p>
 * <p>
 * 注意：JEI 不存在时本类不会被加载（只在 JEI 扫描 {@code @JeiPlugin} 时载入），
 * 因此这里可以直接引用 JEI 类型。
 * </p>
 */
@JeiPlugin
public class SurvivalToolboxJeiPlugin implements IModPlugin {

    /** 九宫格在容器里的起始槽位 */
    private static final int GRID_SLOT_START = 1;
    /** 玩家背包在容器里的起始槽位 */
    private static final int INVENTORY_SLOT_START = 11;
    /** 玩家背包槽位数（27 格背包 + 9 格快捷栏） */
    private static final int INVENTORY_SLOT_COUNT = 36;
    /** 菜单按钮：请把拆解输入槽的东西退还给玩家（见 DisassembleMenu#clickMenuButton 的 case 6；0-5 已被翻页/拆解按钮占用） */
    private static final int BUTTON_RETURN_INPUT = 6;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.parse(SurvivalToolbox.MODID + ":jei_plugin");
    }

    /**
     * 告诉 JEI"右边那块展开的面板占着地方"。
     * <p>
     * JEI 判断配料表放不放得下，靠的是容器界面自己上报的宽度
     * （{@code IGuiProperties} ← {@code AbstractContainerScreen#getXSize()} = imageWidth）。
     * 为了不把 JEI 整个挤掉，面板展开时<b>不改 imageWidth</b>（面板直接画在界面外面），
     * 靠这里把面板的矩形报成 "guiExtraAreas"，JEI 就会自己让开这一块、把配料表挪到剩下的地方；
     * 不报的话它可能把配料表画到面板下面去。
     * </p>
     */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(
                com.zzq.survival_toolbox.screen.PocketDimensionScreen.class,
                new mezz.jei.api.gui.handlers.IGuiContainerHandler
                        <com.zzq.survival_toolbox.screen.PocketDimensionScreen>() {
                    @Override
                    public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
                            com.zzq.survival_toolbox.screen.PocketDimensionScreen screen) {
                        net.minecraft.client.renderer.Rect2i area = screen.openPanelArea();
                        return area == null ? java.util.List.of() : java.util.List.of(area);
                    }
                });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();

        // 注意：JEI 的"配方槽位"参数就是容器槽位下标——配方第 k 格对应容器槽 (起始 + k)。
        // 九宫格是容器槽 1-9，所以起始必须写 1（写 0 会把材料塞进拆解输入槽）。

        // 合成配方：JEI 的 9 个配方格（行优先）与九宫格 1-9 顺序一致，可直接对位转移
        registration.addRecipeTransferHandler(
                new ReturnInputTransferHandler<>(helper.createUnregisteredRecipeTransferHandler(
                        helper.createBasicRecipeTransferInfo(
                                DisassembleMenu.class, ModMenus.DISASSEMBLE.get(), RecipeTypes.CRAFTING,
                                GRID_SLOT_START, 9, INVENTORY_SLOT_START, INVENTORY_SLOT_COUNT))),
                RecipeTypes.CRAFTING);

        // 锻造台配方：模板/基础/材料 三格 → 九宫格前 3 格（容器槽 1-3）。
        // 拆解台匹配材料不看位置（模板/基础/材料三角色由配方自己判定），所以顺序无关。
        registration.addRecipeTransferHandler(
                new ReturnInputTransferHandler<>(helper.createUnregisteredRecipeTransferHandler(
                        helper.createBasicRecipeTransferInfo(
                                DisassembleMenu.class, ModMenus.DISASSEMBLE.get(), RecipeTypes.SMITHING,
                                GRID_SLOT_START, 3, INVENTORY_SLOT_START, INVENTORY_SLOT_COUNT))),
                RecipeTypes.SMITHING);

        // 本模组自己的次元袋界面：合成页 / 拆解页的九宫格。
        // 槽位布局：CRAFT_GRID_BASE 起 9 格 = 合成页九宫格，DIS_MAT_BASE 起 9 格 = 拆解页九宫格，
        // 玩家背包在 INVENTORY_BASE 起 36 格 —— JEI 只需要"输入区间 + 背包区间"。
        // ⚠️ 这里写的区间只是"兜底默认值"：真正用哪一段由 PocketBagRecipeTransferHandler 按
        // "当前展开的是哪一页"动态算（即"打开哪个匹配哪个"），
        // 工作台页和拆解页都没开时它什么都不做（不再自动打开工作台页）。
        // 外面包一层 PocketBagRecipeTransferHandler：从袋子的**所有页/当前页**取料，
        // 由服务端按格对位摆进那一页的九宫格（"+ 从袋子取料"）。
        registration.addRecipeTransferHandler(
                new PocketBagRecipeTransferHandler<>(
                        helper.createUnregisteredRecipeTransferHandler(
                                helper.createBasicRecipeTransferInfo(
                                        com.zzq.survival_toolbox.screen.PocketDimensionMenu.class,
                                        ModMenus.POCKET_DIMENSION.get(), RecipeTypes.CRAFTING,
                                        com.zzq.survival_toolbox.screen.PocketDimensionMenu.CRAFT_GRID_BASE, 9,
                                        com.zzq.survival_toolbox.screen.PocketDimensionMenu.INVENTORY_BASE,
                                        INVENTORY_SLOT_COUNT)),
                        false,
                        com.zzq.survival_toolbox.screen.PocketDimensionMenu.CRAFT_GRID_BASE, 9,
                        helper),
                RecipeTypes.CRAFTING);

        // 原版锻造台（由本模组按钮打开的界面）：材料可以先从袋子所有页取到背包，再交给 JEI 放进输入槽。
        // SmithingMenu 的槽位：0-2 = 模板/基础/材料，3 = 产物，4 起是玩家背包。
        registration.addRecipeTransferHandler(
                new PocketBagRecipeTransferHandler<>(helper.createUnregisteredRecipeTransferHandler(
                        helper.createBasicRecipeTransferInfo(
                                net.minecraft.world.inventory.SmithingMenu.class,
                                net.minecraft.world.inventory.MenuType.SMITHING, RecipeTypes.SMITHING,
                                0, 3, 4, INVENTORY_SLOT_COUNT)),
                        true, 0, 3, helper),
                RecipeTypes.SMITHING);

        // 原版工作台：CraftingMenu 的槽位：0 = 产物，1-9 = 九宫格，10 起是玩家背包。
        registration.addRecipeTransferHandler(
                new PocketBagRecipeTransferHandler<>(helper.createUnregisteredRecipeTransferHandler(
                        helper.createBasicRecipeTransferInfo(
                                net.minecraft.world.inventory.CraftingMenu.class,
                                net.minecraft.world.inventory.MenuType.CRAFTING, RecipeTypes.CRAFTING,
                                1, 9, 10, INVENTORY_SLOT_COUNT)),
                        false, 1, 9, helper),
                RecipeTypes.CRAFTING);
    }

    /**
     * 转移前的预处理：把拆解输入槽（槽 0）的东西退回玩家背包。
     * <p>
     * 输入槽有物品时菜单处于"拆解模式"，材料填进九宫格也只会被当成拆解产物处理、不显示合成结果。
     * 这里在真正转移之前点一下菜单按钮 6（走原版容器按钮包，不需要自定义网络包），
     * 让服务端先清空该槽；按钮包比 JEI 的转移包先发出，服务端按顺序处理，等转移开始时已是合成模式。
     * </p>
     */
    private static final class ReturnInputTransferHandler<C extends AbstractContainerMenu, R>
            implements IRecipeTransferHandler<C, R> {

        private final IRecipeTransferHandler<C, R> delegate;

        private ReturnInputTransferHandler(IRecipeTransferHandler<C, R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Class<? extends C> getContainerClass() {
            return delegate.getContainerClass();
        }

        @Override
        public Optional<MenuType<C>> getMenuType() {
            return delegate.getMenuType();
        }

        @Override
        public RecipeType<R> getRecipeType() {
            return delegate.getRecipeType();
        }

        @Override
        public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            if (doTransfer
                    && container instanceof DisassembleMenu menu
                    && !menu.getSlot(0).getItem().isEmpty()
                    && Minecraft.getInstance().gameMode != null) {
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, BUTTON_RETURN_INPUT);
            }
            return delegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
    }
}
