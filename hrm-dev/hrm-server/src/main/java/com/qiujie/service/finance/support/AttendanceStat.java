package com.qiujie.service.finance.support;

import java.math.BigDecimal;

/**
 * 考勤统计（计薪上下文的一部分，字段名与 Mock {@code employeeAttendanceStat} 出参一致）。
 * <p>
 * 为什么字段名与考勤域内部字段保持一致：{@code hrm.algo.payroll.attendanceFieldMap} 把
 * 「考勤指标（LATE/ABSENT…）→ 统计字段（lateCount/absentCount…）」做成配置映射，
 * 解析器按字段名取值，从而「换指标映射」只改配置、不改解析器（对齐 Mock 的 ATTENDANCE_FIELD 表驱动）。
 * <p>
 * {@code absentCount} / {@code leaveCount} 用 {@link BigDecimal}：P7 请假落地后已批请假按半天粒度
 * 减除缺勤（Mock {@code excludeLeaveDays} 可为 0.5 的倍数），整数类型无法无损承载，故与 Mock 对齐为小数。
 * <p>
 * <b>S2b 班次制新增班次维度字段</b>（方案 §9.2 第 3 项）：{@code requiredShifts}（应出班次）/ {@code attendedShifts}
 * （实出班次）/ {@code leaveShifts}（请假班次）/ {@code absentShifts}（旷工班次），供 {@code PRORATED} 折算取值；
 * {@code absentOrLeaveCount} 为 T1 组合指标（旷工 + 请假），供全勤奖 {@code ABSENT_OR_LEAVE} 判定。
 * <p>
 * <b>历史兼容（方案 §6）</b>：旧账期（{@code month < hrm.algo.payroll.shiftModelFromMonth}）仍按「天」统计，
 * 此时 {@code requiredShifts = attendedShifts = 应到天数}（折算比例恒为 1，basic 不突变），
 * {@code absentOrLeaveCount = absentCount}（全勤奖沿用旧口径）。
 */
public record AttendanceStat(int lateCount,
                             int earlyLeaveCount,
                             BigDecimal absentCount,
                             int abnormalCount,
                             BigDecimal leaveCount,
                             int requiredShifts,
                             int attendedShifts,
                             int leaveShifts,
                             int absentShifts,
                             BigDecimal absentOrLeaveCount) {

    /** 空统计（无考勤数据时按 0，不中断整批算薪） */
    public static AttendanceStat empty() {
        return new AttendanceStat(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 0, 0, 0, BigDecimal.ZERO);
    }

    /**
     * 由班次制汇总构造（S2b 新路径）：{@code absentCount = 旷工班次}、{@code leaveCount = 请假班次}，
     * 组合指标 {@code absentOrLeaveCount = 旷工 + 请假}（T1 全勤奖口径）。
     */
    public static AttendanceStat ofShift(ShiftPayrollPolicy.Result result) {
        BigDecimal absent = BigDecimal.valueOf(result.absentShifts());
        BigDecimal leave = BigDecimal.valueOf(result.leaveShifts());
        return new AttendanceStat(result.lateCount(), result.earlyLeaveCount(), absent, result.abnormalCount(), leave,
                result.requiredShifts(), result.attendedShifts(), result.leaveShifts(), result.absentShifts(),
                absent.add(leave));
    }

    /** 按统计字段名取值（未知名 → 0，等价 Mock {@code attendance[field] || 0}） */
    public BigDecimal byField(String field) {
        if (field == null) {
            return BigDecimal.ZERO;
        }
        return switch (field) {
            case "lateCount" -> BigDecimal.valueOf(lateCount);
            case "earlyLeaveCount" -> BigDecimal.valueOf(earlyLeaveCount);
            case "absentCount" -> absentCount;
            case "abnormalCount" -> BigDecimal.valueOf(abnormalCount);
            case "leaveCount" -> leaveCount;
            case "requiredShifts" -> BigDecimal.valueOf(requiredShifts);
            case "attendedShifts" -> BigDecimal.valueOf(attendedShifts);
            case "leaveShifts" -> BigDecimal.valueOf(leaveShifts);
            case "absentShifts" -> BigDecimal.valueOf(absentShifts);
            case "absentOrLeaveCount" -> absentOrLeaveCount;
            default -> BigDecimal.ZERO;
        };
    }
}
