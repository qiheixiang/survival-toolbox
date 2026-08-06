package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组方块实体注册类
 * 每个方块实体与其对应的方块绑定
 */
public class ModBlockEntities {

    /** 方块实体类型延迟注册器 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SurvivalToolbox.MODID);

    /** 拆解台方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisassembleBlockEntity>> DISASSEMBLE =
            BLOCK_ENTITIES.register("disassemble",
                    () -> BlockEntityType.Builder.of(
                            DisassembleBlockEntity::new,
                            ModBlocks.DISASSEMBLE_TABLE.get()
                    ).build(null));

    /** 微型牧场方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicroFarmBlockEntity>> MICRO_FARM =
            BLOCK_ENTITIES.register("micro_farm",
                    () -> BlockEntityType.Builder.of(MicroFarmBlockEntity::new,
                            ModBlocks.MICRO_FARM.get()).build(null));

    /** 万物转化炉方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmutationFurnaceBlockEntity>> TRANSMUTATION_FURNACE =
            BLOCK_ENTITIES.register("transmutation_furnace",
                    () -> BlockEntityType.Builder.of(TransmutationFurnaceBlockEntity::new,
                            ModBlocks.TRANSMUTATION_FURNACE.get()).build(null));

    /** 镇魂灯方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuardianLanternBlockEntity>> GUARDIAN_LANTERN =
            BLOCK_ENTITIES.register("guardian_lantern",
                    () -> BlockEntityType.Builder.of(GuardianLanternBlockEntity::new,
                            ModBlocks.GUARDIAN_LANTERN.get()).build(null));

    /** 智慧农场方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartFarmBlockEntity>> SMART_FARM =
            BLOCK_ENTITIES.register("smart_farm",
                    () -> BlockEntityType.Builder.of(SmartFarmBlockEntity::new,
                            ModBlocks.SMART_FARM.get()).build(null));

    /** 无限之源方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfiniteSourceBlockEntity>> INFINITE_SOURCE =
            BLOCK_ENTITIES.register("infinite_source",
                    () -> BlockEntityType.Builder.of(InfiniteSourceBlockEntity::new,
                            ModBlocks.INFINITE_SOURCE.get()).build(null));

    /** 万向漏斗方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniHopperBlockEntity>> OMNI_HOPPER =
            BLOCK_ENTITIES.register("omni_hopper",
                    () -> BlockEntityType.Builder.of(OmniHopperBlockEntity::new,
                            ModBlocks.OMNI_HOPPER.get()).build(null));

    /** 附魔数据交换台方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnchantmentTransferBlockEntity>> ENCHANTMENT_TRANSFER =
            BLOCK_ENTITIES.register("enchantment_transfer",
                    () -> BlockEntityType.Builder.of(EnchantmentTransferBlockEntity::new,
                            ModBlocks.ENCHANTMENT_TRANSFER.get()).build(null));

    /** 混沌篝火方块实体 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeastBlockEntity>> FEAST =
            BLOCK_ENTITIES.register("feast",
                    () -> BlockEntityType.Builder.of(FeastBlockEntity::new,
                            ModBlocks.FEAST.get()).build(null));

    /**
     * 空方法，用于触发类加载和注册
     */
    public static void register() {}
}
