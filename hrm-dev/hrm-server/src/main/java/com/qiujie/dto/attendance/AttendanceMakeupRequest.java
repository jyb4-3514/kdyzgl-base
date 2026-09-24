package com.qiujie.dto.attendance;

import lombok.Data;

/**
 * 提交补卡入参（POST /attendance/makeup）。
 * 申请人只能是登录人本人；stationId 取登录人归属，不接受前端传参（防代他人申请）。
 */
@Data
public class AttendanceMakeupRequest {

    /** 补卡日期 yyyy-MM-dd（不能晚于今天） */
    private String workDate;

    /** 时段序号 */
    private Integer periodIndex;

    /** ON / OFF */
    private String checkType;

    /** 补卡理由（2-200 字） */
    private String reason;
}
