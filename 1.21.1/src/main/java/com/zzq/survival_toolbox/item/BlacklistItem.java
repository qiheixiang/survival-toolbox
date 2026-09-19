package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.data.BlacklistEntry;
import com.zzq.survival_toolbox.network.SyncBlacklistPacket;
import com.zzq.survival_toolbox.screen.BlacklistMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 黑白名单物品
 * <p>
 * 功能：记录实体并控制伤害过滤。
 * 使用方式：
 * <ul>
 *   <li>普通右键实体：将该实体记录到名单中</li>
 *   <li>Shift+右键实体：打开黑白名单管理界面</li>
 * </ul>
 * 放在玩家物品栏任意位置即可对攻击生效（无需手持）。
 * </p>
 */
public class BlacklistItem extends Item {

    private static final String TAG_ENTRIES = "Entries";

    public BlacklistItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        // 阻断原交互，由事件监听处理
        return InteractionResult.CONSUME;
    }

    /**
     * 右键打开黑白名单管理界面
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.zzq_survival_toolbox.blacklist");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new BlacklistMenu(id, inv);
                }
            });
        }
        return InteractionResultHolder.consume(stack);
    }

    // ============================================================
    // 条目读写（服务端）
    // ============================================================

    public static List<BlacklistEntry> getEntries(ItemStack stack) {
        List<BlacklistEntry> list = new ArrayList<>();
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) return list;
        ListTag listTag = tag.getList(TAG_ENTRIES, 10);
        for (int i = 0; i < listTag.size(); i++) {
            BlacklistEntry entry = new BlacklistEntry();
            entry.deserializeNBT(listTag.getCompound(i));
            list.add(entry);
        }
        return list;
    }

    public static void setEntries(ItemStack stack, List<BlacklistEntry> entries) {
        ListTag listTag = new ListTag();
        for (BlacklistEntry entry : entries) {
            listTag.add(entry.serializeNBT());
        }
        ItemNbt.edit(stack, t -> t.put(TAG_ENTRIES, listTag));
    }

    // ----- 服务端操作 -----

    public static void addEntry(ItemStack stack, BlacklistEntry entry, Player player) {
        List<BlacklistEntry> list = getEntries(stack);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(entry.uuid)) {
                list.set(i, entry);
                setEntries(stack, list);
                sendSyncPacket(stack, player);
                return;
            }
        }
        list.add(entry);
        setEntries(stack, list);
        sendSyncPacket(stack, player);
    }

    public static void removeEntry(ItemStack stack, UUID uuid, Player player) {
        List<BlacklistEntry> list = getEntries(stack);
        list.removeIf(e -> e.uuid.equals(uuid));
        setEntries(stack, list);
        sendSyncPacket(stack, player);
    }

    public static void updateEntry(ItemStack stack, BlacklistEntry entry, Player player) {
        List<BlacklistEntry> list = getEntries(stack);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(entry.uuid)) {
                list.set(i, entry);
                setEntries(stack, list);
                sendSyncPacket(stack, player);
                return;
            }
        }
    }

    // ----- 客户端操作（自动发送同步包） -----

    public static void addEntryClient(ItemStack stack, BlacklistEntry entry) {
        List<BlacklistEntry> list = getEntries(stack);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(entry.uuid)) {
                list.set(i, entry);
                setEntries(stack, list);
                PacketDistributor.sendToServer(new SyncBlacklistPacket(stack));
                return;
            }
        }
        list.add(entry);
        setEntries(stack, list);
        PacketDistributor.sendToServer(new SyncBlacklistPacket(stack));
    }

    public static void removeEntryClient(ItemStack stack, UUID uuid) {
        List<BlacklistEntry> list = getEntries(stack);
        list.removeIf(e -> e.uuid.equals(uuid));
        setEntries(stack, list);
        PacketDistributor.sendToServer(new SyncBlacklistPacket(stack));
    }

    public static void updateEntryClient(ItemStack stack, BlacklistEntry entry) {
        List<BlacklistEntry> list = getEntries(stack);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid.equals(entry.uuid)) {
                list.set(i, entry);
                setEntries(stack, list);
                PacketDistributor.sendToServer(new SyncBlacklistPacket(stack));
                return;
            }
        }
    }

    public static void syncToServer(ItemStack stack) {
        PacketDistributor.sendToServer(new SyncBlacklistPacket(stack));
    }

    private static void sendSyncPacket(ItemStack stack, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SyncBlacklistPacket(stack));
        }
    }

    // ============================================================
    // 伤害检查
    // ============================================================

    /**
     * 判断是否允许对目标造成伤害
     * <p>
     * 规则：
     * <ul>
     *   <li>黑名单匹配 → 允许伤害</li>
     *   <li>白名单匹配 → 禁止伤害</li>
     *   <li>无条目或未匹配 → 放行</li>
     * </ul>
     * </p>
     *
     * @param stack  黑白名单物品栈
     * @param target 伤害目标
     * @return 是否允许造成伤害
     */
    public static boolean canHurt(ItemStack stack, net.minecraft.world.entity.Entity target) {
        List<BlacklistEntry> list = getEntries(stack);
        if (list.isEmpty()) return true;

        List<BlacklistEntry> blacklist = new ArrayList<>();
        List<BlacklistEntry> whitelist = new ArrayList<>();

        for (BlacklistEntry entry : list) {
            if (entry.action == BlacklistEntry.Action.BLACKLIST) {
                blacklist.add(entry);
            } else if (entry.action == BlacklistEntry.Action.WHITELIST) {
                whitelist.add(entry);
            }
        }

        // 黑名单优先：匹配则允许伤害
        if (!blacklist.isEmpty()) {
            for (BlacklistEntry entry : blacklist) {
                if (entry.matches(target)) return true;
            }
            return false;
        }

        // 白名单：匹配则禁止伤害
        if (!whitelist.isEmpty()) {
            for (BlacklistEntry entry : whitelist) {
                if (entry.matches(target)) return false;
            }
            return true;
        }

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zzq_survival_toolbox.blacklist.desc"));
        int count = getEntries(stack).size();
        tooltip.add(Component.translatable("item.zzq_survival_toolbox.blacklist.count", count));
    }
}