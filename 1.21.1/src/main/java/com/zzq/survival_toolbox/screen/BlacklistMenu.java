package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 黑白名单菜单容器
 * <p>
 * 无实际槽位，仅用于展示黑白名单条目列表。
 * 所有操作由对应的 {@link BlacklistScreen} 层处理。
 * </p>
 */
public class BlacklistMenu extends AbstractContainerMenu {

    public BlacklistMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv);
    }

    public BlacklistMenu(int id, Inventory inv) {
        super(ModMenus.BLACKLIST.get(), id);
        // 无槽位
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }
}