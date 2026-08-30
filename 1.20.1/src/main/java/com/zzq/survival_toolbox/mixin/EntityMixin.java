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

import java.util.List;

/**
 * Entity Mixin
 * <p>
 * 拦截 {@link Entity#setRemainingFireTicks} 方法，
 * 当实体穿戴的盔甲全部适应火焰时，阻止着火状态设置。
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

    /**
     * 次元袋永不消失：掉落超时（5 分钟）与整合包清道夫清理掉落物
     * 都走 discard → remove(DISCARDED)，这里统一拦截（玩家拾取流程放行）。
     */
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At("HEAD"), cancellable = true)
    private void zzq_keepPocketBag(Entity.RemovalReason reason, CallbackInfo ci) {
        if (!((Object) this instanceof net.minecraft.world.entity.item.ItemEntity ie)) return;
        if (!(ie instanceof com.zzq.survival_toolbox.util.PocketBagGuarded guarded)) return;
        if (guarded.zzq_isBeingPickedUp()) return; // 玩家正常拾取
        if (ie.getItem().is(com.zzq.survival_toolbox.registry.ModItems.POCKET_DIMENSION.get())) {
            ci.cancel();
        }
    }
}