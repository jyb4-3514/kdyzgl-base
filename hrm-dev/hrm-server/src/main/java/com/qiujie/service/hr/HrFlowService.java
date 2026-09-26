package com.qiujie.service.hr;

import com.qiujie.common.PageResult;
import com.qiujie.dto.hr.HrFlowQuery;
import com.qiujie.dto.hr.HrFlowRejectRequest;
import com.qiujie.dto.hr.HrOffboardingCreateRequest;
import com.qiujie.dto.hr.HrOnboardingApproveRequest;
import com.qiujie.dto.hr.HrOnboardingCreateRequest;
import com.qiujie.dto.hr.HrStepCompleteRequest;
import com.qiujie.vo.hr.HrFlowVO;

/**
 * 入职 / 离职流程服务（10 接口：入职 5 / 离职 5，仅 ADMIN，api.md / Mock {@code routes/hr.js}，架构 §6.2 P5）。
 * <p>
 * 硬性约束：
 * <ul>
 *   <li>步骤序必须与 Mock 常量逐条一致，<b>按序办理守卫不得放宽</b>（跳步拒绝，错误码 9303/9304）；</li>
 *   <li>入职建档先落 {@code employee.status=0}，仅完成最后一步才置 1；驳回时已建档员工一并禁用；</li>
 *   <li>离职「离岗」必须先有结算单（9306）；离岗后员工 status=0 且档案写 leave_date；</li>
 *   <li>离职「SETTLEMENT」步骤经跨域端口创建财务结算单（ADR-03 断环，P6 接入），不直接写财务表。</li>
 * </ul>
 */
public interface HrFlowService {

    /** 入职流程列表（状态/驿站/关键词过滤，创建时间倒序） */
    PageResult<HrFlowVO> listOnboardings(HrFlowQuery query);

    /** 入职流程详情（不存在 404） */
    HrFlowVO onboardingDetail(Long id);

    /** 发起入职流程 */
    HrFlowVO createOnboarding(HrOnboardingCreateRequest request);

    /**
     * R-2：为员工自助注册创建入职审批单（供注册服务在<b>同一事务</b>内调用）。
     * <p>
     * 硬约束：{@code source=SELF_REGISTER}、{@code operatorId/Name=null}（U-13）、{@code role} 恒 {@code STAFF}
     * （注册不可注入角色，M-3）、步骤全 {@code PENDING}；返回新建流程 id（供回填 {@code registration.flow_id}）。
     *
     * @param realName       姓名（→ candidate_name）
     * @param phone          手机号（→ phone）
     * @param intentStationId 意向驿站（→ station_id，仅意向）
     * @param intentPosition 意向岗位（→ position，仅意向，可空）
     */
    Long createSelfRegisterOnboarding(String realName, String phone, Long intentStationId, String intentPosition);

    /**
     * R-6 审批通过（聚合联动，仅 ADMIN）：单事务内按序推进 5 步
     * {@code SUBMIT_MATERIALS → HR_REVIEW → CREATE_ACCOUNT → ASSIGN_STATION → SET_SALARY}
     * （<b>不含 DONE</b>，M-4 不自动激活），并回写 {@code registration} 终态、清空凭据。
     */
    HrFlowVO approveOnboarding(Long id, HrOnboardingApproveRequest request);

    /** 办理入职步骤（按序守卫；建档/分配/定薪/完成各自的副作用） */
    HrFlowVO completeOnboardingStep(Long id, String key, HrStepCompleteRequest request);

    /** 驳回入职流程（已建档员工一并禁用） */
    HrFlowVO rejectOnboarding(Long id, HrFlowRejectRequest request);

    /** 离职流程列表（状态/驿站/关键词过滤，创建时间倒序） */
    PageResult<HrFlowVO> listOffboardings(HrFlowQuery query);

    /** 离职流程详情（不存在 404） */
    HrFlowVO offboardingDetail(Long id);

    /** 发起离职流程（员工须在职且无进行中离职流程） */
    HrFlowVO createOffboarding(HrOffboardingCreateRequest request);

    /** 办理离职步骤（按序守卫；结算经端口创建、离岗前置结算校验） */
    HrFlowVO completeOffboardingStep(Long id, String key, HrStepCompleteRequest request);

    /** 驳回离职流程 */
    HrFlowVO rejectOffboarding(Long id, HrFlowRejectRequest request);
}
