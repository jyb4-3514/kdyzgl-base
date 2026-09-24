package com.qiujie.service.sync.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 文本编解码（纯函数，RFC 4180）。
 * <p>
 * 为什么自己解析而非 {@code split(',')}：单元格可能含逗号、换行、双引号转义，split 一定会把「备注里带逗号」
 * 的行拆错列；导入是外部输入，必须按 RFC 4180 处理。兼容点（设计 D.3）：忽略 BOM、CRLF/LF 混用、
 * 不必要的包裹引号、末尾空行。与 Mock {@code shared/domain/csv.js} 行为逐位一致，
 * 保证「Mock 导出的文件能被真实后端导入、真实后端导出的文件能被 Mock 导入」。
 */
public final class SyncCsvCodec {

    /** UTF-8 BOM：便于 Excel 直接打开中文 */
    public static final String BOM = "\uFEFF";
    /** 行分隔符固定 CRLF（导出即导入闭环） */
    private static final String CRLF = "\r\n";

    private SyncCsvCodec() {
    }

    /** 单元格转义：含逗号 / 引号 / 换行时用双引号包裹，内部引号翻倍 */
    public static String escape(String value) {
        String text = value == null ? "" : value;
        return text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                ? "\"" + text.replace("\"", "\"\"") + "\""
                : text;
    }

    /** 二维数组 → CSV 文本（BOM + CRLF） */
    public static String serialize(List<List<String>> rows) {
        StringBuilder builder = new StringBuilder(BOM);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                builder.append(CRLF);
            }
            List<String> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) {
                    builder.append(',');
                }
                builder.append(escape(row.get(j)));
            }
        }
        return builder.toString();
    }

    /** 序列化为字节（UTF-8，供文件流响应） */
    public static byte[] toBytes(List<List<String>> rows) {
        return serialize(rows).getBytes(StandardCharsets.UTF_8);
    }

    /** RFC 4180 解析（逐字符扫描） */
    public static List<List<String>> parse(String text) {
        String source = text == null ? "" : text;
        if (source.startsWith(BOM)) {
            source = source.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                quoted = true;
                continue;
            }
            if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
                continue;
            }
            if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
                continue;
            }
            cell.append(ch);
        }
        row.add(cell.toString());
        rows.add(row);
        // 末尾空行（Excel 另存常留一行空）不算数据：整行仅空单元格即剔除
        while (rows.size() > 1 && rows.get(rows.size() - 1).stream().allMatch(item -> item.isEmpty())) {
            rows.remove(rows.size() - 1);
        }
        return rows;
    }
}
