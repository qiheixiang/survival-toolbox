package com.zzq.survival_toolbox.mixin;

import com.zzq.survival_toolbox.util.PocketCreativeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端：玩家带着"创造口袋"时，创造物品栏的取物请求照创造模式的规则执行
 * <p>
 * 原版 {@code handleSetCreativeModeSlot} 整段被 {@code if (player.gameMode.isCreative())} 包着，
 * 生存模式下什么都不做（客户端界面里拿起来的东西，下一次同步就消失了）。
 * 这里在方法开头补一段：<b>只有"背包里真的带着创造口袋"的玩家</b>才走这条路，
 * 规则与原版逐条一致（槽位 1..45、数量不超上限、带方块实体数据的物品要补数据、槽位 &lt; 0 丢到脚下）。
 * 别人（或没带口袋的）原样返回、执行原版逻辑 —— 也就是什么都不做。
 * </p>
 * <p>
 * ⚠️ 该功能的目标是"生存也能像创造一样取物"：判定只看服务端手里的背包，
 * 客户端说了不算；但带上了就等于随身创造物品栏（作弊级，和创造模式给东西是一回事）。
 * </p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void zzq_creativePickerSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (this.player == null || this.player.gameMode.isCreative()) {
            return;                       // 本来就是创造模式：原版那套照旧
        }
        if (!PocketCreativeAccess.hasCreativePicker(this.player)) {
            return;                       // 没带创造口袋：原版逻辑（什么都不做）
        }

        boolean drop = packet.slotNum() < 0;
        ItemStack stack = packet.itemStack();
        if (!stack.isItemEnabled(this.player.level().enabledFeatures())) {
            ci.cancel();
            return;
        }
        // 照原版：带方块实体数据的物品（箱子/熔炉之类）先让方块实体把数据写回物品
        net.minecraft.world.item.component.CustomData customData =
                stack.getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY);
        if (customData.contains("x") && customData.contains("y") && customData.contains("z")) {
            BlockPos pos = BlockEntity.getPosFromTag(customData.copyTag());
            if (this.player.level().isLoaded(pos)) {
                BlockEntity be = this.player.level().getBlockEntity(pos);
                if (be != null) {
                    be.saveToItem(stack, this.player.level().registryAccess());
                }
            }
        }
        boolean slotOk = packet.slotNum() >= 1 && packet.slotNum() <= 45;
        boolean countOk = stack.isEmpty() || (stack.getCount() >= 0 && stack.getCount() <= stack.getMaxStackSize());
        if (slotOk && countOk) {
            this.player.inventoryMenu.getSlot(packet.slotNum()).setByPlayer(stack);
            this.player.inventoryMenu.broadcastChanges();
        } else if (drop && countOk) {
            this.player.drop(stack, true);
        }
        ci.cancel();
    }
}
