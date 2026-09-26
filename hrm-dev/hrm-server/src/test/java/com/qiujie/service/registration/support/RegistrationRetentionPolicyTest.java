package com.qiujie.service.registration.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册数据留存策略单测（M-8，纯逻辑）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class RegistrationRetentionPolicyTest {

    @Test
    @DisplayName("超时判定：now 到达/晚于 expireTime → 失效；早于 → 未失效；expireTime 为空 → 未失效")
    void expiryJudgement() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 26, 10, 0);

        assertTrue(RegistrationRetentionPolicy.isExpired(now.minusSeconds(1), now));
        assertTrue(RegistrationRetentionPolicy.isExpired(now, now));
        assertFalse(RegistrationRetentionPolicy.isExpired(now.plusSeconds(1), now));
        assertFalse(RegistrationRetentionPolicy.isExpired(null, now));
    }

    @Test
    @DisplayName("清理截止：retention<=0 不清理（返回 null）；否则 now - retentionDays")
    void cleanupCutoff() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 26, 10, 0);

        assertEquals(now.minusDays(30), RegistrationRetentionPolicy.cleanupCutoff(now, 30));
        assertNull(RegistrationRetentionPolicy.cleanupCutoff(now, 0));
        assertNull(RegistrationRetentionPolicy.cleanupCutoff(now, -1));
    }
}
