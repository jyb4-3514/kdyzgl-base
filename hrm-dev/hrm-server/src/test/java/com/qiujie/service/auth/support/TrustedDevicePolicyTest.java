package com.qiujie.service.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受信设备可用性判定单测（M4）：受信/撤销/有效期边界、空值。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class TrustedDevicePolicyTest {

    private static final long NOW = 1_700_000_000L;
    /** 30 天有效期（hrm.auth.device-trust-ttl-seconds 默认值） */
    private static final long THIRTY_DAYS = 2_592_000L;

    @Test
    @DisplayName("受信且未撤销且在有效期内 → 可用")
    void usableWhenTrustedAndNotExpired() {
        assertTrue(TrustedDevicePolicy.isUsable(1, 0, NOW + THIRTY_DAYS, NOW));
    }

    @Test
    @DisplayName("有效期边界：差 1 秒有效；恰好到期即失效；已过期失效")
    void expiryBoundary() {
        assertTrue(TrustedDevicePolicy.isUsable(1, 0, NOW + 1, NOW));
        assertFalse(TrustedDevicePolicy.isUsable(1, 0, NOW, NOW));
        assertFalse(TrustedDevicePolicy.isUsable(1, 0, NOW - 1, NOW));
    }

    @Test
    @DisplayName("撤销（revoked=1）即失效，即使仍在有效期内")
    void revokedIsNotUsable() {
        assertFalse(TrustedDevicePolicy.isUsable(1, 1, NOW + THIRTY_DAYS, NOW));
    }

    @Test
    @DisplayName("未受信（trusted=0 / null）即失效")
    void notTrustedIsNotUsable() {
        assertFalse(TrustedDevicePolicy.isUsable(0, 0, NOW + THIRTY_DAYS, NOW));
        assertFalse(TrustedDevicePolicy.isUsable(null, 0, NOW + THIRTY_DAYS, NOW));
    }

    @Test
    @DisplayName("有效期为空（null）视为用配置周期兜底、当前有效；撤销位 null 视为未撤销")
    void nullFieldsAreLenient() {
        assertTrue(TrustedDevicePolicy.isUsable(1, null, null, NOW));
        assertFalse(TrustedDevicePolicy.isUsable(1, 1, null, NOW));
    }
}
