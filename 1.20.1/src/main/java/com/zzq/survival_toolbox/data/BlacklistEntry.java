package com.zzq.survival_toolbox.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * 黑白名单条目
 * <p>
 * 表示一条实体记录，包含实体的 UUID、名称、类型 ID 和类别。
 * 支持两种匹配模式：
 * <ul>
 *   <li>{@link Mode#INDIVIDUAL}：匹配特定个体（通过 UUID）</li>
 *   <li>{@link Mode#TYPE}：匹配整个实体类型（通过类型 ID）</li>
 * </ul>
 * 支持三种动作：
 * <ul>
 *   <li>{@link Action#NONE}：无操作（默认）</li>
 *   <li>{@link Action#WHITELIST}：白名单（禁止伤害）</li>
 *   <li>{@link Action#BLACKLIST}：黑名单（允许伤害）</li>
 * </ul>
 * </p>
 */
public class BlacklistEntry {

    public enum Mode {
        INDIVIDUAL,
        TYPE
    }

    public enum Action {
        NONE,
        WHITELIST,
        BLACKLIST
    }

    public UUID uuid;
    public String name;
    public String typeId;
    public String category;
    public Mode mode;
    public Action action;

    public BlacklistEntry() {
        this.mode = Mode.INDIVIDUAL;
        this.action = Action.NONE;
    }

    public BlacklistEntry(Entity entity) {
        this();
        this.uuid = entity.getUUID();
        this.name = entity.getName().getString();
        this.typeId = EntityType.getKey(entity.getType()).toString();
        this.category = getCategory(entity);
    }

    private static String getCategory(Entity entity) {
        var cat = entity.getType().getCategory();
        if (cat == net.minecraft.world.entity.MobCategory.MONSTER) return "敌对";
        if (cat == net.minecraft.world.entity.MobCategory.CREATURE) return "被动";
        if (cat == net.minecraft.world.entity.MobCategory.AMBIENT) return "被动";
        if (cat == net.minecraft.world.entity.MobCategory.WATER_CREATURE) return "被动";
        if (cat == net.minecraft.world.entity.MobCategory.WATER_AMBIENT) return "被动";
        if (cat == net.minecraft.world.entity.MobCategory.MISC) return "中立";
        return "未知";
    }

    public boolean matches(Entity target) {
        if (target == null) return false;
        if (mode == Mode.INDIVIDUAL) {
            return target.getUUID().equals(this.uuid);
        } else {
            String targetType = EntityType.getKey(target.getType()).toString();
            return targetType.equals(this.typeId);
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("UUID", uuid);
        tag.putString("Name", name);
        tag.putString("TypeId", typeId);
        tag.putString("Category", category);
        tag.putString("Mode", mode.name());
        tag.putString("Action", action.name());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        this.uuid = tag.getUUID("UUID");
        this.name = tag.getString("Name");
        this.typeId = tag.getString("TypeId");
        this.category = tag.getString("Category");
        this.mode = Mode.valueOf(tag.getString("Mode"));
        this.action = Action.valueOf(tag.getString("Action"));
    }
}