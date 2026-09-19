package com.zzq.survival_toolbox.item;

import com.zzq.survival_toolbox.util.ItemNbt;
import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.network.TerrainEditorOperationPacket;
import com.zzq.survival_toolbox.screen.TerrainEditorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 地形编辑器物品
 * <p>
 * 功能：在指定范围内批量执行破坏、替换或填充操作。
 * 支持自定义范围大小、偏移量、保护模式（禁止破坏基岩）、显示范围边框。
 * 交互方式：右键方块或空气执行操作，Shift+右键打开配置 GUI。
 * </p>
 */
public class TerrainEditorItem extends Item {

    private static final String KEY_RANGE_X = "rangeX";
    private static final String KEY_RANGE_Y = "rangeY";
    private static final String KEY_RANGE_Z = "rangeZ";
    private static final String KEY_OFFSET_X = "offsetX";
    private static final String KEY_OFFSET_Y = "offsetY";
    private static final String KEY_OFFSET_Z = "offsetZ";
    private static final String KEY_SHOW_RANGE = "showRange";
    private static final String KEY_BREAK_PROTECTED = "breakProtected";
    private static final String KEY_MODE = "mode";
    private static final String KEY_PLACE_BLOCK = "placeBlock";

    public TerrainEditorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ---------- NBT 读写 ----------

    public static int getRangeX(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_RANGE_X);
    }

    public static void setRangeX(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_RANGE_X, v));
    }

    public static int getRangeY(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_RANGE_Y);
    }

    public static void setRangeY(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_RANGE_Y, v));
    }

    public static int getRangeZ(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_RANGE_Z);
    }

    public static void setRangeZ(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_RANGE_Z, v));
    }

    public static int getOffsetX(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_OFFSET_X);
    }

    public static void setOffsetX(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_OFFSET_X, v));
    }

    public static int getOffsetY(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_OFFSET_Y);
    }

    public static void setOffsetY(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_OFFSET_Y, v));
    }

    public static int getOffsetZ(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_OFFSET_Z);
    }

    public static void setOffsetZ(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_OFFSET_Z, v));
    }

    public static boolean getShowRange(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getBoolean(KEY_SHOW_RANGE);
    }

    public static void setShowRange(ItemStack stack, boolean v) {
        ItemNbt.edit(stack, t -> t.putBoolean(KEY_SHOW_RANGE, v));
    }

    public static boolean getBreakProtected(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getBoolean(KEY_BREAK_PROTECTED);
    }

    public static void setBreakProtected(ItemStack stack, boolean v) {
        ItemNbt.edit(stack, t -> t.putBoolean(KEY_BREAK_PROTECTED, v));
    }

    public static int getMode(ItemStack stack) {
        return ItemNbt.getOrCreateTag(stack).getInt(KEY_MODE);
    }

    public static void setMode(ItemStack stack, int v) {
        ItemNbt.edit(stack, t -> t.putInt(KEY_MODE, v));
    }

    public static ItemStack getPlaceBlock(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = ItemNbt.getOrCreateTag(stack).getCompound(KEY_PLACE_BLOCK);
        if (tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.parseOptional(registries, tag);
    }

    public static void setPlaceBlock(ItemStack stack, ItemStack place, net.minecraft.core.HolderLookup.Provider registries) {
        if (place.isEmpty()) {
            ItemNbt.edit(stack, t -> t.remove(KEY_PLACE_BLOCK));
        } else {
            ItemNbt.edit(stack, t -> t.put(KEY_PLACE_BLOCK, place.save(registries, new CompoundTag())));
        }
    }

    public static void initDefaults(ItemStack stack) {
        setRangeX(stack, 1);
        setRangeY(stack, 1);
        setRangeZ(stack, 1);
        setOffsetX(stack, 0);
        setOffsetY(stack, 0);
        setOffsetZ(stack, 0);
        setShowRange(stack, true);
        setBreakProtected(stack, false);
        setMode(stack, 0);
        setPlaceBlock(stack, ItemStack.EMPTY, net.minecraft.core.RegistryAccess.EMPTY);
    }

    // ============================================================
    // 右键交互（支持方块和空气）
    // ============================================================

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();

        if (player.isCrouching()) {
            if (!level.isClientSide) openGui(player, stack);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 客户端：发送操作包（与 use 和渲染统一使用同一个目标计算逻辑）
        if (level.isClientSide) {
            BlockPos target = getTargetPosClient(player);
            PacketDistributor.sendToServer(new TerrainEditorOperationPacket(target, context.getHand()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isCrouching()) {
            if (!level.isClientSide) openGui(player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // 客户端：计算视线落点坐标，发送操作包
        if (level.isClientSide) {
            BlockPos target = getTargetPosClient(player);
            PacketDistributor.sendToServer(new TerrainEditorOperationPacket(target, hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 客户端计算目标坐标（与渲染器使用同一逻辑）：
     * <ul>
     *   <li>视线命中方块 → 返回被击中的方块本身（而非方块面上的点，避免取整偏 1 格）</li>
     *   <li>视线未命中（空气/远处）→ 返回 20 格极限处的方块</li>
     * </ul>
     */
    public static BlockPos getTargetPosClient(Player player) {
        HitResult hit = player.pick(20, 0, false);
        // 注意：MISS 类型的 HitResult 也是 BlockHitResult，必须用 getType() 判断
        if (hit.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hit).getBlockPos();
        }
        return BlockPos.containing(hit.getLocation());
    }

    private void openGui(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new net.minecraft.world.MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.translatable("container.zzq_survival_toolbox.terrain_editor");
                        }

                        @Override
                        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                                int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                            return new TerrainEditorMenu(id, inv, stack);
                        }
                    }
            );
        }
    }

    // ============================================================
    // 范围操作（静态方法，供网络包调用）
    // ============================================================

    public static void executeOperationStatic(Level level, BlockPos target,
                                              ItemStack stack, Player player) {
        TerrainEditorItem editor = (TerrainEditorItem) stack.getItem();
        editor.executeOperation(level, target, stack, player);
    }

    private void executeOperation(Level level, BlockPos target, ItemStack stack, Player player) {
        int rx = Math.min(getRangeX(stack), 256);
        int ry = Math.min(getRangeY(stack), 256);
        int rz = Math.min(getRangeZ(stack), 256);
        int ox = getOffsetX(stack);
        int oy = getOffsetY(stack);
        int oz = getOffsetZ(stack);
        boolean breakProtected = getBreakProtected(stack);
        int mode = getMode(stack);
        ItemStack placeBlock = getPlaceBlock(stack, level.registryAccess());

        BlockPos start = target.offset(ox, oy, oz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < rx; dx++) {
            for (int dy = 0; dy < ry; dy++) {
                for (int dz = 0; dz < rz; dz++) {
                    pos.set(start.getX() + dx, start.getY() + dy, start.getZ() + dz);
                    BlockState state = level.getBlockState(pos);

                    if (mode == 0) {
                        // 破坏模式
                        if (!breakProtected && state.getBlock() == Blocks.BEDROCK) continue;
                        if (state.getBlock() instanceof LightBlock) continue;
                        if (state.isAir()) continue;
                        if (!state.getFluidState().isEmpty()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        } else {
                            Block.popResource(level, pos, new ItemStack(state.getBlock().asItem()));
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    } else if (mode == 1 || mode == 2) {
                        // 替换/填充模式
                        if (mode == 2 && !state.isAir()) continue;
                        if (!placeBlock.isEmpty()) {
                            BlockState newState = getPlaceState(placeBlock);
                            if (newState != null) {
                                level.setBlock(pos, newState, 3);
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockState getPlaceState(ItemStack placeStack) {
        if (placeStack.isEmpty()) return null;
        Item item = placeStack.getItem();
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (item instanceof BucketItem bucketItem) {
            FluidState fluidState = bucketItem.content.defaultFluidState();
            return fluidState.createLegacyBlock();
        }
        return null;
    }

    // ---------- 辅助 ----------

    public static BlockPos getStart(ItemStack stack, BlockPos target) {
        return target.offset(getOffsetX(stack), getOffsetY(stack), getOffsetZ(stack));
    }

    public static AABB getBoundingBox(ItemStack stack, BlockPos target) {
        int rx = getRangeX(stack);
        int ry = getRangeY(stack);
        int rz = getRangeZ(stack);
        BlockPos start = getStart(stack, target);
        return new AABB(
                start.getX(), start.getY(), start.getZ(),
                start.getX() + rx, start.getY() + ry, start.getZ() + rz
        );
    }
}