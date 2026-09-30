package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班周矩阵出参（对齐 Mock {@code attendanceStore.querySchedules}）：7 天 × 员工，供 PC 排班表直接铺表格。
 */
@Data
public class ScheduleMatrixVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekEnd;

    /** 本周 7 个日期（周一 → 周日） */
    private List<LocalDate> dates;

    /** 该驿站班次（按开始时间升序） */
    private List<AttendanceShiftVO> shifts;

    private List<EmployeeRow> employees;

    /** 员工行：7 天格子 */
    @Data
    public static class EmployeeRow {
        private Long employeeId;
        private String employeeName;
        private List<DayCell> days;
    }

    /**
     * 单日格子（ARCH-C-3：新增 {@code shiftIds}，向后兼容保留首条）。
     * <p>
     * 未排班时 {@code shiftIds} 为空列表、{@code scheduleId}/{@code shiftId} 为 null；
     * 多班次时 {@code shiftIds} 含全部班次，{@code scheduleId}/{@code shiftId} 取首条（按 id 升序）。
     */
    @Data
    public static class DayCell {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate workDate;

        /** 首条排班 id（兼容旧客户端；多班次下为 id 最小者） */
        private Long scheduleId;
        /** 首条班次 id（兼容旧客户端） */
        private Long shiftId;
        /** 当日全部班次 id（多班次；不排班时为空列表） */
        private List<Long> shiftIds;
    }
}
