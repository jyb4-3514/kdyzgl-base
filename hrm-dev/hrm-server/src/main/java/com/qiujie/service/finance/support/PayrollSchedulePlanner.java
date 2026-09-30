package com.qiujie.service.finance.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * 自动算薪「到点判定 / 补跑钟点 / 触发类型」纯逻辑（算法 v1.3 §1.2~§1.4）。
 * <p>
 * 为什么独立成无状态工具类：判定是调度的可测内核——钳位、当日尝试钟点、AUTO/CATCH_UP 标记均为纯函数，
 * 抽离后可在无 DB、无 Spring 上下文下逐条断言边界用例（算法 §3），避免与事务/查询逻辑纠缠。
 * <p>
 * 时区口径（ADR-01）：调用方以 {@code ZoneId(zone)} 派生 {@code now}/{@code day} 后传入；本类不做时区转换，
 * 只做墙钟日历运算，保证「判定」与「落库」同一时区源。
 */
public final class PayrollSchedulePlanner {

    /** 算薪日下界（含） */
    public static final int MIN_DAY = 1;
    /** 算薪日上界（含） */
    public static final int MAX_DAY = 31;

    /** HH:mm（24 小时制，00:00–23:59） */
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private PayrollSchedulePlanner() {
    }

    /** 算薪日是否合法（1..31；U-05 裁定范围） */
    public static boolean isValidDay(Integer day) {
        return day != null && day >= MIN_DAY && day <= MAX_DAY;
    }

    /** 时间是否合法（须为 HH:mm） */
    public static boolean isValidTime(String time) {
        return time != null && TIME_PATTERN.matcher(time).matches();
    }

    /** 解析 HH:mm；非法返回 null（由调用方按「配置非法 / 继承」处理，不抛异常污染调度线程） */
    public static LocalTime parseTimeOrNull(String time) {
        if (!isValidTime(time)) {
            return null;
        }
        return LocalTime.of(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(3, 5)));
    }

    /** 配置是否整体合法（算薪日 + 时间） */
    public static boolean isConfigValid(Integer payrollDay, String payrollTime) {
        return isValidDay(payrollDay) && isValidTime(payrollTime);
    }

    /**
     * 应执行时刻 {@code dueAt}：当月 {@code min(payrollDay, 当月天数)} 日的 {@code payrollTime}（月末钳位，U-05）。
     * <p>钳位保证「每月恒定有且仅有一个 dueAt」，不存在「某月无 dueAt」的隐性漏跑。
     */
    public static LocalDateTime computeDueAt(int payrollDay, LocalTime time, YearMonth month) {
        int length = month.lengthOfMonth();
        int day = Math.min(Math.max(payrollDay, MIN_DAY), length);
        return LocalDateTime.of(month.getYear(), month.getMonthValue(), day, time.getHour(), time.getMinute());
    }

    /**
     * 当日尝试钟点（算法 v1.3 §1.4，复-3 统一）：
     * {@code day == dueAt 日 → payrollTime}；{@code day > dueAt 日 → catchUpTimeOfDay（空则继承 payrollTime）}。
     * <p>补跑日不在凌晨 00:0x 触发（避免早于上游数据日结），与前移限制见 §1.4.1 R-D2。
     */
    public static LocalDateTime attemptTimeOf(LocalDate day, LocalDateTime dueAt, LocalTime payrollTime,
                                              LocalTime catchUpTime) {
        LocalTime effective = day.equals(dueAt.toLocalDate())
                ? payrollTime
                : (catchUpTime != null ? catchUpTime : payrollTime);
        return LocalDateTime.of(day, effective);
    }

    /** 触发类型：与 dueAt 同日 → AUTO；晚于 dueAt 所在日 → CATCH_UP（算法 §1.4 日粒度定义） */
    public static String triggerType(LocalDate day, LocalDateTime dueAt) {
        return day.isAfter(dueAt.toLocalDate())
                ? PayrollRunTriggerType.CATCH_UP.name()
                : PayrollRunTriggerType.AUTO.name();
    }
}
