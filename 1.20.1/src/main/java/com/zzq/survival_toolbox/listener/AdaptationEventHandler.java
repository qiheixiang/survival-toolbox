package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 自适应附魔事件处理器
 * <p>
 * 负责处理自适应附魔的核心逻辑：
 * <ul>
 *   <li>实体 Tick：护盾恢复、效果适应、火焰适应、夜间适应、迷雾适应</li>
 *   <li>死亡事件：原地复活（消耗层数）</li>
 *   <li>装备变化：更新飞行能力和夜视效果</li>
 *   <li>Tooltip：显示层数和护盾信息</li>
 * </ul>
 * </p>
 */
@Mod.EventBusSubscriber(modid = "zzq_survival_toolbox")
public class AdaptationEventHandler {

    private static final Map<UUID, Boolean> cancelHurtEffectMap = new HashMap<>();
    private static final Set<UUID> processingDeath = Collections.synchronizedSet(new HashSet<>());

    public static void setCancelHurtEffect(UUID playerId, boolean cancel) {
        if (cancel) cancelHurtEffectMap.put(playerId, true);
        else cancelHurtEffectMap.remove(playerId);
    }

    public static boolean shouldCancelHurtEffect(UUID playerId) {
        return cancelHurtEffectMap.getOrDefault(playerId, false);
    }

    public static void clearCancelHurtEffect(UUID playerId) {
        cancelHurtEffectMap.remove(playerId);
    }

    // ============================================================
    // 辅助判断方法
    // ============================================================

    private static boolean mayHaveAdaptationArmor(LivingEntity entity) {
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty() && armor.hasTag() && armor.getTag().contains("Enchantments")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNightEnvironment(LivingEntity entity) {
        if (entity.level().isClientSide()) return false;
        long dayTime = entity.level().getDayTime() % 24000;
        boolean isNight = dayTime >= 13000 && dayTime <= 23000;
        if (isNight) return true;

        int light = entity.level().getMaxLocalRawBrightness(entity.blockPosition());
        return light < 4;
    }

    private static boolean isInFog(LivingEntity entity) {
        var fluidType = entity.getEyeInFluidType();
        if (fluidType == null || fluidType.isAir()) return false;
        return true;
    }

    // ============================================================
    // 实体 Tick：护盾恢复 + 适应系统
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        if (!(entity instanceof Player)) {
            if (!mayHaveAdaptationArmor(entity)) return;
        }

        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(entity);
        if (armors.isEmpty()) return;

        double totalLayers = 0;
        for (ItemStack armor : armors) {
            totalLayers += AdaptationHelper.getArmorLayers(armor);
        }

        // ---- 护盾恢复 ----
        boolean shieldRestored = false;
        for (ItemStack armor : armors) {
            float current = AdaptationHelper.getArmorShield(armor);
            float max = AdaptationHelper.getArmorMaxShield(armor);
            if (current < max) {
                double regenBase = ModConfig.CLIENT.adaptShieldRegenBase.get();
                double regenBonus = ModConfig.CLIENT.adaptShieldRegenBonus.get();
                float regenPerTick = (float) ((regenBase + max * regenBonus) / 20.0);
                float newShield = Math.min(max, current + regenPerTick);
                AdaptationHelper.setArmorShield(armor, newShield);
                shieldRestored = true;
            }
        }
        if (!shieldRestored && entity.tickCount % 20 == 0) {
            AdaptationHelper.applyRestore(entity);
        }

        // ② 护盾值现在攒在内存里，这里按**原来写 NBT 的节奏**落盘：
        //    恢复中每 2 tick 一次（客户端护盾条的刷新率和以前一模一样），静止时每秒一次。
        int flushTicks = shieldRestored ? 2 : AdaptationHelper.FLUSH_INTERVAL_TICKS;
        if (entity.tickCount % flushTicks == 0) {
            for (ItemStack armor : armors) {
                AdaptationHelper.flushPendingAdapt(armor);
            }
        }

        // ---- 统一适应系统（Debuff + 着火） ----
        int baseSeconds = ModConfig.CLIENT.adaptTime.get();
        double reduction = ModConfig.CLIENT.adaptTimeReduction.get();
        int thresholdSeconds = (int) Math.max(1, baseSeconds - totalLayers * reduction);
        int threshold = thresholdSeconds * 20;

        // 负面效果适应
        List<MobEffect> effectsToRemove = new ArrayList<>();
        for (MobEffectInstance activeEffect : entity.getActiveEffects()) {
            MobEffect mobEffect = activeEffect.getEffect();
            if (mobEffect.isBeneficial()) continue;

            ResourceLocation rl = ForgeRegistries.MOB_EFFECTS.getKey(mobEffect);
            if (rl == null) continue;
            String effectId = rl.toString();

            boolean alreadyAdapted = false;
            for (ItemStack armor : armors) {
                if (AdaptationHelper.isEffectAdapted(armor, effectId)) {
                    alreadyAdapted = true;
                    break;
                }
            }
            if (alreadyAdapted) {
                effectsToRemove.add(mobEffect);
                continue;
            }

            for (ItemStack armor : armors) {
                AdaptationHelper.addEffectTime(armor, effectId, 1);
            }

            if (AdaptationHelper.getEffectTime(armors.get(0), effectId) >= threshold) {
                for (ItemStack armor : armors) {
                    AdaptationHelper.markEffectAdapted(armor, effectId);
                }
                effectsToRemove.add(mobEffect);
                if (entity instanceof Player player) {
                    player.sendSystemMessage(Component.translatable(
                            "message.zzq_survival_toolbox.adaptation.effect_adapted",
                            mobEffect.getDisplayName()
                    ));
                }
            }
        }
        for (MobEffect effect : effectsToRemove) {
            entity.removeEffect(effect);
        }

        // 着火适应
        if (entity.isOnFire()) {
            boolean allFireAdapted = true;
            for (ItemStack armor : armors) {
                if (!AdaptationHelper.isFireAdapted(armor)) {
                    allFireAdapted = false;
                    break;
                }
            }
            if (!allFireAdapted) {
                for (ItemStack armor : armors) {
                    if (!AdaptationHelper.isFireAdapted(armor)) {
                        AdaptationHelper.addFireExposureTime(armor, 1);
                    }
                }
                if (AdaptationHelper.getFireExposureTime(armors.get(0)) >= threshold) {
                    for (ItemStack armor : armors) {
                        if (!AdaptationHelper.isFireAdapted(armor)) {
                            AdaptationHelper.markFireAdapted(armor);
                        }
                    }
                    entity.clearFire();
                    if (entity instanceof Player player) {
                        player.sendSystemMessage(Component.translatable(
                                "message.zzq_survival_toolbox.adaptation.fire_adapted"
                        ));
                    }
                }
            } else {
                if (entity.isOnFire()) {
                    entity.clearFire();
                }
            }
        }

        // ---- 夜间适应 ----
        if (isNightEnvironment(entity)) {
            boolean anyNightAdapted = false;
            for (ItemStack armor : armors) {
                if (AdaptationHelper.isNightAdapted(armor)) {
                    anyNightAdapted = true;
                    break;
                }
            }
            if (!anyNightAdapted) {
                for (ItemStack armor : armors) {
                    if (!AdaptationHelper.isNightAdapted(armor)) {
                        AdaptationHelper.addNightExposureTime(armor, 1);
                    }
                }
                if (AdaptationHelper.getNightExposureTime(armors.get(0)) >= threshold) {
                    for (ItemStack armor : armors) {
                        if (!AdaptationHelper.isNightAdapted(armor)) {
                            AdaptationHelper.markNightAdapted(armor);
                        }
                    }
                    if (entity instanceof Player player) {
                        player.sendSystemMessage(Component.translatable(
                                "message.zzq_survival_toolbox.adaptation.night_adapted"
                        ));
                    }
                }
            } else {
                if (entity instanceof Player player) {
                    boolean stillAdapted = false;
                    for (ItemStack armor : armors) {
                        if (AdaptationHelper.isNightAdapted(armor)) {
                            stillAdapted = true;
                            break;
                        }
                    }
                    if (stillAdapted) {
                        if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
                            player.addEffect(new MobEffectInstance(
                                    MobEffects.NIGHT_VISION,
                                    -1, 0, false, false
                            ));
                        }
                    } else {
                        player.removeEffect(MobEffects.NIGHT_VISION);
                    }
                }
            }
        } else {
            if (entity instanceof Player player) {
                boolean stillAdapted = false;
                for (ItemStack armor : armors) {
                    if (AdaptationHelper.isNightAdapted(armor)) {
                        stillAdapted = true;
                        break;
                    }
                }
                if (!stillAdapted && player.hasEffect(MobEffects.NIGHT_VISION)) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
            }
        }

        // ---- 迷雾适应 ----
        if (isInFog(entity)) {
            boolean anyFogAdapted = false;
            for (ItemStack armor : armors) {
                if (AdaptationHelper.isFogAdapted(armor)) {
                    anyFogAdapted = true;
                    break;
                }
            }
            if (!anyFogAdapted) {
                for (ItemStack armor : armors) {
                    if (!AdaptationHelper.isFogAdapted(armor)) {
                        AdaptationHelper.addFogExposureTime(armor, 1);
                    }
                }
                if (AdaptationHelper.getFogExposureTime(armors.get(0)) >= threshold) {
                    for (ItemStack armor : armors) {
                        if (!AdaptationHelper.isFogAdapted(armor)) {
                            AdaptationHelper.markFogAdapted(armor);
                        }
                    }
                    if (entity instanceof Player player) {
                        player.sendSystemMessage(Component.translatable(
                                "message.zzq_survival_toolbox.adaptation.fog_adapted"
                        ));
                    }
                }
            }
        }

        // ---- 口渴模糊适应（LSO 低水分时糊屏的那个后处理）----
        // 规则：不直接一刀切抵消该效果，而是逐步适应。
        // 所以这里**只负责攒适应时间**（和火焰/夜视/迷雾同一套阈值），
        // "逐步变淡"在客户端 LsoThirstBlurMixin 里按进度缩放 LSO 的模糊强度。
        if (isThirstBlurred(entity)) {
            boolean anyThirstAdapted = false;
            for (ItemStack armor : armors) {
                if (AdaptationHelper.isThirstBlurAdapted(armor)) {
                    anyThirstAdapted = true;
                    break;
                }
            }
            if (!anyThirstAdapted) {
                for (ItemStack armor : armors) {
                    if (!AdaptationHelper.isThirstBlurAdapted(armor)) {
                        AdaptationHelper.addThirstBlurExposureTime(armor, 1);
                    }
                }
                if (AdaptationHelper.getThirstBlurExposureTime(armors.get(0))
                        >= AdaptationHelper.adaptThresholdTicks(entity)) {
                    for (ItemStack armor : armors) {
                        if (!AdaptationHelper.isThirstBlurAdapted(armor)) {
                            AdaptationHelper.markThirstBlurAdapted(armor);
                        }
                    }
                    if (entity instanceof Player player) {
                        player.sendSystemMessage(Component.translatable(
                                "message.zzq_survival_toolbox.adaptation.thirst_adapted"
                        ));
                    }
                }
            }
        }
    }

    /**
     * 现在是不是"低水分到会被 LSO 糊屏"的状态（服务端判定，用来攒口渴模糊的适应时间）。
     * <p>
     * ⚠️ 全部走<b>反射</b>读 LSO：
     * <ul>
     *   <li>阈值来自 {@code RenderBlurOverlay.HYDRATION_LEVEL_MIN_EFFECT}（private static final，只能反射；读不到用 6）；</li>
     *   <li>水分来自 {@code CapabilityUtil.getThirstCapability(player).getHydrationLevel()}。</li>
     * </ul>
     * LSO 是软依赖：没装 / 接口变了，反射就失败 → 返回 false（这条适应通道自然不攒），绝不报错、绝不崩服务端。
     * </p>
     */
    private static boolean isThirstBlurred(LivingEntity entity) {
        if (!(entity instanceof Player player) || entity.level().isClientSide) return false;
        try {
            Class<?> util = Class.forName("sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil");
            Object cap = util.getMethod("getThirstCapability", Player.class).invoke(null, player);
            if (cap == null) return false;
            Object hydration = cap.getClass().getMethod("getHydrationLevel").invoke(cap);
            return hydration instanceof Integer h && h <= thirstBlurThreshold();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** LSO 开始糊屏的水分阈值（反射读它的私有常量；读不到就用 6 —— LSO 2.3.23.1 的默认值） */
    private static int thirstBlurThreshold() {
        if (THIRST_BLUR_THRESHOLD < 0) {
            int value = 6;
            try {
                java.lang.reflect.Field f = Class.forName(
                                "sfiomn.legendarysurvivaloverhaul.client.render.RenderBlurOverlay")
                        .getDeclaredField("HYDRATION_LEVEL_MIN_EFFECT");
                f.setAccessible(true);
                if (f.get(null) instanceof Integer i) value = i;
            } catch (Throwable ignored) {
                // 没装 LSO / 常量改名：用默认值；这种情况 isThirstBlurred 那边也会失败
            }
            THIRST_BLUR_THRESHOLD = value;
        }
        return THIRST_BLUR_THRESHOLD;
    }

    /** 缓存的"开始糊屏的水分阈值"（-1 = 还没读过） */
    private static int THIRST_BLUR_THRESHOLD = -1;

    // ============================================================
    // 死亡复活
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        // ② 死亡是关键时机：护盾先落盘到物品上（死亡后盔甲会掉出来，掉了也得带着最新护盾）
        AdaptationHelper.flushPendingAdapt(entity);

        int adaptLevel = getAdaptationLevel(entity);
        if (adaptLevel <= 0) return;

        float totalLayers = (float) AdaptationHelper.getTotalLayers(entity);

        int reviveThreshold = ModConfig.CLIENT.adaptReviveThreshold.get();
        int reviveCost = ModConfig.CLIENT.adaptReviveCost.get();

        if (totalLayers >= reviveThreshold) {
            List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(entity);
            if (armors.isEmpty()) return;

            event.setCanceled(true);
            forceRevive(entity);

            float costPerArmor = (float) reviveCost / armors.size();
            for (ItemStack armor : armors) {
                float current = AdaptationHelper.getArmorLayers(armor);
                AdaptationHelper.setArmorLayers(armor, Math.max(0, current - costPerArmor));
                AdaptationHelper.updateArmorMaxShield(armor);
            }

            AdaptationHelper.updateFlightAbility(entity);
            // ② 死亡复活是关键时机：层数刚被扣、护盾上限跟着降，立刻落盘
            AdaptationHelper.flushPendingAdapt(entity);

            // 复活提示与叠层提示共用同一开关：复活同样会改变层数，提示一并可控
            if (entity instanceof Player player
                    && ModConfig.CLIENT.enableAdaptationLayerMessage.get()) {
                player.sendSystemMessage(Component.translatable(
                        "message.zzq_survival_toolbox.adaptation.revive",
                        reviveCost
                ));
            }
        }
    }

    private static void forceRevive(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
        entity.clearFire();
        entity.removeAllEffects();
        entity.invulnerableTime = 10;

        if (entity.isRemoved()) {
            try {
                Field removedField = net.minecraft.world.entity.Entity.class.getDeclaredField("removed");
                removedField.setAccessible(true);
                removedField.setBoolean(entity, false);
            } catch (Exception ignored) {
            }
        }
    }

    private static int getAdaptationLevel(LivingEntity entity) {
        int level = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            level += armor.getEnchantmentLevel(ModEnchantments.ADAPTATION.get());
        }
        return level;
    }

    // ============================================================
    // 装备变化 → 更新飞行能力
    // ============================================================

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        EquipmentSlot slot = event.getSlot();
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return;
        AdaptationHelper.updateFlightAbility(entity);
        AdaptationHelper.updateisNightAbility(entity);
        // ② 换甲是关键时机：卸下来那件的护盾立刻落盘，不能留在内存里跟着新甲一起走
        AdaptationHelper.flushPendingAdapt(event.getFrom());
        AdaptationHelper.flushPendingAdapt(event.getTo());
    }

    // ============================================================
    // ② 关键时机落盘（1.20.1 没有专用同步包，护盾值靠装备重同步到客户端）
    // ============================================================

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        AdaptationHelper.flushPendingAdapt(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        AdaptationHelper.flushPendingAdapt(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        AdaptationHelper.flushPendingAdapt(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        AdaptationHelper.flushPendingAdapt(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            AdaptationHelper.flushPendingAdapt(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        AdaptationHelper.flushAllPendingAdapt();
    }
}