package com.zzq.survival_toolbox;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * 生存工具箱主类
 * <p>
 * 负责模组的初始化、注册表注册、网络通道建立、事件监听注册和命令注册。
 * </p>
 * <p>
 * 注意：本类会被专用服务器加载，因此这里<b>不能</b>出现任何客户端类型的引用
 * （{@code net.minecraft.client.*}、Forge 客户端类、配置界面等），
 * 否则服务器启动时会因「Attempted to load class ... for invalid dist DEDICATED_SERVER」直接崩溃。
 * 客户端相关内容统一放在 {@code com.zzq.survival_toolbox.client} 包，并经由
 * {@link com.zzq.survival_toolbox.client.ClientSetup#init()} 在客户端分支里调用。
 * </p>
 */
@Mod("zzq_survival_toolbox")
public class SurvivalToolbox {

    public static final String MODID = "zzq_survival_toolbox";
    /** 加了 JEI 转移包（PocketJeiTransferPacket）与同步包的托盘图标栈，和旧版本客户端/服务端不互通，必须一起升 */
    public static final String NETWORK_VERSION = "1.6";

    /**
     * 网络通道：所有客户端↔服务端通信均通过此通道传输
     * <p>
     * 此处必须使用 1.20.1 原版的 {@code new ResourceLocation(String)} 构造器，不可写作
     * {@code ResourceLocation.parse(...)} 或 {@code ResourceLocation.fromNamespaceAndPath(...)}：
     * 这两个方法属于 1.21 的 API，Forge 直到 47.3.19 才将其向后移植到 1.20.1
     * （Forge 更新日志："47.3.19 Backport some Vanilla 1.21 ResourceLocation methods (#10241)"）。
     * 低于该版本的 Forge 在模组构造阶段会抛出
     * {@code NoSuchMethodError: 'net.minecraft.resources.ResourceLocation net.minecraft.resources.ResourceLocation.parse(java.lang.String)'}，
     * 导致游戏在加载阶段崩溃；而开发环境通常使用较新的 Forge（47.4.0），本地无法复现。
     * 因此 1.20.1 侧代码只应使用在全部 Forge 47.x 版本中都存在的 API。
     * </p>
     * <p>
     * ⚠️ 不要为了消除 {@code @Deprecated} 警告改回 {@code parse} / {@code fromNamespaceAndPath}：
     * 较新的 Forge（47.4.0）会将 1.20.1 原版构造器标记为
     * "deprecated, marked for removal" 并给出编译警告。该警告是刻意保留的——
     * 兼容 47.1 至 47.4 的全部 Forge 版本优先于消除一条警告。
     * </p>
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("zzq_survival_toolbox:main"),
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

        // ---- 客户端专属初始化（配置界面入口、客户端登出清理等） ----
        // 必须放在 Dist 分支里调用，主类本身不得出现客户端类型，否则专用服务器启动即崩。
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.zzq.survival_toolbox.client.ClientSetup.init();
        }

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

        // 托盘面板开关 / 送出-纳入方向切换（服务端权威：方向存在袋子 NBT 里，关界面后右键容器还要用）
        CHANNEL.registerMessage(id++, PocketTrayActionPacket.class,
                PocketTrayActionPacket::encode,
                PocketTrayActionPacket::decode,
                PocketTrayActionPacket::handle);

        // 本地存储一键并入共享空间（合并式：同种累加、不覆盖共享已有内容）
        CHANNEL.registerMessage(id++, PocketMergeToSharedPacket.class,
                PocketMergeToSharedPacket::encode,
                PocketMergeToSharedPacket::decode,
                PocketMergeToSharedPacket::handle);

        // 整理（当前页 / 全部页）
        CHANNEL.registerMessage(id++, PocketSortPacket.class,
                PocketSortPacket::encode,
                PocketSortPacket::decode,
                PocketSortPacket::handle);
        CHANNEL.registerMessage(id++, PocketGrabPacket.class,
                PocketGrabPacket::encode,
                PocketGrabPacket::decode,
                PocketGrabPacket::handle);

        // 铁砧页的改名框：文字传到服务端，由原版 AnvilMenu 引擎算产物
        CHANNEL.registerMessage(id++, PocketPageTextPacket.class,
                PocketPageTextPacket::encode,
                PocketPageTextPacket::decode,
                PocketPageTextPacket::handle);

        CHANNEL.registerMessage(id++, PocketQuickDepositPacket.class,
                PocketQuickDepositPacket::encode,
                PocketQuickDepositPacket::decode,
                PocketQuickDepositPacket::handle);

        // 透视眼镜方块开关（每副眼镜的列表独立，写在该物品自己的 NBT 里）
        CHANNEL.registerMessage(id++, XrayToggleBlockPacket.class,
                XrayToggleBlockPacket::toBytes,
                XrayToggleBlockPacket::new,
                XrayToggleBlockPacket::handle);

        // 次元袋存储模式切换（共享 ↔ 本地）
        CHANNEL.registerMessage(id++, SyncTradeMachinePacket.class,
                SyncTradeMachinePacket::encode, SyncTradeMachinePacket::decode, SyncTradeMachinePacket::handle);
        CHANNEL.registerMessage(id++, TradeMachineSearchPacket.class,
                TradeMachineSearchPacket::encode, TradeMachineSearchPacket::decode, TradeMachineSearchPacket::handle);
        // 交易机界面打开标记（服务端→客户端）：客户端靠它区分"这台是交易机"还是村民，好叠画搜索框
        CHANNEL.registerMessage(id++, TradeMachineOpenPacket.class,
                TradeMachineOpenPacket::encode, TradeMachineOpenPacket::decode, TradeMachineOpenPacket::handle);
        CHANNEL.registerMessage(id++, TradeMachineActionPacket.class,
                TradeMachineActionPacket::encode, TradeMachineActionPacket::decode, TradeMachineActionPacket::handle);
        CHANNEL.registerMessage(id++, PocketModeTogglePacket.class,
                PocketModeTogglePacket::toBytes,
                PocketModeTogglePacket::new,
                PocketModeTogglePacket::handle);
        // JEI 的"+"号转移：客户端把"配方每格的候选物品表"发上来，服务端取料摆格（见 PocketJeiTransferPacket）
        CHANNEL.registerMessage(id++, PocketJeiTransferPacket.class,
                PocketJeiTransferPacket::encode, PocketJeiTransferPacket::decode, PocketJeiTransferPacket::handle);

        // JEI"+"号的可转移性查询（客户端 → 服务端）与回复（服务端 → 客户端）：
        // 共享模式袋子的内容在服务器存档里、客户端看不见，只能由服务端回答"这套材料凑不凑得齐"
        // （对应的历史问题："1.20.1 就算物品不足 + 号也能点"）。见 PocketJeiQueryPacket 的说明。
        CHANNEL.registerMessage(id++, PocketJeiQueryPacket.class,
                PocketJeiQueryPacket::encode, PocketJeiQueryPacket::decode, PocketJeiQueryPacket::handle);
        CHANNEL.registerMessage(id++, PocketJeiQueryReplyPacket.class,
                PocketJeiQueryReplyPacket::encode, PocketJeiQueryReplyPacket::decode,
                PocketJeiQueryReplyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

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
net.minecraftforge.event.entity.player.PlayerEvent.Clone
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
                // 顺手把它原来那格清零（add 内部 setCount(0)）→ 表现出来就是"袋子顺延到后面去了"。
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
     * 大整合包全量扫描配方很耗时，提前后台构建可避免玩家打开拆解台时卡顿或等待。
     */
    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server.overworld() != null) {
            com.zzq.survival_toolbox.screen.DisassembleMenu.ensureRecipeIndex(server.overworld());
        }
    }
}