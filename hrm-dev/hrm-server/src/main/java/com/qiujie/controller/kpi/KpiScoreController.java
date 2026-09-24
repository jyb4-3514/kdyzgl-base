package com.qiujie.controller.kpi;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.kpi.KpiCalculateRequest;
import com.qiujie.dto.kpi.KpiRankingQuery;
import com.qiujie.dto.kpi.KpiScoreQuery;
import com.qiujie.service.kpi.KpiScoreService;
import com.qiujie.vo.kpi.KpiCalculateResultVO;
import com.qiujie.vo.kpi.KpiRankingPageVO;
import com.qiujie.vo.kpi.KpiScoreDetailVO;
import com.qiujie.vo.kpi.KpiScorePageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI 算分与评分查询接口（4 接口；api.md / Mock {@code routes/kpi.js}，架构 §6.2 P4）。
 * <p>
 * 路由顺序：{@code /kpi/scores/ranking} 为字面量段，优先于 {@code /kpi/scores/{employeeId}}（变量段）；
 * Spring Boot 3 的 {@code PathPatternParser} 按模式特异性排序，无需人为 @Order（回归断言见 KpiRouteOrderTest）。
 * <p>
 * 数据范围：{@code ranking}/{@code scores} 非 ADMIN 静默收敛为本人驿站（{@code StationScopedQuery}）；
 * {@code scores/{employeeId}} 员工查他人 / 站长跨站 → 403（策略由 Service 内按端点口径判定）。
 */
@RestController
@RequestMapping("/api/v1/kpi/scores")
@RequiredArgsConstructor
public class KpiScoreController {

    private final KpiScoreService kpiScoreService;

    /** 按月算分（仅 ADMIN；覆盖重建，幂等） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/calculate")
    public Result<KpiCalculateResultVO> calculate(@RequestBody(required = false) KpiCalculateRequest request) {
        return Result.ok(kpiScoreService.calculate(request));
    }

    /** 排名榜（ADMIN/STATION_ADMIN；字面量端点，优先于 /{employeeId}） */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping("/ranking")
    public Result<KpiRankingPageVO> ranking(@Valid KpiRankingQuery query) {
        return Result.ok(kpiScoreService.ranking(query));
    }

    /** 得分列表（ADMIN/STATION_ADMIN） */
    @RequireRoles({"ADMIN", "STATION_ADMIN"})
    @GetMapping
    public Result<KpiScorePageVO> list(@Valid KpiScoreQuery query) {
        return Result.ok(kpiScoreService.list(query));
    }

    /** 某员工某月得分明细（任意登录角色；员工限本人、站长限本驿站，越权 403） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/{employeeId}")
    public Result<KpiScoreDetailVO> detail(@PathVariable Long employeeId, String month) {
        return Result.ok(kpiScoreService.detail(employeeId, month));
    }
}
