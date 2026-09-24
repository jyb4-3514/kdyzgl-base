package com.qiujie.service.support;

import com.qiujie.dto.systemlog.ClientLogQuery;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;

import java.util.regex.Pattern;

/**
 * 运行日志查询入参校验（对齐 Mock {@code routes/systemLogs.js#list} 的逐条判定与文案）。
 * <p>
 * 纯函数、无状态；分页越界（1-100）由 {@link com.qiujie.dto.support.PageQuery} 的 Bean Validation 承担，
 * 本类只补 level/source/时间格式三项 Mock 级校验。
 */
public final class ClientLogQueryValidator {

    /** 日期格式（Mock 只校验前 10 位是否形如 yyyy-MM-dd，不做日历合法性校验） */
    private static final Pattern DATE_PREFIX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private ClientLogQueryValidator() {
    }

    /**
     * 校验查询条件；不合法抛 {@link ErrorCode#BAD_REQUEST}（HTTP 200 + code 400，文案与 Mock 逐字一致）。
     */
    public static void validate(ClientLogQuery query) {
        if (query == null) {
            return;
        }
        String level = query.getLevel();
        if (!isBlank(level) && !ClientLogSanitizer.LEVELS.contains(level)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "level 取值非法");
        }
        String source = query.getSource();
        if (!isBlank(source) && !ClientLogSanitizer.SOURCES.contains(source)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "source 取值非法");
        }
        checkDateParam("startTime", query.getStartTime());
        checkDateParam("endTime", query.getEndTime());
    }

    private static void checkDateParam(String key, String value) {
        if (!isBlank(value) && !isDatePrefix(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, key + " 格式须为 YYYY-MM-DD");
        }
    }

    /** 前 10 位须形如 yyyy-MM-dd（与 Mock {@code isDate(String(v).slice(0,10))} 等价） */
    private static boolean isDatePrefix(String value) {
        String trimmed = value.trim();
        String prefix = trimmed.length() <= 10 ? trimmed : trimmed.substring(0, 10);
        return DATE_PREFIX.matcher(prefix).matches();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
