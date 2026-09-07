package com.qiujie.service;

import com.qiujie.vo.DashboardVO;

/**
 * 看板服务（api.md 4.2）。
 */
public interface DashboardService {

    /** 员工总数 / 驿站数 / 部门数 / 今日登录数（去重员工数） */
    DashboardVO summary();
}
