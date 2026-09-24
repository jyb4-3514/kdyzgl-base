package com.qiujie.service.parcel.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 游标编解码单测（算法 S7-3，keyset 分页键 {@code (inbound_time, id)}）。
 * <p>
 * 覆盖：往返一致、空白视为无游标、非法 Base64 / 结构错误 / 非法 id 均显式抛错。
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class ParcelCursorCodecTest {

    @Test
    @DisplayName("往返一致：encode → decode 得回原 (inbound_time, id)")
    void roundTrip() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 24, 10, 30, 0);
        String cursor = ParcelCursorCodec.encode(time, 123456L);
        ParcelCursorCodec.Cursor decoded = ParcelCursorCodec.decode(cursor);
        assertEquals(time, decoded.inboundTime());
        assertEquals(123456L, decoded.id());
    }

    @Test
    @DisplayName("空白游标视为无游标（返回 null = 首页），不抛错")
    void blankIsNoCursor() {
        assertNull(ParcelCursorCodec.decode(null));
        assertNull(ParcelCursorCodec.decode(""));
        assertNull(ParcelCursorCodec.decode("   "));
    }

    @Test
    @DisplayName("非法 Base64 → IllegalArgumentException")
    void invalidBase64() {
        assertThrows(IllegalArgumentException.class, () -> ParcelCursorCodec.decode("%%%not-base64%%%"));
    }

    @Test
    @DisplayName("Base64 合法但结构错误（无分隔/id 非数字）→ IllegalArgumentException")
    void invalidStructure() {
        String garbage = base64("garbage");
        assertThrows(IllegalArgumentException.class, () -> ParcelCursorCodec.decode(garbage));

        String badId = base64("2026-09-24 10:30:00|abc");
        assertThrows(IllegalArgumentException.class, () -> ParcelCursorCodec.decode(badId));
    }

    @Test
    @DisplayName("非法 id（≤0）→ IllegalArgumentException")
    void nonPositiveId() {
        assertThrows(IllegalArgumentException.class,
                () -> ParcelCursorCodec.decode(base64("2026-09-24 10:30:00|0")));
    }

    private static String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
