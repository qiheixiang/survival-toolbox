package com.zzq.survival_toolbox.block.entity;

import com.zzq.survival_toolbox.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 拆解台方块实体
 * <p>
 * 存储 11 个槽位：
 * <ul>
 *   <li>0 = 左侧输入槽</li>
 *   <li>1-9 = 中间九宫格（配方材料展示区）</li>
 *   <li>10 = 右侧输出槽</li>
 * </ul>
 * 所有业务逻辑由 {@link com.zzq.survival_toolbox.screen.DisassembleMenu} 层处理，
 * 本类仅负责物品数据的存储、持久化和掉落。
 * </p>
 */
public class DisassembleBlockEntity extends BlockEntity {

    /** 总槽位数 */
    public static final int TOTAL_SLOTS = 11;

    /** 物品列表 */
    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);

    public DisassembleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISASSEMBLE.get(), pos, state);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * 方块被破坏时掉落所有存储物品
     *
     * @param level 世界
     * @param pos   方块位置
     */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                Vec3 offset = Vec3.atCenterOf(pos).add(0, 0.5, 0);
                ItemEntity entity = new ItemEntity(level, offset.x, offset.y, offset.z, stack);
                level.addFreshEntity(entity);
            }
        }
        items.clear();
    }

    // ============================================================
    // NBT 持久化
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }

    // ============================================================
    // 网络同步
    // ============================================================

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
}