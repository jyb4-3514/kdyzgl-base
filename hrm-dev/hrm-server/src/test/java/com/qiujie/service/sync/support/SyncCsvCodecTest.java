package com.qiujie.service.sync.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 文本编解码单测（覆盖：转义 / BOM / CRLF·LF 混用 / 末尾空行剔除 / 序列化-解析往返一致）。
 * <p>
 * <b>本机无 JDK/Maven，无法执行，收敛到服务器阶段运行</b>。
 */
class SyncCsvCodecTest {

    @Test
    @DisplayName("转义：含逗号/引号/换行的单元格用双引号包裹且内部引号翻倍")
    void escape() {
        assertEquals("abc", SyncCsvCodec.escape("abc"));
        assertEquals("\"a,b\"", SyncCsvCodec.escape("a,b"));
        assertEquals("\"a\"\"b\"", SyncCsvCodec.escape("a\"b"));
        assertEquals("\"a\nb\"", SyncCsvCodec.escape("a\nb"));
        assertEquals("", SyncCsvCodec.escape(null));
    }

    @Test
    @DisplayName("解析：忽略 BOM、兼容 CRLF/LF、还原转义、剔除末尾空行")
    void parseCompat() {
        String csv = "\uFEFFa,b\r\n\"x,y\",\"p\"\"q\"\nz\n\n";
        List<List<String>> rows = SyncCsvCodec.parse(csv);
        assertEquals(3, rows.size());
        assertEquals(List.of("a", "b"), rows.get(0));
        assertEquals(List.of("x,y", "p\"q"), rows.get(1));
        assertEquals(List.of("z"), rows.get(2));
    }

    @Test
    @DisplayName("序列化：BOM 开头 + CRLF 分隔")
    void serialize() {
        List<List<String>> rows = List.of(List.of("a", "b"), List.of("c", "d"));
        String text = SyncCsvCodec.serialize(rows);
        assertTrue(text.startsWith("\uFEFF"));
        assertEquals("\uFEFFa,b\r\nc,d", text);
    }

    @Test
    @DisplayName("往返一致：parse(serialize(rows)) == rows（含转义单元格）")
    void roundTripStable() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("记录类型", "配置项Key", "备注"));
        rows.add(List.of("ITEM", "data_source", "含,逗号与\"引号\""));
        rows.add(List.of("GLOBAL", "retry_times", ""));
        String text = SyncCsvCodec.serialize(rows);
        assertEquals(rows, SyncCsvCodec.parse(text));
    }
}
