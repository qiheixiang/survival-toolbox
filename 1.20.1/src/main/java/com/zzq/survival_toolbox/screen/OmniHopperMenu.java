package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.OmniHopperBlockEntity;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 万向漏斗主菜单容器
 * <p>
 * 显示 27 格缓存区。点击方向按钮可进入对应的方向配置子菜单。
 * 按钮 ID：0~5 分别对应上、下、前、后、左、右六个方向。
 * </p>
 */
public class OmniHopperMenu extends AbstractContainerMenu {

    private final OmniHopperBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public OmniHopperMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, getBlockEntity(inv, data));
    }

    private static OmniHopperBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf data) {
        Level level = inv.player.level();
        BlockEntity be = level.getBlockEntity(data.readBlockPos());
        if (be instanceof OmniHopperBlockEntity) return (OmniHopperBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public OmniHopperMenu(int id, Inventory inv, OmniHopperBlockEntity blockEntity) {
        super(ModMenus.OMNI_HOPPER.get(), id);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // ---- 缓存区（3×9） ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(blockEntity, row * 9 + col, 8 + col * 18, 77 + row * 18));
            }
        }

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    public OmniHopperBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * 根据显示索引和玩家放置朝向，计算对应的世界方向
     *
     * @param index         显示索引（0=上，1=下，2=前，3=后，4=左，5=右）
     * @param playerFacing  玩家放置时的朝向
     * @return 对应的世界方向
     */
    private Direction displayIndexToWorldDirection(int index, Direction playerFacing) {
        return switch (index) {
            case 0 -> Direction.UP;
            case 1 -> Direction.DOWN;
            case 2 -> playerFacing;
            case 3 -> playerFacing.getOpposite();
            case 4 -> playerFacing.getCounterClockWise();
            case 5 -> playerFacing.getClockWise();
            default -> Direction.NORTH;
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id <= 5) {
            Direction playerFacing = blockEntity.getPlayerFacing();
            if (playerFacing == null) playerFacing = Direction.NORTH;
            Direction worldDir = displayIndexToWorldDirection(id, playerFacing);

            if (player instanceof ServerPlayer serverPlayer) {
                if (blockEntity == null || blockEntity.isRemoved()) return true;

                NetworkHooks.openScreen(serverPlayer,
                        new MenuProvider() {
                            @Override
                            public Component getDisplayName() {
                                String key = switch (id) {
                                    case 0 -> "gui.zzq_survival_toolbox.omni_hopper.up";
                                    case 1 -> "gui.zzq_survival_toolbox.omni_hopper.down";
                                    case 2 -> "gui.zzq_survival_toolbox.omni_hopper.front";
                                    case 3 -> "gui.zzq_survival_toolbox.omni_hopper.back";
                                    case 4 -> "gui.zzq_survival_toolbox.omni_hopper.left";
                                    case 5 -> "gui.zzq_survival_toolbox.omni_hopper.right";
                                    default -> "gui.zzq_survival_toolbox.omni_hopper.up";
                                };
                                return Component.translatable("container.zzq_survival_toolbox.omni_hopper.direction",
                                        Component.translatable(key));
                            }

                            @Override
                            public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                                return new DirectionConfigMenu(windowId, inv, worldDir, blockEntity);
                            }
                        },
                        (buf) -> {
                            buf.writeEnum(worldDir);
                            buf.writeBlockPos(blockEntity.getBlockPos());
                        }
                );
                return true;
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // 缓存区槽位索引：0~26，玩家背包槽位：27~62
            if (index < 27) {
                if (!this.moveItemStackTo(slotStack, 27, 63, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, 27, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, slotStack);
        }

        return itemstack;
    }
}