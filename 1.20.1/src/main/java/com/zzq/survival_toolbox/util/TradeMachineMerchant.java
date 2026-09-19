package com.zzq.survival_toolbox.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易机的"随身村民"：把交易机物品里记录的报价封装成原版 {@link Merchant}，
 * 于是界面就是**原版村民交易界面**（只换标题），成交/校验全走原版。
 * <p>
 * 另外提供搜索：{@link #setFilter} 按关键词重建报价列表并重发（原版报价包），
 * 静态 {@link #ACTIVE} 表记录"谁在看哪台机器"，搜索包靠它找到实例。
 * </p>
 */
public class TradeMachineMerchant implements Merchant {

    /** 正在看某台交易机的玩家 → 那台机器的 merchant（关界面时移除） */
    private static final Map<UUID, TradeMachineMerchant> ACTIVE = new ConcurrentHashMap<>();

    private final Player player;
    private final ItemStack machine;
    private final MerchantOffers offers = new MerchantOffers();
    /** 全部记录（未过滤，已按产物名排序） */
    private final List<TradeMachineData.Trade> source;
    /** 当前列表里每一条对应的记录（与 offers 一一对应） */
    private final List<TradeMachineData.Trade> builtFrom = new ArrayList<>();
    private String filter = "";
    private Player tradingPlayer;

    public TradeMachineMerchant(Player player, ItemStack machine) {
        this.player = player;
        this.machine = machine;
        this.source = new ArrayList<>(TradeMachineData.read(machine));
        this.source.sort(java.util.Comparator.comparing(t -> t.result.getHoverName().getString()));
        buildOffers();
    }

    public static TradeMachineMerchant get(Player player) {
        return player == null ? null : ACTIVE.get(player.getUUID());
    }

    // ---------- 报价列表（含搜索过滤） ----------

    private void buildOffers() {
        this.offers.clear();
        this.builtFrom.clear();
        for (TradeMachineData.Trade t : this.source) {
            if (t.result.isEmpty() || !matches(t)) continue;
            this.offers.add(new MerchantOffer(t.costA.copy(), t.costB.copy(), t.result.copy(), Math.max(0, t.uses), Integer.MAX_VALUE, 0, 0.0F));
            this.builtFrom.add(t);
        }
    }

    /** 关键词匹配：产物 / 两个输入 / 来源 NPC 名字（显示名、注册名、拼音都算） */
    private boolean matches(TradeMachineData.Trade t) {
        if (this.filter.isEmpty()) return true;
        String k = this.filter.toLowerCase(Locale.ROOT);
        return nameHit(t.result, k) || nameHit(t.costA, k) || nameHit(t.costB, k) || npcHit(t.npc, k);
    }

    /**
     * 单个物品的匹配：显示名 → 拼音 → 注册名。
     * <p>
     * 拼音那一步走 {@link PinyinUtil}（反射调 JustEnoughCharacters 的 PinIn）：
     * 装了这类拼音搜索 mod 就自动支持"全拼 / 声母 / 模糊音"，没装直接返回 false、回退普通匹配 ——
     * 和次元袋搜索、透视菜单搜索是同一套实现。
     * </p>
     */
    private static boolean nameHit(ItemStack stack, String k) {
        if (stack == null || stack.isEmpty()) return false;
        String display = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (display.contains(k)) return true;
        if (PinyinUtil.matches(display, k)) return true;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                .toString().toLowerCase(Locale.ROOT);
        return id.contains(k);
    }

    /** 来源 NPC 名字：同样支持拼音（"cunmin" 也能搜到"村民"） */
    private static boolean npcHit(String npc, String k) {
        if (npc == null) return false;
        String name = npc.toLowerCase(Locale.ROOT);
        return name.contains(k) || PinyinUtil.matches(name, k);
    }

    /** 服务端：设置搜索关键词并刷新列表（客户端搜索框输入变化时调用） */
    public void setFilter(String keyword) {
        String next = keyword == null ? "" : keyword.trim();
        if (next.equals(this.filter)) return;
        this.filter = next;
        buildOffers();
        resendOffers();
    }

    /** 把当前列表重新发给玩家（原版报价包，客户端列表会立刻刷新） */
    public void resendOffers() {
        if (this.tradingPlayer instanceof ServerPlayer sp
                && sp.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu) {
            sp.sendMerchantOffers(menu.containerId, this.offers, 0, 0, false, false);
        }
    }

    // ---------- Merchant 接口 ----------

    @Override
    public void setTradingPlayer(Player player) {
        this.tradingPlayer = player;
        if (player == null) {
            ACTIVE.values().remove(this);
        } else {
            ACTIVE.put(player.getUUID(), this);
        }
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
        return this.player != null && this.player.level().isClientSide;
    }

    @Override
    public boolean canRestock() {
        return false;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        if (this.player == null || this.player.level().isClientSide) return;
        int idx = this.offers.indexOf(offer);
        if (idx < 0 || idx >= this.builtFrom.size()) return;
        this.builtFrom.get(idx).uses = offer.getUses();
        TradeMachineData.write(this.machine, this.source);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public net.minecraft.sounds.SoundEvent getNotifyTradeSound() {
        return net.minecraft.sounds.SoundEvents.VILLAGER_YES;
    }

    /** 开原版村民界面（客户端拿到的就是 MenuType.MERCHANT → 原版 MerchantScreen），并给客户端打个标记 */
    @Override
    public void openTradingScreen(Player player, Component displayName, int level) {
        java.util.OptionalInt id = player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (i, inv, p) -> new com.zzq.survival_toolbox.screen.TradeMachineMenu(i, inv, this),
                displayName));
        if (id.isPresent() && player instanceof ServerPlayer sp) {
            sp.sendMerchantOffers(id.getAsInt(), this.offers, level, 0, false, false);
            // 标记包：告诉客户端"这个容器是交易机" → 客户端在原版界面上叠画搜索框。
            // 必须发在 openMenu 之后：客户端按到达顺序处理，先建界面再收标记。
            com.zzq.survival_toolbox.SurvivalToolbox.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                    new com.zzq.survival_toolbox.network.TradeMachineOpenPacket(id.getAsInt()));
        }
    }
}