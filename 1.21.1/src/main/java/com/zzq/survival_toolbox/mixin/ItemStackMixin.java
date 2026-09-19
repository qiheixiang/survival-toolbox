package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * ItemStack Mixin
 * <p>
 * 拦截 {@link ItemStack#hurtAndBreak} 方法，
 * 用自适应层数和护盾吸收装备耐久损耗。
 * 顺序：层数 → 护盾 → 耐久
 * </p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow
    public abstract boolean isDamageableItem();

    @Shadow
    public abstract int getDamageValue();

    @Shadow
    public abstract void setDamageValue(int damage);

    @Shadow
    public abstract int getMaxDamage();

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void onHurtAndBreak(int amount, ServerLevel level, LivingEntity user, Consumer<Item> onBroken, CallbackInfo ci) {
        if (user == null) return;

        if (!AdaptationHelper.mayHaveAdaptationArmor(user)) return;

        ItemStack stack = (ItemStack) (Object) this;
        if (!isDamageableItem() || stack.isEmpty()) return;

        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(user);
        if (armors.isEmpty()) return;

        // 1. 层数抵扣
        double totalLayers = AdaptationHelper.getTotalLayers(user);
        double remaining = amount;
        double layerAbsorb = Math.min(remaining, totalLayers);
        remaining -= layerAbsorb;

        // 2. 护盾吸收
        int remainingInt = (int) remaining;
        for (ItemStack armor : armors) {
            if (remainingInt <= 0) break;
            float shield = AdaptationHelper.getArmorShield(armor);
            if (shield > 0) {
                float shieldUsed = Math.min(shield, remainingInt);
                AdaptationHelper.setArmorShield(armor, shield - shieldUsed);
                remainingInt -= (int) shieldUsed;
            }
        }
        // ② 受伤是关键时机：护盾刚被扣掉，立刻落盘
        AdaptationHelper.flushPendingAdapt(user);

        // 3. 护盾完全吸收 → 取消原版扣耐久
        if (remainingInt <= 0) {
            ci.cancel();
            return;
        }

        // 4. 剩余损耗正常扣除
        int newDamage = getDamageValue() + remainingInt;
        if (newDamage >= getMaxDamage()) {
            ci.cancel();
            setDamageValue(getMaxDamage());
            onBroken.accept(stack.getItem());
        } else {
            ci.cancel();
            setDamageValue(newDamage);
        }
    }
}
