package com.zzq.survival_toolbox.listener;

import com.zzq.survival_toolbox.screen.DisassembleMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * 配方索引失效处理
 * <p>
 * 拆解台的全局配方索引是 static 缓存，只构建一次。数据包重载（F3+T）或
 * 切换世界后配方会变化，若不重置会沿用旧世界的缓存，导致显示错误配方。
 * 本类在 {@link TagsUpdatedEvent}（数据包/标签重载）与
 * {@link ServerStoppingEvent}（离开世界）时重置索引。
 * </p>
 */
@EventBusSubscriber(modid = "zzq_survival_toolbox")
public class RecipeIndexResetHandler {

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // 只在服务端加载数据包时重置；CLIENT_PACKET_RECEIVED 是客户端收到标签，
        // 单机时也会触发，会误重置客户端与服务端共享的 static 索引，导致构建被反复打断。
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        DisassembleMenu.resetRecipeIndex();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DisassembleMenu.resetRecipeIndex();
    }
}
