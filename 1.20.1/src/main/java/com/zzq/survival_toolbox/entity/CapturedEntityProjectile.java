package com.zzq.survival_toolbox.entity;

import com.zzq.survival_toolbox.item.CapturedEntityItem;
import com.zzq.survival_toolbox.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.NetworkHooks;

/**
 * 被捕获实体投射物实体
 * <p>
 * 由 {@link CapturedEntityItem} 右键投掷生成。
 * 命中目标位置后释放存储的实体到世界中。
 * </p>
 */
public class CapturedEntityProjectile extends ThrowableItemProjectile {

    private static final EntityDataAccessor<CompoundTag> DATA_CAPTURED_NBT =
            SynchedEntityData.defineId(CapturedEntityProjectile.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<String> DATA_ENTITY_TYPE_ID =
            SynchedEntityData.defineId(CapturedEntityProjectile.class, EntityDataSerializers.STRING);

    public CapturedEntityProjectile(EntityType<? extends CapturedEntityProjectile> type, Level level) {
        super(type, level);
    }

    public CapturedEntityProjectile(Level level, Player shooter) {
        super(ModEntities.CAPTURED_ENTITY_PROJECTILE.get(), shooter, level);
    }

    public void setCapturedData(ItemStack stack) {
        setCapturedData(
                CapturedEntityItem.getEntityNBT(stack),
                CapturedEntityItem.getEntityTypeId(stack)
        );
    }

    public void setCapturedData(CompoundTag nbt, String typeId) {
        entityData.set(DATA_CAPTURED_NBT, nbt);
        entityData.set(DATA_ENTITY_TYPE_ID, typeId);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CAPTURED_NBT, new CompoundTag());
        entityData.define(DATA_ENTITY_TYPE_ID, "");
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            CompoundTag capturedNBT = entityData.get(DATA_CAPTURED_NBT);
            String entityTypeId = entityData.get(DATA_ENTITY_TYPE_ID);
            if (capturedNBT.isEmpty() || entityTypeId.isEmpty()) return;

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(
                    ResourceLocation.parse(entityTypeId)
            );
            if (entityType == null) return;

            Entity entity = entityType.create(level());
            if (entity == null) return;

            entity.load(capturedNBT);
            entity.setPos(result.getLocation().x, result.getLocation().y, result.getLocation().z);
            if (entity instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }
            level().addFreshEntity(entity);
            this.discard();
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public CompoundTag getCapturedNBT() {
        return entityData.get(DATA_CAPTURED_NBT);
    }

    public String getEntityTypeId() {
        return entityData.get(DATA_ENTITY_TYPE_ID);
    }
}