package com.zzq.survival_toolbox;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置文件
 * <p>
 * 分为 ClientConfig（客户端配置）和 CommonConfig（通用配置）两部分。
 * 所有配置项都提供默认值常量，便于 GUI 重置功能使用。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", bus = EventBusSubscriber.Bus.MOD)
public class ModConfig {

    // ============================================================
    // 嗜血附魔默认值
    // ============================================================

    public static final boolean DEFAULT_ENABLE_KILL_MESSAGE = true;
    public static final boolean DEFAULT_ENABLE_ATTACK_MESSAGE = false;
    public static final double DEFAULT_KILL_ABSORB_MULTIPLIER = 0.01;
    public static final double DEFAULT_MAX_BONUS = Float.MAX_VALUE;
    public static final double DEFAULT_HEAL_MULTIPLIER = 0.1;
    public static final double DEFAULT_REPAIR_MULTIPLIER = 1.0;

    // ============================================================
    // 自适应附魔默认值
    // ============================================================

    public static final boolean DEFAULT_ENABLE_LAYER_MESSAGE = true;
    public static final boolean DEFAULT_ENABLE_RESTORE_MESSAGE = false;
    public static final boolean DEFAULT_SHOW_SHIELD_BAR_TEXT = true;
    public static final int DEFAULT_SHIELD_BAR_X = -1;
    public static final int DEFAULT_SHIELD_BAR_Y = -1;
    public static final int DEFAULT_SHIELD_BAR_WIDTH = 182;
    public static final int DEFAULT_SHIELD_BAR_HEIGHT = 5;
    public static final int DEFAULT_ADAPT_FLIGHT_THRESHOLD = 200;
    public static final int DEFAULT_ADAPT_REVIVE_THRESHOLD = 500;
    public static final int DEFAULT_ADAPT_REVIVE_COST = 100;
    public static final int DEFAULT_ADAPT_TIME = 10;
    public static final double DEFAULT_ADAPT_TIME_REDUCTION = 0.01;
    public static final double DEFAULT_ADAPT_MAX_LAYERS = Float.MAX_VALUE;
    public static final double DEFAULT_ADAPT_LAYER_GAIN_MULTIPLIER = 0.01;
    public static final double DEFAULT_ADAPT_LAYER_DAMAGE_REDUCTION = 1.0;
    public static final double DEFAULT_ADAPT_SHIELD_PER_LAYER = 0.5;
    public static final double DEFAULT_ADAPT_SHIELD_CAP = 1000.0;
    public static final double DEFAULT_ADAPT_SHIELD_REGEN_BASE = 1.0;
    public static final double DEFAULT_ADAPT_SHIELD_REGEN_BONUS = 0.01;
    public static final double DEFAULT_ADAPT_HEAL_MULTIPLIER = 0.01;
    public static final double DEFAULT_ADAPT_ARMOR_REPAIR_PER_LAYER = 0.1;

    // ============================================================
    // 铁砧球默认值
    // ============================================================

    public static final double DEFAULT_ANVIL_ORB_HEALTH_THRESHOLD = 1.0;
    public static final List<String> DEFAULT_ANVIL_ORB_BLACKLIST = new ArrayList<>();

    // ============================================================
    // 配置对象
    // ============================================================

    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final CommonConfig COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<ClientConfig, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();

        Pair<CommonConfig, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(CommonConfig::new);
        COMMON_SPEC = commonPair.getRight();
        COMMON = commonPair.getLeft();
    }

    public static void bake() {
        // 配置加载后的回调（当前无实际逻辑）
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        bake();
    }

    // ============================================================
    // 重置方法（供 GUI 调用）
    // ============================================================

    public static void resetBloodthirstyToDefault() {
        CLIENT.enableKillMessage.set(DEFAULT_ENABLE_KILL_MESSAGE);
        CLIENT.enableAttackMessage.set(DEFAULT_ENABLE_ATTACK_MESSAGE);
        CLIENT.killAbsorbMultiplier.set(DEFAULT_KILL_ABSORB_MULTIPLIER);
        CLIENT.maxBonus.set(DEFAULT_MAX_BONUS);
        CLIENT.healMultiplier.set(DEFAULT_HEAL_MULTIPLIER);
        CLIENT.repairMultiplier.set(DEFAULT_REPAIR_MULTIPLIER);
        CLIENT_SPEC.save();
    }

    public static void resetAdaptationToDefault() {
        CLIENT.adaptMaxLayers.set(DEFAULT_ADAPT_MAX_LAYERS);
        CLIENT.adaptLayerGainMultiplier.set(DEFAULT_ADAPT_LAYER_GAIN_MULTIPLIER);
        CLIENT.adaptLayerDamageReduction.set(DEFAULT_ADAPT_LAYER_DAMAGE_REDUCTION);
        CLIENT.adaptShieldPerLayer.set(DEFAULT_ADAPT_SHIELD_PER_LAYER);
        CLIENT.adaptShieldCap.set(DEFAULT_ADAPT_SHIELD_CAP);
        CLIENT.adaptShieldRegenBase.set(DEFAULT_ADAPT_SHIELD_REGEN_BASE);
        CLIENT.adaptShieldRegenBonus.set(DEFAULT_ADAPT_SHIELD_REGEN_BONUS);
        CLIENT.adaptFlightThreshold.set(DEFAULT_ADAPT_FLIGHT_THRESHOLD);
        CLIENT.adaptReviveThreshold.set(DEFAULT_ADAPT_REVIVE_THRESHOLD);
        CLIENT.adaptReviveCost.set(DEFAULT_ADAPT_REVIVE_COST);
        CLIENT.adaptTime.set(DEFAULT_ADAPT_TIME);
        CLIENT.adaptTimeReduction.set(DEFAULT_ADAPT_TIME_REDUCTION);
        CLIENT.adaptHealMultiplier.set(DEFAULT_ADAPT_HEAL_MULTIPLIER);
        CLIENT.adaptArmorRepairPerLayer.set(DEFAULT_ADAPT_ARMOR_REPAIR_PER_LAYER);
        CLIENT.enableAdaptationLayerMessage.set(DEFAULT_ENABLE_LAYER_MESSAGE);
        CLIENT.enableAdaptationRestoreMessage.set(DEFAULT_ENABLE_RESTORE_MESSAGE);
        CLIENT.showShieldBarText.set(DEFAULT_SHOW_SHIELD_BAR_TEXT);
        CLIENT.shieldBarX.set(DEFAULT_SHIELD_BAR_X);
        CLIENT.shieldBarY.set(DEFAULT_SHIELD_BAR_Y);
        CLIENT.shieldBarWidth.set(DEFAULT_SHIELD_BAR_WIDTH);
        CLIENT.shieldBarHeight.set(DEFAULT_SHIELD_BAR_HEIGHT);
        CLIENT_SPEC.save();
    }

    // ============================================================
    // ClientConfig 内部类
    // ============================================================

    public static class ClientConfig {
        // 嗜血附魔
        public final ModConfigSpec.BooleanValue enableKillMessage;
        public final ModConfigSpec.BooleanValue enableAttackMessage;
        public final ModConfigSpec.DoubleValue killAbsorbMultiplier;
        public final ModConfigSpec.DoubleValue maxBonus;
        public final ModConfigSpec.DoubleValue healMultiplier;
        public final ModConfigSpec.DoubleValue repairMultiplier;

        // 自适应核心
        public final ModConfigSpec.DoubleValue adaptMaxLayers;
        public final ModConfigSpec.DoubleValue adaptLayerGainMultiplier;
        public final ModConfigSpec.DoubleValue adaptLayerDamageReduction;
        public final ModConfigSpec.DoubleValue adaptShieldCap;
        public final ModConfigSpec.DoubleValue adaptShieldPerLayer;
        public final ModConfigSpec.DoubleValue adaptShieldRegenBase;
        public final ModConfigSpec.DoubleValue adaptShieldRegenBonus;

        // 能力阈值
        public final ModConfigSpec.IntValue adaptFlightThreshold;
        public final ModConfigSpec.IntValue adaptReviveThreshold;
        public final ModConfigSpec.IntValue adaptReviveCost;

        // 统一适应
        public final ModConfigSpec.IntValue adaptTime;
        public final ModConfigSpec.DoubleValue adaptTimeReduction;
        public final ModConfigSpec.BooleanValue adaptBlockScreenEffects;

        // 恢复相关
        public final ModConfigSpec.DoubleValue adaptHealMultiplier;
        public final ModConfigSpec.DoubleValue adaptArmorRepairPerLayer;

        // 消息开关
        public final ModConfigSpec.BooleanValue enableAdaptationLayerMessage;
        public final ModConfigSpec.BooleanValue enableAdaptationRestoreMessage;

        // 护盾条 UI
        public final ModConfigSpec.BooleanValue showShieldBarText;
        public final ModConfigSpec.IntValue shieldBarX;
        public final ModConfigSpec.IntValue shieldBarY;
        public final ModConfigSpec.IntValue shieldBarWidth;
        public final ModConfigSpec.IntValue shieldBarHeight;

        public ClientConfig(ModConfigSpec.Builder builder) {
            builder.push("general");

            // ---- 嗜血模块 ----
            builder.push("bloodthirsty");
            enableKillMessage = builder
                    .comment("是否显示击杀怪物时吸收攻击力的提示")
                    .define("enableKillMessage", DEFAULT_ENABLE_KILL_MESSAGE);
            enableAttackMessage = builder
                    .comment("是否显示攻击时恢复生命和修复耐久的提示")
                    .define("enableAttackMessage", DEFAULT_ENABLE_ATTACK_MESSAGE);
            killAbsorbMultiplier = builder
                    .comment("击杀怪物时，吸收其最大生命值的比例（0.01 = 1%）")
                    .defineInRange("killAbsorbMultiplier", DEFAULT_KILL_ABSORB_MULTIPLIER, 0, 100);
            maxBonus = builder
                    .comment("攻击力加成可达到的最大值（Float最大值 = 几乎无上限）")
                    .defineInRange("maxBonus", DEFAULT_MAX_BONUS, 0, Float.MAX_VALUE);
            healMultiplier = builder
                    .comment("攻击时，恢复生命值 = 攻击力加成 × 该值（0.1 = 10%）")
                    .defineInRange("healMultiplier", DEFAULT_HEAL_MULTIPLIER, 0, 100);
            repairMultiplier = builder
                    .comment("攻击时，修复耐久 = 攻击力加成 × 该值（取整，至少1点）")
                    .defineInRange("repairMultiplier", DEFAULT_REPAIR_MULTIPLIER, 0, 100);
            builder.pop();

            // ---- 自适应模块 ----
            builder.push("adaptation");
            adaptMaxLayers = builder
                    .comment("层数可达到的最大值")
                    .defineInRange("adaptMaxLayers", DEFAULT_ADAPT_MAX_LAYERS, 0, Float.MAX_VALUE);
            adaptLayerGainMultiplier = builder
                    .comment("每受到1点伤害获得的层数")
                    .defineInRange("adaptLayerGainMultiplier", DEFAULT_ADAPT_LAYER_GAIN_MULTIPLIER, 0, 100);
            adaptLayerDamageReduction = builder
                    .comment("每层提供的伤害抵扣比例（1.0 = 1层抵消1点伤害，0.5 = 2层抵消1点伤害）")
                    .defineInRange("adaptLayerDamageReduction", DEFAULT_ADAPT_LAYER_DAMAGE_REDUCTION, 0.0, 1.0);
            adaptShieldPerLayer = builder
                    .comment("每层提供的护盾值")
                    .defineInRange("adaptShieldPerLayer", DEFAULT_ADAPT_SHIELD_PER_LAYER, 0, 1000);
            adaptShieldCap = builder
                    .comment("单件盔甲护盾上限（护盾值 = 层数 × 每层护盾值，但不超过此值，设为 0 则完全无护盾）")
                    .defineInRange("adaptShieldCap", DEFAULT_ADAPT_SHIELD_CAP, 0, 1_000_000_000);
            adaptShieldRegenBase = builder
                    .comment("护盾每秒基础恢复量")
                    .defineInRange("adaptShieldRegenBase", DEFAULT_ADAPT_SHIELD_REGEN_BASE, 0, 1000);
            adaptShieldRegenBonus = builder
                    .comment("每层额外增加的每秒恢复量")
                    .defineInRange("adaptShieldRegenBonus", DEFAULT_ADAPT_SHIELD_REGEN_BONUS, 0, 100);
            adaptFlightThreshold = builder
                    .comment("达到此层数后获得创造飞行")
                    .defineInRange("adaptFlightThreshold", DEFAULT_ADAPT_FLIGHT_THRESHOLD, 0, Integer.MAX_VALUE);
            adaptReviveThreshold = builder
                    .comment("达到此层数后可原地复活")
                    .defineInRange("adaptReviveThreshold", DEFAULT_ADAPT_REVIVE_THRESHOLD, 0, Integer.MAX_VALUE);
            adaptReviveCost = builder
                    .comment("原地复活时消耗的层数")
                    .defineInRange("adaptReviveCost", DEFAULT_ADAPT_REVIVE_COST, 0, Integer.MAX_VALUE);
            adaptTime = builder
                    .comment("负面效果（中毒/凋零/黑暗/着火等）适应所需基础秒数")
                    .defineInRange("adaptTime", DEFAULT_ADAPT_TIME, 1, 600);
            adaptTimeReduction = builder
                    .comment("每层减少适应秒数（最低1秒，例如：基础10秒，层数100，减少1秒，则需9秒适应）")
                    .defineInRange("adaptTimeReduction", DEFAULT_ADAPT_TIME_REDUCTION, 0, 100);
            adaptBlockScreenEffects = builder
                    .comment("免疫燃烧效果和液体迷雾后，屏蔽所有屏幕视觉效果（失明黑雾/火焰红屏/水雾/冰冻/传送门等），对自定义渲染的模组效果不一定生效")
                    .define("adaptBlockScreenEffects", true);
            adaptHealMultiplier = builder
                    .comment("每次恢复时，每层恢复的生命值比例")
                    .defineInRange("adaptHealMultiplier", DEFAULT_ADAPT_HEAL_MULTIPLIER, 0, 100);
            adaptArmorRepairPerLayer = builder
                    .comment("每层恢复护甲耐久值（修复量 = 层数 × 该值）")
                    .defineInRange("adaptArmorRepairPerLayer", DEFAULT_ADAPT_ARMOR_REPAIR_PER_LAYER, 0, 100);
            enableAdaptationLayerMessage = builder
                    .comment("是否显示自适应附魔的叠层提示")
                    .define("enableAdaptationLayerMessage", DEFAULT_ENABLE_LAYER_MESSAGE);
            enableAdaptationRestoreMessage = builder
                    .comment("是否显示自适应附魔的恢复提示")
                    .define("enableAdaptationRestoreMessage", DEFAULT_ENABLE_RESTORE_MESSAGE);
            builder.pop();

            // ---- 护盾条 ----
            builder.push("shield_bar");
            showShieldBarText = builder
                    .comment("是否显示护盾条上方的数字（当前值/最大值）")
                    .define("showShieldBarText", DEFAULT_SHOW_SHIELD_BAR_TEXT);
            shieldBarX = builder
                    .comment("护盾条左上角X坐标（-1表示自动定位）")
                    .defineInRange("shieldBarX", DEFAULT_SHIELD_BAR_X, -1, 4096);
            shieldBarY = builder
                    .comment("护盾条左上角Y坐标（-1表示自动定位）")
                    .defineInRange("shieldBarY", DEFAULT_SHIELD_BAR_Y, -1, 4096);
            shieldBarWidth = builder
                    .comment("护盾条宽度（像素，0表示不显示）")
                    .defineInRange("shieldBarWidth", DEFAULT_SHIELD_BAR_WIDTH, 0, 4096);
            shieldBarHeight = builder
                    .comment("护盾条高度（像素，0表示不显示）")
                    .defineInRange("shieldBarHeight", DEFAULT_SHIELD_BAR_HEIGHT, 0, 256);
            builder.pop();

            builder.pop();
        }
    }

    // ============================================================
    // CommonConfig 内部类
    // ============================================================

    public static class CommonConfig {
        public final ModConfigSpec.DoubleValue anvilOrbHealthThreshold;
        public final ModConfigSpec.ConfigValue<List<? extends String>> anvilOrbBlacklist;

        public CommonConfig(ModConfigSpec.Builder builder) {
            builder.push("anvil_orb");
            anvilOrbHealthThreshold = builder
                    .comment("血量阈值（0~1），实体当前血量 ≤ 最大血量 × 该值才可捕获")
                    .defineInRange("anvilOrbHealthThreshold", DEFAULT_ANVIL_ORB_HEALTH_THRESHOLD, 0.01, 1.0);
            anvilOrbBlacklist = builder
                    .comment("禁止捕获的实体ID列表（如 [\"minecraft:ender_dragon\"]），玩家硬编码禁止")
                    .define("anvilOrbBlacklist", DEFAULT_ANVIL_ORB_BLACKLIST);
            builder.pop();
        }
    }
}