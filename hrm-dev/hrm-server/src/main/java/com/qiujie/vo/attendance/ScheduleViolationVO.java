package com.qiujie.vo.attendance;

import lombok.Data;

/**
 * 排班违规项（算法失败降级时随贪心解返回，站长可见「哪天/哪班缺人」）。
 */
@Data
public class ScheduleViolationVO {

    /** MIN_STAFF（每日每班最少在岗不足）/ CONSECUTIVE（连续工作超限） */
    private String rule;

    /** 0 基天序号 */
    private Integer dayIndex;

    /** 0 基班次序号（CONSECUTIVE 时为 null） */
    private Integer shiftIndex;

    /** 涉及的员工（CONSECUTIVE 时为其在入参员工列表中的下标） */
    private Integer employeeIndex;

    private String detail;
}
