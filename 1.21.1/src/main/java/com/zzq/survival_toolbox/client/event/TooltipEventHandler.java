package com.zzq.survival_toolbox.client.event;

import net.neoforged.fml.common.EventBusSubscriber;
import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import com.zzq.survival_toolbox.util.ItemNbt;
import com.mojang.datafixers.util.Either;
import com.zzq.survival_toolbox.client.gui.DropsTooltipComponent;
import com.zzq.survival_toolbox.item.CapturedEntityItem;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tooltip 事件处理器
 * <p>
 * 将被捕获实体物品的掉落列表标记替换为自定义的 DropsTooltipComponent 渲染组件。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class TooltipEventHandler {

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        int markerIndex = -1;
        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> either = elements.get(i);
            if (either.left().isPresent()) {
                FormattedText text = either.left().get();
                if (text.getString().equals("\u0000")) {
                    markerIndex = i;
                    break;
                }
            }
        }
        if (markerIndex == -1) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof CapturedEntityItem)) {
            elements.remove(markerIndex);
            return;
        }

        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) {
            elements.remove(markerIndex);
            return;
        }

        List<ItemStack> drops = new ArrayList<>();

        // 优先从 DropList 读取
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.core.HolderLookup.Provider lookup = mc.level.registryAccess();
        List<CapturedEntityItem.DropEntry> dropEntries = CapturedEntityItem.getDropList(stack, lookup);
        if (!dropEntries.isEmpty()) {
            for (CapturedEntityItem.DropEntry entry : dropEntries) {
                drops.add(entry.stack);
            }
        } else {
            // 回退到旧 PossibleDrops
            CompoundTag dropsTag = tag.getCompound(CapturedEntityItem.TAG_POSSIBLE_DROPS);
            if (dropsTag != null && !dropsTag.isEmpty()) {
                int count = dropsTag.getInt("Count");
                for (int i = 0; i < count; i++) {
                    CompoundTag itemTag = dropsTag.getCompound("Item" + i);
                    ItemStack drop = ItemStack.parseOptional(lookup, itemTag);
                    if (drop != null && !drop.isEmpty()) {
                        drops.add(drop);
                    }
                }
            }
        }

        if (drops.isEmpty()) {
            elements.remove(markerIndex);
        } else {
            elements.set(markerIndex, Either.right(new DropsTooltipComponent(drops)));
        }
    }

    // ============================================================
    // 自适应附魔 tooltip
    // 默认只显示层数 + 护盾；按住 Shift 追加显示适应详情（已适应效果、火焰/夜间/迷雾适应）
    // ============================================================

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (event.getEntity() == null) return;

        if (stack.getEnchantments().getLevel(ModEnchantments.adaptation(event.getEntity().level().registryAccess())) <= 0) return;

        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag != null && tag.contains("adapt_layers")) {
            float layers = tag.getFloat("adapt_layers");
            event.getToolTip().add(Component.translatable(
                    "tooltip.zzq_survival_toolbox.adaptation.layers", layers
            ));
            float current = AdaptationHelper.getArmorShield(stack);
            float max = AdaptationHelper.getArmorMaxShield(stack);
            event.getToolTip().add(Component.translatable(
                    "tooltip.zzq_survival_toolbox.adaptation.shield",
                    current, max
            ));
        } else {
            event.getToolTip().add(Component.translatable(
                    "tooltip.zzq_survival_toolbox.adaptation.no_layers"
            ));
        }

        if (Screen.hasShiftDown()) {
            appendSpecialAbilities(event, stack);
            appendAdaptationDetails(event, stack);
        } else {
            event.getToolTip().add(Component.translatable(
                    "tooltip.zzq_survival_toolbox.adaptation.shift_hint"));
        }
    }

    private static void appendSpecialAbilities(ItemTooltipEvent event, ItemStack stack) {
        // 层数累计激活的特殊能力：飞行 / 复活。基于玩家当前总层数，激活才显示，放在"已适应"上面。
        Player player = event.getEntity();
        if (player == null) return;
        double totalLayers = AdaptationHelper.getTotalLayers(player);
        List<String> abilities = new ArrayList<>();
        if (totalLayers >= ModConfig.CLIENT.adaptFlightThreshold.get()) {
            abilities.add(Component.translatable("tooltip.zzq_survival_toolbox.adaptation.flight_ability").getString());
        }
        if (totalLayers >= ModConfig.CLIENT.adaptReviveThreshold.get()) {
            abilities.add(Component.translatable("tooltip.zzq_survival_toolbox.adaptation.revive_ability").getString());
        }
        if (abilities.isEmpty()) return;
        event.getToolTip().add(Component.translatable(
                "tooltip.zzq_survival_toolbox.adaptation.abilities",
                String.join(", ", abilities)));
    }

    private static void appendAdaptationDetails(ItemTooltipEvent event, ItemStack stack) {
        // 只列出"已适应"的项：各种 debuff（含模组 debuff）+ 火焰/夜间/迷雾（仅当已适应才出现）。
        // 全部都没有已适应的 → 不显示任何内容。
        List<String> adaptedItems = new ArrayList<>();
        for (String id : AdaptationHelper.getAdaptedEffects(stack)) {
            String name = id;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
                if (holder.isPresent()) {
                    name = holder.get().value().getDisplayName().getString();
                }
            }
            adaptedItems.add(name);
        }
        if (AdaptationHelper.isFireAdapted(stack)) {
            adaptedItems.add(Component.translatable("tooltip.zzq_survival_toolbox.adaptation.fire_name").getString());
        }
        if (AdaptationHelper.isNightAdapted(stack)) {
            adaptedItems.add(Component.translatable("tooltip.zzq_survival_toolbox.adaptation.night_name").getString());
        }
        if (AdaptationHelper.isFogAdapted(stack)) {
            adaptedItems.add(Component.translatable("tooltip.zzq_survival_toolbox.adaptation.fog_name").getString());
        }
        if (adaptedItems.isEmpty()) return;
        event.getToolTip().add(Component.translatable(
                "tooltip.zzq_survival_toolbox.adaptation.adapted",
                String.join(", ", adaptedItems)));
    }
}