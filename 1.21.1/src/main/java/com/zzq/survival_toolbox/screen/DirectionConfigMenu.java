package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.OmniHopperBlockEntity;
import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.util.DirectionConfig;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 万向漏斗方向配置菜单容器
 * <p>
 * 显示单个方向的过滤物品列表（27格）以及速度/模式控制按钮。
 * 按钮 ID：
 * <ul>
 *   <li>0：速度减</li>
 *   <li>1：速度加</li>
 *   <li>2：过滤模式切换</li>
 *   <li>3：方向模式切换</li>
 *   <li>6：返回主菜单</li>
 * </ul>
 * </p>
 */
public class DirectionConfigMenu extends AbstractContainerMenu {

    private final Direction direction;
    private final DirectionConfig config;
    private final ContainerLevelAccess access;
    private final OmniHopperBlockEntity blockEntity;
    private final Player player;
    /** 过滤名单格数（3×9） */
    private static final int FILTER_SLOTS = 27;
    private final ItemStackHandler filterHandler;

    public DirectionConfigMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, data.readEnum(Direction.class), getBlockEntity(inv, data));
    }

    private static OmniHopperBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf data) {
        Level level = inv.player.level();
        BlockEntity be = level.getBlockEntity(data.readBlockPos());
        if (be instanceof OmniHopperBlockEntity) return (OmniHopperBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public DirectionConfigMenu(int id, Inventory inv, Direction direction,
                               OmniHopperBlockEntity blockEntity) {
        super(ModMenus.DIRECTION_CONFIG.get(), id);
        this.direction = direction;
        this.blockEntity = blockEntity;
        this.config = blockEntity.getConfig(direction);
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.player = inv.player;

        // ---- 过滤物品 Handler，自动同步到 config ----
        this.filterHandler = new ItemStackHandler(config.getFilterItems().getSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                for (int i = 0; i < getSlots(); i++) {
                    config.getFilterItems().setStackInSlot(i, getStackInSlot(i));
                }

                boolean hasAnyItem = false;
                for (int i = 0; i < getSlots(); i++) {
                    if (!getStackInSlot(i).isEmpty()) {
                        hasAnyItem = true;
                        break;
                    }
                }
                if (!hasAnyItem) {
                    if (config.getFilterMode() == DirectionConfig.FilterMode.WHITELIST ||
                            config.getFilterMode() == DirectionConfig.FilterMode.BLACKLIST) {
                        config.setFilterMode(DirectionConfig.FilterMode.DISABLED);
                        blockEntity.setChanged();
                        if (!blockEntity.getLevel().isClientSide) {
                            blockEntity.getLevel().sendBlockUpdated(blockEntity.getBlockPos(),
                                    blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                        }
                    }
                }
                blockEntity.setChanged();
                if (!blockEntity.getLevel().isClientSide) {
                    blockEntity.getLevel().sendBlockUpdated(blockEntity.getBlockPos(),
                            blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            }
        };

        // 复制数据到 filterHandler
        for (int i = 0; i < config.getFilterItems().getSlots(); i++) {
            filterHandler.setStackInSlot(i, config.getFilterItems().getStackInSlot(i).copy());
        }

        // ---- 过滤物品格子（3行×9列） ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                // 幽灵条目格：实物不许进名单，条目也取不出来（放/删都在 clicked 里处理）
                this.addSlot(new SlotItemHandler(filterHandler, slotIndex, 7 + col * 18, 74 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }
                });
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

    public DirectionConfig getConfig() {
        return config;
    }

    public Direction getDirection() {
        return direction;
    }

    public OmniHopperBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case 0: // 速度减
                config.setSpeed(config.getSpeed() - 1);
                blockEntity.setChanged();
                if (!player.level().isClientSide) {
                    player.level().sendBlockUpdated(blockEntity.getBlockPos(),
                            blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
                return true;
            case 1: // 速度加
                config.setSpeed(config.getSpeed() + 1);
                blockEntity.setChanged();
                if (!player.level().isClientSide) {
                    player.level().sendBlockUpdated(blockEntity.getBlockPos(),
                            blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
                return true;
            case 2: // 过滤模式切换
                DirectionConfig.FilterMode current = config.getFilterMode();
                if (current == DirectionConfig.FilterMode.WHITELIST) {
                    config.setFilterMode(DirectionConfig.FilterMode.BLACKLIST);
                } else if (current == DirectionConfig.FilterMode.BLACKLIST) {
                    config.setFilterMode(DirectionConfig.FilterMode.DISABLED);
                } else {
                    config.setFilterMode(DirectionConfig.FilterMode.WHITELIST);
                }
                blockEntity.setChanged();
                if (!player.level().isClientSide) {
                    player.level().sendBlockUpdated(blockEntity.getBlockPos(),
                            blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
                return true;
            case 3: // 方向模式切换
                DirectionConfig.Mode mode = config.getMode();
                if (mode == DirectionConfig.Mode.IN) {
                    config.setMode(DirectionConfig.Mode.OUT);
                } else if (mode == DirectionConfig.Mode.OUT) {
                    config.setMode(DirectionConfig.Mode.BLOCKED);
                } else {
                    config.setMode(DirectionConfig.Mode.IN);
                }
                blockEntity.setChanged();
                if (!player.level().isClientSide) {
                    player.level().sendBlockUpdated(blockEntity.getBlockPos(),
                            blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
                return true;
            case 6: // 返回主菜单
                if (player instanceof ServerPlayer serverPlayer) {
                    for (int i = 0; i < filterHandler.getSlots(); i++) {
                        config.getFilterItems().setStackInSlot(i, filterHandler.getStackInSlot(i));
                    }
                    blockEntity.setChanged();
                    serverPlayer.openMenu(
                            new MenuProvider() {
                                @Override
                                public Component getDisplayName() {
                                    return Component.translatable("container.zzq_survival_toolbox.omni_hopper");
                                }

                                @Override
                                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                                    return new OmniHopperMenu(id, inv, blockEntity);
                                }
                            },
                            (buf) -> buf.writeBlockPos(blockEntity.getBlockPos())
                    );
                }
                return true;
            default:
                return super.clickMenuButton(player, id);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    /**
     * 过滤名单格 = 幽灵条目
     * <p>
     * 左键：把光标上那件东西的<b>信息</b>复制进这一格（数量恒为 1），<b>光标上的实物原样不动</b>；
     * 右键（或空手左键）：清掉这一格。
     * 名单里放的不是实物，所以也不存在"把名单里的东西取出来"这回事（格子 mayPickup 恒 false）。
     * </p>
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType,
                        Player player) {
        if (player.level().isClientSide) return;
        if (clickType == net.minecraft.world.inventory.ClickType.PICKUP
                && slotId >= 0 && slotId < FILTER_SLOTS) {
            ItemStack carried = getCarried();
            if (button == 1 || carried.isEmpty()) {
                this.filterHandler.setStackInSlot(slotId, ItemStack.EMPTY);
            } else {
                ItemStack ghost = carried.copy();
                ghost.setCount(1);
                this.filterHandler.setStackInSlot(slotId, ghost);
            }
            this.blockEntity.setChanged();
            return;   // 绝不走原版路径：实物既不进名单格、也不会被拿走
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide) {
            for (int i = 0; i < filterHandler.getSlots(); i++) {
                config.getFilterItems().setStackInSlot(i, filterHandler.getStackInSlot(i));
            }
            blockEntity.setChanged();
        }
        super.removed(player);
    }
}