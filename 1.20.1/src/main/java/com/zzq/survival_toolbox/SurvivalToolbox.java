package com.zzq.survival_toolbox;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.client.gui.MainConfigScreen;
import com.zzq.survival_toolbox.command.AdaptCommand;
import com.zzq.survival_toolbox.command.BloodthirstyCommand;
import com.zzq.survival_toolbox.listener.*;
import com.zzq.survival_toolbox.network.*;
import com.zzq.survival_toolbox.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 生存工具箱主类
 * <p>
 * 负责模组的初始化、注册表注册、网络通道建立、事件监听注册和命令注册。
 * </p>
 */
@Mod("zzq_survival_toolbox")
public class SurvivalToolbox {

    public static final String MODID = "zzq_survival_toolbox";
    public static final String NETWORK_VERSION = "1.0";

    /**
     * 网络通道：所有客户端↔服务端通信均通过此通道传输
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse("zzq_survival_toolbox:main"),
            () -> NETWORK_VERSION,
            version -> version.equals(NETWORK_VERSION),
            version -> version.equals(NETWORK_VERSION)
    );

    public SurvivalToolbox() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ---- 注册表 ----
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        // ---- 配置文件 ----
        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                ModConfig.CLIENT_SPEC
        );
        ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                ModConfig.COMMON_SPEC
        );

        // ---- 事件总线 ----
        // AdaptationEventHandler / BloodthirstyEventHandler / StaticLeashEventHandler /
        // BlacklistEventHandler / BlacklistInteractEventHandler 都标了 @Mod.EventBusSubscriber，
        // 会被 Forge 自动注册到 FORGE 总线，这里不再手动注册（否则每个事件处理跑两遍、数值翻倍）。
        // GuardianLanternEventHandler 没标注解，需要手动注册。
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new GuardianLanternEventHandler());

        // ---- 配置界面（Forge 主菜单入口） ----
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new MainConfigScreen(parent)
                )
        );

        // ---- 网络包注册 ----
        int id = 0;
        CHANNEL.registerMessage(id++, CancelHurtEffectPacket.class,
                CancelHurtEffectPacket::encode,
                CancelHurtEffectPacket::decode,
                CancelHurtEffectPacket::handle);

        CHANNEL.registerMessage(id++, UpdateGuardianLanternPacket.class,
                UpdateGuardianLanternPacket::toBytes,
                UpdateGuardianLanternPacket::new,
                UpdateGuardianLanternPacket::handle);

        CHANNEL.registerMessage(id++, UpdateSmartFarmPacket.class,
                UpdateSmartFarmPacket::toBytes,
                UpdateSmartFarmPacket::new,
                UpdateSmartFarmPacket::handle);

        CHANNEL.registerMessage(id++, UpdateInfiniteSourcePacket.class,
                UpdateInfiniteSourcePacket::toBytes,
                UpdateInfiniteSourcePacket::new,
                UpdateInfiniteSourcePacket::handle);

        CHANNEL.registerMessage(id++, SyncBlacklistPacket.class,
                SyncBlacklistPacket::encode,
                SyncBlacklistPacket::decode,
                SyncBlacklistPacket::handle);

        CHANNEL.registerMessage(id++, OpenBlacklistScreenPacket.class,
                OpenBlacklistScreenPacket::encode,
                OpenBlacklistScreenPacket::decode,
                OpenBlacklistScreenPacket::handle);

        CHANNEL.registerMessage(id++, TerrainEditorOperationPacket.class,
                TerrainEditorOperationPacket::encode,
                TerrainEditorOperationPacket::decode,
                TerrainEditorOperationPacket::handle);

        CHANNEL.registerMessage(id++, SyncRecipeIndexProgressPacket.class,
                SyncRecipeIndexProgressPacket::encode,
                SyncRecipeIndexProgressPacket::decode,
                SyncRecipeIndexProgressPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /**
     * 注册命令
     * <p>
     * 提供以下子命令：
     * <ul>
     *   <li>{@code /zzq_survival_toolbox adapt get} - 获取当前自适应层数</li>
     *   <li>{@code /zzq_survival_toolbox adapt set &lt;layers&gt;} - 设置自适应层数</li>
     *   <li>{@code /zzq_survival_toolbox adapt add &lt;layers&gt;} - 增加自适应层数</li>
     *   <li>{@code /zzq_survival_toolbox bloodthirsty get} - 获取当前嗜血加成</li>
     *   <li>{@code /zzq_survival_toolbox bloodthirsty set &lt;bonus&gt;} - 设置嗜血加成</li>
     *   <li>{@code /zzq_survival_toolbox bloodthirsty add &lt;bonus&gt;} - 增加嗜血加成</li>
     * </ul>
     * </p>
     *
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AdaptCommand.register(event.getDispatcher());
        BloodthirstyCommand.register(event.getDispatcher());
    }

    /**
     * 服务器启动后立即在后台构建拆解台配方索引。
     * 大整合包全量扫描配方很耗时，提前后台构建可避免玩家打开拆解台时卡顿或等待。
     */
    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server.overworld() != null) {
            com.zzq.survival_toolbox.screen.DisassembleMenu.ensureRecipeIndex(server.overworld());
        }
    }

    /**
     * 客户端登出时清理客户端缓存实例
     * <p>
     * 防止残留引用导致内存泄漏。
     * </p>
     *
     * @param event 客户端登出事件
     */
    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GuardianLanternBlockEntity.getClientInstances().clear();
    }
}