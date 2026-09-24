package com.qiujie.dto.attendance;

import lombok.Data;

/**
 * 我的排班查询（GET /schedules/my）：{@code weekStart} 可空（默认本周），人员取登录人。
 */
@Data
public class MyScheduleQuery {

    /** 周起始日期 yyyy-MM-dd（可空） */
    private String weekStart;
}
