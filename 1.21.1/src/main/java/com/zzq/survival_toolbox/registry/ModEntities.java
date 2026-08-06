package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.entity.AnvilOrbProjectile;
import com.zzq.survival_toolbox.entity.CapturedEntityProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组实体类型注册类
 * 注册铁砧球投射物和被捕获实体投射物
 */
public class ModEntities {

    /** 实体类型延迟注册器 */
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SurvivalToolbox.MODID);

    /** 铁砧球投射物实体（用于捕获生物） */
    public static final DeferredHolder<EntityType<?>, EntityType<AnvilOrbProjectile>> ANVIL_ORB =
            ENTITIES.register("anvil_orb",
                    () -> EntityType.Builder.<AnvilOrbProjectile>of(AnvilOrbProjectile::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("anvil_orb")
            );

    /** 被捕获实体投射物实体（用于释放被捕获的生物） */
    public static final DeferredHolder<EntityType<?>, EntityType<CapturedEntityProjectile>> CAPTURED_ENTITY_PROJECTILE =
            ENTITIES.register("captured_entity_projectile",
                    () -> EntityType.Builder.<CapturedEntityProjectile>of(CapturedEntityProjectile::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .build("captured_entity_projectile")
            );
}
