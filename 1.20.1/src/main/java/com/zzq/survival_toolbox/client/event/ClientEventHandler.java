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
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端事件处理器
 * <p>
 * 负责注册所有客户端相关的渲染器、菜单屏幕、Tooltip 组件等。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ANVIL_ORB.get(),
                AnvilOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.CAPTURED_ENTITY_PROJECTILE.get(),
                CapturedEntityProjectileRenderer::new);
    }

    @SubscribeEvent
    public static void onItemColor(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0xFF8B8B8B,
                ModItems.ANVIL_ORB.get());
    }

    @SubscribeEvent
    public static void onModelRegister(ModelEvent.RegisterAdditional event) {
        event.register(ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "captured_entity"));
    }

    @SubscribeEvent
    public static void onModelBakingCompleted(ModelEvent.BakingCompleted event) {
        registerCustomItemRenderer();
    }

    private static void registerCustomItemRenderer() {
        Item item = ModItems.CAPTURED_ENTITY.get();
        ItemProperties.register(item,
                ResourceLocation.fromNamespaceAndPath("zzq_survival_toolbox", "captured_entity"),
                (stack, level, entity, seed) -> 0);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.DISASSEMBLE.get(), DisassembleScreen::new);
            MenuScreens.register(ModMenus.MICRO_FARM.get(), MicroFarmScreen::new);
            EntityRenderers.register(ModEntities.ANVIL_ORB.get(), AnvilOrbRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.MICRO_FARM.get(), MicroFarmRenderer::new);
            MenuScreens.register(ModMenus.TRANSMUTATION_FURNACE.get(), TransmutationFurnaceScreen::new);
            MenuScreens.register(ModMenus.GUARDIAN_LANTERN.get(), GuardianLanternScreen::new);
            MenuScreens.register(ModMenus.SMART_FARM.get(), SmartFarmScreen::new);
            MenuScreens.register(ModMenus.INFINITE_SOURCE.get(), InfiniteSourceScreen::new);
            BlockEntityRenderers.register(ModBlockEntities.INFINITE_SOURCE.get(), InfiniteSourceRenderer::new);
            MenuScreens.register(ModMenus.TERRAIN_EDITOR.get(), TerrainEditorScreen::new);
            MenuScreens.register(ModMenus.OMNI_HOPPER.get(), OmniHopperScreen::new);
            MenuScreens.register(ModMenus.DIRECTION_CONFIG.get(), DirectionConfigScreen::new);
            MenuScreens.register(ModMenus.ENCHANTMENT_TRANSFER.get(), EnchantmentTransferScreen::new);
            MenuScreens.register(ModMenus.BLACKLIST.get(), BlacklistScreen::new);
            MenuScreens.register(ModMenus.FEAST.get(), FeastScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerTooltipComponent(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DropsTooltipComponent.class, component -> component);
    }
}