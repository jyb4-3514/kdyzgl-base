package com.qiujie.service.support;

import com.qiujie.dto.systemlog.ClientLogQuery;
import com.qiujie.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClientLogQueryValidator} 单元测试（level/source/时间格式校验，文案对齐 Mock）。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段跑</b>。
 */
class ClientLogQueryValidatorTest {

    @Test
    void validQueryPasses() {
        ClientLogQuery query = new ClientLogQuery();
        query.setLevel("ERROR");
        query.setSource("PC");
        query.setStartTime("2026-09-01 00:00:00");
        query.setEndTime("2026-09-30 23:59:59");
        assertDoesNotThrow(() -> ClientLogQueryValidator.validate(query));
    }

    @Test
    void blankQueryPasses() {
        assertDoesNotThrow(() -> ClientLogQueryValidator.validate(new ClientLogQuery()));
        assertDoesNotThrow(() -> ClientLogQueryValidator.validate(null));
    }

    @Test
    void invalidLevelRejected() {
        ClientLogQuery query = new ClientLogQuery();
        query.setLevel("TRACE");
        BusinessException e = assertThrows(BusinessException.class, () -> ClientLogQueryValidator.validate(query));
        assertEquals(400, e.getCode());
        assertEquals("level 取值非法", e.getMessage());
    }

    @Test
    void invalidSourceRejected() {
        ClientLogQuery query = new ClientLogQuery();
        query.setSource("WATCH");
        BusinessException e = assertThrows(BusinessException.class, () -> ClientLogQueryValidator.validate(query));
        assertEquals(400, e.getCode());
        assertEquals("source 取值非法", e.getMessage());
    }

    @Test
    void invalidStartTimeRejectedWithKeyInMessage() {
        ClientLogQuery query = new ClientLogQuery();
        query.setStartTime("2026/09/01");
        BusinessException e = assertThrows(BusinessException.class, () -> ClientLogQueryValidator.validate(query));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().startsWith("startTime 格式须为 YYYY-MM-DD"));
    }

    @Test
    void dateOnlyAndDateTimeBothAccepted() {
        ClientLogQuery query = new ClientLogQuery();
        query.setStartTime("2026-09-01");
        query.setEndTime("2026-09-30 23:59:59");
        assertDoesNotThrow(() -> ClientLogQueryValidator.validate(query));
    }
}
