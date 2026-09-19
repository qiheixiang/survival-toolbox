package com.zzq.survival_toolbox.client.event;

import com.zzq.survival_toolbox.client.gui.TradeMachineSearchOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 交易机搜索栏的事件接线（仅客户端，逻辑在 {@link TradeMachineSearchOverlay}）
 * <p>
 * 交易机的界面就是原版村民界面，无法直接在上面添加控件，
 * 所以在这些事件里"叠"上去：
 * <ul>
 *   <li>{@code Init.Post} —— 有界面初始化：不是交易机界面就把标记清掉；</li>
 *   <li>{@code ContainerScreenEvent.Render.Foreground} —— 画输入框（底色 / 边框 / 文字 / 光标）。
 *       **不能用 {@code ScreenEvent.Render.Post}**：1.21.1 的 {@code AbstractContainerScreen#render}
 *       把 {@code super.render} 的实现抄进去自己跑了（见其源码里的 "Neo: replicate the super method's
 *       implementation"），于是 {@code ScreenEvent.Render.Post} 在容器界面上**根本不会触发** ——
 *       框就永远不会被画出来（等于又一次"搜索框看不见"）。Foreground 是 NeoForge 在
 *       {@code renderLabels} 之后发的，用它顺带就把原版左列那句"交易"标签压在框底下；</li>
 *   <li>{@code MouseButtonPressed.Pre} —— 点在输入框里才吃掉（否则会顺带点到格子）；</li>
 *   <li>{@code KeyPressed.Pre} —— 有焦点时吃掉打字按键（**必须**：不然 'E' 会把界面关掉、
 *       'Q' 会丢东西、1~9 会换快捷栏）；</li>
 *   <li>{@code CharacterTyped.Pre} —— 有焦点时收字符（含输入法提交的中文）。</li>
 * </ul>
 * Pre 事件被取消后原版屏幕<b>和按键绑定</b>都不会再收到这次输入
 * （NeoForge 是在 {@code KeyboardHandler} 里按"Pre → 屏幕 → Post"串起来调的）。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class TradeMachineSearchHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        TradeMachineSearchOverlay.onScreenInit(event.getScreen());
    }

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        TradeMachineSearchOverlay.render(event.getGuiGraphics(), event.getContainerScreen(),
                event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (TradeMachineSearchOverlay.mousePressed(event.getScreen(),
                event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (TradeMachineSearchOverlay.keyPressed(event.getScreen(),
                event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (TradeMachineSearchOverlay.charTyped(event.getScreen(), event.getCodePoint())) {
            event.setCanceled(true);
        }
    }
}
