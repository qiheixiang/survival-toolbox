package com.zzq.survival_toolbox.util;

/**
 * 次元袋拾取标记接口（ItemEntityMixin 实现，EntityMixin 读取）
 * <p>
 * 玩家拾取掉落物与掉落超时/清道夫清理都走 { discard() → remove(DISCARDED) }，
 * 无法从移除原因区分。玩家拾取流程（playerTouch）中标记为 true，
 * EntityMixin 拦截实体移除时据此放行拾取、拦截其他移除（袋永不消失）。
 * </p>
 */
public interface PocketBagGuarded {

    boolean zzq_isBeingPickedUp();
}
