package com.qiujie.service.auth.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二次验证票据到期判定单测（M4）：5 分钟 TTL 边界、空到期时刻、非法值。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class DeviceTicketTest {

    /** 票据 TTL 5 分钟（hrm.auth.device-ticket-ttl-seconds 默认 300） */
    private static final long TTL_MILLIS = 300_000L;

    @Test
    @DisplayName("票据到期边界：差 1ms 未到期；恰好到期即失效；已过期失效")
    void expiryBoundary() {
        long now = 1_700_000_000_000L;
        DeviceTicket ticket = new DeviceTicket();
        ticket.setExpireAtEpochMillis(now + TTL_MILLIS);

        assertFalse(ticket.isExpired(now + TTL_MILLIS - 1));
        assertTrue(ticket.isExpired(now + TTL_MILLIS));
        assertTrue(ticket.isExpired(now + TTL_MILLIS + 1));
    }

    @Test
    @DisplayName("到期时刻为空 → 一律视为已到期（防止脏数据长期可用）")
    void nullExpiryIsExpired() {
        DeviceTicket ticket = new DeviceTicket();
        assertTrue(ticket.isExpired(System.currentTimeMillis()));
    }
}
