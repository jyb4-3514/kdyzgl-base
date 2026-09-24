package com.qiujie.controller.finance;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.Result;
import com.qiujie.dto.finance.MyPayrollQuery;
import com.qiujie.dto.finance.PayrollApproveRequest;
import com.qiujie.dto.finance.PayrollGenerateRequest;
import com.qiujie.dto.finance.PayrollItemsUpdateRequest;
import com.qiujie.dto.finance.PayrollObjectionRequest;
import com.qiujie.dto.finance.PayrollPublishRequest;
import com.qiujie.dto.finance.PayrollQuery;
import com.qiujie.dto.finance.PayrollSubmitRequest;
import com.qiujie.service.finance.PayrollService;
import com.qiujie.vo.finance.MyPayrollPageVO;
import com.qiujie.vo.finance.PayrollGenerateVO;
import com.qiujie.vo.finance.PayrollPageVO;
import com.qiujie.vo.finance.PayrollPublishVO;
import com.qiujie.vo.finance.PayrollSubmitVO;
import com.qiujie.vo.finance.PayrollVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工资单接口（10 接口；Mock {@code routes/finance.js}，架构 §6.2 P6）。
 * <p>
 * <b>路由顺序</b>：{@code /payrolls} 下的静态段（{@code my} / {@code generate} / {@code submit} / {@code publish}）
 * 必须优先于 {@code /payrolls/{id}}，否则 {@code GET /payrolls/my} 会被 {@code {id}} 吞成「工资单不存在」。
 * Spring Boot 3 的 {@code PathPatternParser} 按模式特异性排序（字面量段 &gt; 变量段），天然满足，无需人为 @Order
 * （回归断言见 {@code PayrollRouteOrderTest}）。
 * <p>
 * <b>越权口径</b>：{@code my} / {@code {id}} / {@code confirm} / {@code objection} 为任意角色，一律以登录身份过滤
 * （详情：ADMIN 全量，其余角色仅本人且仅已发布/已确认 → 9404/9403）；其余端点仅 ADMIN。
 */
@RestController
@RequestMapping("/api/v1/finance/payrolls")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    // ==================== 员工侧（本人） ====================

    /** 我的工资单（只返回本人已发布/已确认） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/my")
    public Result<MyPayrollPageVO> myPayrolls(@Valid MyPayrollQuery query) {
        return Result.ok(payrollService.myPayrolls(query));
    }

    // ==================== 管理端（ADMIN） ====================

    /** 按月批量生成草稿（同月已提交/已发布 → 9405） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/generate")
    public Result<PayrollGenerateVO> generate(@RequestBody(required = false) PayrollGenerateRequest request) {
        return Result.ok(payrollService.generate(request));
    }

    /** 批量提交审核（ids 非空数组） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/submit")
    public Result<PayrollSubmitVO> submit(@RequestBody(required = false) PayrollSubmitRequest request) {
        return Result.ok(payrollService.submit(request == null ? null : request.getIds()));
    }

    /** 批量发布（ids 或「month + stationId」二选一） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/publish")
    public Result<PayrollPublishVO> publish(@RequestBody(required = false) PayrollPublishRequest request) {
        return Result.ok(payrollService.publish(request));
    }

    /** 工资单列表（附各状态计数） */
    @RequireRoles({"ADMIN"})
    @GetMapping
    public Result<PayrollPageVO> list(@Valid PayrollQuery query) {
        return Result.ok(payrollService.list(query));
    }

    /** 工资单详情（ADMIN 全量；其余角色仅本人且仅已发布/已确认） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @GetMapping("/{id}")
    public Result<PayrollVO> detail(@PathVariable Long id) {
        return Result.ok(payrollService.detail(id));
    }

    /** 修改人工项金额（仅 MANUAL 项，且单据可编辑） */
    @RequireRoles({"ADMIN"})
    @PutMapping("/{id}/items")
    public Result<PayrollVO> updateItems(@PathVariable Long id,
                                         @RequestBody(required = false) PayrollItemsUpdateRequest request) {
        return Result.ok(payrollService.updateItems(id, request == null ? null : request.getItems()));
    }

    /** 审核（approved 布尔值） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/{id}/approve")
    public Result<PayrollVO> approve(@PathVariable Long id,
                                     @RequestBody(required = false) PayrollApproveRequest request) {
        return Result.ok(payrollService.approve(id,
                request == null ? null : request.getApproved(),
                request == null ? null : request.getApproveRemark()));
    }

    /** 员工确认（仅本人 + 已发布） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/{id}/confirm")
    public Result<PayrollVO> confirm(@PathVariable Long id) {
        return Result.ok(payrollService.confirm(id));
    }

    /** 员工提异议（仅本人 + 已发布；退回待审核） */
    @RequireRoles({"ADMIN", "STATION_ADMIN", "STAFF"})
    @PostMapping("/{id}/objection")
    public Result<PayrollVO> objection(@PathVariable Long id,
                                       @RequestBody(required = false) PayrollObjectionRequest request) {
        return Result.ok(payrollService.objection(id, request == null ? null : request.getReason()));
    }
}
