package com.qiujie.vo.dashboard;

import lombok.Data;

/**
 * 看板统计（api.md 4.2）：
 * 员工/驿站/部门为未删除计数（含禁用/停用），今日登录数为去重员工数。
 */
@Data
public class DashboardVO {

    private Long employeeTotal;
    private Long stationTotal;
    private Long departmentTotal;
    private Long todayLoginCount;
}
