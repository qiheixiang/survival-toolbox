package com.zzq.survival_toolbox.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 自适应附魔管理命令
 * <p>
 * 提供以下子命令：
 * <ul>
 *   <li>{@code get} - 显示当前自适应总层数</li>
 *   <li>{@code set &lt;layers&gt;} - 设置每件盔甲的自适应层数</li>
 *   <li>{@code set max} - 直接设成配置里的最大层数（{@code ModConfig adaptMaxLayers}）</li>
 *   <li>{@code add &lt;layers&gt;} - 增加每件盔甲的自适应层数</li>
 *   <li>{@code add max} - 直接加"最大层数"那么多（和手输那个数字完全一样）</li>
 * </ul>
 * </p>
 */
public class AdaptCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zzq_survival_toolbox")
                .then(Commands.literal("adapt")
                        .then(Commands.literal("get")
                                .executes(AdaptCommand::getLayers)
                        )
                        .then(Commands.literal("set")
                                // /adapt set max = 直接用配置里的最大层数
                                .then(Commands.literal("max")
                                        .executes(AdaptCommand::setLayersMax)
                                )
                                .then(Commands.argument("layers", FloatArgumentType.floatArg(0))
                                        .executes(AdaptCommand::setLayers)
                                )
                        )
                        .then(Commands.literal("add")
                                // /adapt add max = 直接加"最大层数"那么多
                                .then(Commands.literal("max")
                                        .executes(AdaptCommand::addLayersMax)
                                )
                                .then(Commands.argument("layers", FloatArgumentType.floatArg())
                                        .executes(AdaptCommand::addLayers)
                                )
                        )
                )
        );
    }

    /**
     * 生成当前 4 个护甲槽位的诊断信息（物品名、附魔数量、自适应附魔等级），
     * 用于排查命令为何检测不到自适应盔甲。
     */
    private static String armorSlotStatus(Player player) {
        StringBuilder sb = new StringBuilder();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = player.getItemBySlot(slot);
            sb.append(slot.getName()).append(": ");
            if (stack.isEmpty()) {
                sb.append("空 | ");
                continue;
            }
            int adaptLevel;
            try {
                adaptLevel = stack.getEnchantments()
                        .getLevel(ModEnchantments.adaptation(player.level().registryAccess()));
            } catch (Exception e) {
                adaptLevel = -1;
            }
            sb.append(stack.getHoverName().getString())
                    .append("[附魔数=").append(stack.getEnchantments().entrySet().size())
                    .append(", 自适应等级=").append(adaptLevel).append("] | ");
        }
        return sb.toString();
    }

    private static int getLayers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        double total = AdaptationHelper.getTotalLayers(player);
        source.sendSuccess(() -> Component.literal("当前自适应总层数: " + total), false);
        return 1;
    }

    /** {@code /adapt set max}：直接用配置里的最大层数（{@code ModConfig.CLIENT.adaptMaxLayers}） */
    private static int setLayersMax(CommandContext<CommandSourceStack> ctx) {
        return setLayersTo(ctx.getSource(), ModConfig.CLIENT.adaptMaxLayers.get().floatValue());
    }

    private static int setLayers(CommandContext<CommandSourceStack> ctx) {
        return setLayersTo(ctx.getSource(), FloatArgumentType.getFloat(ctx, "layers"));
    }

    /** set 的公共实现：普通数值和 max 走同一条路，行为完全一致 */
    private static int setLayersTo(CommandSourceStack source, float value) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(player);
        if (armors.isEmpty()) {
            source.sendFailure(Component.literal("你没有穿戴任何自适应盔甲。诊断: " + armorSlotStatus(player)));
            return 0;
        }
        for (ItemStack armor : armors) {
            AdaptationHelper.setArmorLayers(armor, value);
            AdaptationHelper.updateArmorMaxShield(armor);
        }
        // ② 命令直接改层数/上限：先把内存里攒着的护盾落盘，别让旧值回头盖掉新值
        AdaptationHelper.flushPendingAdapt(player);
        AdaptationHelper.syncAdaptationDataToClient(player, true);
        double total = AdaptationHelper.getTotalLayers(player);
        source.sendSuccess(() -> Component.literal("已设置每件盔甲层数为 " + value + "，总层数: " + total), false);
        return 1;
    }

    /** {@code /adapt add max}：直接加"最大层数"那么多（和手输这个数字完全一样） */
    private static int addLayersMax(CommandContext<CommandSourceStack> ctx) {
        return addLayersTo(ctx.getSource(), ModConfig.CLIENT.adaptMaxLayers.get().floatValue());
    }

    private static int addLayers(CommandContext<CommandSourceStack> ctx) {
        return addLayersTo(ctx.getSource(), FloatArgumentType.getFloat(ctx, "layers"));
    }

    /** add 的公共实现 */
    private static int addLayersTo(CommandSourceStack source, float addValue) {
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("只有玩家可以使用此命令"));
            return 0;
        }
        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(player);
        if (armors.isEmpty()) {
            source.sendFailure(Component.literal("你没有穿戴任何自适应盔甲。诊断: " + armorSlotStatus(player)));
            return 0;
        }
        for (ItemStack armor : armors) {
            float current = AdaptationHelper.getArmorLayers(armor);
            float newVal = Math.max(0, current + addValue);
            AdaptationHelper.setArmorLayers(armor, newVal);
            AdaptationHelper.updateArmorMaxShield(armor);
        }
        // ② 同上：先落盘再同步
        AdaptationHelper.flushPendingAdapt(player);
        AdaptationHelper.syncAdaptationDataToClient(player, true);
        double total = AdaptationHelper.getTotalLayers(player);
        source.sendSuccess(() -> Component.literal("已增加 " + addValue + " 层，当前总层数: " + total), false);
        return 1;
    }
}