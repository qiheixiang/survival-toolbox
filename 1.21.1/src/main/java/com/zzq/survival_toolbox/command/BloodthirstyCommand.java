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
 *   <li>{@code add &lt;bonus&gt;} - 增加主手武器的嗜血攻击力加成</li>
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
                                .then(Commands.argument("bonus", FloatArgumentType.floatArg(0))
                                        .executes(BloodthirstyCommand::setBonus)
                                )
                        )
                        .then(Commands.literal("add")
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

        if (weapon.getEnchantments().getLevel(ModEnchantments.bloodthirsty(player.level().registryAccess())) <= 0) {
            source.sendFailure(Component.literal("主手武器没有嗜血附魔"));
            return 0;
        }

        float bonus = getBloodthirstyBonus(weapon);
        source.sendSuccess(() -> Component.literal("当前嗜血攻击力加成: " + String.format("%.2f", bonus)), false);
        return 1;
    }

    private static int setBonus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        float value = FloatArgumentType.getFloat(ctx, "bonus");
        ItemStack weapon = player.getMainHandItem();

        if (weapon.isEmpty()) {
            source.sendFailure(Component.literal("主手没有武器"));
            return 0;
        }

        if (weapon.getEnchantments().getLevel(ModEnchantments.bloodthirsty(player.level().registryAccess())) <= 0) {
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

    private static int addBonus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }

        float addValue = FloatArgumentType.getFloat(ctx, "bonus");
        ItemStack weapon = player.getMainHandItem();

        if (weapon.isEmpty()) {
            source.sendFailure(Component.literal("主手没有武器"));
            return 0;
        }

        if (weapon.getEnchantments().getLevel(ModEnchantments.bloodthirsty(player.level().registryAccess())) <= 0) {
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
        CompoundTag modData = ItemNbt.getOrCreateTag(weapon).getCompound("zzq_survival_toolbox_data");
        return modData.getFloat("bloodthirsty_bonus");
    }

    private static void setBloodthirstyBonus(ItemStack weapon, float bonus) {
        CompoundTag root = ItemNbt.getOrCreateTag(weapon).copy();
        CompoundTag modData = root.getCompound("zzq_survival_toolbox_data");
        modData.putFloat("bloodthirsty_bonus", Math.max(0, bonus));
        root.put("zzq_survival_toolbox_data", modData);
        ItemNbt.setTag(weapon, root);
    }
}