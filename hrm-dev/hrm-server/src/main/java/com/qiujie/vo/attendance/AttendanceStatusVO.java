package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 今日打卡状态出参（对齐 Mock {@code attendanceStore.todayStatus}）。
 * <p>
 * 未排班时 {@code shift} 回落为规则合成的兜底班次（{@code hasSchedule=false}）；
 * {@code periods} 按时段展开，窗口（windowStart/windowEnd）直接下发，员工端不必再自存余量常量。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AttendanceStatusVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate workDate;

    /** 今日是否有排班 */
    private Boolean hasSchedule;
    /** 今日班次（未排班时为规则兜底班次；无规则时为 null） */
    private AttendanceShiftVO shift;
    private Boolean onChecked;
    private Boolean offChecked;
    /** 今日最近一次有效上班卡（无则 null）；与 records 行结构一致 */
    private AttendanceRecordVO onRecord;
    private AttendanceRecordVO offRecord;
    /** 规则要求的每日打卡次数（无规则时 null） */
    private Integer checkFrequency;
    /** 规则要求摘要（无规则时 null） */
    private String requireSummary;
    private List<PeriodStatus> periods;
    /** 当前驿站打卡规则（无规则时 null） */
    private AttendanceRuleVO rule;

    /** 时段打卡状态（频次为 4 时员工端渲染 4 个打卡按钮） */
    @Data
    public static class PeriodStatus {
        private Integer periodIndex;
        private String name;
        private String startTime;
        private String endTime;
        /** 时间窗起（时段开始 − allowEarlyMin） */
        private String windowStart;
        /** 时间窗止（时段结束 + allowLateMin） */
        private String windowEnd;
        private Boolean onChecked;
        private Boolean offChecked;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime onTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime offTime;
    }
}
