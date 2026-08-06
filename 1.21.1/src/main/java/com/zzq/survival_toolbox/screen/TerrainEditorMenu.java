package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.item.TerrainEditorItem;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 地形编辑器菜单容器
 * <p>
 * 配置项：
 * <ul>
 *   <li>范围 X/Y/Z</li>
 *   <li>偏移量 X/Y/Z</li>
 *   <li>显示范围开关</li>
 *   <li>保护模式开关</li>
 *   <li>模式切换（破坏/替换/填充）</li>
 * </ul>
 * 含 1 个放置方块槽位，用于替换/填充模式。
 * 按钮 ID：0~11 控制范围/偏移加减，12=显示范围切换，13=保护切换，14=模式切换。
 * </p>
 */
public class TerrainEditorMenu extends AbstractContainerMenu {

    private final ItemStack editorStack;
    private final Player player;
    private final ItemStackHandler customInventory;

    private final DataSlot rangeX = DataSlot.standalone();
    private final DataSlot rangeY = DataSlot.standalone();
    private final DataSlot rangeZ = DataSlot.standalone();
    private final DataSlot offsetX = DataSlot.standalone();
    private final DataSlot offsetY = DataSlot.standalone();
    private final DataSlot offsetZ = DataSlot.standalone();
    private final DataSlot showRange = DataSlot.standalone();
    private final DataSlot breakProtected = DataSlot.standalone();
    private final DataSlot mode = DataSlot.standalone();

    public TerrainEditorMenu(int id, Inventory inv, ItemStack stack) {
        super(ModMenus.TERRAIN_EDITOR.get(), id);
        this.editorStack = stack;
        this.player = inv.player;

        // ---- 初始化 DataSlot ----
        rangeX.set(TerrainEditorItem.getRangeX(editorStack));
        rangeY.set(TerrainEditorItem.getRangeY(editorStack));
        rangeZ.set(TerrainEditorItem.getRangeZ(editorStack));
        offsetX.set(TerrainEditorItem.getOffsetX(editorStack));
        offsetY.set(TerrainEditorItem.getOffsetY(editorStack));
        offsetZ.set(TerrainEditorItem.getOffsetZ(editorStack));
        showRange.set(TerrainEditorItem.getShowRange(editorStack) ? 1 : 0);
        breakProtected.set(TerrainEditorItem.getBreakProtected(editorStack) ? 1 : 0);
        mode.set(TerrainEditorItem.getMode(editorStack));

        addDataSlot(rangeX);
        addDataSlot(rangeY);
        addDataSlot(rangeZ);
        addDataSlot(offsetX);
        addDataSlot(offsetY);
        addDataSlot(offsetZ);
        addDataSlot(showRange);
        addDataSlot(breakProtected);
        addDataSlot(mode);

        // ---- 放置方块槽位 ----
        this.customInventory = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                ItemStack stack = getStackInSlot(slot);
                TerrainEditorItem.setPlaceBlock(editorStack, stack, inv.player.level().registryAccess());
                if (!stack.isEmpty()) {
                    if (mode.get() == 0) {
                        mode.set(1);
                        TerrainEditorItem.setMode(editorStack, 1);
                    }
                } else {
                    mode.set(0);
                    TerrainEditorItem.setMode(editorStack, 0);
                }
            }
        };

        ItemStack place = TerrainEditorItem.getPlaceBlock(editorStack, inv.player.level().registryAccess());
        if (!place.isEmpty()) {
            customInventory.setStackInSlot(0, place);
        }

        this.addSlot(new SlotItemHandler(customInventory, 0, 190, 171) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem;
            }
        });

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

    public ItemStack getEditorStack() {
        return editorStack;
    }

    public int getRangeX() {
        return rangeX.get();
    }

    public int getRangeY() {
        return rangeY.get();
    }

    public int getRangeZ() {
        return rangeZ.get();
    }

    public int getOffsetX() {
        return offsetX.get();
    }

    public int getOffsetY() {
        return offsetY.get();
    }

    public int getOffsetZ() {
        return offsetZ.get();
    }

    public boolean getShowRange() {
        return showRange.get() == 1;
    }

    public boolean getBreakProtected() {
        return breakProtected.get() == 1;
    }

    public int getMode() {
        return mode.get();
    }

    public ItemStack getPlaceBlock() {
        return customInventory.getStackInSlot(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        boolean changed = false;
        int delta = (id % 2 == 0) ? -1 : 1;
        int dim = id / 2;

        // ---- 范围控制（ID 0~5） ----
        if (dim >= 0 && dim < 3) {
            int current = switch (dim) {
                case 0 -> rangeX.get();
                case 1 -> rangeY.get();
                default -> rangeZ.get();
            };
            int newVal = Math.max(1, Math.min(256, current + delta));
            switch (dim) {
                case 0 -> {
                    rangeX.set(newVal);
                    TerrainEditorItem.setRangeX(editorStack, newVal);
                }
                case 1 -> {
                    rangeY.set(newVal);
                    TerrainEditorItem.setRangeY(editorStack, newVal);
                }
                case 2 -> {
                    rangeZ.set(newVal);
                    TerrainEditorItem.setRangeZ(editorStack, newVal);
                }
            }
            changed = true;
        }

        // ---- 偏移控制（ID 6~11） ----
        dim = (id - 6) / 2;
        if (dim >= 0 && dim < 3 && (id >= 6 && id < 12)) {
            int current = switch (dim) {
                case 0 -> offsetX.get();
                case 1 -> offsetY.get();
                default -> offsetZ.get();
            };
            int newVal = Math.max(-256, Math.min(256, current + delta));
            switch (dim) {
                case 0 -> {
                    offsetX.set(newVal);
                    TerrainEditorItem.setOffsetX(editorStack, newVal);
                }
                case 1 -> {
                    offsetY.set(newVal);
                    TerrainEditorItem.setOffsetY(editorStack, newVal);
                }
                case 2 -> {
                    offsetZ.set(newVal);
                    TerrainEditorItem.setOffsetZ(editorStack, newVal);
                }
            }
            changed = true;
        }

        // ---- 显示范围切换 ----
        if (id == 12) {
            boolean newVal = !(showRange.get() == 1);
            showRange.set(newVal ? 1 : 0);
            TerrainEditorItem.setShowRange(editorStack, newVal);
            changed = true;
        }

        // ---- 保护模式切换 ----
        if (id == 13) {
            boolean newVal = !(breakProtected.get() == 1);
            breakProtected.set(newVal ? 1 : 0);
            TerrainEditorItem.setBreakProtected(editorStack, newVal);
            changed = true;
        }

        // ---- 模式切换 ----
        if (id == 14) {
            int current = mode.get();
            int newVal = (current + 1) % 3;
            if (getPlaceBlock().isEmpty() && newVal != 0) return true;
            mode.set(newVal);
            TerrainEditorItem.setMode(editorStack, newVal);
            changed = true;
        }

        return changed || super.clickMenuButton(player, id);
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