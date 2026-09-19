package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自适应附魔：免疫"传奇生存改革（Legendary Survival Overhaul）"口渴带来的<b>视野模糊</b>
 * <p>
 * 需求：亡者世界里口渴造成的视野模糊也要由自适应附魔免疫。
 * 查过 LSO 的实现（反编译 <b>2.3.23</b>）后确认：这个模糊<b>不是药水效果</b>，而是它自己的客户端后处理：
 * </p>
 * <ul>
 *   <li>{@code RenderBlurOverlay#updateBlurIntensity(player)} 直接读
 *       {@code CapabilityUtil.getThirstCapability(player).getHydrationLevel()}：水分 &le; 6 时把
 *       {@code shaderIntensity} 拉起来；</li>
 *   <li>{@code render(player)} 再用 {@code FocusShader} 按这个强度对屏幕做模糊处理。</li>
 * </ul>
 * <p>
 * 所以"取消药水效果"那套（{@code adapt_data.adapted_effects}）天生管不到它 —— 这里改成：
 * <b>按"口渴模糊适应进度"逐步把模糊强度压下去</b>：刚穿上自适应盔甲时仍然模糊，适应到一半则模糊减半，
 * 攒满适应时间（和火焰/夜视/迷雾同一个阈值，见 {@code AdaptationEventHandler}）才完全不模糊。
 * 口渴条本身照旧，只是视野越来越清楚 —— 即"逐步适应"，而非一刀切抵消。
 * </p>
 * <p>
 * ⚠️ 客户端专属、<b>软依赖</b>：
 * </p>
 * <ul>
 *   <li>{@code require = 0} + {@code remap = false}：没装 LSO 的整合包里这条 Mixin 被安静跳过；</li>
 *   <li>LSO 的 jar 放在仓库 {@code libs/} 里、只声明 {@code compileOnly}（照 Embeddium/Flywheel 的老规矩）：
 *       这样 mixin AP 能校验目标类与字段名，而且**不会**在别处缺这个 jar 时编译失败；
 *       运行时不依赖、不打包。</li>
 * </ul>
 */
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.client.render.RenderBlurOverlay", remap = false)
public class LsoThirstBlurMixin {

    /** LSO 的模糊强度：0 = 完全不糊 */
    @Shadow
    private static float shaderIntensity;

    @Inject(method = "updateBlurIntensity(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"), require = 0)
    private static void zzq_thirstBlurByAdaptation(Player player, CallbackInfo ci) {
        if (player == null) return;
        // 按"适应进度"**逐步**减弱，不是有盔甲就一刀切归零（即"逐步适应"而非直接抵消）：
        // 刚穿上照糊，适应到一半糊一半，攒满适应时间（与火焰/夜视/迷雾同一阈值）
        // 才完全不糊。进度由服务端在 AdaptationEventHandler 里按秒累计、写进盔甲 NBT，这里只读。
        float progress = AdaptationHelper.getThirstBlurAdaptProgress(
                player, AdaptationHelper.adaptThresholdTicks(player));
        if (progress <= 0.0F) return;
        shaderIntensity = Math.max(0.0F, shaderIntensity * (1.0F - Math.min(1.0F, progress)));
    }
}
