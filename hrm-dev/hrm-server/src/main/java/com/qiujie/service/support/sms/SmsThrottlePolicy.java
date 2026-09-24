package com.qiujie.service.support.sms;

/**
 * 短信频控与验证码生命周期判定（纯逻辑，可离线单测）。
 * <p>
 * 只做「是否触发限制 / 是否过期」的判定，<b>不触碰 Redis</b>；计数与 TTL 由业务服务按配置写入 Redis
 * （架构 §4.4.2 三维度频控键 {@code hrm:sms:limit:*} 与 §4.4.3 验证码/尝试键）。
 * <p>
 * 方法语义统一为「返回 {@code true} 表示<b>应拦截</b>」：调用方据此返回 1101（频控）/1102（错误或过期）/1103（尝试超限）。
 * 默认值全部来自 {@code hrm.sms.*}（禁止硬编码，规则 §11.4）。
 * <p>
 * TODO(扩展): 令牌桶/滑动窗口的具体算法属算法工程师选型范畴（路由 R09）；本类仅提供阈值判定的最小实现，
 *   待算法出「四件套」后替换为 {@code hrm.algo.ratelimit} 参数化实现。
 */
public final class SmsThrottlePolicy {

    private SmsThrottlePolicy() {
    }

    /**
     * 是否处于同手机号最小发送间隔内（应拦截）。
     *
     * @param lastSentEpochSeconds 上次成功发送的时刻（秒；null/从未发送 = 不拦截）
     * @param nowEpochSeconds      当前服务端时刻（秒）
     * @param intervalSeconds      最小间隔（{@code hrm.sms.send-interval-seconds}，{@code <=0} 视为不限）
     */
    public static boolean withinSendInterval(Long lastSentEpochSeconds, long nowEpochSeconds, int intervalSeconds) {
        if (lastSentEpochSeconds == null || intervalSeconds <= 0) {
            return false;
        }
        return nowEpochSeconds - lastSentEpochSeconds < intervalSeconds;
    }

    /**
     * 是否已达当日发送上限（应拦截）。
     *
     * @param sentToday  当日已发送次数
     * @param dailyLimit 每日上限（{@code hrm.sms.daily-limit-per-phone}，{@code <=0} 视为不限）
     */
    public static boolean dailyLimitExceeded(int sentToday, int dailyLimit) {
        return dailyLimit > 0 && sentToday >= dailyLimit;
    }

    /**
     * 是否已达校验尝试上限（应拦截并作废验证码）。
     *
     * @param attempts    已失败次数
     * @param maxAttempts 尝试上限（{@code hrm.sms.max-verify-attempts}，{@code <=0} 视为不限）
     */
    public static boolean attemptsExceeded(int attempts, int maxAttempts) {
        return maxAttempts > 0 && attempts >= maxAttempts;
    }

    /**
     * 验证码是否已过期。
     *
     * @param createdAtEpochSeconds 验证码生成时刻（秒；null = 视为不存在/已过期）
     * @param nowEpochSeconds       当前服务端时刻（秒）
     * @param ttlSeconds            有效期（{@code hrm.sms.code-ttl-seconds}，{@code <=0} 视为立即过期）
     */
    public static boolean expired(Long createdAtEpochSeconds, long nowEpochSeconds, int ttlSeconds) {
        if (createdAtEpochSeconds == null) {
            return true;
        }
        if (ttlSeconds <= 0) {
            return true;
        }
        return nowEpochSeconds - createdAtEpochSeconds >= ttlSeconds;
    }
}
