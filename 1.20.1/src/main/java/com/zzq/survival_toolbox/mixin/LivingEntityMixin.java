package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.ModConfig;
import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
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
        // 本次减伤使用"叠层前"的层数：本次受击新增的层数不参与本次减伤
        float totalLayers = (float) AdaptationHelper.getTotalLayers(entity);

        // 1. 层数减伤（基于叠层前的层数）
        float reductionPerLayer = ModConfig.CLIENT.adaptLayerDamageReduction.get().floatValue();
        float layerAbsorb = Math.min(originalAmount, totalLayers * reductionPerLayer);
        float afterLayer = originalAmount - layerAbsorb;

        // 2. 护盾吸收
        float remaining = afterLayer;
        for (ItemStack armor : adaptArmors) {
            float shield = AdaptationHelper.getArmorShield(armor);
            float shieldUsed = Math.min(shield, remaining);
            remaining -= shieldUsed;
            AdaptationHelper.setArmorShield(armor, shield - shieldUsed);
        }

        // 3. 更新护盾上限
        for (ItemStack armor : adaptArmors) {
            AdaptationHelper.updateArmorMaxShield(armor);
        }

        // 4. 层数增长：以本次"未减伤的原始伤害"为基准，在减伤结算之后叠加
        AdaptationHelper.applyLayerGain(entity, originalAmount);

        // ② 受伤是关键时机：护盾刚被扣掉，立刻落盘（否则崩服会让已扣掉的护盾凭空恢复）
        AdaptationHelper.flushPendingAdapt(entity);

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

    /**
     * 已适应的负面效果在"挂上之前"直接拒绝（不再先挂上再清除）。
     */
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void zzq_blockAdaptedEffect(net.minecraft.world.effect.MobEffectInstance instance,
                                        net.minecraft.world.entity.Entity source,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (instance == null) return;
        net.minecraft.world.effect.MobEffect effect = instance.getEffect();
        if (effect.isBeneficial()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        net.minecraft.resources.ResourceLocation rl =
                net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (rl == null) return;
        String effectId = rl.toString();
        for (ItemStack armor : AdaptationHelper.getAdaptationArmors(self)) {
            if (AdaptationHelper.isEffectAdapted(armor, effectId)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}