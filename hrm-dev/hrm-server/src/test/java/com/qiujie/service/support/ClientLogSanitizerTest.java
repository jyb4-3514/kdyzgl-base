package com.qiujie.service.support;

import com.qiujie.dto.systemlog.ClientLogItem;
import com.qiujie.entity.ClientLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClientLogSanitizer} 单元测试（白名单过滤 / 文本截断 / 凭据擦除 / path 去 query / 指纹计算）。
 * <p>
 * 对应 S8 §10.4 单测边界清单 ⑥⑦⑩⑪⑫ 与架构 §6.2 P1 验收 ①②③。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段跑</b>；此处为可执行断言资产。
 */
class ClientLogSanitizerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 10, 0, 0);

    @Test
    void emptyItemFallsBackToDefaults() {
        ClientLog log = ClientLogSanitizer.sanitize(new ClientLogItem(), NOW, 2000);

        assertEquals(NOW, log.getTime(), "缺省时间取服务端当前时间");
        assertEquals("ERROR", log.getLevel());
        assertEquals("PC", log.getSource());
        assertEquals("未提供错误信息", log.getMessage());
        assertNull(log.getRoute());
        assertNull(log.getMethod());
        assertNull(log.getStack());
        assertNull(log.getStatus());
        assertNull(log.getCode());
        assertNull(log.getDuration());
        // 白名单：employeeId/count/first/last 由服务端填充，sanitize 不产出
        assertNull(log.getEmployeeId());
        assertNull(log.getCount());
    }

    @Test
    void illegalLevelAndSourceNormalized() {
        ClientLogItem item = new ClientLogItem();
        item.setLevel("TRACE");
        item.setSource("WATCH");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertEquals("ERROR", log.getLevel());
        assertEquals("PC", log.getSource());
    }

    @Test
    void messageTruncatedToTextMax() {
        ClientLogItem item = new ClientLogItem();
        item.setMessage("x".repeat(100));
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 10);
        assertEquals(10, log.getMessage().length());
    }

    @Test
    void credentialTokenScrubbed() {
        ClientLogItem item = new ClientLogItem();
        item.setMessage("request failed ?token=abc123&a=1");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertTrue(log.getMessage().contains("token=***"), "凭据值应被擦除");
        assertFalse(log.getMessage().contains("abc123"), "原凭据值不得残留");
        assertTrue(log.getMessage().contains("a=1"), "非凭据内容应保留");
    }

    @Test
    void credentialPasswordInStackScrubbed() {
        ClientLogItem item = new ClientLogItem();
        item.setStack("at Login(password=Secret!23)");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertTrue(log.getStack().contains("password=***"));
        assertFalse(log.getStack().contains("Secret!23"));
    }

    @Test
    void pathStripsQueryAndHash() {
        ClientLogItem item = new ClientLogItem();
        item.setPath("/api/v1/orders?token=abc#frag");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertEquals("/api/v1/orders", log.getPath());
    }

    @Test
    void pathTruncatedToColumnWidth() {
        ClientLogItem item = new ClientLogItem();
        item.setPath("/" + "a".repeat(800));
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertEquals(500, log.getPath().length());
    }

    @Test
    void methodUppercasedAndTruncated() {
        ClientLogItem item = new ClientLogItem();
        item.setMethod("postxyzabcde");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertEquals("POSTXYZABC", log.getMethod());
    }

    @Test
    void blankRouteNormalizedToNull() {
        ClientLogItem item = new ClientLogItem();
        item.setRoute("   ");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertNull(log.getRoute());
    }

    @Test
    void numericFieldsParsedLeniently() {
        ClientLogItem item = new ClientLogItem();
        item.setStatus("500");
        item.setCode("not-a-number");
        item.setDuration("12.0");
        ClientLog log = ClientLogSanitizer.sanitize(item, NOW, 2000);
        assertEquals(500, log.getStatus());
        assertNull(log.getCode(), "非数字业务码归一为 null，而非整批失败");
        assertEquals(12, log.getDuration());
    }

    @Test
    void fingerprintUsesMessageRouteCodeWithNullsAsEmpty() {
        ClientLog log = new ClientLog();
        log.setMessage("boom");
        log.setRoute(null);
        log.setCode(null);
        assertEquals("boom||", ClientLogSanitizer.fingerprint(log));

        log.setRoute("/a");
        log.setCode(500);
        assertEquals("boom|/a|500", ClientLogSanitizer.fingerprint(log));
    }

    @Test
    void parseLenientDateTimeVariants() {
        assertEquals(LocalDateTime.of(2026, 9, 24, 0, 0, 0),
                ClientLogSanitizer.parseLenientDateTime("2026-09-24", NOW));
        assertEquals(LocalDateTime.of(2026, 9, 24, 23, 59, 59),
                ClientLogSanitizer.parseLenientDateTime("2026-09-24 23:59:59", NOW));
        assertEquals(LocalDateTime.of(2026, 9, 24, 8, 0, 0),
                ClientLogSanitizer.parseLenientDateTime("2026-09-24T08:00:00", NOW));
        assertEquals(NOW, ClientLogSanitizer.parseLenientDateTime(null, NOW));
        assertEquals(NOW, ClientLogSanitizer.parseLenientDateTime("garbage", NOW));
    }
}
