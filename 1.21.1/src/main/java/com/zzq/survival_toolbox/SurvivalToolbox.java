package com.zzq.survival_toolbox;

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
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
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
 * <p>
 * 注意：本类会被专用服务器加载，因此这里<b>不能</b>出现任何客户端类型的引用
 * （{@code net.minecraft.client.*}、NeoForge 客户端类、配置界面等），
 * 否则服务器启动时会因「Attempted to load class ... for invalid dist DEDICATED_SERVER」直接崩溃。
 * 客户端相关内容统一放在 {@code com.zzq.survival_toolbox.client} 包，并经由
 * {@link com.zzq.survival_toolbox.client.ClientSetup#init} 在客户端分支里调用。
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

        // ---- 客户端专属初始化（配置界面入口、客户端登出清理等） ----
        // 必须放在 Dist 分支里调用，主类本身不得出现客户端类型，否则专用服务器启动即崩。
        if (FMLEnvironment.dist.isClient()) {
            com.zzq.survival_toolbox.client.ClientSetup.init(modContainer);
        }

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
        // 次元袋同步：客户端处理逻辑写在包类里（playToClient 只会在客户端执行）
        registrar.playToClient(PocketDimensionSyncPacket.TYPE, PocketDimensionSyncPacket.STREAM_CODEC,
                PocketDimensionSyncPacket::handle);
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
        // 托盘面板开关 / 送出-纳入方向切换（服务端权威：方向存在袋子 NBT 里，关界面后右键容器还要用）
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketTrayActionPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketTrayActionPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.handleTrayAction(payload.action());
                    }
                }));
        // JEI 的"+"号转移：客户端把"配方每格的候选物品表"发上来，服务端取料摆格（见 PocketJeiTransferPacket）
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketJeiTransferPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketJeiTransferPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.PocketJeiTransferPacket::handle);
        // JEI"+"号的可转移性查询（客户端 → 服务端）与回复（服务端 → 客户端）：
        // 共享模式袋子的内容在服务器存档里、客户端看不见，只能由服务端回答"这套材料凑不齐"
        // （即使物品不足，+ 号也允许点击）。见 PocketJeiQueryPacket 的说明。
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketJeiQueryPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketJeiQueryPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.PocketJeiQueryPacket::handle);
        registrar.playToClient(com.zzq.survival_toolbox.network.PocketJeiQueryReplyPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketJeiQueryReplyPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.PocketJeiQueryReplyPacket::handle);
        // 交易机：报价列表同步（服务端→客户端）与成交/刷新（客户端→服务端）
        registrar.playToClient(com.zzq.survival_toolbox.network.SyncTradeMachinePacket.TYPE,
                com.zzq.survival_toolbox.network.SyncTradeMachinePacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.SyncTradeMachinePacket::handle);
        registrar.playToServer(com.zzq.survival_toolbox.network.TradeMachineSearchPacket.TYPE,
                com.zzq.survival_toolbox.network.TradeMachineSearchPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.TradeMachineSearchPacket::handle);
        // 交易机界面打开标记（服务端→客户端）：客户端靠它区分"这台是交易机"还是村民，好叠画搜索框
        registrar.playToClient(com.zzq.survival_toolbox.network.TradeMachineOpenPacket.TYPE,
                com.zzq.survival_toolbox.network.TradeMachineOpenPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.TradeMachineOpenPacket::handle);
        registrar.playToServer(com.zzq.survival_toolbox.network.TradeMachineActionPacket.TYPE,
                com.zzq.survival_toolbox.network.TradeMachineActionPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.TradeMachineActionPacket::handle);
        registrar.playToServer(PocketQuickDepositPacket.TYPE, PocketQuickDepositPacket.STREAM_CODEC,
                PocketQuickDepositPacket::handle);
        // 存储模式切换（共享 ↔ 本地）
        registrar.playToServer(PocketModeTogglePacket.TYPE, PocketModeTogglePacket.STREAM_CODEC,
                PocketModeTogglePacket::handle);
        // 本地存储一键并入共享空间（合并式：同种累加、不覆盖共享已有内容）
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketMergeToSharedPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketMergeToSharedPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.mergeLocalToShared();
                    }
                }));
        // 整理（当前页 / 全部页）
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketSortPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketSortPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.sort(payload.scope(), payload.sortBy());
                    }
                }));
        // 长按提起整格（提起后左键点目标格完成整格移动/对调；见 PocketGrabPacket）
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketGrabPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketGrabPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.toggleGrab(payload.slotId());
                    }
                }));
        // 铁砧页的改名框：文字传到服务端，由原版 AnvilMenu 引擎算产物
        registrar.playToServer(com.zzq.survival_toolbox.network.PocketPageTextPacket.TYPE,
                com.zzq.survival_toolbox.network.PocketPageTextPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu
                            instanceof com.zzq.survival_toolbox.screen.PocketDimensionMenu menu) {
                        menu.setAnvilName(payload.text());
                    }
                }));
        registrar.playToServer(com.zzq.survival_toolbox.network.XrayToggleBlockPacket.TYPE,
                com.zzq.survival_toolbox.network.XrayToggleBlockPacket.STREAM_CODEC,
                com.zzq.survival_toolbox.network.XrayToggleBlockPacket::handle);
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
     * <p>
     * 只在<b>没开"死亡不掉落"（keepInventory）</b>时才真的需要搬：死亡掉落走
     * {@code Inventory#dropAll}（被 {@link com.zzq.survival_toolbox.mixin.InventoryMixin} 拦住，袋子留在旧背包），
     * 而没开 keepInventory 时原版不会复制背包，袋子会随旧实体一起消失，所以这里手动转移。
     * </p>
     * <p>
     * ⚠️ <b>开了 keepInventory 时原版已经复制过一份了</b>：{@code ServerPlayer#restoreFrom} 里有一段按
     * {@code RULE_KEEPINVENTORY} 判断的分支，会把整份背包（含袋子）复制给新玩家，而且发生在<b>本事件之前</b>
     * （1.21.1：偏移 188 读规则 → 205 复制，Clone 事件在 406；1.20.1：127 → 154，Clone 在 340，字节码确认过）。
     * 老写法以为"新玩家已有袋子时 add 会失败"，其实袋子是 stacksTo(1)、不会合并，add 会直接另找空格塞进去
     * —— 结果是<b>两个袋子</b>。所以这里改成"新玩家身上已经有袋子就不给"，而不是简单看游戏规则：
     * 这样 keepInventory 开/关、以及别的 mod 插手背包复制都不会重复；万一哪天原版不复制了，下面的 add 仍会补上。
     * </p>
     */
    @SubscribeEvent
    public void onPlayerClone(
net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone
 event) {
        if (!event.isWasDeath()) return;
        net.minecraft.world.entity.player.Player original = event.getOriginal();
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (original.level().isClientSide) return;
        for (int i = 0; i < original.getInventory().items.size(); i++) {
            net.minecraft.world.item.ItemStack stack = original.getInventory().items.get(i);
            if (!stack.isEmpty() && stack.is(ModItems.POCKET_DIMENSION.get())) {
                original.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
                // keepInventory（或别的 mod）已经把袋子复制给新玩家了 → 跳过：
                // ⚠️ 复制过来的是**同一个 ItemStack 实例**，再 add 一次会把这个实例搬到第一个空位、
                // 同时把它原来那格清零（add 内部 setCount(0)）→ 表现出来就是"袋子顺延到后面去了"。
                if (hasPocketBag(player)) continue;
                // 放回**原来那一格**（同一个位置，看着不突兀）；那格真被占了才退回"找第一个空位"，
                // 实在没地方才掉在死亡地点（旧实体还在场上），至少不消失。
                net.minecraft.world.entity.player.Inventory newInv = player.getInventory();
                if (i < newInv.items.size() && newInv.items.get(i).isEmpty()) {
                    newInv.items.set(i, stack);
                } else if (!newInv.add(stack)) {
                    original.drop(stack, false, false);
                }
            }
        }
    }

    /** 新玩家身上（含护甲/副手槽）是否已经有次元袋 */
    private static boolean hasPocketBag(net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.POCKET_DIMENSION.get())) return true;
        }
        return false;
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
}
