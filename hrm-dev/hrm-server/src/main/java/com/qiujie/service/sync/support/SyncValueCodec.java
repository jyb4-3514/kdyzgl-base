package com.qiujie.service.sync.support;

/**
 * 配置值编解码（纯函数）：类型化值 ↔ 落库字符串。
 * <p>
 * 为什么需要：数据库 {@code sync_config_global.value} / {@code sync_config_station_override.value} 为
 * VARCHAR(255)，而契约出参里 NUMBER 必须是数字、单选/文本/时间必须是字符串。本类是该转换的唯一入口，
 * 避免「一处按字符串比较、一处按数字比较」的隐性漂移。
 * <p>
 * 转换规则（对齐 Mock {@code coerceValue} 与出参反序列化后的类型）：
 * <ul>
 *   <li>NUMBER：落库存数字文本，出参还原为 {@code Integer}（整数）或 {@code Double}；</li>
 *   <li>SINGLE_SELECT / TEXT / TIME：原样字符串；</li>
 *   <li>TIME_RANGE：落库用 {@code HH:mm-HH:mm} 串化，出参按串返回（Mock 允许数组或串，本期无内置时段范围项）。</li>
 * </ul>
 */
public final class SyncValueCodec {

    private SyncValueCodec() {
    }

    /** 类型化值 → 落库字符串；null / 空串 → null（保持列为 NULL 而非 "null"） */
    public static String toStored(String valueType, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    /** 落库字符串 → 类型化值；空 → null */
    public static Object toTyped(String valueType, String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        if ("NUMBER".equals(valueType)) {
            return parseNumber(stored);
        }
        return stored;
    }

    /** CSV 文本 → 类型化值（空 → null；NUMBER 解析失败时按原文保留，交由校验器报 9506） */
    public static Object coerceFromText(String valueType, String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if ("NUMBER".equals(valueType)) {
            Double num = tryDouble(text);
            return num == null ? text : num;
        }
        return text;
    }

    /** 判断配置值是否为空（null 或全空白字符串） */
    public static boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof String text && text.trim().isEmpty();
    }

    /** 数字文本 → Integer（整数值）或 Double */
    public static Object parseNumber(String stored) {
        Double num = tryDouble(stored);
        if (num == null) {
            return stored;
        }
        if (num == Math.rint(num) && !Double.isInfinite(num)) {
            return (int) num.doubleValue();
        }
        return num;
    }

    private static Double tryDouble(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
