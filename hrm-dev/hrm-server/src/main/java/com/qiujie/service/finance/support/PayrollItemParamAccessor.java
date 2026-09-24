package com.qiujie.service.finance.support;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 规则项参数访问器（纯逻辑）。
 * <p>
 * 为什么需要：{@code params} 是 JSON 反序列化出的 {@code Map<String,Object>}，其值类型不稳定
 * （Integer / Double / String / BigDecimal 皆可能）。JS 侧 {@code Number(x) || 0} 的宽松语义必须在此复刻，
 * 否则同一份规则在 Node 与 Java 两端会算出不同金额。
 * <ul>
 *   <li>{@link #number(String)}：不可解析或缺失 → {@code null}（对齐 JS {@code Number(x)} 为 NaN）；</li>
 *   <li>{@link #numberOrZero(String)}：不可解析或缺失 → {@code 0}（对齐 JS {@code Number(x) || 0}）。</li>
 * </ul>
 */
public final class PayrollItemParamAccessor {

    private final Map<String, Object> params;

    public PayrollItemParamAccessor(Map<String, Object> params) {
        this.params = params;
    }

    /** 参数是否存在（区分「未配」与「配为 null」，KPI capRatio 默认值判定需要） */
    public boolean has(String key) {
        return params != null && params.containsKey(key);
    }

    /** 字符串值（缺失 → null）；不做 trim，保持与原值一致 */
    public String string(String key) {
        if (params == null) {
            return null;
        }
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 数值（缺失或不可解析 → null） */
    public BigDecimal number(String key) {
        if (params == null) {
            return null;
        }
        return toNumber(params.get(key));
    }

    /** 数值（缺失或不可解析 → 0，对齐 JS {@code Number(x) || 0}） */
    public BigDecimal numberOrZero(String key) {
        BigDecimal value = number(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Object → BigDecimal：Number 直取，数字字符串可解析，其它一律 null */
    private static BigDecimal toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            // Double/Float 用 toString 兜住精度，避免 doubleValue 引入二进制误差
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
