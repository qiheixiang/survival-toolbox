package com.zzq.survival_toolbox.listener;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * 悬停基本说明（需求："涉及复杂操作的，鼠标悬浮上去给个基本说明"）
 * <p>
 * 做法是**通用**的，不给每个物品改代码：
 * 只要物品/方块属于本 mod，就找 lang 键 {@code tooltip.<命名空间>.<名字>.hint}，
 * <b>找不到就什么都不加</b>（翻译键取不到时 {@code getString()} 会返回键本身，用它来判断），
 * 找到就插在名字下面一行。
 * </p>
 * <p>
 * 所以以后要给谁写说明：只加一条 lang（zh_cn + en_us），代码一行都不用动。
 * 已经自带 tooltip 的东西（次元袋、透视眼镜、铁砧球、捕获实体、黑名单、定身绳、交易机…）
 * 不写这个键即可，不会出现两套说明。
 * </p>
 * <p>
 * 只在客户端生效（tooltip 本来就是客户端的事），不引用任何客户端专属类型，专用服务器也安全。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class TooltipHintHandler {

    private static final String MODID = "zzq_survival_toolbox";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!MODID.equals(id.getNamespace())) return;

        String key = "tooltip." + MODID + "." + id.getPath() + ".hint";
        Component hint = Component.translatable(key);
        // 没写这条说明：翻译键取不到，getString() 会把键原样还回来
        if (hint.getString().startsWith("tooltip.")) return;

        List<Component> lines = event.getToolTip();
        if (lines.contains(hint)) return;
        // 插在名字下面（列表第 0 行是名字）
        lines.add(Math.min(1, lines.size()), hint);
    }
}
