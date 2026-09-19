package com.zzq.survival_toolbox.screen;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * 交易机的菜单：**直接继承原版 {@link MerchantMenu}**
 * <p>
 * 这样界面就是原版村民交易界面（列表 + 两个输入格 + 箭头 + 产物 + 物品栏），
 * 只有标题不同（标题来自 {@code openTradingScreen} 传进去的显示名）。
 * </p>
 * <p>
 * 客户端那份菜单需要一个 {@link Merchant}：原版村民是服务端发包把报价塞进菜单的
 * （{@code ClientboundMerchantOffersPacket} → {@code MerchantMenu#setOffers}），
 * 所以客户端只要一个**哑 merchant**（报价为空）就够了；它的 {@code getTradingPlayer()}
 * 必须返回客户端玩家，否则客户端校验不过会把界面关掉。
 * </p>
 */
public class TradeMachineMenu extends MerchantMenu {

    public TradeMachineMenu(int id, Inventory inv, Merchant merchant) {
        super(id, inv, merchant);
    }

    /** 客户端用的哑 merchant（报价为空，等原版报价包填进来） */
    public static class DummyMerchant implements Merchant {
        private Player tradingPlayer;
        private final MerchantOffers offers = new MerchantOffers();

        public DummyMerchant(Player player) {
            this.tradingPlayer = player;
        }

        @Override
        public void setTradingPlayer(Player player) {
            this.tradingPlayer = player;
        }

        @Override
        public Player getTradingPlayer() {
            return this.tradingPlayer;
        }

        @Override
        public MerchantOffers getOffers() {
            return this.offers;
        }

        @Override
        public void overrideOffers(MerchantOffers offers) {
            this.offers.clear();
            if (offers != null) this.offers.addAll(offers);
        }

        @Override
        public void overrideXp(int xp) {
        }

        @Override
        public int getVillagerXp() {
            return 0;
        }

        @Override
        public boolean showProgressBar() {
            return false;
        }

        @Override
        public boolean isClientSide() {
            return true;
        }

        @Override
        public boolean canRestock() {
            return false;
        }

        @Override
        public void notifyTrade(MerchantOffer offer) {
        }

        @Override
        public void notifyTradeUpdated(ItemStack stack) {
        }

        @Override
        public net.minecraft.sounds.SoundEvent getNotifyTradeSound() {
            return net.minecraft.sounds.SoundEvents.VILLAGER_YES;
        }
    }

    /** 兼容旧调用（旧的成交包还引用着它）：把手里那台交易机的报价重新推一遍 */
    public static void syncTo(net.minecraft.server.level.ServerPlayer player, ItemStack machine) {
        com.zzq.survival_toolbox.util.TradeMachineMerchant merchant =
                com.zzq.survival_toolbox.util.TradeMachineMerchant.get(player);
        if (merchant != null) merchant.resendOffers();
    }
}