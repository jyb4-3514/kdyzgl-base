package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 打卡概况出参（对齐 Mock {@code attendanceStore.attendanceSummary}）。
 * 六个计数字段与明细维度一一对应；{@code absentCount = max(0, shouldCount − actualCount)}。
 */
@Data
public class AttendanceSummaryVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 应到 = 当天有排班人数 */
    private Integer shouldCount;
    /** 实到 = 有有效上班卡的人数（去重） */
    private Integer actualCount;
    private Integer normalCount;
    private Integer lateCount;
    private Integer earlyLeaveCount;
    /** 缺卡 = 应到 − 实到（不为负） */
    private Integer absentCount;
}
