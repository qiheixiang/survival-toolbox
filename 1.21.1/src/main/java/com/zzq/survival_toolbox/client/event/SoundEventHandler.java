package com.zzq.survival_toolbox.client.event;

import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * 音效事件处理器
 * <p>
 * 静音镇魂灯自动攻击产生的攻击音效，避免听觉干扰。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox", value = Dist.CLIENT)
public class SoundEventHandler {

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) return;

        if (sound.getLocation().equals(SoundEvents.PLAYER_ATTACK_SWEEP.getLocation()) ||
                sound.getLocation().equals(SoundEvents.PLAYER_ATTACK_STRONG.getLocation()) ||
                sound.getLocation().equals(SoundEvents.PLAYER_ATTACK_NODAMAGE.getLocation()) ||
                sound.getLocation().equals(SoundEvents.PLAYER_ATTACK_WEAK.getLocation())) {
            event.setSound(null);
        }
    }
}