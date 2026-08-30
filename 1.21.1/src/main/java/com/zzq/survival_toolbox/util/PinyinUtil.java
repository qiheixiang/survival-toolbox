package com.zzq.survival_toolbox.util;

/**
 * 拼音匹配工具（联动 JustEnoughCharacters / PinIn）
 * <p>
 * 运行时反射调用 JEC 核心库 PinIn（me.towdium.pinin.PinIn）的 contains 匹配：
 * 安装 JEC 后次元袋/透视菜单搜索支持拼音/声母/模糊音；未安装时返回 false（回退普通匹配）。
 * </p>
 */
public final class PinyinUtil {

    private static Object pinIn = null;
    private static boolean checked = false;

    private PinyinUtil() {
    }

    private static Object getPinIn() {
        if (checked) return pinIn;
        checked = true;
        try {
            Class<?> cls = Class.forName("me.towdium.pinin.PinIn");
            pinIn = cls.getConstructor().newInstance();
        } catch (Throwable ignored) {
            pinIn = null;
        }
        return pinIn;
    }

    /** 拼音匹配：文本是否包含查询串（依赖 JEC 的 PinIn，未安装返回 false） */
    public static boolean matches(String text, String query) {
        Object p = getPinIn();
        if (p == null) return false;
        try {
            return (Boolean) p.getClass().getMethod("contains", String.class, String.class)
                    .invoke(p, text, query);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
