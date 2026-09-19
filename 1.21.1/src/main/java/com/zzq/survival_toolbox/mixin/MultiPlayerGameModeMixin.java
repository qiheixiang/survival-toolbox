package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.PocketCreativeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端：带着"创造口袋"时，把创造物品栏那道"必须真是创造模式"的门开给他
 * <p>
 * 原版 {@code CreativeModeInventoryScreen#init()} 第一句就是
 * {@code if (!gameMode.hasInfiniteItems()) setScreen(new InventoryScreen(...))} ——
 * 生存模式打开创造界面会立刻被换成普通背包，等于点不开。取物本身走
 * {@code handleCreativeModeItemAdd}（发包给服务端），那里也有 {@code localPlayerMode.isCreative()} 挡着。
 * 所以这里：
 * </p>
 * <ul>
 *   <li>{@code hasInfiniteItems()}：带着口袋就按 true（只影响"界面能不能用/手部动画"这类判断，
 *       原版全代码里读它的地方只有创造界面和 Minecraft 那处右键动画）；</li>
 *   <li>取物 / 丢弃：带着口袋就照创造模式那样发 {@code ServerboundSetCreativeModeSlotPacket}
 *       （服务端那边由 {@code ServerGamePacketListenerImplMixin} 复核"是不是真的带着口袋"）。</li>
 * </ul>
 * 客户端专属（登记在 mixins.json 的 client 段），专用服务器永远不会加载它。
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Shadow
    private ClientPacketListener connection;

    @Inject(method = "hasInfiniteItems", at = @At("RETURN"), cancellable = true)
    private void zzq_creativePickerHasInfiniteItems(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;                 // 本来就是创造模式：原版结果
        // ⚠️⚠️ 只在"创造界面自己"这一侧放宽，**绝不能全局放宽**：
        //    InventoryScreen#init / containerTick 也会问这句话（"不是创造就换成普通背包"），
        //    全局放宽的后果就是"生存下按 E 直接跳进创造物品栏"（实测）。
        //    setScreen 是先赋 this.screen 再调 init()，所以创造界面 init 期间这句判定是成立的。
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null
                && mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                && PocketCreativeAccess.hasCreativePicker(mc.player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void zzq_creativePickerItemAdd(ItemStack stack, int slotId, CallbackInfo ci) {
        if (zzq_hasPicker() && !zzq_isCreative()) {
            // 照原版那句发出去：服务端认"带着口袋"就照创造模式的规则给物品
            this.connection.send(new ServerboundSetCreativeModeSlotPacket(slotId, stack));
            ci.cancel();
        }
    }

    @Inject(method = "handleCreativeModeItemDrop", at = @At("HEAD"), cancellable = true)
    private void zzq_creativePickerItemDrop(ItemStack stack, CallbackInfo ci) {
        if (zzq_hasPicker() && !zzq_isCreative() && !stack.isEmpty()) {
            this.connection.send(new ServerboundSetCreativeModeSlotPacket(-1, stack));
            ci.cancel();
        }
    }

    /** 玩家背包里有没有创造口袋（客户端本地判定，只用来决定"界面能不能用"） */
    private static boolean zzq_hasPicker() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && PocketCreativeAccess.hasCreativePicker(mc.player);
    }

    /** 本来就是创造模式的话，走原版那套，不要插手 */
    private static boolean zzq_isCreative() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.isCreative();
    }
}
