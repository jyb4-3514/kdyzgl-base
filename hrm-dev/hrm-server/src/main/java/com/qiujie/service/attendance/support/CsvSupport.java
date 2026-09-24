package com.qiujie.service.attendance.support;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 输出工具（与 Mock {@code shared/domain/csv.js} 逐条对齐）。
 * <p>
 * 为什么走内存流：导出为一次性响应，直接拼接字节数组即可，<b>不落盘</b>（架构 1.4.5 硬约束：
 * 临时文件不得写系统盘；确需落盘时目录须走数据盘 export 目录）。带 BOM 便于 Excel 直接打开中文。
 */
public final class CsvSupport {

    /** CSV MIME（带 charset） */
    public static final String CSV_CONTENT_TYPE = "text/csv;charset=utf-8";

    private CsvSupport() {
    }

    /** 单元格转义：含逗号 / 引号 / 换行时用双引号包裹，内部引号翻倍 */
    public static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    /** 二维数组 → CSV 字节流（BOM + CRLF 行分隔，与 Mock {@code toCsvBlob} 一致） */
    public static byte[] toCsvBytes(List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append('\ufeff');
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                builder.append("\r\n");
            }
            List<String> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) {
                    builder.append(',');
                }
                builder.append(escape(row.get(j)));
            }
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 附件响应头（RFC 5987 编码，避免中文文件名乱码）。
     * 注意：BOM 已随 {@link #toCsvBytes} 写入正文，这里不再重复。
     */
    public static String disposition(String filename) {
        return "attachment; filename*=UTF-8''" + encode(filename);
    }

    private static String encode(String filename) {
        try {
            return java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            return filename;
        }
    }
}
