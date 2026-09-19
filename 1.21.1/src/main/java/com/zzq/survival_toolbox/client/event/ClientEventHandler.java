package com.zzq.survival_toolbox.client.event;

import com.zzq.survival_toolbox.client.gui.DropsTooltipComponent;
import com.zzq.survival_toolbox.client.renderer.AnvilOrbRenderer;
import com.zzq.survival_toolbox.client.renderer.CapturedEntityProjectileRenderer;
import com.zzq.survival_toolbox.client.renderer.InfiniteSourceRenderer;
import com.zzq.survival_toolbox.client.renderer.MicroFarmRenderer;
import com.zzq.survival_toolbox.registry.ModBlockEntities;
import com.zzq.survival_toolbox.registry.ModEntities;
import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.screen.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端事件处理器
 * <p>
 * 负责注册所有客户端相关的渲染器、菜单屏幕、Tooltip 组件等。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ANVIL_ORB.get(),
                AnvilOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.CAPTURED_ENTITY_PROJECTILE.get(),
                CapturedEntityProjectileRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MICRO_FARM.get(), MicroFarmRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INFINITE_SOURCE.get(), InfiniteSourceRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 交易机的界面**就是原版村民界面**（MerchantMenu 把菜单类型写死成 MenuType.MERCHANT），
        // 这个自建菜单类型客户端其实永远收不到，就按原版界面注册；搜索框靠事件叠画。
        event.register(com.zzq.survival_toolbox.registry.ModMenus.TRADE_MACHINE.get(),
                net.minecraft.client.gui.screens.inventory.MerchantScreen::new);
        event.register(ModMenus.DISASSEMBLE.get(), DisassembleScreen::new);
        event.register(ModMenus.MICRO_FARM.get(), MicroFarmScreen::new);
        event.register(ModMenus.TRANSMUTATION_FURNACE.get(), TransmutationFurnaceScreen::new);
        event.register(ModMenus.GUARDIAN_LANTERN.get(), GuardianLanternScreen::new);
        event.register(ModMenus.SMART_FARM.get(), SmartFarmScreen::new);
        event.register(ModMenus.INFINITE_SOURCE.get(), InfiniteSourceScreen::new);
        event.register(ModMenus.TERRAIN_EDITOR.get(), TerrainEditorScreen::new);
        event.register(ModMenus.OMNI_HOPPER.get(), OmniHopperScreen::new);
        event.register(ModMenus.DIRECTION_CONFIG.get(), DirectionConfigScreen::new);
        event.register(ModMenus.ENCHANTMENT_TRANSFER.get(), EnchantmentTransferScreen::new);
        event.register(ModMenus.BLACKLIST.get(), BlacklistScreen::new);
        event.register(ModMenus.FEAST.get(), FeastScreen::new);
        event.register(ModMenus.POCKET_DIMENSION.get(), PocketDimensionScreen::new);
        event.register(ModMenus.TEMPERING_BOX.get(), TemperingBoxScreen::new);
    }

    @SubscribeEvent
    public static void onItemColor(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0xFF8B8B8B,
                ModItems.ANVIL_ORB.get());
    }

    @SubscribeEvent
    public static void registerTooltipComponent(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DropsTooltipComponent.class, component -> component);
    }
}
