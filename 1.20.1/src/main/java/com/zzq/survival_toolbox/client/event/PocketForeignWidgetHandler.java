package com.zzq.survival_toolbox.client.event;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 次元袋界面"清场"：屏蔽其他整理/背包增强 mod 加进来的按钮（1.20.1）
 * <p>
 * 需求：有些整理 mod 会给任意容器界面自动加自己的整理按钮（甚至自动排序），
 * 这些按钮不应出现在次元袋界面上——整理用袋子自己的按钮（参考精妙背包的做法）。
 * </p>
 * <p>
 * 做法：其他 mod 一般在 {@code ScreenEvent.Init.Post} 之前把按钮加进 {@code Screen#children()}，
 * 本处理器在这个事件里把"不是本界面自己的控件"全部移除（界面自己创建的控件都有登记）。
 * 注意：只会画东西、不往控件列表里加按钮的 mod（例如自己 hook 渲染 + 鼠标事件的、
 * 或者用快捷键排序的）用这个办法拦不住，那种只能在它们自己的配置里关掉。
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class PocketForeignWidgetHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof com.zzq.survival_toolbox.screen.PocketDimensionScreen screen) {
            screen.removeForeignWidgets();
        }
    }
}
