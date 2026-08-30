package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ItemEntity Mixin
 * <p>
 * 掉落物形态的次元袋对爆炸/火焰/岩浆等环境伤害免疫（受环境伤害不销毁），
 * 免疫时同时扑灭身上的火。玩家主动攻击仍可正常拾取（不影响原版交互）。
 * 掉出世界底部前传送到世界最高点，防止虚空销毁。
 * 防止"掉落时间过长消失"与清道夫类清理：原版 5 分钟超时与整合包
 * 清道夫清理掉落物都走 discard()，这里在非玩家拾取流程中拦截（拾取放行）。
 * </p>
 */
@Mixin(ItemEntity.class)
public class ItemEntityMixin implements com.zzq.survival_toolbox.util.PocketBagGuarded {

    /** 玩家拾取流程标记（拾取时 discard 放行，其他 discard 拦截） */
    private boolean zzq_pickupFlag = false;

    @Override
    public boolean zzq_isBeingPickedUp() {
        return this.zzq_pickupFlag;
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void zzq_protectPocketBag(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().is(ModItems.POCKET_DIMENSION.get())) {
            if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.ON_FIRE)
                    || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.LAVA)
                    || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.FALLING_BLOCK)
                    || source.is(DamageTypes.LIGHTNING_BOLT)) {
                self.clearFire();
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void zzq_saveFromVoid(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.getItem().is(ModItems.POCKET_DIMENSION.get())) return;
        // 原版在 y < minBuildHeight-64 时销毁掉落物；先一步传送到世界最高点
        if (self.getY() < self.level().getMinBuildHeight() - 64) {
            self.teleportTo(self.getX(), self.level().getMaxBuildHeight(), self.getZ());
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void zzq_pickupStart(Player player, CallbackInfo ci) {
        this.zzq_pickupFlag = true;
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void zzq_pickupEnd(Player player, CallbackInfo ci) {
        this.zzq_pickupFlag = false;
    }
}
