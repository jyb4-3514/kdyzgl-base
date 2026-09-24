package com.qiujie.util;

/**
 * 数据范围收敛上下文（ThreadLocal，L1）。
 * <p>
 * {@code QueryDataScopeInterceptor} 在 preHandle 写入「非 ADMIN 的强制收敛 stationId」，请求结束清理；
 * 统一 Query 基类 {@code dto.support.StationScopedQuery} 读取本上下文，实现「一处收敛、各 Service 不重复写」。
 * <p>
 * 为什么用上下文而不是包装 {@code HttpServletRequest}：{@code HandlerInterceptor} 无法替换交给 handler 的 request 对象，
 * 包装需下降为 Servlet Filter 并调整过滤链顺序（侵入既有链路、影响面更大）；上下文 + 基类读取等价且无链路侵入。
 */
public final class DataScopeContext {

    /**
     * 「无归属」非 ADMIN 的收敛哨兵：驿站 id 恒为正，-1 永不命中任何数据。
     * 语义对齐架构 8-2 推荐项 B「收敛到无数据」（不改登录态，前端表现为空态）。
     */
    public static final long NO_DATA_STATION_ID = -1L;

    private static final ThreadLocal<Long> CONVERGED_STATION_ID = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /** 写入强制收敛值（仅非 ADMIN 调用） */
    public static void set(Long stationId) {
        CONVERGED_STATION_ID.set(stationId);
    }

    /** 读取强制收敛值；null 表示「不收敛」（ADMIN / 公开端点 / 未认证） */
    public static Long get() {
        return CONVERGED_STATION_ID.get();
    }

    /** 请求结束清理，防止线程复用串号 */
    public static void clear() {
        CONVERGED_STATION_ID.remove();
    }
}
