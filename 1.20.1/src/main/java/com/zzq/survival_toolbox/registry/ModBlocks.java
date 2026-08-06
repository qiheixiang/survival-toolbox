package com.zzq.survival_toolbox.registry;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.*;
import com.zzq.survival_toolbox.item.GuardianLanternBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 模组方块注册类
 * 所有方块及其对应的 BlockItem 在此统一注册
 * 特殊方块（如镇魂灯）使用自定义 BlockItem
 */
public class ModBlocks {

    /** 方块延迟注册器 */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SurvivalToolbox.MODID);

    // ============================================================
    // 方块注册
    // ============================================================

    /** 拆解台方块（用于逆向合成与酿造拆解） */
    public static final RegistryObject<Block> DISASSEMBLE_TABLE = registerBlock(
            "disassemble_table",
            () -> new DisassembleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    /** 微型牧场方块（使用被捕获实体自动生产掉落物） */
    public static final RegistryObject<Block> MICRO_FARM = registerBlock("micro_farm",
            () -> new MicroFarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .sound(SoundType.WOOD))
    );

    /** 万物转化炉方块（消耗燃料复制物品） */
    public static final RegistryObject<Block> TRANSMUTATION_FURNACE = registerBlock("transmutation_furnace",
            () -> new TransmutationFurnaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F, 4.0F)
                    .sound(SoundType.METAL))
    );

    /** 镇魂灯方块（照明、镇压怪物生成、自动攻击） */
    public static final RegistryObject<Block> GUARDIAN_LANTERN = registerBlock("guardian_lantern",
            () -> new GuardianLanternBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F, 3.0F)
                    .lightLevel(state -> state.getValue(GuardianLanternBlock.LIT) ? 15 : 0)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()),
            (block) -> new GuardianLanternBlockItem(block, new Item.Properties().stacksTo(1))
    );

    /** 智慧农场方块（全自动耕作、播种、收获、浇水） */
    public static final RegistryObject<Block> SMART_FARM = registerBlock("smart_farm",
            () -> new SmartFarmBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion())
    );

    /** 无限之源方块（无限流体供应源） */
    public static final RegistryObject<Block> INFINITE_SOURCE = registerBlock("infinite_source",
            () -> new InfiniteSourceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(0.5F, 3.0F)
                    .noOcclusion()
                    .sound(SoundType.STONE))
    );

    /** 万向漏斗方块（六向可配置物品传输） */
    public static final RegistryObject<Block> OMNI_HOPPER = registerBlock("omni_hopper",
            () -> new OmniHopperBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(0.5F, 3.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion())
    );

    /** 附魔数据交换台方块（在装备间互换嗜血/自适应数据） */
    public static final RegistryObject<Block> ENCHANTMENT_TRANSFER = registerBlock(
            "enchantment_transfer",
            () -> new EnchantmentTransferBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.5F, 3.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion())
    );

    /** 混沌篝火方块（对范围内生物施加食物/药水效果） */
    public static final RegistryObject<Block> FEAST = registerBlock(
            "feast",
            () -> new FeastBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> state.getValue(FeastBlock.LIT) ? 10 : 0)
                    .noOcclusion())
    );

    // ============================================================
    // 注册辅助方法
    // ============================================================

    /**
     * 标准注册：使用默认 BlockItem
     *
     * @param name  方块注册名
     * @param block 方块供应器
     * @param <T>   方块类型
     * @return 注册后的方块对象
     */
    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    /**
     * 高级注册：使用自定义 BlockItem（如镇魂灯）
     *
     * @param name       方块注册名
     * @param block      方块供应器
     * @param itemFactory 自定义 BlockItem 工厂函数
     * @param <T>        方块类型
     * @return 注册后的方块对象
     */
    private static <T extends Block> RegistryObject<T> registerBlock(
            String name,
            Supplier<T> block,
            Function<Block, Item> itemFactory
    ) {
        RegistryObject<T> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> itemFactory.apply(registered.get()));
        return registered;
    }

    /**
     * 空方法，用于触发类加载和注册
     */
    public static void register() {}
}