package com.zzq.survival_toolbox.client;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.client.gui.MainConfigScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端专属初始化
 * <p>
 * 这里出现的类型（{@code Screen}、{@code IConfigScreenFactory}、{@code ClientPlayerNetworkEvent}）
 * 在专用服务器上并不存在，因此整块逻辑必须留在客户端专用类中，
 * 由 {@code SurvivalToolbox} 构造函数里的 {@code FMLEnvironment.dist.isClient()} 分支调用。
 * 一旦把这些代码写回主类，专用服务器会在模组构造阶段直接崩溃。
 * </p>
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    /**
     * 客户端初始化：注册模组配置界面入口与客户端事件监听。
     *
     * @param modContainer 本模组的容器
     */
    public static void init(ModContainer modContainer) {
        // 配置界面（NeoForge 主菜单 → 模组列表里的配置按钮）
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new MainConfigScreen(parent));

        // 装了「一键背包整理Next」的话，让它的整理按钮/快捷键都跳过次元袋界面
        com.zzq.survival_toolbox.client.compat.InventoryProfilesNextCompat.applyIgnoreHint();

        // 客户端登出时清理客户端缓存实例，防止残留引用导致内存泄漏
        NeoForge.EVENT_BUS.register(ClientSetup.class);
    }

    /**
     * 客户端登出：清空守护者提灯的客户端实例缓存。
     *
     * @param event 客户端登出事件
     */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GuardianLanternBlockEntity.getClientInstances().clear();
    }
}
