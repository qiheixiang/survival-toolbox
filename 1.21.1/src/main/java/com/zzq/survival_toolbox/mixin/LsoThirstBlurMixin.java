package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.AdaptationHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自适应附魔：免疫"传奇生存改革（Legendary Survival Overhaul）"口渴带来的<b>视野模糊</b>
 * <p>
 * 症状：亡者世界里口渴会造成视野模糊，而自适应附魔无法免疫它。
 * 查过 LSO 的实现（反编译 <b>2.3.23</b>）后确认：这个模糊<b>不是药水效果</b>，而是它自己的客户端后处理：
 * </p>
 * <ul>
 *   <li>{@code RenderBlurOverlay#updateBlurIntensity(player)} 直接读
 *       {@code CapabilityUtil.getThirstCapability(player).getHydrationLevel()}：水分 &le; 6 时把
 *       {@code shaderIntensity} 拉起来；</li>
 *   <li>{@code render(player)} 再用 {@code FocusShader} 按这个强度糊屏幕。</li>
 * </ul>
 * <p>
 * 所以"取消药水效果"那套（{@code adapt_data.adapted_effects}）天生管不到它 —— 这里改成：
 * <b>按"口渴模糊适应进度"逐步把模糊强度压下去</b>：刚穿上自适应盔甲照糊，适应到一半糊一半，
 * 攒满适应时间（和火焰/夜视/迷雾同一个阈值，见 {@code AdaptationEventHandler}）才完全不糊。
 * 口渴条本身照旧，只是视野越来越清楚 —— 即"逐步适应"，不是一刀切抵消。
 * </p>
 * <p>
 * ⚠️ 客户端专属、<b>软依赖</b>：{@code require = 0} + {@code remap = false}，目标类不存在（没装 LSO 的整合包）
 * 时这条 Mixin 被安静跳过。强度字段走反射而不是 {@code @Shadow}：LSO 不在编译期 classpath 上，
 * {@code @Shadow} 会让 Mixin 注解处理器直接报"target could not be found"而编译失败
 * （只写 {@code @Inject} 时它只警告）。
 * </p>
 */
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.client.render.RenderBlurOverlay", remap = false)
public class LsoThirstBlurMixin {

    /** 反射拿到的 {@code shaderIntensity} 字段（拿不到就一直为 null，等于不做归零） */
    private static java.lang.reflect.Field zzq_intensityField;
    private static boolean zzq_fieldResolved = false;

    // ⚠️ 方法选择器只写**名字**（不写参数描述符）：目标类不在编译期 classpath 上时，
    //    Mixin 注解处理器对"带描述符的选择器"会直接报错（target could not be found），
    //    只写名字才会退化成警告、让构建过（和 Embeddium/Flywheel 那两条软依赖 Mixin 一个套路）。
    @Inject(method = "updateBlurIntensity",
            at = @At("RETURN"), require = 0)
    private static void zzq_thirstBlurByAdaptation(Player player, CallbackInfo ci) {
        if (player == null) return;
        // 按"适应进度"**逐步**减弱，不是有盔甲就一刀切归零（需求是逐步适应、不要直接抵消）：
        // 刚穿上照糊，适应到一半糊一半，攒满适应时间（与火焰/夜视/迷雾同一阈值）
        // 才完全不糊。进度由服务端在 AdaptationEventHandler 里按秒累计、写进盔甲 NBT，这里只读。
        float progress = AdaptationHelper.getThirstBlurAdaptProgress(
                player, AdaptationHelper.adaptThresholdTicks(player));
        if (progress <= 0.0F) return;              // 没盔甲 / 还没开始适应：一点儿都不动，照糊
        zzq_scaleIntensity(1.0F - Math.min(1.0F, progress));
    }

    /** 把 {@code shaderIntensity} 乘一个系数（字段走反射拿，见类注释） */
    private static void zzq_scaleIntensity(float factor) {
        if (!zzq_fieldResolved) {
            zzq_fieldResolved = true;
            try {
                zzq_intensityField = Class.forName("sfiomn.legendarysurvivaloverhaul.client.render.RenderBlurOverlay")
                        .getDeclaredField("shaderIntensity");
                zzq_intensityField.setAccessible(true);
            } catch (Throwable ignored) {
                zzq_intensityField = null;      // 字段名变了也不崩，只是不调强度
            }
        }
        if (zzq_intensityField == null) return;
        try {
            float now = zzq_intensityField.getFloat(null);
            zzq_intensityField.setFloat(null, Math.max(0.0F, now * factor));
        } catch (Throwable ignored) {
        }
    }
}
