package com.zzq.survival_toolbox;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.client.gui.MainConfigScreen;
import com.zzq.survival_toolbox.command.AdaptCommand;
import com.zzq.survival_toolbox.command.BloodthirstyCommand;
import com.zzq.survival_toolbox.entity.AnvilOrbProjectile;
import com.zzq.survival_toolbox.entity.CapturedEntityProjectile;
import com.zzq.survival_toolbox.listener.*;
import com.zzq.survival_toolbox.network.*;
import com.zzq.survival_toolbox.registry.*;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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
    public static final String NETWORK_VERSION = "1.2";

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

        CHANNEL.registerMessage(id++, PocketDimensionSyncPacket.class,
                PocketDimensionSyncPacket::encode,
                PocketDimensionSyncPacket::decode,
                PocketDimensionSyncPacket::handle);

        CHANNEL.registerMessage(id++, PocketDimensionSearchPacket.class,
                PocketDimensionSearchPacket::encode,
                PocketDimensionSearchPacket::decode,
                PocketDimensionSearchPacket::handle);

        CHANNEL.registerMessage(id++, PocketDimensionPageActionPacket.class,
                PocketDimensionPageActionPacket::encode,
                PocketDimensionPageActionPacket::decode,
                PocketDimensionPageActionPacket::handle);

        CHANNEL.registerMessage(id++, PocketQuickDepositPacket.class,
                PocketQuickDepositPacket::encode,
                PocketQuickDepositPacket::decode,
                PocketQuickDepositPacket::handle);

        // ---- 发射器行为：铁砧球与捕获实体放入发射器后，红石触发等同右键投掷 ----
        // 注册需在 FMLCommonSetupEvent（注册表填充完成后）执行；
        // 构造函数里 RegistryObject 尚未就绪，直接 .get() 会抛 "Registry Object not present"。
        modEventBus.addListener(SurvivalToolbox::onCommonSetup);
    }

    /**
     * 通用初始化（注册表已填充）：注册发射器行为。
     * <p>
     * 铁砧球与捕获实体放入发射器后，红石触发等同右键投掷：
     * 按发射器朝向发射弹射物（默认威力 1.1、散布 6.0、发射音效），每发消耗 1 个。
     * </p>
     *
     * @param event 通用初始化事件
     */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(ModItems.CAPTURED_ENTITY.get(), new AbstractProjectileDispenseBehavior() {
                @Override
                protected Projectile getProjectile(Level level, Position position, ItemStack stack) {
                    CapturedEntityProjectile projectile = new CapturedEntityProjectile(level, position.x(), position.y(), position.z());
                    projectile.setCapturedData(stack);
                    return projectile;
                }
            });

            DispenserBlock.registerBehavior(ModItems.ANVIL_ORB.get(), new AbstractProjectileDispenseBehavior() {
                @Override
                protected Projectile getProjectile(Level level, Position position, ItemStack stack) {
                    AnvilOrbProjectile projectile = new AnvilOrbProjectile(level, position.x(), position.y(), position.z());
                    projectile.setItem(stack);
                    return projectile;
                }
            });
        });
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
     * 1.20.1 Forge 玩家死亡掉落统一走 LivingDropsEvent
     * （Forge patch 的 dropAllDeathLoot 用 captureDrops 捕获背包+装备全部物品后触发）。
     * 配合 ItemEntityMixin 的环境伤害免疫，袋子不会因死亡/爆炸/火焰而丢失。
     */
    @SubscribeEvent
    public void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
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
    public void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
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