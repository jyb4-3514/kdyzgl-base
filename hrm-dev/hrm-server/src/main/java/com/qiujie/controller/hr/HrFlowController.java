package com.qiujie.controller.hr;

import com.qiujie.annotation.RequireRoles;
import com.qiujie.common.PageResult;
import com.qiujie.common.Result;
import com.qiujie.dto.hr.HrFlowQuery;
import com.qiujie.dto.hr.HrFlowRejectRequest;
import com.qiujie.dto.hr.HrOffboardingCreateRequest;
import com.qiujie.dto.hr.HrOnboardingApproveRequest;
import com.qiujie.dto.hr.HrOnboardingCreateRequest;
import com.qiujie.dto.hr.HrStepCompleteRequest;
import com.qiujie.service.hr.HrFlowService;
import com.qiujie.vo.hr.HrFlowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入职 / 离职流程接口（10 接口，全部仅 ADMIN，api.md / Mock {@code routes/hr.js}，架构 §6.2 P5）。
 * <p>
 * 路由顺序：{@code /hr/onboarding}、{@code /hr/offboarding} 为静态段，与带 {@code /{id}} 的路径段数不同，
 * 不会互相遮蔽；{@code /{id}/steps/{key}/complete} 与 {@code /{id}} 段数不同，同样不冲突
 * （回归断言见 HrRouteOrderTest）。
 */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrFlowController {

    private final HrFlowService hrFlowService;

    // ==================== 入职流程 ====================

    /** 入职流程列表 */
    @RequireRoles({"ADMIN"})
    @GetMapping("/onboarding")
    public Result<PageResult<HrFlowVO>> listOnboardings(@Valid HrFlowQuery query) {
        return Result.ok(hrFlowService.listOnboardings(query));
    }

    /** 发起入职流程 */
    @RequireRoles({"ADMIN"})
    @PostMapping("/onboarding")
    public Result<HrFlowVO> createOnboarding(@RequestBody(required = false) HrOnboardingCreateRequest request) {
        return Result.ok(hrFlowService.createOnboarding(request));
    }

    /** 入职流程详情 */
    @RequireRoles({"ADMIN"})
    @GetMapping("/onboarding/{id}")
    public Result<HrFlowVO> onboardingDetail(@PathVariable Long id) {
        return Result.ok(hrFlowService.onboardingDetail(id));
    }

    /** 办理入职步骤（按序守卫） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/onboarding/{id}/steps/{key}/complete")
    public Result<HrFlowVO> completeOnboardingStep(@PathVariable Long id, @PathVariable String key,
                                                   @RequestBody(required = false) HrStepCompleteRequest request) {
        return Result.ok(hrFlowService.completeOnboardingStep(id, key, request));
    }

    /**
     * R-6 审批通过（聚合联动，仅 ADMIN）：单事务按序推进 5 步（不含 DONE，不自动激活），
     * 回写注册申请终态并清凭据；激活由后续 {@code DONE} 二次确认（U-01）。
     */
    @RequireRoles({"ADMIN"})
    @PostMapping("/onboarding/{id}/approve")
    public Result<HrFlowVO> approveOnboarding(@PathVariable Long id,
                                              @Valid @RequestBody HrOnboardingApproveRequest request) {
        return Result.ok(hrFlowService.approveOnboarding(id, request));
    }

    /** 驳回入职流程 */
    @RequireRoles({"ADMIN"})
    @PostMapping("/onboarding/{id}/reject")
    public Result<HrFlowVO> rejectOnboarding(@PathVariable Long id,
                                             @RequestBody(required = false) HrFlowRejectRequest request) {
        return Result.ok(hrFlowService.rejectOnboarding(id, request));
    }

    // ==================== 离职流程 ====================

    /** 离职流程列表 */
    @RequireRoles({"ADMIN"})
    @GetMapping("/offboarding")
    public Result<PageResult<HrFlowVO>> listOffboardings(@Valid HrFlowQuery query) {
        return Result.ok(hrFlowService.listOffboardings(query));
    }

    /** 发起离职流程 */
    @RequireRoles({"ADMIN"})
    @PostMapping("/offboarding")
    public Result<HrFlowVO> createOffboarding(@RequestBody(required = false) HrOffboardingCreateRequest request) {
        return Result.ok(hrFlowService.createOffboarding(request));
    }

    /** 离职流程详情 */
    @RequireRoles({"ADMIN"})
    @GetMapping("/offboarding/{id}")
    public Result<HrFlowVO> offboardingDetail(@PathVariable Long id) {
        return Result.ok(hrFlowService.offboardingDetail(id));
    }

    /** 办理离职步骤（按序守卫；SETTLEMENT 经跨域端口创建结算单） */
    @RequireRoles({"ADMIN"})
    @PostMapping("/offboarding/{id}/steps/{key}/complete")
    public Result<HrFlowVO> completeOffboardingStep(@PathVariable Long id, @PathVariable String key,
                                                    @RequestBody(required = false) HrStepCompleteRequest request) {
        return Result.ok(hrFlowService.completeOffboardingStep(id, key, request));
    }

    /** 驳回离职流程 */
    @RequireRoles({"ADMIN"})
    @PostMapping("/offboarding/{id}/reject")
    public Result<HrFlowVO> rejectOffboarding(@PathVariable Long id,
                                              @RequestBody(required = false) HrFlowRejectRequest request) {
        return Result.ok(hrFlowService.rejectOffboarding(id, request));
    }
}
