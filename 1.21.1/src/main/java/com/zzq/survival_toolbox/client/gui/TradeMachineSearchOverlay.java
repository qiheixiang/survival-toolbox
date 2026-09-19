package com.zzq.survival_toolbox.client.gui;

import com.zzq.survival_toolbox.network.TradeMachineSearchPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 交易机搜索栏：**在界面之外用客户端事件叠画**
 * <p>
 * 为什么不用 {@code EditBox} + 自建界面：交易机的界面是原版村民界面
 * （{@code MerchantMenu} 把菜单类型写死成 {@code MenuType.MERCHANT}，客户端拿到的永远是
 * 原版 {@code MerchantScreen}），继承出来的界面类**永远不会被实例化**，
 * 加进去的控件自然也永远看不到。想换成自己的界面就得复刻一整套，
 * 所以改成"原版界面照跑，在它上面自己画一个输入框、自己收输入"。
 * </p>
 * <p>
 * 分工：
 * <ul>
 *   <li>打开交易机时服务端发 {@link TradeMachineOpenPacket} 标记包 → {@link #mark(int)} 记下容器 id；</li>
 *   <li>{@code ContainerScreenEvent.Render.Foreground} → {@link #render} 自己画底、边框、文字与光标
 *       （**不能用 {@code ScreenEvent.Render.Post}**：1.21.1 的 {@code AbstractContainerScreen#render}
 *       没再调 {@code super.render}，那个事件在容器界面上根本不会触发；而且 Foreground 在
 *       {@code renderLabels} 之后，画的框正好压住原版左列那句"交易"标签）；</li>
 *   <li>{@code ScreenEvent.MouseButtonPressed.Pre} / {@code KeyPressed.Pre} / {@code CharacterTyped.Pre}
 *       → {@link #mousePressed} / {@link #keyPressed} / {@link #charTyped}：只有落在输入框上的
 *       点击、只有输入框有焦点时的按键才吃掉，其余一律放行给原版；</li>
 *   <li>内容一变就发 {@link TradeMachineSearchPacket} → 服务端
 *       {@code TradeMachineMerchant#setFilter} → 原版报价包刷新列表。</li>
 * </ul>
 * 这里只做"纯逻辑 + 画"，事件靠 {@code client/event/TradeMachineSearchHandler} 转发，
 * 两版本（1.21.1 / 1.20.1）的这份代码因此几乎完全一致。
 * </p>
 * <p>
 * <b>坐标</b>：对外收的是**屏幕坐标**（事件给的），内部一律换算成**界面相对坐标**再比较/绘制 ——
 * 因为 Foreground 事件触发时原版已经把 pose 平移到界面左上角了（{@code translate(leftPos, topPos)}），
 * 用屏幕坐标画会整体偏移一个界面位置。
 * </p>
 */
public final class TradeMachineSearchOverlay {

    /** 输入框里的提示文字（为空且没焦点时显示） */
    private static final Component HINT =
            Component.translatable("gui.zzq_survival_toolbox.trade_machine.search_hint");

    /**
     * 服务端发来标记包时记下的容器 id；{@code -1} = 当前不是交易机界面。
     * 用容器 id 而不是布尔量：同一台机器重开会换新 id，旧标记自然作废。
     */
    private static int markedContainerId = -1;

    /** 搜索框内容 / 光标位置 / 是否有焦点（全部自己维护，不依赖 EditBox） */
    private static String text = "";
    private static int cursor;
    private static boolean focused;
    private static long focusTime;

    /**
     * 输入框在界面里的位置（界面相对坐标，和原版左列那句"交易"标签同一块地方，把它顶掉）：
     * 左列 x 5~93 / y 3~16 —— 上面是界面顶框，下面是第 1 条报价按钮（原版在 y = topPos + 18 起）。
     * 宽度和下面的报价按钮列对齐（88），不要做长（长条压到右面板很难看）。
     */
    private static final int BOX_X = 5;
    private static final int BOX_Y = 3;
    private static final int BOX_W = 88;
    private static final int BOX_H = 13;
    /** 文字左右各留 4px（和原版 EditBox 的内边距一致） */
    private static final int TEXT_PAD = 4;
    /** 必须 ≤ 服务端包的 writeUtf(…, 32)：超了会直接抛 EncoderException */
    private static final int MAX_LENGTH = 32;

    private TradeMachineSearchOverlay() {
    }

    // ---------- 标记 ----------

    /** 标记包到达：这个容器就是交易机界面。重开新界面时内容自然重置（服务端过滤器也是空的） */
    public static void mark(int containerId) {
        markedContainerId = containerId;
        text = "";
        cursor = 0;
        focused = false;
        focusTime = 0L;
    }

    /** 当前界面不是交易机了 → 标记作废（关界面、打开别的界面都会走到这） */
    private static void invalidate() {
        markedContainerId = -1;
        text = "";
        cursor = 0;
        focused = false;
    }

    /** 有界面被初始化时调用：不是交易机界面就把标记清掉（防止标记残留到村民界面上） */
    public static void onScreenInit(Screen screen) {
        if (!isTradeMachineScreen(screen)) invalidate();
    }

    /** 当前界面是不是"被标记的交易机界面"：必须是原版村民界面，且容器 id 对得上 */
    private static boolean isTradeMachineScreen(Screen screen) {
        if (markedContainerId < 0) return false;
        // 只认原版村民界面：下面那些坐标就是按它的版面定的，换别的界面会画错地方
        return screen instanceof MerchantScreen ms && ms.getMenu().containerId == markedContainerId;
    }

    // ---------- 画 ----------

    public static void render(GuiGraphics gui, Screen screen, int mouseX, int mouseY) {
        if (!isTradeMachineScreen(screen)) {
            // 顺带清一次：界面已经换成别的了（连标记一起扔）
            if (markedContainerId >= 0) invalidate();
            return;
        }
        Font font = Minecraft.getInstance().font;
        // 事件给的是屏幕坐标，这里换算成界面相对坐标（Foreground 的 pose 已经平移过了，画的时候要用相对坐标）
        int mx = mouseX - guiLeft(screen);
        int my = mouseY - guiTop(screen);

        // 底 + 边框：照原版 EditBox 的观感（有焦点白框、否则灰框，里面纯黑）
        int border = focused ? 0xFFFFFFFF : (isInside(mx, my) ? 0xFFC8C8C8 : 0xFFA0A0A0);
        gui.fill(BOX_X - 1, BOX_Y - 1, BOX_X + BOX_W + 1, BOX_Y + BOX_H + 1, border);
        gui.fill(BOX_X, BOX_Y, BOX_X + BOX_W, BOX_Y + BOX_H, 0xFF000000);

        // 只画放得下的那一段（相当于原版 EditBox 的 plainSubstrByWidth），这样就不需要 scissor，
        // 也绕开了"scissor 在不同版本里是否吃 pose"的问题
        int start = displayStart(font);
        int tx = BOX_X + TEXT_PAD - font.width(text.substring(0, start));
        int ty = BOX_Y + (BOX_H - 8) / 2;
        if (text.isEmpty()) {
            if (!focused) gui.drawString(font, HINT, tx, ty, 0xFF808080, false);
        } else {
            gui.drawString(font, font.plainSubstrByWidth(text.substring(start), BOX_W - TEXT_PAD * 2),
                    tx, ty, 0xFFE0E0E0, false);
        }
        // 光标：有焦点时按 300ms 闪烁（和原版 EditBox 一样），一直画在框内
        if (focused && (System.currentTimeMillis() - focusTime) / 300L % 2L == 0L) {
            int caretIdx = Math.max(start, Math.min(cursor, text.length()));
            int caretX = tx + font.width(text.substring(start, caretIdx));
            caretX = Math.max(BOX_X + 1, Math.min(BOX_X + BOX_W - 2, caretX));
            gui.fill(caretX, BOX_Y + 2, caretX + 1, BOX_Y + BOX_H - 2, 0xFFD0D0D0);
        }
    }

    // ---------- 输入 ----------

    /** @return true = 这次点击被搜索框吃掉了（界面不要再处理，以免连带点到格子） */
    public static boolean mousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (!isTradeMachineScreen(screen)) return false;
        double mx = mouseX - guiLeft(screen);
        double my = mouseY - guiTop(screen);
        if (!isInside(mx, my)) {
            // 点框外 = 失焦，这次点击照旧交给原版
            focused = false;
            return false;
        }
        if (button != 0) return false;
        focused = true;
        focusTime = System.currentTimeMillis();
        cursor = caretAt(mx);
        return true;
    }

    /** @return true = 这次按键被搜索框吃掉了 */
    public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        if (!isTradeMachineScreen(screen) || !focused) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE:
                // ESC 不拦：和次元袋搜索框一致，ESC 永远是"关界面"
                return false;
            case GLFW.GLFW_KEY_BACKSPACE:
                if (cursor > 0) {
                    int from = prevIndex(cursor);
                    text = text.substring(0, from) + text.substring(cursor);
                    cursor = from;
                    flush();
                }
                return true;
            case GLFW.GLFW_KEY_DELETE:
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(nextIndex(cursor));
                    flush();
                }
                return true;
            case GLFW.GLFW_KEY_LEFT:
                cursor = prevIndex(cursor);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                cursor = nextIndex(cursor);
                return true;
            case GLFW.GLFW_KEY_HOME:
                cursor = 0;
                return true;
            case GLFW.GLFW_KEY_END:
                cursor = text.length();
                return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
            case GLFW.GLFW_KEY_TAB:
                // 吃掉但不做事：免得回车/切焦点跑到别的界面
                return true;
            case GLFW.GLFW_KEY_V:
                // Ctrl+V 粘贴（和原版 EditBox 一样的判断，Ctrl+Shift+V 之类的组合不算）
                if (Screen.isPaste(keyCode)) {
                    paste();
                    return true;
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (Screen.isCopy(keyCode)) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                    return true;
                }
                break;
            default:
                break;
        }
        // F1~F25 与修饰键放行：截图、调试屏、全屏这些该能用
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) return false;
        if (keyCode >= GLFW.GLFW_KEY_LEFT_SHIFT && keyCode <= GLFW.GLFW_KEY_MENU) return false;
        // 其余一律吃掉：打字时不能触发快捷键（E 关界面、Q 丢东西、1~9 换快捷栏……）
        return true;
    }

    /** @return true = 这个字符被搜索框吃掉了 */
    public static boolean charTyped(Screen screen, char codePoint) {
        if (!isTradeMachineScreen(screen) || !focused) return false;
        // 控制字符与格式化符号（即原版颜色代码前缀）不收，其余照单全收（中文/拼音输入法提交上来的字也算）
        if (codePoint >= ' ' && codePoint != 167 && text.length() < MAX_LENGTH) {
            text = text.substring(0, cursor) + codePoint + text.substring(cursor);
            cursor++;
            flush();
        }
        return true;
    }

    // ---------- 内部 ----------

    /** 内容变了就发给服务端：服务端重建报价 → 原版报价包刷新列表 */
    private static void flush() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TradeMachineSearchPacket(text));
    }

    private static void paste() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        StringBuilder sb = new StringBuilder(text.substring(0, cursor));
        for (int i = 0; i < clip.length(); ) {
            int cp = clip.codePointAt(i);
            int n = Character.charCount(cp);
            if (cp >= ' ' && cp != 167) {
                if (sb.length() + n > MAX_LENGTH) break;
                sb.appendCodePoint(cp);
            }
            i += n;
        }
        int newCursor = sb.length();
        sb.append(text, cursor, text.length());
        if (sb.length() > MAX_LENGTH) sb.setLength(MAX_LENGTH);
        text = sb.toString();
        cursor = Math.min(newCursor, text.length());
        flush();
    }

    /** 界面左上角的屏幕坐标（调用前必须已经过 {@link #isTradeMachineScreen} 判定） */
    private static int guiLeft(Screen screen) {
        return ((AbstractContainerScreen<?>) screen).getGuiLeft();
    }

    private static int guiTop(Screen screen) {
        return ((AbstractContainerScreen<?>) screen).getGuiTop();
    }

    /** 鼠标是否落在输入框上（界面相对坐标；边框那 1px 也算，好点一点） */
    private static boolean isInside(double mx, double my) {
        return mx >= BOX_X - 1 && mx < BOX_X + BOX_W + 1 && my >= BOX_Y - 1 && my < BOX_Y + BOX_H + 1;
    }

    /** 文字比框宽时要左移多少（像素）：保证光标那一侧始终看得见 */
    private static int textShift(Font font) {
        int inner = BOX_W - TEXT_PAD * 2;
        int total = font.width(text);
        if (total <= inner) return 0;
        int caret = font.width(text.substring(0, Math.min(cursor, text.length())));
        return Math.min(total - inner, Math.max(0, caret));
    }

    /** 前半段滚出框外、不用画的字符数（把 {@link #textShift} 的像素数落到字符边界上） */
    private static int displayStart(Font font) {
        int shift = textShift(font);
        if (shift <= 0) return 0;
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cw = font.width(new String(Character.toChars(cp)));
            if (w + cw > shift) return i;
            w += cw;
            i += Character.charCount(cp);
        }
        return Math.max(0, text.length() - 1);
    }

    /** 点击位置（界面相对坐标）→ 光标位置（点哪插哪，和原版 EditBox 一个手感） */
    private static int caretAt(double mx) {
        Font font = Minecraft.getInstance().font;
        int start = displayStart(font);
        int relX = (int) mx - (BOX_X + TEXT_PAD - font.width(text.substring(0, start)));
        int w = 0;
        for (int i = start; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cw = font.width(new String(Character.toChars(cp)));
            if (relX < w + cw / 2) return i;
            w += cw;
            i += Character.charCount(cp);
        }
        return text.length();
    }

    /** 光标左移一格（按码点走，不要拆开代理对） */
    private static int prevIndex(int index) {
        if (index <= 0) return 0;
        int prev = index - 1;
        if (Character.isLowSurrogate(text.charAt(prev)) && prev > 0
                && Character.isHighSurrogate(text.charAt(prev - 1))) {
            prev--;
        }
        return prev;
    }

    /** 光标右移一格（按码点走） */
    private static int nextIndex(int index) {
        if (index >= text.length()) return text.length();
        int next = index + 1;
        if (Character.isHighSurrogate(text.charAt(index)) && next < text.length()
                && Character.isLowSurrogate(text.charAt(next))) {
            next++;
        }
        return next;
    }
}
