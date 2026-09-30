package com.qiujie.service.finance.support;

import com.qiujie.service.attendance.support.AttendanceConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 班次制算薪纯逻辑（S2b，唯一真源 {@code hrm-dev/docs/algo-payroll-shift.md} v2.0 §2；原型 {@code algo-scripts/s2b-payroll-shift.mjs}）。
 * <p>
 * <b>核心变换</b>：把「排班行」作为唯一计量单位——一行排班 = 一个应出勤班次；出勤/请假/旷工都在<b>班次单元</b>上做集合运算：
 * <pre>
 * 班次单元 = epochDay × 2 + 班次序号（早班 0 / 晚班 1）   // 与 LeaveIntervalPolicy.unitOf 同构：AM=早班, PM=晚班
 * R = 排班行去重（workDate + 班次序号）            // 应出班次
 * A = 有效上班卡（check_type=ON 且 status≠ABNORMAL）经三态优先级映射到班次
 * L = 已批请假单元 ∩ R
 * 旷工 = |R \ (A ∪ L)|        折算基本 = 定薪字段 × |A ∩ R| ÷ |R|
 * </pre>
 * 为什么抽成纯逻辑类：① 折算与罚款均涉员工实发金额，必须可离线逐例断言（不依赖 Spring/DB）；
 * ② 复杂度与边界（三态优先级、缺勤恒等式、封顶链）集中一处，便于后续维护与算法对照。
 */
public final class ShiftPayrollPolicy {

    /** zeroSchedulePolicy：应出班次为 0 时按全额基本工资兜底（不因数据缺失克扣） */
    public static final String ZERO_SCHEDULE_FULL_BASIC = "FULL_BASIC";
    /** zeroSchedulePolicy：应出班次为 0 时按 0 计 */
    public static final String ZERO_SCHEDULE_ZERO = "ZERO";

    /** lateGranularity：按次（每张有效 ON 迟到卡计 1 次，= 现状源码口径，默认） */
    public static final String LATE_PER_CARD = "PER_CARD";
    /** lateGranularity：按日去重（口径变更，本轮未采纳，须用户再裁定方可启用） */
    public static final String LATE_PER_DAY = "PER_DAY";

    /** 空 period_name 且当日多班次 → 告警原因（交人工复核，不克扣） */
    public static final String WARN_PERIOD_NAME_MISSING = "PERIOD_NAME_MISSING";
    /** 排班行缺少可用班次时间 → 跳过该行并留痕 */
    public static final String WARN_SCHEDULE_SHIFT_MISSING = "SCHEDULE_SHIFT_MISSING";

    private ShiftPayrollPolicy() {
    }

    // ==================== 单元编码 / 班次序号 ====================

    /** 班次单元：epochDay × 2 + 序号（早班 0 / 晚班 1）；与 {@code LeaveIntervalPolicy.unitOf} 同构，故请假单元可直接与应出集合取交 */
    public static long shiftUnit(LocalDate workDate, int ordinal) {
        return workDate.toEpochDay() * 2L + ordinal;
    }

    /**
     * 班次序号：由 {@code attendance_shift.start_time} 判定（分钟数 &lt; 界值 → 早班 0，否则晚班 1）。
     * <p>
     * 返回 -1 表示时间缺失/不可解析（调用方跳过该排班行，见 {@link #compute}）；不静默回落到某个序号，
     * 否则会与相邻日的班次单元串号（如 -1 落回上一日晚班）。
     */
    public static int shiftOrdinal(String startTime, int middayBoundaryMinute) {
        if (startTime == null) {
            return -1;
        }
        String[] parts = startTime.trim().split(":");
        if (parts.length < 2) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            return hour * 60 + minute < middayBoundaryMinute ? 0 : 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 有效上班卡判定：复用 {@link AttendanceConstants#isValidCard}（ABNORMAL 不计出勤，与现状一致） */
    public static boolean isValidCard(String status) {
        return AttendanceConstants.isValidCard(status);
    }

    /** 记录侧 {@code period_name} 是否为空（null / 空白串） */
    private static boolean isBlankPeriodName(String name) {
        return name == null || name.trim().isEmpty();
    }

    // ==================== 集合运算 ====================

    /**
     * 应出勤班次集合（O(S)）：排班行按 {@code (workDate, shiftOrdinal)} 去重。
     *
     * @param warn 可空：收集「排班行缺班次时间」等告警
     */
    public static Set<Long> requiredShiftSet(Collection<ScheduleRow> schedules, int middayBoundaryMinute, List<Warning> warn) {
        Set<Long> required = new LinkedHashSet<>();
        if (schedules == null) {
            return required;
        }
        for (ScheduleRow row : schedules) {
            if (row == null || row.workDate() == null) {
                continue;
            }
            int ordinal = shiftOrdinal(row.startTime(), middayBoundaryMinute);
            if (ordinal < 0) {
                // 排班侧数据缺失：跳过该行（不计入 R）并留痕，不抛异常、不阻塞整批（方案 §8）
                if (warn != null) {
                    warn.add(new Warning(row.workDate(), WARN_SCHEDULE_SHIFT_MISSING, 1));
                }
                continue;
            }
            required.add(shiftUnit(row.workDate(), ordinal));
        }
        return required;
    }

    /**
     * 当日排班班次键（供三态优先级判定「当日排班班次 &gt; 1」）。
     */
    private static Map<LocalDate, Set<Long>> dayKeysOf(Collection<ScheduleRow> schedules, int middayBoundaryMinute) {
        Map<LocalDate, Set<Long>> dayKeys = new HashMap<>();
        if (schedules == null) {
            return dayKeys;
        }
        for (ScheduleRow row : schedules) {
            if (row == null || row.workDate() == null) {
                continue;
            }
            int ordinal = shiftOrdinal(row.startTime(), middayBoundaryMinute);
            if (ordinal < 0) {
                continue;
            }
            dayKeys.computeIfAbsent(row.workDate(), k -> new LinkedHashSet<>()).add(shiftUnit(row.workDate(), ordinal));
        }
        return dayKeys;
    }

    /**
     * 实际出勤班次集合（O(R+S)）：逐个有效上班卡映射到班次键。
     * <p>
     * <b>记录 → 班次的唯一判定优先级（三态，方案 §2.1，本节为唯一定义处）</b>：
     * <ol>
     *   <li>{@code period_name} 命中哨兵（默认 {@code 全天班}）→ 覆盖<b>当日全部排班班次</b>（单班制历史兼容，ratio 不突变）；</li>
     *   <li>{@code period_name} 为空/NULL：当日排班班次 &gt; 1 → 按当日<b>全部</b>班次计入出勤并置 {@code PERIOD_NAME_MISSING} 告警
     *       （<b>不得判为亏勤</b>，防克扣；数据质量问题暴露为告警而非扣款）；= 1 → 归属该唯一班次；无排班 → 不产生班次；</li>
     *   <li>其余（非空且非哨兵）→ 按 {@code (workDate, period_index)} 精确定位。</li>
     * </ol>
     */
    public static Set<Long> attendedShiftSet(Collection<RecordRow> records,
                                             Map<LocalDate, Set<Long>> dayKeys,
                                             String legacyPeriodSentinel,
                                             List<Warning> warn) {
        Set<Long> attended = new HashSet<>();
        if (records == null) {
            return attended;
        }
        for (RecordRow row : records) {
            if (row == null || row.workDate() == null || !AttendanceConstants.CHECK_TYPE_ON.equals(row.checkType())) {
                continue;
            }
            if (!isValidCard(row.status())) {
                continue;
            }
            Set<Long> keys = dayKeys.getOrDefault(row.workDate(), Set.of());
            if (legacyPeriodSentinel != null && legacyPeriodSentinel.equals(row.periodName())) {
                attended.addAll(keys);
                continue;
            }
            if (isBlankPeriodName(row.periodName())) {
                if (keys.size() > 1) {
                    attended.addAll(keys);
                    if (warn != null) {
                        warn.add(new Warning(row.workDate(), WARN_PERIOD_NAME_MISSING, keys.size()));
                    }
                } else if (keys.size() == 1) {
                    attended.add(keys.iterator().next());
                }
                continue;
            }
            Integer periodIndex = row.periodIndex();
            if (periodIndex != null) {
                attended.add(shiftUnit(row.workDate(), periodIndex));
            }
        }
        return attended;
    }

    /**
     * 请假班次集合：已批请假单元与应出集合取交（L ∩ R）。
     * <p>
     * 与 R 取交 ⇒ 只有落在应出排班上的请假单元才抵扣缺勤；请假超出应到不会反向放大缺勤（方案 §2.1 约束 4）。
     */
    public static Set<Long> leaveShiftSet(Collection<Long> leaveUnits, Set<Long> required) {
        Set<Long> leave = new HashSet<>();
        if (leaveUnits == null) {
            return leave;
        }
        for (Long unit : leaveUnits) {
            if (unit != null && required.contains(unit)) {
                leave.add(unit);
            }
        }
        return leave;
    }

    /**
     * 缺勤班次单元集合 {@code R \ (A ∪ L)}（与 {@link #compute} 的 {@code absentShifts} 同源）。
     * <p>
     * 为什么单列此方法：考勤<b>明细</b>缺卡名单需按班次粒度逐项列出「哪个员工哪一个班次缺」，
     * 而 {@code compute} 只回计数。抽出集合口径供明细复用，避免明细另写一套缺卡判定导致与概况漂移。
     * 入参与 {@link #compute} 同构；考勤口径传 {@code leaveUnits = 空集}（不抵扣请假）。
     */
    public static Set<Long> absentShiftSet(Collection<ScheduleRow> schedules,
                                           Collection<RecordRow> records,
                                           Collection<Long> leaveUnits,
                                           int middayBoundaryMinute,
                                           String legacyPeriodSentinel) {
        Map<LocalDate, Set<Long>> dayKeys = dayKeysOf(schedules, middayBoundaryMinute);
        Set<Long> required = requiredShiftSet(schedules, middayBoundaryMinute, null);
        Set<Long> attended = attendedShiftSet(records, dayKeys, legacyPeriodSentinel, null);
        Set<Long> leave = leaveShiftSet(leaveUnits, required);
        Set<Long> absent = new LinkedHashSet<>();
        for (Long key : required) {
            if (!attended.contains(key) && !leave.contains(key)) {
                absent.add(key);
            }
        }
        return absent;
    }

    /** 迟到计数：PER_CARD（每张有效 ON 迟到卡计 1 次，= 现状） / PER_DAY（按日去重，口径变更） */
    public static int lateCountOf(Collection<RecordRow> records, String lateGranularity) {
        int perCard = 0;
        Set<LocalDate> days = new HashSet<>();
        if (records != null) {
            for (RecordRow row : records) {
                if (row == null || !AttendanceConstants.CHECK_TYPE_ON.equals(row.checkType())) {
                    continue;
                }
                if (!AttendanceConstants.STATUS_LATE.equals(row.status())) {
                    continue;
                }
                perCard++;
                if (row.workDate() != null) {
                    days.add(row.workDate());
                }
            }
        }
        return LATE_PER_DAY.equalsIgnoreCase(lateGranularity) ? days.size() : perCard;
    }

    // ==================== 主计算 ====================

    /**
     * 班次制考勤汇总（纯函数）。<b>不抛异常</b>：脏数据按三态优先级处理 / 排班缺班次时间跳过并留痕（方案 §8）。
     *
     * @param schedules           排班行（workDate + shift.startTime）
     * @param records             打卡记录（workDate + periodIndex + periodName + checkType + status）
     * @param leaveUnits          已批请假班次单元（可为空集）
     * @param middayBoundaryMinute 班次序号判定界值（{@code hrm.algo.payroll.middayBoundaryMinute}）
     * @param legacyPeriodSentinel 单班制历史哨兵（{@code hrm.algo.payroll.legacyPeriodSentinel}）
     * @param lateGranularity     迟到计数粒度（{@code hrm.algo.payroll.lateGranularity}）
     */
    public static Result compute(Collection<ScheduleRow> schedules,
                                 Collection<RecordRow> records,
                                 Collection<Long> leaveUnits,
                                 int middayBoundaryMinute,
                                 String legacyPeriodSentinel,
                                 String lateGranularity) {
        List<Warning> warnings = new ArrayList<>();
        Map<LocalDate, Set<Long>> dayKeys = dayKeysOf(schedules, middayBoundaryMinute);
        Set<Long> required = requiredShiftSet(schedules, middayBoundaryMinute, warnings);
        Set<Long> attendedRaw = attendedShiftSet(records, dayKeys, legacyPeriodSentinel, warnings);
        // A ∩ R：不可直接用打卡条数（方案 §2.1 约束 2）
        Set<Long> attended = new HashSet<>();
        for (Long key : attendedRaw) {
            if (required.contains(key)) {
                attended.add(key);
            }
        }
        Set<Long> leave = leaveShiftSet(leaveUnits, required);

        int absent = 0;
        for (Long key : required) {
            if (!attended.contains(key) && !leave.contains(key)) {
                absent++;
            }
        }

        int earlyLeave = 0;
        int abnormal = 0;
        if (records != null) {
            for (RecordRow row : records) {
                if (row == null) {
                    continue;
                }
                if (AttendanceConstants.STATUS_ABNORMAL.equals(row.status())) {
                    abnormal++;
                    continue;
                }
                if (AttendanceConstants.CHECK_TYPE_OFF.equals(row.checkType())
                        && AttendanceConstants.STATUS_EARLY_LEAVE.equals(row.status())) {
                    earlyLeave++;
                }
            }
        }

        return new Result(required.size(), attended.size(), leave.size(), absent,
                lateCountOf(records, lateGranularity), earlyLeave, abnormal, warnings);
    }

    // ==================== 金额（折算 / 封顶） ====================

    /**
     * 折算后金额 = 基数 × 实出班次 ÷ 应出班次（HALF_UP 到分）。
     * <p>
     * 应出班次为 0 时按 {@code zeroSchedulePolicy} 兜底：{@code FULL_BASIC}（默认，不因数据缺失克扣）/ {@code ZERO}，同时挡除零（方案 §8）。
     */
    public static BigDecimal prorated(BigDecimal base, int attendedShifts, int requiredShifts, String zeroSchedulePolicy) {
        BigDecimal amount = base == null ? BigDecimal.ZERO : base;
        if (requiredShifts <= 0) {
            return ZERO_SCHEDULE_FULL_BASIC.equalsIgnoreCase(zeroSchedulePolicy)
                    ? amount.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(BigDecimal.valueOf(attendedShifts))
                .divide(BigDecimal.valueOf(requiredShifts), 2, RoundingMode.HALF_UP);
    }

    /**
     * 封顶链级 2 的上限值 = 折算后基本工资 × 系数；系数 &le; 0 返回 {@code null}（本级不封顶，默认关闭）。
     */
    public static BigDecimal ratioCap(BigDecimal basicProrated, BigDecimal capRatio) {
        if (capRatio == null || capRatio.signum() <= 0) {
            return null;
        }
        BigDecimal base = basicProrated == null ? BigDecimal.ZERO : basicProrated;
        return base.multiply(capRatio).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 对罚款施加封顶链级 2（配置级比例封顶，方案 §7.1）：{@code min(fineGross, ratioCap)}；
     * 本级不封顶时原样返回。
     * <p>
     * 逐级取更严（min），故与级 1（规则项 {@code params.cap}）叠加时不会「重复施加」。
     */
    public static BigDecimal capFineByRatio(BigDecimal fineGross, BigDecimal basicProrated, BigDecimal capRatio) {
        BigDecimal cap = ratioCap(basicProrated, capRatio);
        if (fineGross == null || cap == null) {
            return fineGross;
        }
        return fineGross.compareTo(cap) > 0 ? cap : fineGross;
    }

    // ==================== 输入 / 输出载体 ====================

    /** 排班行（workDate + 班次开始时间） */
    public record ScheduleRow(LocalDate workDate, String startTime) {
    }

    /** 打卡记录行（三态判定所需字段） */
    public record RecordRow(LocalDate workDate, Integer periodIndex, String periodName, String checkType, String status) {
    }

    /** 告警（当前载体为内存列表；落库/展示载体见方案 §8 与 TODO(扩展)） */
    public record Warning(LocalDate workDate, String reason, int shifts) {
    }

    /** 班次制考勤汇总结果 */
    public record Result(int requiredShifts,
                         int attendedShifts,
                         int leaveShifts,
                         int absentShifts,
                         int lateCount,
                         int earlyLeaveCount,
                         int abnormalCount,
                         List<Warning> warnings) {
    }
}
