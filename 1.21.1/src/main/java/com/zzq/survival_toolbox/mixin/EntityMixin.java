package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Entity Mixin
 * <p>
 * 拦截 {@link Entity#setRemainingFireTicks} 与 {@link Entity#isOnFire}：
 * 当实体穿戴的盔甲全部适应火焰时，阻止着火状态设置，并强制 {@code isOnFire()} 返回 false。
 * </p>
 * <p>
 * {@code isOnFire()} 同时是客户端火焰屏幕特效（“眼前的火焰”）的判定来源，
 * 拦截它可以确保无论火焰从哪种途径（岩浆/火焰方块/着火实体/弹射物）产生，都不会显示火焰特效。
 * </p>
 */
@Mixin(Entity.class)
public class EntityMixin {

    @Shadow
    private int remainingFireTicks;

    @Inject(method = "setRemainingFireTicks", at = @At("HEAD"), cancellable = true)
    private void onSetRemainingFireTicks(int ticks, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof LivingEntity living)) return;

        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(living);
        if (armors.isEmpty()) return;

        boolean allAdapted = true;
        for (ItemStack armor : armors) {
            if (!AdaptationHelper.isFireAdapted(armor)) {
                allAdapted = false;
                break;
            }
        }

        if (allAdapted) {
            ci.cancel();
            this.remainingFireTicks = 0;
        }
    }

    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    private void onIsOnFire(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof LivingEntity living)) return;
        // isOnFire 可能在实体加入世界前被调用（此时 level() 可能为 null），需判空防御。
        if (living.level() == null) return;
        // 火焰屏幕特效仅与客户端相关；服务端火焰状态已由 setRemainingFireTicks 拦截，
        // 因此仅在客户端执行检查，避免服务端对每个实体每次 isOnFire 都进行盔甲判断。
        if (!living.level().isClientSide()) return;
        // 快速过滤：绝大多数实体未装备自适应盔甲，直接返回，避免每帧检查开销。
        if (!AdaptationHelper.mayHaveAdaptationArmor(living)) return;

        List<ItemStack> armors = AdaptationHelper.getAdaptationArmors(living);
        if (armors.isEmpty()) return;

        boolean allAdapted = true;
        for (ItemStack armor : armors) {
            if (!AdaptationHelper.isFireAdapted(armor)) {
                allAdapted = false;
                break;
            }
        }

        if (allAdapted) {
            cir.setReturnValue(false);
        }
    }
}