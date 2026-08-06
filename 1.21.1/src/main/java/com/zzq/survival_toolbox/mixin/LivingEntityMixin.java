package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * LivingEntity Mixin
 * <p>
 * 拦截 {@link LivingEntity#hurt} 方法：
 * <ul>
 *   <li>修改伤害值：层数抵扣 → 护盾吸收</li>
 *   <li>取消受击反馈：当最终伤害 ≤ 0 时取消动画和音效</li>
 * </ul>
 * </p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @ModifyVariable(
            method = "hurt",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float modifyDamageAmount(float amount, DamageSource source) {
        if (source.getDirectEntity() instanceof com.zzq.survival_toolbox.entity.AnvilOrbProjectile) {
            return amount;
        }

        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) return amount;

        List<ItemStack> adaptArmors = AdaptationHelper.getAdaptationArmors(entity);
        if (adaptArmors.isEmpty()) return amount;

        float originalAmount = amount;
        float totalLayers = (float) AdaptationHelper.getTotalLayers(entity);

        // 1. 层数增长
        if (originalAmount > totalLayers) {
            AdaptationHelper.applyLayerGain(entity, originalAmount);
            totalLayers = (float) AdaptationHelper.getTotalLayers(entity);
        }

        // 2. 层数减伤
        float reductionPerLayer = ModConfig.CLIENT.adaptLayerDamageReduction.get().floatValue();
        float layerAbsorb = Math.min(originalAmount, totalLayers * reductionPerLayer);
        float afterLayer = originalAmount - layerAbsorb;

        // 3. 护盾吸收
        float remaining = afterLayer;
        for (ItemStack armor : adaptArmors) {
            float shield = AdaptationHelper.getArmorShield(armor);
            float shieldUsed = Math.min(shield, remaining);
            remaining -= shieldUsed;
            AdaptationHelper.setArmorShield(armor, shield - shieldUsed);
        }

        // 4. 更新护盾上限
        for (ItemStack armor : adaptArmors) {
            AdaptationHelper.updateArmorMaxShield(armor);
        }

        // 护盾被消耗后立即推送最新数据给客户端，让 HUD 及时反映
        if (entity instanceof Player player) {
            AdaptationHelper.syncAdaptationDataToClient(player);
        }

        return remaining;
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getDirectEntity() instanceof com.zzq.survival_toolbox.entity.AnvilOrbProjectile) {
            return;
        }

        LivingEntity entity = (LivingEntity) (Object) this;
        List<ItemStack> adaptArmors = AdaptationHelper.getAdaptationArmors(entity);
        if (adaptArmors.isEmpty()) return;

        if (amount <= 0) {
            cir.setReturnValue(false);
        }
    }
}