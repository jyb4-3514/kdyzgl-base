package com.qiujie.controller.kpi;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.kpi.KpiMetricBatchRequest;
import com.qiujie.dto.kpi.KpiMetricRequest;
import com.qiujie.service.kpi.KpiMetricService;
import com.qiujie.vo.kpi.KpiMetricBatchResultVO;
import com.qiujie.vo.kpi.KpiMetricListVO;
import com.qiujie.vo.kpi.KpiMetricVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KPI 指标配置接口（5 接口，仅 ADMIN；api.md / Mock {@code routes/kpi.js}，架构 §6.2 P4）。
 * <p>
 * 路由顺序：{@code /kpi/metrics/batch} 为字面量段，Spring Boot 3 的 {@code PathPatternParser} 按模式特异性排序
 * （字面量段 &gt; 变量段），天然优先于 {@code /kpi/metrics/{id}}，无需人为 @Order（回归断言见 KpiRouteOrderTest）。
 */
@RestController
@RequestMapping("/api/v1/kpi/metrics")
@RequiredArgsConstructor
public class KpiMetricController {

    private final KpiMetricService kpiMetricService;

    /** 指标列表（含启用权重合计） */
    @RequireRoles({"ADMIN"})
    @GetMapping
    public Result<KpiMetricListVO> list() {
        return Result.ok(kpiMetricService.list());
    }

    /** 新增指标 */
    @RequireRoles({"ADMIN"})
    @PostMapping
    public Result<KpiMetricVO> create(@RequestBody(required = false) KpiMetricRequest request) {
        return Result.ok(kpiMetricService.create(request));
    }

    /** 批量保存（权重 + 启用状态），原子提交（字面量端点，优先于 /{id}） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/batch")
    public Result<KpiMetricBatchResultVO> saveBatch(@RequestBody(required = false) KpiMetricBatchRequest request) {
        return Result.ok(kpiMetricService.saveBatch(request));
    }

    /** 编辑指标 */
    @RequireRoles({"ADMIN"})
    @PutMapping("/{id}")
    public Result<KpiMetricVO> update(@PathVariable Long id,
                                      @RequestBody(required = false) KpiMetricRequest request) {
        return Result.ok(kpiMetricService.update(id, request));
    }

    /** 删除指标（逻辑删除） */
    @RequireRoles({"ADMIN"})
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        kpiMetricService.remove(id);
        return Result.ok();
    }
}
