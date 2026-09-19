package com.zzq.survival_toolbox.client.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 与「一键背包整理Next（InventoryProfilesNext，IPN）」的兼容处理（1.20.1）
 * <p>
 * 需求：整理 mod 会自动给容器界面加自己的排序按钮，次元袋界面不需要它们
 * （袋子有自己的整理按钮）。IPN 提供了官方的"界面提示"机制，用它来告诉 IPN
 * <b>完全忽略次元袋界面</b>（不加按钮、快捷键排序也不会动这个界面的槽位）。
 * </p>
 * <p>
 * 做法（已核对 IPN 的 jar 与它生成的配置文件）：
 * IPN 会读 <code>config/inventoryprofilesnext/integrationHints/</code> 下的玩家提示文件，
 * 其中 <code>player-defined.json</code> 的格式是 <code>{ "全限定类名": { "ignore": true } }</code>。
 * 客户端初始化时的处理：
 * <ul>
 *   <li>没装 IPN → 什么都不做；</li>
 *   <li>装了 IPN → 读那份 JSON（没有就新建），补一条把本模组的界面标成 ignore，
 *       而且<b>只在缺少这个键时才写</b>（玩家自己改过就尊重玩家的设置）；</li>
 *   <li>文件里已经有别的界面的配置 → 原样保留，不会被本模组清掉。</li>
 * </ul>
 * 注意：IPN 一般在启动时读一次提示文件，所以第一次写入后建议重启客户端才生效；
 * 本模组这边，界面同时还有"清掉非本界面控件"的兜底，所以当次也不会看到它的按钮。
 * </p>
 */
public final class InventoryProfilesNextCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** IPN 的 modid */
    private static final String IPN_MODID = "inventoryprofilesnext";
    /** IPN 的玩家提示文件（相对 config/inventoryprofilesnext/integrationHints/） */
    private static final String HINTS_FILE = "player-defined.json";
    /** 次元袋界面（让 IPN 忽略的就是这个类） */
    private static final String POCKET_SCREEN = "com.zzq.survival_toolbox.screen.PocketDimensionScreen";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private InventoryProfilesNextCompat() {
    }

    /**
     * 若装了 IPN，就往它的玩家提示文件里补一条"忽略次元袋界面"。
     * <p>
     * 全部异常自己吞掉并记日志：这是"锦上添花"的兼容处理，绝不能因为它让游戏起不来。
     * </p>
     */
    public static void applyIgnoreHint() {
        try {
            if (!ModList.get().isLoaded(IPN_MODID)) return;
            Path dir = FMLPaths.CONFIGDIR.get().resolve(IPN_MODID).resolve("integrationHints");
            Path file = dir.resolve(HINTS_FILE);

            JsonObject root;
            if (Files.exists(file)) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonElement parsed = JsonParser.parseString(text);
                if (!parsed.isJsonObject()) {
                    LOGGER.warn("[zzq_survival_toolbox] {} 不是 JSON 对象，跳过 IPN 兼容写入（没动你的文件）", file);
                    return;
                }
                root = parsed.getAsJsonObject();
            } else {
                Files.createDirectories(dir);
                root = new JsonObject();
            }

            // 玩家自己给这个界面写过配置 → 尊重玩家的，不动
            if (root.has(POCKET_SCREEN) && root.get(POCKET_SCREEN).isJsonObject()
                    && root.getAsJsonObject(POCKET_SCREEN).has("ignore")) {
                return;
            }
            JsonObject entry = root.has(POCKET_SCREEN) && root.get(POCKET_SCREEN).isJsonObject()
                    ? root.getAsJsonObject(POCKET_SCREEN)
                    : new JsonObject();
            entry.addProperty("ignore", true);
            root.add(POCKET_SCREEN, entry);

            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            LOGGER.info("[zzq_survival_toolbox] 已让 {} 忽略次元袋界面（写入 {}；若当次没生效，重启客户端即可）",
                    IPN_MODID, file);
        } catch (Throwable t) {
            LOGGER.warn("[zzq_survival_toolbox] 写入 IPN 兼容提示失败（不影响游戏）", t);
        }
    }
}
