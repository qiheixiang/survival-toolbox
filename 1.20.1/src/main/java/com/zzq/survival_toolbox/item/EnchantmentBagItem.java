package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.registry.ModEnchantments;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;

/**
 * 附魔包物品
 * <p>
 * 右键使用后，同时生成一本嗜血附魔书和一本自适应附魔书，
 * 并放入玩家背包（若背包已满则掉落至地面）。
 * </p>
 */
public class EnchantmentBagItem extends Item {

    public EnchantmentBagItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            // 创建嗜血附魔书（1级）
            ItemStack bloodthirstyBook = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(ModEnchantments.BLOODTHIRSTY.get(), 1)
            );

            // 创建自适应附魔书（1级）
            ItemStack adaptationBook = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(ModEnchantments.ADAPTATION.get(), 1)
            );

            // 尝试放入玩家背包
            boolean addedBlood = player.getInventory().add(bloodthirstyBook);
            boolean addedAdapt = player.getInventory().add(adaptationBook);

            // 背包满则掉落至地面
            if (!addedBlood) {
                player.drop(bloodthirstyBook, false);
            }
            if (!addedAdapt) {
                player.drop(adaptationBook, false);
            }

            // 播放音效
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 消耗一个附魔包（非创造模式）
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}