package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 我的排班出参（对齐 Mock {@code attendanceStore.mySchedules}）：按周返回本人 7 天的班次明细。
 */
@Data
public class MyScheduleVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate weekEnd;

    /** 本周 7 个日期（周一 → 周日） */
    private List<LocalDate> dates;

    private List<Day> list;

    /** 单日班次明细（未排班时 scheduleId/shiftId 及班次字段均为 null） */
    @Data
    public static class Day {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate workDate;

        private Long scheduleId;
        private Long shiftId;
        private String shiftName;
        private String startTime;
        private String endTime;
        private String color;
        private Integer restMinutes;
    }
}
