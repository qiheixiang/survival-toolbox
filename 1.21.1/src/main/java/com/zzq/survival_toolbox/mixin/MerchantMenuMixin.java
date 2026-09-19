package com.zzq.survival_toolbox.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MerchantMenu Mixin：商人不是实体时别去放那个交易音效（原版这里会强转 Entity 崩服务端）
 * <p>
 * 原版 {@code MerchantMenu#playTradeSound()} 里有一句 {@code Entity entity = (Entity) this.trader;}，
 * 只在"商人本身就是实体"（村民 / 流浪商人）时才成立。交易机的 merchant 是
 * {@code util.TradeMachineMerchant}（不是实体），而它的 {@code isClientSide()} 在服务端返回 false
 * （服务端要真交易），于是走到这句就直接 ClassCastException。
 * </p>
 * <p>
 * 触发点是 {@code quickMoveStack} 里 {@code index == 2} 那一支（**Shift+点击产物格**，
 * 也就是"一次成交并把产物收进背包"）：普通点击产物格走的是 {@code MerchantResultSlot#onTake}，
 * 不经过这里，所以之前一直没暴露。这里在真正会抛的那一句之前退出：
 * 商人不是实体就不放这个音效（普通村民界面完全不受影响）。
 * </p>
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    /** 原版菜单里正在做生意的那个商人（私有 final 字段，只能 @Shadow） */
    @Shadow
    @Final
    private Merchant trader;

    @Inject(method = "playTradeSound", at = @At("HEAD"), cancellable = true)
    private void zzq_skipSoundForNonEntityTrader(CallbackInfo ci) {
        if (!(this.trader instanceof Entity)) {
            ci.cancel();
        }
    }
}
