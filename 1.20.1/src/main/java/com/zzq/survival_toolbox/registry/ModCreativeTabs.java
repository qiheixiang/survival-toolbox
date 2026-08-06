package com.zzq.survival_toolbox.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组创造模式标签页注册类
 * 包含所有模组物品、方块和自定义附魔书
 */
public class ModCreativeTabs {

    /** 创造模式标签页延迟注册器 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "zzq_survival_toolbox");

    /**
     * 生存工具箱主标签页
     * 包含所有模组物品、方块以及嗜血/自适应附魔书
     */
    public static final RegistryObject<CreativeModeTab> SURVIVAL_TOOLBOX_TAB = CREATIVE_MODE_TABS.register(
            "survival_toolbox",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.zzq_survival_toolbox"))
                    .icon(() -> new ItemStack(ModItems.ANVIL_ORB.get()))
                    .displayItems((parameters, output) -> {
                        // ---- 物品 ----
                        output.accept(ModItems.ANVIL_ORB.get());
                        output.accept(ModItems.CAPTURED_ENTITY.get());
                        output.accept(ModItems.DISARM_STAFF.get());
                        output.accept(ModItems.TERRAIN_EDITOR.get());
                        output.accept(ModItems.STATIC_LEASH.get());
                        output.accept(ModItems.BLACKLIST.get());
                        output.accept(ModItems.ENCHANTMENT_BAG.get());

                        // ---- 方块 ----
                        output.accept(ModBlocks.DISASSEMBLE_TABLE.get());
                        output.accept(ModBlocks.MICRO_FARM.get());
                        output.accept(ModBlocks.TRANSMUTATION_FURNACE.get());
                        output.accept(ModBlocks.GUARDIAN_LANTERN.get());
                        output.accept(ModBlocks.SMART_FARM.get());
                        output.accept(ModBlocks.INFINITE_SOURCE.get());
                        output.accept(ModBlocks.OMNI_HOPPER.get());
                        output.accept(ModBlocks.ENCHANTMENT_TRANSFER.get());
                        output.accept(ModBlocks.FEAST.get());

                        // ---- 附魔书 ----
                        ItemStack bloodthirstyBook = EnchantedBookItem.createForEnchantment(
                                new EnchantmentInstance(ModEnchantments.BLOODTHIRSTY.get(), 1)
                        );
                        output.accept(bloodthirstyBook);

                        ItemStack adaptationBook = EnchantedBookItem.createForEnchantment(
                                new EnchantmentInstance(ModEnchantments.ADAPTATION.get(), 1)
                        );
                        output.accept(adaptationBook);
                    })
                    .build()
    );
}