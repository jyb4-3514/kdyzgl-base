package com.qiujie.service.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话重认证窗口判定单测（M4）：3 天边界、空值、非法窗口、时钟回拨。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class SessionExpiryPolicyTest {

    /** 3 天 = 259200 秒（hrm.auth.periodic-reauth-seconds 默认值） */
    private static final long THREE_DAYS = 259200L;

    @Test
    @DisplayName("3 天边界：差 259199 秒未到期；恰好 259200 秒即到期；超过亦到期")
    void threeDayBoundary() {
        long auth = 1_000_000L;
        assertFalse(SessionExpiryPolicy.isWindowElapsed(auth, auth + THREE_DAYS - 1, THREE_DAYS));
        assertTrue(SessionExpiryPolicy.isWindowElapsed(auth, auth + THREE_DAYS, THREE_DAYS));
        assertTrue(SessionExpiryPolicy.isWindowElapsed(auth, auth + THREE_DAYS + 1, THREE_DAYS));
    }

    @Test
    @DisplayName("小时窗边界（59 分未到期 / 60 分即到期 / 61 分已到期）")
    void hourlyBoundary() {
        long auth = 500L;
        long oneHour = 3600L;
        assertFalse(SessionExpiryPolicy.isWindowElapsed(auth, auth + 59 * 60, oneHour));   // 59 分
        assertTrue(SessionExpiryPolicy.isWindowElapsed(auth, auth + 60 * 60, oneHour));    // 60 分
        assertTrue(SessionExpiryPolicy.isWindowElapsed(auth, auth + 61 * 60, oneHour));    // 61 分
    }

    @Test
    @DisplayName("空值/非法值：认证时刻 null 或窗口 <=0 一律判定未到期（保持改造前行为）")
    void nullAndInvalidInputs() {
        assertFalse(SessionExpiryPolicy.isWindowElapsed(null, 1_000_000L, THREE_DAYS));
        assertFalse(SessionExpiryPolicy.isWindowElapsed(1_000_000L, 1_000_000L, 0L));
        assertFalse(SessionExpiryPolicy.isWindowElapsed(1_000_000L, 1_000_000L, -1L));
    }

    @Test
    @DisplayName("时钟回拨：认证时刻晚于当前时刻（负差）不得判为到期")
    void negativeDifferenceIsNotExpired() {
        assertFalse(SessionExpiryPolicy.isWindowElapsed(2_000_000L, 1_000_000L, THREE_DAYS));
    }
}
