package com.zzq.survival_toolbox.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 创造口袋（新物品：配方是 9 个拆解台）
 * <p>
 * 右键打开<b>创造模式物品栏</b>，而且<b>生存模式也能真正取出物品</b>——
 * 原版该界面只在创造模式可用（{@code CreativeModeInventoryScreen#init} 里
 * "不是创造 → 换成普通背包界面"），取物走的 {@code ServerboundSetCreativeModeSlotPacket}
 * 在服务端也被 {@code gameMode.isCreative()} 挡着。所以这里用两处 Mixin 把这一限制对"携带该物品的玩家"放开：
 * </p>
 * <ul>
 *   <li>客户端 {@code MultiPlayerGameModeMixin}：携带它时 {@code hasInfiniteItems()} 算 true
 *       （界面才不会被换成普通背包），并且取物/丢弃照常发包；</li>
 *   <li>服务端 {@code ServerGamePacketListenerImplMixin}：携带它时按原版同样的规则把物品放进背包
 *       （槽位 1..45，数量不超上限；槽位 &lt; 0 = 丢到脚下）。</li>
 * </ul>
 * <p>
 * <b>这是一个"作弊级"物品，效果等同于创造模式给予物品</b>：服务端只认"背包里真的携带它"，
 * 不认客户端宣称的内容，但只要携带它，就等于随身带有创造物品栏。这是有意设计的行为。
 * </p>
 */
public class CreativePickerItem extends Item {

    public CreativePickerItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // 只在客户端开界面；客户端专属类由 dist 判断兜住（专用服务器上这段永远不会执行）
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                com.zzq.survival_toolbox.client.ClientHooks.openCreativePicker();
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 按设计不显示任何提示：不加任何说明行（原先"作弊级物品"的提示行已移除）
    }
}
