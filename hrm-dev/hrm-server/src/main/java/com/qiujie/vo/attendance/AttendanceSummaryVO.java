package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 打卡概况出参（对齐 Mock {@code attendanceStore.attendanceSummary}）。
 * 六个计数字段与明细维度一一对应。
 * <p>
 * <b>B7b 口径</b>（{@code hrm.algo.attendance.absentGranularity=PER_SHIFT}，用户已裁定）：
 * 应到 = 排班班次数（一天两班 = 2）；实到 = 有效上班卡映射到班次后与应到取交（{@code |A∩R|}，多打卡不超额）；
 * 缺卡 = 应到班次中无有效卡的班次数。字段名保持不变以兼容前端契约，仅语义由「人/天」更正为「班次」。
 */
@Data
public class AttendanceSummaryVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 应到 = 当范围排班班次数（一天两班 = 2） */
    private Integer shouldCount;
    /** 实到 = 有效上班卡映射到班次后与应到取交的班次数（{@code |A∩R|}，封顶不超过应到） */
    private Integer actualCount;
    private Integer normalCount;
    private Integer lateCount;
    private Integer earlyLeaveCount;
    /** 缺卡 = 应到班次中无有效卡的班次数（不为负） */
    private Integer absentCount;
}
