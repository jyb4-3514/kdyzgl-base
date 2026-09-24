package com.qiujie.service.support;

import com.qiujie.dto.systemlog.ClientLogItem;
import com.qiujie.entity.ClientLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 运行日志入库前处理（对齐 Mock {@code clientLogStore.sanitizeLog} 与 api.md §7.5 白名单）。
 * <p>
 * 三条硬约束（S8 §10.1(b)）：
 * <ol>
 *   <li><b>白名单复制</b>：只取显式允许的字段。请求体里未列出的字段（token/密码/身份证/银行卡/请求响应体原文）
 *       因 {@link ClientLogItem} 不含对应属性而不被绑定，天然丢弃——黑名单删除在新增字段时必然漏脱敏，故不用黑名单；</li>
 *   <li><b>凭据串二次擦除</b>：白名单挡不住「写进 message/stack 的凭据片段」（如 ?token=xxx），此处按凭据形态正则擦除；</li>
 *   <li><b>文本截断</b>：单字段超 textMax 截断；path 去 query/hash。</li>
 * </ol>
 * 纯函数、无状态、无 Spring 依赖，便于单测覆盖边界（空值 / 超长文本 / 凭据擦除 / 非法枚举归一）。
 */
public final class ClientLogSanitizer {

    /** 级别白名单（与 dict.SYNC_LOG_LEVEL 的 label 同口径） */
    public static final List<String> LEVELS = List.of("INFO", "WARN", "ERROR");
    /** 上报端白名单（与 dict.CLIENT_LOG_SOURCE 同口径） */
    public static final List<String> SOURCES = List.of("PC", "H5", "SHELL");

    /** 非法级别归一值（对齐 Mock：非白名单一律当 ERROR，避免脏值污染级别筛选） */
    public static final String DEFAULT_LEVEL = "ERROR";
    /** 非法上报端归一值 */
    public static final String DEFAULT_SOURCE = "PC";
    /** 空 message 兜底文案（对齐 Mock，保证 message 非空、指纹可算） */
    public static final String DEFAULT_MESSAGE = "未提供错误信息";

    /** 各列的长度上限（与 V3__client_log.sql 的 VARCHAR 长度一致，防 DB 截断报错） */
    private static final int ROUTE_MAX = 200;
    private static final int PATH_MAX = 500;
    private static final int METHOD_MAX = 10;
    private static final int UA_MAX = 300;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 凭据形态（对齐 Mock SCRUB_PATTERN；仅擦凭据值，不改动其余可读内容） */
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(token=|accessToken=|refreshToken=|password=|pwd=)([^\\s&#,;)]+)", Pattern.CASE_INSENSITIVE);

    private ClientLogSanitizer() {
    }

    /**
     * 白名单清洗：把不可信上报项归一为可安全入库的 {@link ClientLog}（不含 employeeId/count/first/last，
     * 前者由 Service 以登录身份填充，后三者由聚合逻辑填充）。
     *
     * @param item    上报项（可为 null，按全空处理）
     * @param now     服务端当前时间（time 缺失/不可解析时的兜底）
     * @param textMax 单字段文本截断长度（hrm.algo.log.textMax）
     */
    public static ClientLog sanitize(ClientLogItem item, LocalDateTime now, int textMax) {
        ClientLogItem safe = item == null ? new ClientLogItem() : item;
        int max = Math.max(1, textMax);

        ClientLog log = new ClientLog();
        log.setTime(parseLenientDateTime(safe.getTime(), now));
        // LEVELS/SOURCES 为 List.of 不可变列表，contains(null) 会抛 NPE；先判空再白名单命中（未上报即归一为默认值）
        log.setLevel(safe.getLevel() != null && LEVELS.contains(safe.getLevel()) ? safe.getLevel() : DEFAULT_LEVEL);
        log.setSource(safe.getSource() != null && SOURCES.contains(safe.getSource()) ? safe.getSource() : DEFAULT_SOURCE);
        log.setRoute(blankToNull(truncate(safe.getRoute(), ROUTE_MAX)));

        // message：截断 → 擦除凭据 → 空值兜底（仅 null/'' 兜底，保留语义上的空白内容，对齐 Mock 的 || 判定）
        String message = scrub(truncate(safe.getMessage(), max));
        log.setMessage(message == null || message.isEmpty() ? DEFAULT_MESSAGE : message);
        log.setStack(blankToNull(scrub(truncate(safe.getStack(), max))));

        String method = safe.getMethod();
        log.setMethod(isBlank(method) ? null : truncate(method.toUpperCase(), METHOD_MAX));
        log.setPath(blankToNull(truncate(stripQuery(safe.getPath()), PATH_MAX)));
        log.setStatus(toIntegerOrNull(safe.getStatus()));
        log.setCode(toIntegerOrNull(safe.getCode()));
        log.setDuration(toIntegerOrNull(safe.getDuration()));
        log.setUa(blankToNull(truncate(safe.getUa(), UA_MAX)));
        return log;
    }

    /**
     * 指纹 = {@code message|route|code}（与 Mock {@code fingerprintOf} 及 S8 逐字一致）。
     * null 的 route/code 归一为空串，保证「缺失」与「空值」得到同一指纹，从而正确聚合。
     */
    public static String fingerprint(ClientLog log) {
        String route = log.getRoute() == null ? "" : log.getRoute();
        String code = log.getCode() == null ? "" : String.valueOf(log.getCode());
        return log.getMessage() + "|" + route + "|" + code;
    }

    /** 凭据串擦除（非凭据内容原样保留） */
    public static String scrub(String value) {
        return value == null ? null : CREDENTIAL.matcher(value).replaceAll("$1***");
    }

    /** 去掉 hash 与 query（防 ?token=xxx 随路径入库） */
    public static String stripQuery(String url) {
        if (url == null) {
            return null;
        }
        return url.split("#", -1)[0].split("\\?", -1)[0];
    }

    /** 超长则截断（null 原样返回） */
    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * 宽容解析日期时间（客户端可能上报 {@code yyyy-MM-dd HH:mm:ss}、ISO {@code T} 分隔或仅日期）。
     * 解析失败返回 {@code fallback}（不抛异常：日志上报不应因客户端时间格式瑕疵整批失败，对齐 Mock 的容忍口径）。
     */
    public static LocalDateTime parseLenientDateTime(String value, LocalDateTime fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        String normalized = value.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(normalized, DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续按「仅日期」尝试
        }
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // 继续按「取前 10 位日期」尝试（容忍 "yyyy-MM-dd 非法时间" 这类半合法串）
        }
        if (normalized.length() > 10) {
            try {
                return LocalDate.parse(normalized.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // 落到兜底
            }
        }
        return fallback;
    }

    /** 空串/空白转 null（出参表现与 Mock 存 '' 一致：前端按 {@code value || '—'} 渲染） */
    public static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    /** 字符串 → Integer；非数字/非有限值返回 null（对齐 Mock {@code toNumberOrNull}） */
    public static Integer toIntegerOrNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
