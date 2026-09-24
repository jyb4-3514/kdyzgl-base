package com.qiujie.service.attendance.support;

import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.CheckPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 时段模型解析（打卡时间判定的唯一真源）。
 * <p>
 * 与 Mock {@code attendanceStore} 的 {@code minutesOfDay / clockOfMinutes / periodsOf / timeAt} 逐位等价：
 * <ul>
 *   <li>{@code 'HH:mm'} → 当日分钟数，{@code '24:00'} 按 1440 处理；不可解析 → {@code NaN}
 *       （判定时与任何数比较均 false，等价「不做该分支」）；</li>
 *   <li>规则未配置时段时用「全天班 + 首末派生的上下班时间」兜底，保证脏数据不使判定整体失效；</li>
 *   <li>「日期 + 分钟数」↔ 时间：分钟溢出（如 24:10）自动进位次日，<b>按本地时区</b>计算
 *       （避免 UTC 解析错位——Mock {@code localDate} 已踩过该坑）。</li>
 * </ul>
 */
public final class AttendancePeriodResolver {

    private AttendancePeriodResolver() {
    }

    /** 解析后的时段（带序号，用于记录归属与去重槽位） */
    public record ResolvedPeriod(int periodIndex, String name, String startTime, String endTime) {
    }

    /** {@code 'HH:mm'} → 当日分钟数；{@code '24:00'} = 1440；不可解析 → NaN */
    public static double minutesOfDay(String text) {
        String source = text == null ? "" : text.trim();
        String[] parts = source.split(":", -1);
        if (parts.length != 2) {
            return Double.NaN;
        }
        Double hour = parseNumber(parts[0]);
        Double minute = parseNumber(parts[1]);
        if (hour == null || minute == null) {
            return Double.NaN;
        }
        return hour * 60 + minute;
    }

    /** 当日分钟数 → {@code 'HH:mm'}；越界（如 07:00-30 或 23:00+60）按 00:00 / 24:00 截断，避免前端出现非法时刻 */
    public static String clockOfMinutes(double minutes) {
        double raw = Double.isFinite(minutes) ? minutes : 0;
        long value = (long) Math.min(24 * 60, Math.max(0, Math.floor(raw)));
        return String.format("%02d:%02d", value / 60, value % 60);
    }

    /**
     * 规则时段解析：优先 {@code checkPeriods}（唯一真源）；为空时兜底为单段「全天班」。
     * 兜底段的起止取 {@code workStartTime / workEndTime}（首末派生值），与 Mock {@code periodsOf} 一致。
     */
    public static List<ResolvedPeriod> resolve(AttendanceRule rule) {
        List<CheckPeriod> declared = rule == null ? null : rule.getCheckPeriods();
        List<ResolvedPeriod> list = new ArrayList<>();
        if (declared != null && !declared.isEmpty()) {
            for (int i = 0; i < declared.size(); i++) {
                CheckPeriod period = declared.get(i);
                list.add(new ResolvedPeriod(i, period.getName(), period.getStartTime(), period.getEndTime()));
            }
            return list;
        }
        String start = rule == null ? null : rule.getWorkStartTime();
        String end = rule == null ? null : rule.getWorkEndTime();
        list.add(new ResolvedPeriod(0, AttendanceConstants.DEFAULT_PERIOD_NAME, start, end));
        return list;
    }

    /** 「日期 + 当日分钟数」→ 时间；分钟溢出自动进位次日。NaN 按 0 处理（不可达路径的防御兜底） */
    public static LocalDateTime at(LocalDate workDate, double minutes) {
        long offset = Double.isFinite(minutes) ? (long) Math.floor(minutes) : 0L;
        return workDate.atStartOfDay().plusMinutes(offset);
    }

    /** 数字解析：空串 / 非数字 → null（对齐 JS {@code Number.isFinite} 判定）。'08' 解析为 8 */
    private static Double parseNumber(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(trimmed);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
