package com.qiujie.service.registration.support;

import java.time.LocalDateTime;

/**
 * 注册数据留存策略（M-8，纯逻辑，便于离线单测）。
 * <p>
 * 两件事分离：① 未过审申请的「超时失效」判定（惰性 + 清理任务共用）；
 * ② 终态数据的「留存到期」判定（超过保留期即清理）。策略与时长参数解耦，参数由配置外置。
 */
public final class RegistrationRetentionPolicy {

    private RegistrationRetentionPolicy() {
    }

    /**
     * 是否已超时失效：{@code expireTime} 非空、{@code now} 到达/晚于其值。
     * <p>仅当申请仍处于 SUBMITTED 时由调用方结合状态使用（本方法只看时间）。
     */
    public static boolean isExpired(LocalDateTime expireTime, LocalDateTime now) {
        return expireTime != null && now != null && !now.isBefore(expireTime);
    }

    /**
     * 清理截止时间：早于该时间的终态数据视为超过留存期（{@code retentionDays<=0} 时不清理，返回 null）。
     */
    public static LocalDateTime cleanupCutoff(LocalDateTime now, int retentionDays) {
        if (retentionDays <= 0 || now == null) {
            return null;
        }
        return now.minusDays(retentionDays);
    }
}
