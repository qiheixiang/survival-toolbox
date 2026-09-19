package com.zzq.survival_toolbox.util;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Curios 饰品兼容层（缴械法杖"连饰品一起扒"用）
 * <p>
 * 整合包里装的饰品后端是 <b>Curios</b>：1.20.1 是 {@code curios-forge 5.x}，1.21.1 是 {@code curios-neoforge 9.x}。
 * 两边 API 几乎一模一样，<b>只有一处不同</b>（也是适配中最容易出问题的地方）：
 * <ul>
 *   <li>1.20.1（5.x）：{@code CuriosApi.getCuriosInventory(entity)} 返回 Forge 的 {@code LazyOptional}</li>
 *   <li>1.21.1（9.x）：同一个方法返回 {@code java.util.Optional}</li>
 * </ul>
 * 所以这里<b>全程反射</b>，两种都认：拿到的如果不是 {@code Optional} 就先调一次 {@code resolve()}。
 * 好处：
 * <ul>
 *   <li><b>没有编译期依赖</b>：两个测试整合包没装饰品 mod，本 mod 照样能编译、能跑；</li>
 *   <li><b>不用为两个版本各写一份</b>：连 {@code IItemHandlerModifiable} 这种带 jdk 前缀的接口名也是字符串解析
 *       （1.20.1 是 {@code net.minecraftforge.items}，1.21.1 是 {@code net.neoforged.neoforge.items}）；</li>
 *   <li>反射都打在<b>接口</b>上再调（{@code getMethod} 走接口类），实现类不是 public 也不会 IllegalAccess。</li>
 * </ul>
 * </p>
 * <p>
 * ⚠️ 只在服务端调（扒饰品会改实体数据）。
 * </p>
 */
public final class CuriosCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String API_CLASS = "top.theillusivec4.curios.api.CuriosApi";
    private static final String HANDLER_CLASS = "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler";

    /** 物品容器接口：1.20.1 / Forge 与 1.21.1 / NeoForge 包名不同，按顺序找 */
    private static final String[] ITEM_HANDLER_CLASSES = {
            "net.minecraftforge.items.IItemHandlerModifiable",
            "net.neoforged.neoforge.items.IItemHandlerModifiable",
            "net.minecraftforge.items.IItemHandler",
            "net.neoforged.neoforge.items.IItemHandler"
    };

    /** 没装饰品 mod 时记住结果，避免每次点击都 Class.forName 抛一遍异常 */
    private static boolean unavailable = false;

    private CuriosCompat() {
    }

    /** 这个环境里有没有 Curios（没有就什么都不做） */
    public static boolean isAvailable() {
        if (unavailable) return false;
        try {
            Class.forName(HANDLER_CLASS);
            return true;
        } catch (Throwable t) {
            unavailable = true;
            return false;
        }
    }

    /**
     * 把目标身上装备着的饰品**全部取下来**（取下来的物品由调用方决定掉在哪里）。
     * <p>
     * 这里只负责"拿"，不管掉落位置 —— 掉落由 {@code DisarmStaffItem#dropToPosition} 统一处理，
     * 保证饰品和手上/盔甲装备的掉落表现完全一致。
     * </p>
     *
     * @return 取下来的饰品列表（0 件 = 没装饰品 mod / 目标身上没饰品 / 出错）
     */
    public static List<ItemStack> takeAll(LivingEntity target) {
        List<ItemStack> taken = new ArrayList<>();
        if (target == null || target.level().isClientSide()) return taken;
        if (!isAvailable()) return taken;

        Object equipped = equippedHandler(target);
        if (equipped == null) return taken;

        try {
            Class<?> handlerClass = resolveItemHandlerClass();
            Method getSlots = handlerClass.getMethod("getSlots");
            Method getStack = handlerClass.getMethod("getStackInSlot", int.class);

            // 优先 setStackInSlot(i, EMPTY)（IItemHandlerModifiable 才有）；
            // 拿不到就退回 extractItem(i, n, false)，两个都没有就干脆不动手，
            // 免得出现"以为扒了、其实还在身上"的假象。
            Method setStack = null;
            Method extract = null;
            try {
                setStack = handlerClass.getMethod("setStackInSlot", int.class, ItemStack.class);
            } catch (NoSuchMethodException ignored) {
                try {
                    extract = handlerClass.getMethod("extractItem", int.class, int.class, boolean.class);
                } catch (NoSuchMethodException ignored2) {
                    return taken;
                }
            }

            int slots = (Integer) getSlots.invoke(equipped);
            for (int i = 0; i < slots; i++) {
                ItemStack inSlot = (ItemStack) getStack.invoke(equipped, i);
                if (inSlot == null || inSlot.isEmpty()) continue;
                ItemStack copy = inSlot.copy();
                if (setStack != null) {
                    setStack.invoke(equipped, i, ItemStack.EMPTY);
                } else {
                    extract.invoke(equipped, i, copy.getCount(), false);
                }
                taken.add(copy);
            }
        } catch (Throwable t) {
            // 饰品 mod 版本差异导致任何反射失败都只是"扒不到"，绝不能让缴械本身炸掉
            LOGGER.warn(
                    "[zzq_survival_toolbox] 读取饰品（Curios）失败，本次跳过饰品：{}", t.toString());
        }
        return taken;
    }

    /** 取目标的"已装备饰品"容器（两种返回值形态都认） */
    private static Object equippedHandler(LivingEntity target) {
        try {
            Class<?> api = Class.forName(API_CLASS);
            Object result = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, target);
            if (result == null) return null;
            // 5.x（1.20.1）返回 Forge 的 LazyOptional，先 resolve 成 java.util.Optional
            if (!(result instanceof Optional<?>)) {
                result = result.getClass().getMethod("resolve").invoke(result);
            }
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                Object handler = optional.get();
                // 方法打在接口上（实现类可能不是 public，直接 class.getMethod 会 IllegalAccess）
                Method getEquipped = Class.forName(HANDLER_CLASS).getMethod("getEquippedCurios");
                Object equipped = getEquipped.invoke(handler);
                return equipped;
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    private static Class<?> resolveItemHandlerClass() throws ClassNotFoundException {
        for (String name : ITEM_HANDLER_CLASSES) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
                // 换下一个
            }
        }
        throw new ClassNotFoundException("找不到 IItemHandlerModifiable / IItemHandler");
    }
}
