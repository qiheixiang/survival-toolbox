package com.zzq.survival_toolbox;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.client.gui.MainConfigScreen;
import com.zzq.survival_toolbox.command.AdaptCommand;
import com.zzq.survival_toolbox.command.BloodthirstyCommand;
import com.zzq.survival_toolbox.listener.*;
import com.zzq.survival_toolbox.network.*;
import com.zzq.survival_toolbox.registry.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 生存工具箱主类
 * <p>
 * 负责模组的初始化、注册表注册、网络通道建立、事件监听注册和命令注册。
 * </p>
 */
@Mod("zzq_survival_toolbox")
public class SurvivalToolbox {

    public static final String MODID = "zzq_survival_toolbox";

    public SurvivalToolbox(IEventBus modEventBus, ModContainer modContainer) {

        // ---- 注册表 ----
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        // ---- 配置文件 ----
        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.CLIENT,
                ModConfig.CLIENT_SPEC
        );
        modContainer.registerConfig(
                net.neoforged.fml.config.ModConfig.Type.COMMON,
                ModConfig.COMMON_SPEC
        );

        // ---- 配置界面（NeoForge 主菜单入口） ----
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new MainConfigScreen(parent));

        // ---- 网络包注册 ----
        modEventBus.addListener(this::onRegisterPayloads);

        // ---- 能力注册 ----
        modEventBus.addListener(RegisterCapabilitiesEvent.class, event -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SMART_FARM.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MICRO_FARM.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.TRANSMUTATION_FURNACE.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.GUARDIAN_LANTERN.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INFINITE_SOURCE.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.OMNI_HOPPER.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FEAST.get(), (be, side) -> new SidedInvWrapper(be, side));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.INFINITE_SOURCE.get(), (be, side) -> be);
        });

        // ---- 发射器行为：铁砧球与捕获实体放入发射器后，红石触发等同右键投掷 ----
        // 物品实现 ProjectileItem，由发射器按朝向发射弹射物（默认威力 1.1、散布 6.0、发射音效）。
        // 注册需在注册表填充后（RegisterEvent）执行；构造函数里 DeferredHolder 尚未就绪，直接 .get() 会抛异常。
        modEventBus.addListener(SurvivalToolbox::onRegisterDispenserBehaviors);

        // ---- 事件总线 ----
        // AdaptationEventHandler / BloodthirstyEventHandler / StaticLeashEventHandler /
        // BlacklistEventHandler / BlacklistInteractEventHandler 均标注 @EventBusSubscriber，
        // 会被 FML 自动注册到 GAME 总线，此处不再手动注册（否则每个事件处理器将执行两次，数值翻倍）。
        // GuardianLanternEventHandler 未标注解，需手动注册。
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new GuardianLanternEventHandler());
    }

    /**
     * 注册表填充后注册发射器行为（铁砧球/捕获实体放入发射器后，红石触发等同右键投掷）。
     *
     * @param event 注册事件
     */
    private static void onRegisterDispenserBehaviors(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.ITEM) return;
        DispenserBlock.registerProjectileBehavior(ModItems.ANVIL_ORB.get());
        DispenserBlock.registerProjectileBehavior(ModItems.CAPTURED_ENTITY.get());
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);

        registrar.playToClient(CancelHurtEffectPacket.TYPE, CancelHurtEffectPacket.STREAM_CODEC, CancelHurtEffectPacket::handle);
        registrar.playToClient(SyncShieldDataPacket.TYPE, SyncShieldDataPacket.STREAM_CODEC, SyncShieldDataPacket::handle);
        registrar.playToClient(SyncRecipeIndexProgressPacket.TYPE, SyncRecipeIndexProgressPacket.STREAM_CODEC, SyncRecipeIndexProgressPacket::handle);
        registrar.playToClient(PocketDimensionSyncPacket.TYPE, PocketDimensionSyncPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (net.minecraft.client.Minecraft.getInstance().player != null
                            && net.minecraft.client.Minecraft.getInstance().player.containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.setSyncedData(payload.pageOffset(), payload.currentPage(),
                                payload.pageNames(), payload.pageCounts(), payload.slots());
                    }
                }));
        registrar.playToServer(UpdateGuardianLanternPacket.TYPE, UpdateGuardianLanternPacket.STREAM_CODEC, UpdateGuardianLanternPacket::handle);
        registrar.playToServer(UpdateSmartFarmPacket.TYPE, UpdateSmartFarmPacket.STREAM_CODEC, UpdateSmartFarmPacket::handle);
        registrar.playToServer(UpdateInfiniteSourcePacket.TYPE, UpdateInfiniteSourcePacket.STREAM_CODEC, UpdateInfiniteSourcePacket::handle);
        registrar.playBidirectional(SyncBlacklistPacket.TYPE, SyncBlacklistPacket.STREAM_CODEC, SyncBlacklistPacket::handle);
        registrar.playToServer(OpenBlacklistScreenPacket.TYPE, OpenBlacklistScreenPacket.STREAM_CODEC, OpenBlacklistScreenPacket::handle);
        registrar.playToServer(TerrainEditorOperationPacket.TYPE, TerrainEditorOperationPacket.STREAM_CODEC, TerrainEditorOperationPacket::handle);
        registrar.playToServer(PocketDimensionSearchPacket.TYPE, PocketDimensionSearchPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.setSearch(payload.keyword());
                    }
                }));
        registrar.playToServer(PocketDimensionPageActionPacket.TYPE, PocketDimensionPageActionPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.handlePageAction(payload.action(), payload.index(), payload.name());
                    }
                }));
        registrar.playToServer(PocketQuickDepositPacket.TYPE, PocketQuickDepositPacket.STREAM_CODEC,
                PocketQuickDepositPacket::handle);
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
     * 死亡不掉落次元袋：从掉落物列表移除并放回背包。
     * NeoForge 玩家死亡掉落统一走 LivingDropsEvent
     * （dropAllDeathLoot 用 captureDrops 捕获背包+装备全部物品后触发）。
     * 配合 ItemEntityMixin 的环境伤害免疫，袋子不会因死亡/爆炸/火焰而丢失。
     */
    @SubscribeEvent
    public void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return;
        if (player.level().isClientSide) return;
        java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops = event.getDrops();
        for (java.util.Iterator<net.minecraft.world.entity.item.ItemEntity> it = drops.iterator(); it.hasNext(); ) {
            net.minecraft.world.entity.item.ItemEntity entity = it.next();
            if (entity.getItem().is(ModItems.POCKET_DIMENSION.get())) {
                it.remove();
                if (!player.getInventory().add(entity.getItem())) {
                    // 背包满的极端情况：掉到脚边，至少不消失
                    player.drop(entity.getItem(), false, false);
                }
            }
        }
    }

    /**
     * 重生时把次元袋从旧玩家背包转移给新玩家。
     * 死亡掉落关闭 keepInventory 时重生不会复制背包，
     * 袋子（死亡时被 InventoryMixin 留在旧背包）会随旧实体销毁而消失，
     * 这里在 Clone 事件中手动转移，确保重生后袋子一定在背包里。
     */
    @SubscribeEvent
    public void onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        net.minecraft.world.entity.player.Player original = event.getOriginal();
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (original.level().isClientSide) return;
        for (int i = 0; i < original.getInventory().items.size(); i++) {
            net.minecraft.world.item.ItemStack stack = original.getInventory().items.get(i);
            if (!stack.isEmpty() && stack.is(ModItems.POCKET_DIMENSION.get())) {
                original.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) {
                    // 新玩家已有袋子（keepInventory 复制）或背包满：丢弃旧的，避免重复
                }
            }
        }
    }

    /**
     * 服务器启动后立即在后台构建拆解台配方索引。
     * ATM 这类大型整合包全量扫描配方耗时较长，提前后台构建可避免玩家打开拆解台时的卡顿或等待。
     */
    @SubscribeEvent
    public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
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
