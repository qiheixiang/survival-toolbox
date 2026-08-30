package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory Mixin
 * <p>
 * 死亡掉落源头拦截：dropAll 里跳过次元袋（不生成掉落物），
 * 全部掉落结束后把袋子放回背包——袋子从始至终不离开背包，
 * 不依赖任何死亡掉落事件，整合包的其他死亡处理也不会把它带走。
 * </p>
 */
@Mixin(Inventory.class)
public class InventoryMixin {

    @Shadow
    private Player player;

    private ItemStack zzq_savedBag = null;

    @Redirect(method = "dropAll",
              at = @At(value = "INVOKE",
                      target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity zzq_skipPocketBag(Player player, ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        if (stack.is(ModItems.POCKET_DIMENSION.get())) {
            zzq_savedBag = stack.copy();
            return null; // 不掉落
        }
        return player.drop(stack, throwRandomly, retainOwnership);
    }

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void zzq_restorePocketBag(CallbackInfo ci) {
        if (zzq_savedBag == null) return;
        Inventory inv = (Inventory) (Object) this;
        if (!inv.add(zzq_savedBag)) {
            // 背包满的极端情况：掉到脚边，至少不消失
            inv.player.drop(zzq_savedBag, false, false);
        }
        zzq_savedBag = null;
    }
}
