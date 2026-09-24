package com.qiujie.controller.dashboard;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.service.dashboard.DashboardService;
import com.qiujie.vo.dashboard.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 看板接口（api.md 4.2），仅 ADMIN。
 */
@RequireRoles({"ADMIN"})
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 4.2.1 看板统计：员工总数/驿站数/部门数/今日登录数 */
    @GetMapping("/summary")
    public Result<DashboardVO> summary() {
        return Result.ok(dashboardService.summary());
    }
}
