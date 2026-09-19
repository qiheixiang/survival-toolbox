package com.zzq.survival_toolbox.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 次元袋共享空间（末影箱式）
 * <p>
 * 数据存在世界存档里（主世界 DataStorage 的 {@code zzq_survival_toolbox_pocket_shared}），
 * 按玩家 UUID 分开保存，页面格式与袋子本地存储完全一致（复用
 * {@link PocketStorageHelper#readPages(CompoundTag)} / {@link PocketStorageHelper#writePagesTag}）。
 * </p>
 * <p>
 * 这样袋子只是个"入口"：袋子丢了/被销毁，东西仍在服务器存档里。
 * 每个玩家一份，别的玩家读不到，也不存在两人同时写同一份数据的并发问题
 * （一个玩家同时只能打开一个容器界面）。
 * </p>
 * <p>
 * 存档结构：{@code {Players: {"<uuid>": {Pages: [...]}}}}。
 * </p>
 */
public class PocketSharedStorage extends SavedData {

    private static final String DATA_NAME = "zzq_survival_toolbox_pocket_shared";
    private static final String TAG_PLAYERS = "Players";

    private final Map<UUID, CompoundTag> players = new HashMap<>();

    /** 取得本存档的共享空间数据（不存在则创建） */
    public static PocketSharedStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(PocketSharedStorage::load, PocketSharedStorage::new, DATA_NAME);
    }

    private static PocketSharedStorage load(CompoundTag tag) {
        PocketSharedStorage data = new PocketSharedStorage();
        CompoundTag players = tag.getCompound(TAG_PLAYERS);
        for (String key : players.getAllKeys()) {
            try {
                data.players.put(UUID.fromString(key), players.getCompound(key));
            } catch (IllegalArgumentException ignored) {
                // 非法 UUID 键（手改存档等）跳过，不让整份数据读不出来
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> entry : this.players.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue());
        }
        tag.put(TAG_PLAYERS, players);
        return tag;
    }

    /**
     * 取某玩家的共享页数据。
     *
     * @param player 玩家 UUID
     * @return 页列表；该玩家还没有数据时返回一个空的第 1 页
     */
    public List<PocketStorageHelper.Page> getPages(UUID player) {
        CompoundTag tag = this.players.get(player);
        List<PocketStorageHelper.Page> pages = tag == null ? new ArrayList<>() : PocketStorageHelper.readPages(tag);
        if (pages.isEmpty()) pages.add(new PocketStorageHelper.Page("1"));
        return pages;
    }

    /**
     * 写回某玩家的共享页数据。
     *
     * @param player 玩家 UUID
     * @param pages  页列表
     */
    public void setPages(UUID player, List<PocketStorageHelper.Page> pages) {
        // 注意：物品页与流体页存在同一份玩家数据里，这里只替换 Pages 键，别把 FluidPages 一起抹掉
        CompoundTag tag = this.players.get(player);
        CompoundTag root = tag == null ? new CompoundTag() : tag.copy();
        root.put("Pages", PocketStorageHelper.writePagesTag(pages).getList("Pages", 10));
        this.players.put(player, root);
        setDirty();
    }

    /** 取某玩家的共享流体页（没有则返回一个空的第 1 页） */
    public List<PocketStorageHelper.FluidPage> getFluidPages(UUID player) {
        CompoundTag tag = this.players.get(player);
        List<PocketStorageHelper.FluidPage> pages =
                tag == null ? new ArrayList<>() : PocketStorageHelper.readFluidPages(tag);
        if (pages.isEmpty()) pages.add(new PocketStorageHelper.FluidPage("1"));
        return pages;
    }

    /** 写回某玩家的共享流体页（与物品页写在同一份玩家数据里） */
    public void setFluidPages(UUID player, List<PocketStorageHelper.FluidPage> fluidPages) {
        CompoundTag tag = this.players.get(player);
        CompoundTag root = tag == null ? new CompoundTag() : tag.copy();
        root.put("FluidPages", PocketStorageHelper.writeFluidPagesTag(fluidPages).getList("FluidPages", 10));
        this.players.put(player, root);
        setDirty();
    }
}
