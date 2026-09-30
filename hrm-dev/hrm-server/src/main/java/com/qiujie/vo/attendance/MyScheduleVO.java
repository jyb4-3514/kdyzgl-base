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

    /**
     * 单日班次明细（ARCH-C-4：新增 {@code shifts} 列表，向后兼容保留首条扁平字段）。
     * <p>
     * 未排班时 {@code shifts} 为空列表、扁平字段均为 null；多班次时 {@code shifts} 含全部班次，
     * 扁平字段取首条（按 id 升序）。
     */
    @Data
    public static class Day {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate workDate;

        /** 首条排班 id（兼容旧客户端） */
        private Long scheduleId;
        /** 首条班次 id（兼容旧客户端） */
        private Long shiftId;
        private String shiftName;
        private String startTime;
        private String endTime;
        private String color;
        private Integer restMinutes;

        /** 当日全部班次明细（多班次；不排班时为空列表） */
        private List<ShiftDetail> shifts;
    }

    /** 单班次明细（我的排班出参元素） */
    @Data
    public static class ShiftDetail {
        private Long scheduleId;
        private Long shiftId;
        private String shiftName;
        private String startTime;
        private String endTime;
        private String color;
        private Integer restMinutes;
    }
}
