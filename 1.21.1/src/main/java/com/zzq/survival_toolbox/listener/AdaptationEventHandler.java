package com.zzq.survival_toolbox.listener;

import net.neoforged.fml.common.EventBusSubscriber;
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
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.network.chat.Component;

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
@EventBusSubscriber(modid = "zzq_survival_toolbox")
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
            if (!armor.isEmpty()
                    && armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess())) > 0) {
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
    public static void onLivingTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
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
        // 护盾值每 tick 写入 NBT；客户端通过专用网络包同步。
        // 恢复中每 2 tick（10 次/秒）推送，HUD 足够丝滑；护盾满时每秒推送一次同步标记变化。
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

        // 推送最新自适应数据给客户端：
        // 护盾恢复中每 2 tick 一次（丝滑），护盾满时每秒一次（同步层数/适应标记变化）
        boolean shouldSync = shieldRestored ? entity.tickCount % 2 == 0 : entity.tickCount % 20 == 0;
        if (shouldSync && entity instanceof Player player) {
            AdaptationHelper.syncAdaptationDataToClient(player);
        }

        // ---- 统一适应系统（Debuff + 着火） ----
        int baseSeconds = ModConfig.CLIENT.adaptTime.get();
        double reduction = ModConfig.CLIENT.adaptTimeReduction.get();
        int thresholdSeconds = (int) Math.max(1, baseSeconds - totalLayers * reduction);
        int threshold = thresholdSeconds * 20;

        // 负面效果适应
        List<net.minecraft.core.Holder<MobEffect>> effectsToRemove = new ArrayList<>();
        for (MobEffectInstance activeEffect : entity.getActiveEffects()) {
            net.minecraft.core.Holder<MobEffect> mobEffect = activeEffect.getEffect();
            if (mobEffect.value().isBeneficial()) continue;

            ResourceLocation rl = mobEffect.getKey().location();
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
                            mobEffect.value().getDisplayName()
                    ));
                }
            }
        }
        for (net.minecraft.core.Holder<MobEffect> effect : effectsToRemove) {
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
    }

    // ============================================================
    // 死亡复活
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

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

            if (entity instanceof Player player) {
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
            level += armor.getEnchantments().getLevel(ModEnchantments.adaptation(entity.level().registryAccess()));
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
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return;
        AdaptationHelper.updateFlightAbility(entity);
        AdaptationHelper.updateisNightAbility(entity);
    }
}