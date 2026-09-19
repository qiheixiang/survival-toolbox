package com.zzq.survival_toolbox.client.compat.jei;

import java.util.HashMap;
import java.util.Map;

/**
 * JEI"+"号可转移性的<b>客户端小缓存</b>（只在这个类里，纯客户端用）
 * <p>
 * <b>为什么要有它</b>（对应的历史问题："1.20.1 就算物品不足 + 号也能点"）：
 * 共享模式袋子的数据在服务器存档里，客户端看不见内容，只能"问服务端"。
 * 但那个来回是异步的：问一次要等几十毫秒才有答案，而 JEI 判定"能不能点"是<b>每帧/每次悬停</b>都在跑。
 * 于是这里缓存答案：同一件事（同一个签名）只问一次，之后直接用缓存里的答案。
 * </p>
 * <p>
 * <b>签名怎么来的</b>：客户端按"袋子身份 + 配方每一格的候选物品 + 输入格内容 + 页号"算一个 long
 * （见 {@code PocketBagRecipeTransferHandler#signatureOf}）。任何一样变了签名就变，缓存自然失效、
 * 重新去问服务端——所以"材料变了 / 换配方 / 翻页"都不会拿到旧答案。
 * </p>
 * <p>
 * <b>TTL（{@value #TTL_MS} 毫秒）</b>：袋子里有多少东西是会被玩家自己改的（往袋子里塞、
 * 从袋子取出、整理…），客户端不可能知道；靠 TTL 兜住这种"客户端没有观察到的变化"，
 * 过期就重新问一次。TTL 太长会拿着过期的"凑得齐"让玩家点了没反应，太短会一直发包刷服务器。
 * </p>
 * <p>
 * <b>"还没答案"也占一条缓存（PENDING）</b>：问出去之后到答案回来之前这段时间，
 * 缓存里存的是"问过了、还不知道"——既能避免每帧重复发包，也让判定方知道"这时候不能乐观放行"。
 * 它同样按 TTL 过期：万一同一个包丢了/服务端没回，过期后还能再问，不会永远卡在"不知道"。
 * </p>
 * <p>
 * ⚠️ 只在客户端加载（本类在 {@code client.compat.jei} 里，会被 JEI 的存在门住）。
 * 线程：全部读写都在客户端主线程（JEI 判定 + 包处理都走 {@code enqueueWork}），不需要加锁。
 * </p>
 */
public final class PocketJeiAvailabilityCache {

    /** 答案的有效期（毫秒） */
    private static final long TTL_MS = 750L;
    /** 缓存最多留多少条（防止长时间游玩后无限增长；超了先清最老的几条） */
    private static final int MAX_ENTRIES = 64;

    /** 还没拿到答案的标记（值用这个对象表示，null = 没问过） */
    private static final Boolean PENDING = null;

    /** 一条缓存：答案（null = 已发出询问、等回复）+ 写入时刻 */
    private static final class Entry {
        final Boolean answer;
        final long at;

        Entry(Boolean answer) {
            this.answer = answer;
            this.at = System.currentTimeMillis();
        }

        boolean fresh() {
            return System.currentTimeMillis() - this.at <= TTL_MS;
        }
    }

    private static final Map<Long, Entry> CACHE = new HashMap<>();

    /** 上一次真的把查询包发出去的时刻（限流用） */
    private static long lastQueryAt = 0L;
    /** 两次查询之间至少隔这么久（毫秒）：袋子实例一变签名就变，避免在极端情况下刷爆网络包 */
    private static final long QUERY_THROTTLE_MS = 100L;

    private PocketJeiAvailabilityCache() {
    }

    /** 现在能不能真的发一个查询包出去（限流；返回 false 时当作"还不知道"） */
    public static boolean canSendQuery() {
        long now = System.currentTimeMillis();
        if (now - lastQueryAt < QUERY_THROTTLE_MS) return false;
        lastQueryAt = now;
        return true;
    }

    /** 0 = 没答案（没问过 / 等回复中 / 已过期），1 = 凑得齐，-1 = 确实凑不齐 */
    public static final int UNKNOWN = 0;
    public static final int YES = 1;
    public static final int NO = -1;

    /**
     * 查一次：没问过或已过期就顺手记下"在问了"并返回 {@link #UNKNOWN}，调用方负责真的把包发出去。
     * <p>
     * ⚠️ 返回 {@link #UNKNOWN} 时调用方<b>不能</b>当成"可以点"（那正是"凑不齐 + 号也能点"的根源）。
     * </p>
     */
    public static int lookup(long signature) {
        Entry e = CACHE.get(signature);
        if (e == null) return UNKNOWN;
        if (!e.fresh()) {
            CACHE.remove(signature);
            return UNKNOWN;
        }
        if (e.answer == PENDING) return UNKNOWN;
        return e.answer ? YES : NO;
    }

    /** 记下"这个签名已经问过了"，在答案回来之前不再重复发包 */
    public static void markPending(long signature) {
        Entry e = CACHE.get(signature);
        if (e != null && e.fresh()) return;
        put(signature, PENDING);
    }

    /** 写入答案（服务端回复到达时调用；也会覆盖上面的"在问了"） */
    public static void put(long signature, boolean available) {
        CACHE.put(signature, new Entry(available));
        prune();
    }

    private static void put(long signature, Boolean pending) {
        CACHE.put(signature, new Entry(pending));
        prune();
    }

    /** 条数太多就整体清一遍（这只是加速用的缓存，清掉最多多问几次服务端） */
    private static void prune() {
        if (CACHE.size() <= MAX_ENTRIES) return;
        CACHE.entrySet().removeIf(en -> !en.getValue().fresh());
        if (CACHE.size() > MAX_ENTRIES) CACHE.clear();
    }

    /** 退出服务器/切换世界时清空（不同世界的袋子内容完全不同，留着只会误导） */
    public static void clear() {
        CACHE.clear();
    }
}
