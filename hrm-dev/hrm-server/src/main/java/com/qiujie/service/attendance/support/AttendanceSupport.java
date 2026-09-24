package com.qiujie.service.attendance.support;

import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 考勤域公共工具（纯函数，零 Spring 依赖）：日期/时间格式、入参正则、文案长度判定。
 * <p>
 * 为什么集中：日期格式与文案长度在「查询校验 / 明细排序 / CSV 导出」三处复用，
 * 散落会导致口径漂移（如导出与页面筛选结果对不上——Mock 曾以此为由抽出同一套 filterRecords）。
 * 正则与 Mock {@code mock/validate.js} 及 {@code routes/attendance.js} 逐条一致。
 */
public final class AttendanceSupport {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** yyyy-MM-dd（对齐 Mock {@code isDate}） */
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /** yyyy-MM（对齐 Mock {@code isMonth}） */
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    /** HH:mm（00:00-23:59，对齐 Mock {@code isClock}） */
    private static final Pattern CLOCK_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    /** #RRGGBB（对齐 Mock {@code isHexColor}） */
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private AttendanceSupport() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 文本长度（trim 后字符数） */
    public static boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }

    /** 日期格式合法（仅格式，不含日历合法性） */
    public static boolean isDate(String value) {
        return value == null || value.isBlank() || DATE_PATTERN.matcher(value).matches();
    }

    /** 严格日期格式（非空且格式合法） */
    public static boolean isStrictDate(String value) {
        return value != null && DATE_PATTERN.matcher(value).matches();
    }

    public static boolean isMonth(String value) {
        return value == null || value.isBlank() || MONTH_PATTERN.matcher(value).matches();
    }

    public static boolean isClock(String value) {
        return value != null && CLOCK_PATTERN.matcher(value).matches();
    }

    /** 收班时间允许 24:00（晚班收在零点） */
    public static boolean isEndClock(String value) {
        return isClock(value) || "24:00".equals(value);
    }

    public static boolean isHexColor(String value) {
        return value != null && HEX_COLOR_PATTERN.matcher(value).matches();
    }

    /** yyyy-MM-dd → LocalDate（本地时区语义，不用 UTC 解析）；格式/日历非法抛 {@link DateTimeParseException} */
    public static LocalDate parseDate(String value) {
        return LocalDate.parse(value, DATE_FMT);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : DATE_FMT.format(date);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATETIME_FMT.format(dateTime);
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static String currentMonth() {
        return formatDate(today()).substring(0, 7);
    }

    /** 所在周的周一（排班以周一为周首，对齐 Mock {@code mondayOf}） */
    public static LocalDate mondayOf(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue(); // 周一=1 … 周日=7
        return date.minusDays(dayOfWeek - 1L);
    }

    /** 中文姓名排序（对齐 Mock {@code localeCompare(..., 'zh')}） */
    public static Collator chineseCollator() {
        Collator collator = Collator.getInstance(Locale.CHINA);
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }
}
