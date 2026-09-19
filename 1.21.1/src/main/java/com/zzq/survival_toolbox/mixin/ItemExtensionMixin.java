package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.BloodthirstyHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔适用性 Mixin
 * <p>
 * 1.21 的附魔由数据包定义，能否附在某件物品上完全取决于 {@code supported_items} 标签，
 * 而模组弓与模组枪械都无法写进静态标签（枪械 id 是运行时按枪包动态注册的）。
 * 这里在 NeoForge 明确开放给模组的判定入口
 * {@link IItemExtension#supportsEnchantment(ItemStack, Holder)} 上补一层代码判定，
 * 使嗜血可以附到弓弩与模组枪械上；其余附魔与判定结果不受影响。
 * </p>
 */
@Mixin(IItemExtension.class)
public interface ItemExtensionMixin {

    @Inject(method = "supportsEnchantment(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;)Z",
            at = @At("HEAD"), cancellable = true)
    private void zzq_survival_toolbox$bloodthirstyOnRangedWeapons(ItemStack stack, Holder<Enchantment> enchantment,
                                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!BloodthirstyHelper.usesBloodthirstyTag(enchantment)) return;
        if (BloodthirstyHelper.canBeEnchanted(stack)) {
            cir.setReturnValue(true);
        }
    }
}
