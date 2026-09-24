package com.qiujie.service.parcel.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 游标分页编解码（算法 S7-3，ADR-06）。
 * <p>
 * 游标即「上一页末行的键 {@code (inbound_time, id)}」的不透明编码（URL-Safe Base64），
 * 前端无需理解其结构；服务端解码后用 keyset 条件
 * {@code (inbound_time < t) OR (inbound_time = t AND id > i)} 直接定位下一页，
 * 扫描行恒定 {@code O(log N + pageSize)}（20 万级 38 行 vs 深分页 20020 行）。
 * <p>
 * 为什么编码时间而非 epoch：{@code DATETIME} 无时区语义，编码 ISO 字符串可避免时区换算引入的偏差，
 * 且与 DB 列精度（秒）天然一致，往返无损。
 */
public final class ParcelCursorCodec {

    private static final String SEPARATOR = "|";

    private ParcelCursorCodec() {
    }

    /** 解码结果：上一页末行的键 */
    public record Cursor(LocalDateTime inboundTime, long id) {
    }

    /** 编码：{@code base64url("yyyy-MM-dd HH:mm:ss|id")}，无填充 */
    public static String encode(LocalDateTime inboundTime, long id) {
        String raw = inboundTime.format(ParcelConstants.DATE_TIME_FMT) + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码。
     *
     * @return 空白入参返回 {@code null}（表示「无游标」= 首页）；格式非法抛 {@link IllegalArgumentException}
     * （由调用方转为 400，避免静默当作首页导致翻页错位）
     */
    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("游标格式不正确", e);
        }
        int sep = raw.lastIndexOf(SEPARATOR);
        if (sep <= 0 || sep >= raw.length() - 1) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        try {
            LocalDateTime time = LocalDateTime.parse(raw.substring(0, sep), ParcelConstants.DATE_TIME_FMT);
            long id = Long.parseLong(raw.substring(sep + 1));
            if (id <= 0) {
                throw new IllegalArgumentException("游标格式不正确");
            }
            return new Cursor(time, id);
        } catch (DateTimeParseException | NumberFormatException e) {
            throw new IllegalArgumentException("游标格式不正确", e);
        }
    }
}
