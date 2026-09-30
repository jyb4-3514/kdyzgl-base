package com.qiujie.service.attendance.support;

import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.CheckPeriod;
import com.qiujie.service.finance.support.ShiftPayrollPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>
 * <b>真源统一（打卡时间真源统一方案 v1.2 §4）</b>：时段由「该驿站启用班次」派生（{@link #resolveByShifts}），
 * {@code periodIndex = shiftOrdinal(start_time, 界值)}（早 0 / 晚 1，<b>非数组下标</b>）。
 * 取值一律按值查找（{@link #findByOrdinal}），禁止 {@code List.get(periodIndex)} 下标取值（M1 硬性规则）。
 */
public final class AttendancePeriodResolver {

    private static final Logger log = LoggerFactory.getLogger(AttendancePeriodResolver.class);

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
     * 【遗留 / 回滚保留】规则时段解析：优先 {@code checkPeriods}；为空时兜底为单段「全天班」。
     * <p>
     * <b>已非生产真源</b>：真源统一后生产路径一律走 {@link #resolveByShifts}；本方法保留仅供
     * 回滚（方案 §9.4 R-1 恢复 {@code resolve(rule)} 原语义）与解析器自测（{@code AttendancePeriodResolverTest}）。
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

    /**
     * 【真源统一】由「该驿站启用班次」派生打卡时段。
     * <p>
     * {@code periodIndex = shiftOrdinal(start_time, 界值)}（早 0 / 晚 1，<b>非数组下标</b>），
     * {@code name = 班次名快照}，起止取班次起止。<b>不读 {@code attendance_rule.check_periods}</b>。
     * <p>
     * {@code ordinal} 缺失（{@code start_time} 缺失/不可解析）的班次**不进入派生集合**并记
     * {@code WARN_SCHEDULE_SHIFT_MISSING}（与计薪侧口径一致）；若因此无任何时段，调用方按「0 启用班次」处理（9113）。
     *
     * @param shifts 该驿站启用班次（建议已按 {@code start_time} 升序；本方法不改序）
     * @param middayBoundaryMinute 班次序号界值（{@code hrm.algo.payroll.middayBoundaryMinute}，默认 720）
     */
    public static List<ResolvedPeriod> resolveByShifts(List<AttendanceShift> shifts, int middayBoundaryMinute) {
        List<ResolvedPeriod> list = new ArrayList<>();
        if (shifts == null) {
            return list;
        }
        for (AttendanceShift shift : shifts) {
            // 防御：调用方通常已按 status=1 过滤；此处再兜一层，避免停用/空行混入
            if (shift == null || shift.getStatus() == null || shift.getStatus() != 1) {
                continue;
            }
            int ordinal = shiftOrdinal(shift.getStartTime(), middayBoundaryMinute);
            if (ordinal < 0) {
                log.warn("WARN_SCHEDULE_SHIFT_MISSING：班次开始时间缺失/不可解析，已从打卡时段中排除，shiftId={}, startTime={}",
                        shift.getId(), shift.getStartTime());
                continue;
            }
            list.add(new ResolvedPeriod(ordinal, shift.getShiftName(), shift.getStartTime(), shift.getEndTime()));
        }
        return list;
    }

    /**
     * 按 {@code ordinal}（= {@code period_index}）<b>按值查找</b>派生时段；禁止下标取值（M1 硬性规则）。
     *
     * @return 命中时段；无匹配返回 {@code null}（调用方回 9107）
     */
    public static ResolvedPeriod findByOrdinal(List<ResolvedPeriod> periods, Integer periodIndex) {
        if (periods == null || periodIndex == null) {
            return null;
        }
        for (ResolvedPeriod period : periods) {
            if (period.periodIndex() == periodIndex) {
                return period;
            }
        }
        return null;
    }

    /** 首班开始（派生 {@code work_start_time}）；无时段返回 {@code null} */
    public static String firstStartTime(List<ResolvedPeriod> periods) {
        return periods == null || periods.isEmpty() ? null : periods.get(0).startTime();
    }

    /** 末班结束（派生 {@code work_end_time}）；无时段返回 {@code null} */
    public static String lastEndTime(List<ResolvedPeriod> periods) {
        return periods == null || periods.isEmpty() ? null : periods.get(periods.size() - 1).endTime();
    }

    /**
     * 班次序号（早 0 / 晚 1）；{@code -1} = 时间不可解析。复用计薪唯一实现（{@link ShiftPayrollPolicy#shiftOrdinal}），
     * 避免在班次定义侧派生第三处实现而漂移（方案 §6.2 评审 M-5）。
     */
    public static int shiftOrdinal(String startTime, int middayBoundaryMinute) {
        return ShiftPayrollPolicy.shiftOrdinal(startTime, middayBoundaryMinute);
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
