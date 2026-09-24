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
 */
public record AttendanceStat(int lateCount,
                             int earlyLeaveCount,
                             BigDecimal absentCount,
                             int abnormalCount,
                             BigDecimal leaveCount) {

    /** 空统计（无考勤数据时按 0，不中断整批算薪） */
    public static AttendanceStat empty() {
        return new AttendanceStat(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
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
            default -> BigDecimal.ZERO;
        };
    }
}
