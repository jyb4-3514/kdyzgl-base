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

    /** 单日格子：未排班时 scheduleId / shiftId 为 null */
    @Data
    public static class DayCell {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate workDate;

        private Long scheduleId;
        private Long shiftId;
    }
}
