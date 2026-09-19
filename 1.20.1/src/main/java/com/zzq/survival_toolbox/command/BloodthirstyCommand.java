package com.zzq.survival_toolbox.command;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 嗜血附魔管理命令
 * <p>
 * 提供以下子命令：
 * <ul>
 *   <li>{@code get} - 显示主手武器的嗜血攻击力加成</li>
 *   <li>{@code set &lt;bonus&gt;} - 设置主手武器的嗜血攻击力加成</li>
 *   <li>{@code set max} - 直接设成配置里的最大加成（{@code ModConfig maxBonus}）</li>
 *   <li>{@code add &lt;bonus&gt;} - 增加主手武器的嗜血攻击力加成</li>
 *   <li>{@code add max} - 直接加满（加完由上限兜底）</li>
 * </ul>
 * </p>
 */
public class BloodthirstyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zzq_survival_toolbox")
                .then(Commands.literal("bloodthirsty")
                        .then(Commands.literal("get")
                                .executes(BloodthirstyCommand::getBonus)
                        )
                        .then(Commands.literal("set")
                                // /bloodthirsty set max = 直接用配置里的最大加成
                                .then(Commands.literal("max")
                                        .executes(BloodthirstyCommand::setBonusMax)
                                )
                                .then(Commands.argument("bonus", FloatArgumentType.floatArg(0))
                                        .executes(BloodthirstyCommand::setBonus)
                                )
                        )
                        .then(Commands.literal("add")
                                // /bloodthirsty add max = 直接加满（仍由上限兜底）
                                .then(Commands.literal("max")
                                        .executes(BloodthirstyCommand::addBonusMax)
                                )
                                .then(Commands.argument("bonus", FloatArgumentType.floatArg())
                                        .executes(BloodthirstyCommand::addBonus)
                                )
                        )
                )
        );
    }

    private static int getBonus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty()) {
            source.sendFailure(Component.literal("主手没有武器"));
            return 0;
        }

        if (weapon.getEnchantmentLevel(ModEnchantments.BLOODTHIRSTY.get()) <= 0) {
            source.sendFailure(Component.literal("主手武器没有嗜血附魔"));
            return 0;
        }

        float bonus = getBloodthirstyBonus(weapon);
        source.sendSuccess(() -> Component.literal("当前嗜血攻击力加成: " + String.format("%.2f", bonus)), false);
        return 1;
    }

    /** {@code /bloodthirsty set max}：直接用配置里的最大加成（{@code ModConfig.CLIENT.maxBonus}） */
    private static int setBonusMax(CommandContext<CommandSourceStack> ctx) {
        return setBonusTo(ctx.getSource(), ModConfig.CLIENT.maxBonus.get().floatValue());
    }

    private static int setBonus(CommandContext<CommandSourceStack> ctx) {
        return setBonusTo(ctx.getSource(), FloatArgumentType.getFloat(ctx, "bonus"));
    }

    /** set 的公共实现：普通数值和 max 走同一条路（超过上限同样会提示并调整） */
    private static int setBonusTo(CommandSourceStack source, float value) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        ItemStack weapon = player.getMainHandItem();

        if (weapon.isEmpty()) {
            source.sendFailure(Component.literal("主手没有武器"));
            return 0;
        }

        if (weapon.getEnchantmentLevel(ModEnchantments.BLOODTHIRSTY.get()) <= 0) {
            source.sendFailure(Component.literal("主手武器没有嗜血附魔"));
            return 0;
        }

        double maxBonus = ModConfig.CLIENT.maxBonus.get();
        if (value > maxBonus) {
            value = (float) maxBonus;
            float finalValue = value;
            source.sendSuccess(() -> Component.literal("数值超过上限，已自动调整为 " + String.format("%.2f", finalValue)), false);
        }

        setBloodthirstyBonus(weapon, value);
        float finalValue1 = value;
        source.sendSuccess(() -> Component.literal("已设置嗜血攻击力加成为 " + String.format("%.2f", finalValue1)), false);
        return 1;
    }

    /** {@code /bloodthirsty add max}：直接加满（仍由配置上限兜底） */
    private static int addBonusMax(CommandContext<CommandSourceStack> ctx) {
        return addBonusTo(ctx.getSource(), ModConfig.CLIENT.maxBonus.get().floatValue());
    }

    private static int addBonus(CommandContext<CommandSourceStack> ctx) {
        return addBonusTo(ctx.getSource(), FloatArgumentType.getFloat(ctx, "bonus"));
    }

    /** add 的公共实现 */
    private static int addBonusTo(CommandSourceStack source, float addValue) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        ItemStack weapon = player.getMainHandItem();

        if (weapon.isEmpty()) {
            source.sendFailure(Component.literal("主手没有武器"));
            return 0;
        }

        if (weapon.getEnchantmentLevel(ModEnchantments.BLOODTHIRSTY.get()) <= 0) {
            source.sendFailure(Component.literal("主手武器没有嗜血附魔"));
            return 0;
        }

        float current = getBloodthirstyBonus(weapon);
        double maxBonus = ModConfig.CLIENT.maxBonus.get();
        float newBonus = Math.min(current + addValue, (float) maxBonus);

        setBloodthirstyBonus(weapon, newBonus);
        source.sendSuccess(() -> Component.literal("已增加 " + String.format("%.2f", addValue) +
                "，当前加成: " + String.format("%.2f", newBonus)), false);
        return 1;
    }

    private static float getBloodthirstyBonus(ItemStack weapon) {
        CompoundTag modData = weapon.getOrCreateTag().getCompound("zzq_survival_toolbox_data");
        return modData.getFloat("bloodthirsty_bonus");
    }

    private static void setBloodthirstyBonus(ItemStack weapon, float bonus) {
        CompoundTag modData = weapon.getOrCreateTag().getCompound("zzq_survival_toolbox_data");
        modData.putFloat("bloodthirsty_bonus", Math.max(0, bonus));
        ItemNbt.edit(weapon, t -> t.put("zzq_survival_toolbox_data", modData));
    }
}