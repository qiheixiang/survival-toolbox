package com.zzq.survival_toolbox.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 万向漏斗方向配置
 * <p>
 * 每个方向独立控制其物品传输行为。
 * </p>
 */
public class DirectionConfig {

    /**
     * 方向模式
     * <ul>
     *   <li>{@link Mode#IN}：从该方向拉取物品</li>
     *   <li>{@link Mode#OUT}：向该方向推送物品</li>
     *   <li>{@link Mode#BLOCKED}：禁止传输</li>
     * </ul>
     */
    public enum Mode {
        IN, OUT, BLOCKED
    }

    /**
     * 过滤模式
     * <ul>
     *   <li>{@link FilterMode#WHITELIST}：仅允许匹配的物品通过</li>
     *   <li>{@link FilterMode#BLACKLIST}：禁止匹配的物品通过</li>
     *   <li>{@link FilterMode#DISABLED}：禁用过滤（全部放行）</li>
     * </ul>
     */
    public enum FilterMode {
        WHITELIST, BLACKLIST, DISABLED
    }

    private Mode mode = Mode.BLOCKED;
    private int speed = 20;
    private FilterMode filterMode = FilterMode.DISABLED;
    private final ItemStackHandler filterItems = new ItemStackHandler(27);

    private int cooldown = 0;

    // ===== Getter / Setter =====

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = Math.max(1, Math.min(6000, speed));
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode mode) {
        this.filterMode = mode;
    }

    public ItemStackHandler getFilterItems() {
        return filterItems;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public void tickCooldown() {
        if (cooldown > 0) cooldown--;
    }

    public void resetCooldown() {
        cooldown = speed;
    }

    // ===== 持久化 =====

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Speed", speed);
        tag.putInt("FilterMode", filterMode.ordinal());
        tag.put("FilterItems", filterItems.serializeNBT());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        mode = Mode.values()[tag.getInt("Mode")];
        speed = tag.getInt("Speed");
        filterMode = FilterMode.values()[tag.getInt("FilterMode")];
        filterItems.deserializeNBT(tag.getCompound("FilterItems"));
    }

    // ===== 物品过滤判断 =====

    /**
     * 判断给定物品是否允许通过当前方向
     *
     * @param stack 物品栈
     * @return 是否允许通过
     */
    public boolean isItemAllowed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (filterMode == FilterMode.DISABLED) return true;

        boolean hasItems = false;
        for (int i = 0; i < filterItems.getSlots(); i++) {
            ItemStack filter = filterItems.getStackInSlot(i);
            if (!filter.isEmpty()) {
                hasItems = true;
                if (ItemStack.isSameItemSameTags(filter, stack)) {
                    return filterMode == FilterMode.WHITELIST;
                }
            }
        }
        // 过滤列表为空，不过滤
        if (!hasItems) return true;
        // 黑名单模式下，未匹配到的物品放行
        return filterMode == FilterMode.BLACKLIST;
    }
}