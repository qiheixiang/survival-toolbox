package com.zzq.survival_toolbox.client.event;

import com.zzq.survival_toolbox.registry.ModItems;
import com.zzq.survival_toolbox.util.XrayOreHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 透视眼镜客户端处理器
 * <p>
 * 每 tick 检测主手物品：切换到护目镜时激活矿透并重编译区块，
 * 切走/丢出/死亡后自动恢复（主手为空或其他物品即视为非激活）。
 * 激活时自动附加无限夜视（提升光照，否则埋着的矿石因光照不足渲染为黑色）。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class XrayClientHandler {

    private static Item lastMainHand = null;
    /** 是否由矿透附加的无限夜视（关闭时需移除，避免误删玩家自己的夜视） */
    private static boolean addedNightVision = false;
    /** 矿透前的亮度滑块值（结束时恢复） */
    private static double savedGamma = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            // 离开世界/未进世界：确保关闭矿透
            if (XrayOreHelper.isActive()) {
                XrayOreHelper.setActive(false);
                if (mc.level != null) mc.levelRenderer.allChanged();
            }
            XrayOreHelper.updateCurrent(net.minecraft.world.item.ItemStack.EMPTY);
            addedNightVision = false;
            lastMainHand = null;
            return;
        }

        Item cur = mc.player.getMainHandItem().getItem();
        boolean holdingGoggles = cur == ModItems.XRAY_GOGGLES.get();

        // 手持眼镜的白名单缓存刷新：换了一副眼镜、或在选择菜单里改了开关时返回 true
        boolean listChanged = XrayOreHelper.updateCurrent(
                holdingGoggles ? mc.player.getMainHandItem() : net.minecraft.world.item.ItemStack.EMPTY);

        boolean recompiled = false;
        if (cur != lastMainHand) {
            lastMainHand = cur;
            boolean active = holdingGoggles;
            if (active != XrayOreHelper.isActive()) {
                if (active) {
                    applyXrayEffects(mc);
                } else {
                    removeXrayEffects(mc);
                }
                if (mc.level != null) {
                    mc.levelRenderer.allChanged();
                    // flywheel（Create 实例化渲染）的视觉不走原版渲染链，已创建的
                    // 齿轮/轴/传送带/表盘 Visual 不会随区块重建而销毁，需手动 reset 使
                    // VisualManagerMixin 的拦截对开/关都生效。
                    resetFlywheelVisuals(mc);
                    recompiled = true;
                }
            }
        }

        // 同一副眼镜改了白名单：重编译区块让开关即时生效
        if (listChanged && !recompiled && mc.level != null) {
            mc.levelRenderer.allChanged();
            resetFlywheelVisuals(mc);
        }

        // 死亡/重生等会清空效果：激活状态下夜视丢失则重新附加；
        // 同时移除压暗视野的黑暗/失明效果（整合包常见），保证矿透视野明亮
        if (XrayOreHelper.isActive()) {
            Player p = mc.player;
            if (!p.hasEffect(MobEffects.NIGHT_VISION)) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false));
                addedNightVision = true;
            }
            if (p.hasEffect(MobEffects.DARKNESS)) {
                p.removeEffect(MobEffects.DARKNESS);
            }
            if (p.hasEffect(MobEffects.BLINDNESS)) {
                p.removeEffect(MobEffects.BLINDNESS);
            }
        }
    }

    /** 激活矿透：开启透视 + 附加无限夜视 + 亮度拉满（整合包 Embeddium/Oculus 下光照 mixin 不生效，gamma 通用） */
    private static void applyXrayEffects(Minecraft mc) {
        XrayOreHelper.setActive(true);
        Player p = mc.player;
        if (p != null && !p.hasEffect(MobEffects.NIGHT_VISION)) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    MobEffectInstance.INFINITE_DURATION, 0, false, false));
            addedNightVision = true;
        }
        if (savedGamma < 0) savedGamma = mc.options.gamma().get();
        mc.options.gamma().set(1.0D);
    }

    /** 关闭矿透：取消透视 + 移除由矿透附加的夜视 + 恢复亮度滑块 */
    private static void removeXrayEffects(Minecraft mc) {
        XrayOreHelper.setActive(false);
        if (addedNightVision && mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        addedNightVision = false;
        if (savedGamma >= 0) {
            mc.options.gamma().set(savedGamma);
            savedGamma = -1;
        }
    }

    /**
     * 重置 flywheel 实例化渲染的视觉管理器（反射调用，无 flywheel/Create 时静默跳过）。
     * xray 开关后所有 BlockEntity 视觉重新创建，避开白名单外机械的渲染。
     */
    private static void resetFlywheelVisuals(Minecraft mc) {
        try {
            Class<?> c = Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl");
            java.lang.reflect.Method m = c.getMethod("reset", net.minecraft.world.level.LevelAccessor.class);
            m.invoke(null, mc.level);
        } catch (Throwable ignored) {
            // 没有 flywheel（如未装 Create）时忽略
        }
    }
}
