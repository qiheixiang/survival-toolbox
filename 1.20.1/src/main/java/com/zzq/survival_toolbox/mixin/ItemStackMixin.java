package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * 兼容旧档：1.20.1 旧版镇魂灯掉落物/容器/背包里的物品，
     * {@code tag.BlockEntityTag} 里可能带着巨大的 LightBlocks 坐标列表
     * （范围大时超过网络 2MB NBT 上限，导致加载/同步时连接丢失）。
     * 物品从 NBT 加载时直接剔除 LightBlocks；放置回世界时
     * 镇魂灯 onLoad 会按 range 自动重新放置光方块，数据无损失。
     */
    @Inject(method = "of(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private static void stripLegacyLightBlocksFromTag(CompoundTag tag, CallbackInfoReturnable<ItemStack> cir) {
        if (tag == null) return;
        if (!tag.contains("tag", 10)) return;
        CompoundTag nested = tag.getCompound("tag");
        if (!nested.contains("BlockEntityTag", 10)) return;
        CompoundTag bet = nested.getCompound("BlockEntityTag");
        if (bet.contains("LightBlocks")) {
            bet.remove("LightBlocks");
        }
    }

    @Inject(method = "hurtAndBreak", at = @At("HEAD"), cancellable = true)
    private void onHurtAndBreak(int amount, LivingEntity user, Consumer<LivingEntity> onBroken, CallbackInfo ci) {
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
            onBroken.accept(user);
        } else {
            ci.cancel();
            setDamageValue(newDamage);
        }
    }
}