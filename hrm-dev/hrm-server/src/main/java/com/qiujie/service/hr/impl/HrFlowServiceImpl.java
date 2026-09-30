package com.qiujie.service.hr.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.common.PageResult;
import com.qiujie.config.HrProperties;
import com.qiujie.dto.employee.EmployeeStatusRequest;
import com.qiujie.dto.hr.HrFlowQuery;
import com.qiujie.dto.hr.HrFlowRejectRequest;
import com.qiujie.dto.hr.HrOffboardingCreateRequest;
import com.qiujie.dto.hr.HrOnboardingApproveRequest;
import com.qiujie.dto.hr.HrOnboardingCreateRequest;
import com.qiujie.dto.hr.HrStepCompleteRequest;
import com.qiujie.dto.hr.SalaryAllowanceItem;
import com.qiujie.entity.Employee;
import com.qiujie.entity.EmployeeRegistration;
import com.qiujie.entity.HrAllowance;
import com.qiujie.entity.HrFlow;
import com.qiujie.entity.HrFlowStep;
import com.qiujie.entity.HrProfile;
import com.qiujie.entity.HrSalary;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.EmployeeRegistrationMapper;
import com.qiujie.mapper.HrFlowMapper;
import com.qiujie.mapper.HrFlowStepMapper;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.employee.EmployeeService;
import com.qiujie.service.hr.HrFlowService;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementPort;
import com.qiujie.service.hr.port.PayrollSettlementRef;
import com.qiujie.service.audit.OperationAuditWriter;
import com.qiujie.service.hr.support.HrConstants;
import com.qiujie.service.hr.support.HrFlowStepGuard;
import com.qiujie.service.hr.support.HrSalaryValidator;
import com.qiujie.service.hr.support.HrValidateSupport;
import com.qiujie.service.hr.support.PositionConstants;
import com.qiujie.service.registration.support.RegistrationConstants;
import com.qiujie.service.registration.support.RegistrationRetentionPolicy;
import com.qiujie.util.DesensitizeUtil;
import com.qiujie.util.FieldValidator;
import com.qiujie.util.PasswordUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.hr.HrFlowProgressVO;
import com.qiujie.vo.hr.HrFlowStepVO;
import com.qiujie.vo.hr.HrFlowVO;
import com.qiujie.vo.registration.RegistrationSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入职 / 离职流程服务实现（对齐 Mock {@code hrStore} 流程段 + {@code routes/hr.js}）。
 * <p>
 * 关键对齐点：
 * <ul>
 *   <li>步骤序取自 {@link HrConstants}，与 Mock 常量逐条一致；按序守卫走 {@link HrFlowStepGuard}（跳步拒绝）；</li>
 *   <li>入职建档先落 {@code status=0}，仅「完成」步骤置 1；驳回时已建档员工一并禁用；</li>
 *   <li>离职「离岗」必须先有结算单（9306）；离岗后员工 status=0 且档案写 leave_date；</li>
 *   <li>离职「SETTLEMENT」经 {@link PayrollSettlementPort} 创建结算单（ADR-03 断环），不直接写财务表。</li>
 * </ul>
 * 员工状态变更复用 {@link EmployeeService#changeStatus}（获得「禁用即强制下线」的既有副作用），
 * 仅备注字段另行走 Mapper 更新（changeStatus 不承载 remark）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrFlowServiceImpl implements HrFlowService {

    private static final DateTimeFormatter FLOW_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HrFlowMapper hrFlowMapper;
    private final HrFlowStepMapper hrFlowStepMapper;
    private final EmployeeRegistrationMapper registrationMapper;
    private final HrProfileMapper hrProfileMapper;
    private final HrSalaryMapper hrSalaryMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final StationMapper stationMapper;
    private final EmployeeService employeeService;
    private final HrSalaryWriter hrSalaryWriter;
    private final PayrollSettlementPort payrollSettlementPort;
    private final HrProperties hrProperties;
    /** 操作审计留痕出口（ARCH-S-2 / §3.3 #7/#7b；入职建档与分配驿站为间接账号写路径） */
    private final OperationAuditWriter operationAuditWriter;

    // ==================== 入职流程 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<HrFlowVO> listOnboardings(HrFlowQuery query) {
        return listFlows(HrConstants.FLOW_TYPE_ONBOARDING, query);
    }

    @Override
    @Transactional(readOnly = true)
    public HrFlowVO onboardingDetail(Long id) {
        HrFlowVO vo = toFlowVO(requireFlow(id, HrConstants.FLOW_TYPE_ONBOARDING));
        // R-8：审批详情附 registration 子对象（仅明细，避免列表 N+1；出参脱敏面在 R-3）
        vo.setRegistration(registrationSummary(vo.getId()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO createOnboarding(HrOnboardingCreateRequest request) {
        validateOnboardingCreate(request);
        HrFlow flow = new HrFlow();
        flow.setFlowType(HrConstants.FLOW_TYPE_ONBOARDING);
        flow.setCandidateName(trim(request.getCandidateName()));
        flow.setPhone(trim(request.getPhone()));
        flow.setGender(request.getGender() == null ? 0 : request.getGender());
        flow.setEducation(blankToNull(request.getEducation()));
        flow.setDeptId(request.getDeptId());
        flow.setStationId(request.getStationId());
        flow.setPosition(blankToNull(request.getPosition()));
        flow.setRole("STAFF");
        flow.setExpectedEntryDate(request.getExpectedEntryDate() == null || request.getExpectedEntryDate().isBlank()
                ? LocalDate.now() : LocalDate.parse(request.getExpectedEntryDate().trim()));
        flow.setRemark(blankToNull(request.getRemark()));
        flow.setStatus(HrConstants.FLOW_STATUS_IN_PROGRESS);
        flow.setSource(HrConstants.FLOW_SOURCE_ADMIN);
        flow.setOperatorId(UserContext.getUserId());
        flow.setOperatorName(currentOperatorName());
        insertFlowWithSteps(flow, HrConstants.ONBOARDING_STEPS);
        return toFlowVO(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSelfRegisterOnboarding(String realName, String phone, Long intentStationId, String intentPosition) {
        HrFlow flow = new HrFlow();
        flow.setFlowType(HrConstants.FLOW_TYPE_ONBOARDING);
        flow.setCandidateName(trim(realName));
        flow.setPhone(trim(phone));
        flow.setGender(0);
        flow.setStationId(intentStationId);
        flow.setPosition(blankToNull(intentPosition));
        // 角色恒 STAFF：注册不可注入角色（M-3/§5.4-2）
        flow.setRole("STAFF");
        flow.setExpectedEntryDate(LocalDate.now());
        flow.setStatus(HrConstants.FLOW_STATUS_IN_PROGRESS);
        // M-9：自助来源可区分；U-13：无后台操作人（留痕走 source + submit 时间）
        flow.setSource(HrConstants.FLOW_SOURCE_SELF_REGISTER);
        flow.setOperatorId(null);
        flow.setOperatorName(null);
        insertFlowWithSteps(flow, HrConstants.ONBOARDING_STEPS);
        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO approveOnboarding(Long id, HrOnboardingApproveRequest request) {
        validateApproveRequest(request);
        // 事务首条：锁 hr_flow 单行（§11.7 固定加锁顺序 ①hr_flow → ②registration），消除并发双建/双定薪
        HrFlow flow = hrFlowMapper.selectByIdForUpdate(id);
        if (flow == null || !HrConstants.FLOW_TYPE_ONBOARDING.equals(flow.getFlowType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入职流程不存在");
        }
        // 重复审批 → 9303（既有一致口径）
        if (!HrConstants.FLOW_STATUS_IN_PROGRESS.equals(flow.getStatus())) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID,
                    "流程已" + nullToEmpty(HrConstants.flowStatusLabel(flow.getStatus())) + "，不可审批");
        }
        // ② registration 行锁：仅自助注册流程支持聚合审批（申请侧状态守卫 9309）
        EmployeeRegistration registration = registrationMapper.selectByFlowIdForUpdate(flow.getId());
        if (registration == null) {
            throw new BusinessException(ErrorCode.REGISTRATION_STATUS_INVALID, "该流程无关联注册申请，不支持聚合审批");
        }
        if (!RegistrationConstants.STATUS_SUBMITTED.equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.REGISTRATION_STATUS_INVALID);
        }
        // 惰性超时（§1.3：查询/审批前判）：已过失效基准的申请不得再审批（终态由清理任务落库）
        if (RegistrationRetentionPolicy.isExpired(registration.getExpireTime(), LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REGISTRATION_STATUS_INVALID, "申请已超时失效");
        }

        // 按序推进 5 步（复用既有 completeOnboardingStep，内部含 markStepDone；不含 DONE，M-4 不激活）
        completeOnboardingStep(flow.getId(), "SUBMIT_MATERIALS", null);
        completeOnboardingStep(flow.getId(), "HR_REVIEW", null);
        completeOnboardingStep(flow.getId(), HrConstants.ONBOARD_CREATE_ACCOUNT, createBodyForApprove(request, flow));
        completeOnboardingStep(flow.getId(), HrConstants.ONBOARD_ASSIGN_STATION, assignBodyForApprove(request));
        completeOnboardingStep(flow.getId(), HrConstants.ONBOARD_SET_SALARY, salaryBodyForApprove(request));

        // 回读推进后流程：employeeId 已回填、currentStepKey==DONE、status 仍 IN_PROGRESS、employee.status 仍 0
        HrFlow updated = hrFlowMapper.selectById(flow.getId());
        // registration 终态回写 + 清凭据（与主流程同事务，全成功或全回滚）
        registration.setStatus(RegistrationConstants.STATUS_APPROVED);
        registration.setApprovedEmployeeId(updated == null ? null : updated.getEmployeeId());
        registration.setApproveTime(LocalDateTime.now());
        registration.setPasswordHash(null);
        registration.setQueryTokenHash(null);
        registrationMapper.updateById(registration);
        log.info("注册申请审批通过：applyNo={}, flowNo={}, employeeId={}", registration.getApplyNo(),
                updated == null ? null : updated.getFlowNo(), updated == null ? null : updated.getEmployeeId());
        return toFlowVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO completeOnboardingStep(Long id, String key, HrStepCompleteRequest request) {
        HrStepCompleteRequest safe = request == null ? new HrStepCompleteRequest() : request;
        validateStepRequest(key, safe);
        HrFlow flow = requireFlow(id, HrConstants.FLOW_TYPE_ONBOARDING);
        List<HrFlowStep> steps = loadSteps(flow.getId());
        // 按序办理守卫：跳步/重复办理/状态非法统一 9303（文案对齐 Mock）
        String guardError = HrFlowStepGuard.checkOrder(flow.getStatus(), toStepStates(steps), key);
        if (guardError != null) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID, guardError);
        }

        switch (key) {
            case HrConstants.ONBOARD_CREATE_ACCOUNT -> createEmployeeForFlow(flow, safe);
            case HrConstants.ONBOARD_ASSIGN_STATION -> assignForFlow(flow, safe);
            case HrConstants.ONBOARD_SET_SALARY -> salaryForFlow(flow, safe);
            case HrConstants.ONBOARD_DONE -> finishOnboarding(flow);
            default -> {
                // 其余步骤（提交资料 / 人事审核）无副作用，仅标记完成
            }
        }
        markStepDone(flow, steps, key, safe.getRemark());
        return toFlowVO(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO rejectOnboarding(Long id, HrFlowRejectRequest request) {
        String reason = validateReject(request);
        // 首条锁 hr_flow（§11.7 固定加锁顺序 ①hr_flow → ②registration）
        HrFlow flow = hrFlowMapper.selectByIdForUpdate(id);
        if (flow == null || !HrConstants.FLOW_TYPE_ONBOARDING.equals(flow.getFlowType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入职流程不存在");
        }
        if (!HrConstants.FLOW_STATUS_IN_PROGRESS.equals(flow.getStatus())) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID,
                    "流程已" + nullToEmpty(HrConstants.flowStatusLabel(flow.getStatus())) + "，不可驳回");
        }
        // R-9：自助注册流程同事务联动申请单（存在则校验状态为 SUBMITTED，否则 9309）
        EmployeeRegistration registration = registrationMapper.selectByFlowIdForUpdate(flow.getId());
        if (registration != null && !RegistrationConstants.STATUS_SUBMITTED.equals(registration.getStatus())) {
            throw new BusinessException(ErrorCode.REGISTRATION_STATUS_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        flow.setStatus(HrConstants.FLOW_STATUS_REJECTED);
        flow.setRejectReason(reason);
        flow.setRejectedBy(currentOperatorName());
        flow.setRejectedTime(now);
        hrFlowMapper.updateById(flow);
        // 已建档员工一并禁用：流程被驳回却留着可登录账号属于自相矛盾的数据
        if (flow.getEmployeeId() != null) {
            updateEmployeeStatus(flow.getEmployeeId(), 0, "入职流程 " + flow.getFlowNo() + " 已驳回");
        }
        if (registration != null) {
            registration.setStatus(RegistrationConstants.STATUS_REJECTED);
            registration.setRejectReason(reason);
            // 终态清凭据（§2.2 凭据卫生）
            registration.setPasswordHash(null);
            registration.setQueryTokenHash(null);
            registrationMapper.updateById(registration);
        }
        return toFlowVO(flow);
    }

    // ==================== 离职流程 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<HrFlowVO> listOffboardings(HrFlowQuery query) {
        return listFlows(HrConstants.FLOW_TYPE_OFFBOARDING, query);
    }

    @Override
    @Transactional(readOnly = true)
    public HrFlowVO offboardingDetail(Long id) {
        return toFlowVO(requireFlow(id, HrConstants.FLOW_TYPE_OFFBOARDING));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO createOffboarding(HrOffboardingCreateRequest request) {
        validateOffboardingCreate(request);
        Employee employee = employeeMapper.selectById(request.getEmployeeId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        // 发起条件：在职（status=1）且无进行中的离职流程（对齐 Mock 文案：含「账号已禁用」）
        if (employee.getStatus() == null || employee.getStatus() != 1) {
            throw new BusinessException(ErrorCode.HR_EMPLOYEE_RESIGNED, "该员工已离职或账号已禁用，不可发起离职");
        }
        if (hasInProgressOffboarding(employee.getId())) {
            throw new BusinessException(ErrorCode.HR_OFFBOARDING_STATUS_INVALID, "该员工已有进行中的离职流程");
        }

        HrFlow flow = new HrFlow();
        flow.setFlowType(HrConstants.FLOW_TYPE_OFFBOARDING);
        flow.setEmployeeId(employee.getId());
        flow.setStationId(employee.getStationId());
        flow.setType(request.getType());
        flow.setReason(trim(request.getReason()));
        flow.setLastWorkDate(LocalDate.parse(request.getLastWorkDate().trim()));
        flow.setStatus(HrConstants.FLOW_STATUS_IN_PROGRESS);
        flow.setSource(HrConstants.FLOW_SOURCE_ADMIN);
        flow.setOperatorId(UserContext.getUserId());
        flow.setOperatorName(currentOperatorName());
        insertFlowWithSteps(flow, HrConstants.OFFBOARDING_STEPS);
        return toFlowVO(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO completeOffboardingStep(Long id, String key, HrStepCompleteRequest request) {
        HrStepCompleteRequest safe = request == null ? new HrStepCompleteRequest() : request;
        validateStepRequest(key, safe);
        HrFlow flow = requireFlow(id, HrConstants.FLOW_TYPE_OFFBOARDING);
        List<HrFlowStep> steps = loadSteps(flow.getId());
        String guardError = HrFlowStepGuard.checkOrder(flow.getStatus(), toStepStates(steps), key);
        if (guardError != null) {
            throw new BusinessException(ErrorCode.HR_OFFBOARDING_STATUS_INVALID, guardError);
        }

        if (HrConstants.OFFBOARD_SETTLEMENT.equals(key)) {
            settleForFlow(flow);
        } else if (HrConstants.OFFBOARD_LEAVE.equals(key)) {
            leaveForFlow(flow);
        }
        markStepDone(flow, steps, key, safe.getRemark());
        return toFlowVO(flow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrFlowVO rejectOffboarding(Long id, HrFlowRejectRequest request) {
        String reason = validateReject(request);
        HrFlow flow = requireFlow(id, HrConstants.FLOW_TYPE_OFFBOARDING);
        if (!HrConstants.FLOW_STATUS_IN_PROGRESS.equals(flow.getStatus())) {
            throw new BusinessException(ErrorCode.HR_OFFBOARDING_STATUS_INVALID,
                    "流程已" + nullToEmpty(HrConstants.flowStatusLabel(flow.getStatus())) + "，不可驳回");
        }
        LocalDateTime now = LocalDateTime.now();
        flow.setStatus(HrConstants.FLOW_STATUS_REJECTED);
        flow.setRejectReason(reason);
        flow.setRejectedBy(currentOperatorName());
        flow.setRejectedTime(now);
        hrFlowMapper.updateById(flow);
        return toFlowVO(flow);
    }

    // ==================== 入职步骤副作用 ====================

    /** 建档并生成员工与账号：员工先落 status=0（未生效），最后一步才转在职 */
    private void createEmployeeForFlow(HrFlow flow, HrStepCompleteRequest body) {
        if (flow.getEmployeeId() != null) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID, "该流程已生成员工，不可重复建档");
        }
        String username = trim(body.getUsername());
        if (!FieldValidator.isValidUsername(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录账号须为字母开头、4-30 位字母数字下划线");
        }
        if (usernameExists(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // M-5：建档补手机号活跃查重（与 V16 DB 活跃唯一双保险；DB 约束为最终防线）
        if (phoneExistsActive(flow.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        if (!FieldValidator.isStrongPassword(body.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "初始密码须为 8-20 位且同时包含字母和数字");
        }
        Long deptId = body.getDeptId() != null ? body.getDeptId() : flow.getDeptId();
        if (deptId == null || departmentMapper.selectById(deptId) == null) {
            throw new BusinessException(ErrorCode.DEPT_NOT_FOUND);
        }
        Long stationId = body.getStationId() != null ? body.getStationId() : flow.getStationId();
        Station station = stationId == null ? null : stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        if (station.getStatus() == null || station.getStatus() != 1) {
            throw new BusinessException(ErrorCode.STATION_DISABLED);
        }

        Employee employee = new Employee();
        employee.setUsername(username);
        // 密码一律 BCrypt 存储（Mock 存明文，服务端不可照搬）
        employee.setPassword(PasswordUtil.hash(body.getPassword()));
        employee.setRealName(flow.getCandidateName());
        employee.setPhone(flow.getPhone());
        employee.setGender(flow.getGender() == null ? 0 : flow.getGender());
        employee.setDeptId(deptId);
        employee.setStationId(stationId);
        employee.setRole(flow.getRole() == null ? "STAFF" : flow.getRole());
        employee.setStatus(0);            // 建档未生效
        employee.setPwdChanged(0);        // 首登强制改密
        employee.setEntryDate(flow.getExpectedEntryDate());
        employee.setRemark("入职流程 " + flow.getFlowNo() + " 转入");
        employeeMapper.insert(employee);

        // 审计留痕（§3.3 #7）：入职流程建档开户 = 账号的「增」，属 A-⑥ 范围；口令不涉及（只留流程号）
        operationAuditWriter.record(OperationAuditWriter.TARGET_EMPLOYEE, employee.getId(), employee.getRealName(),
                OperationAuditWriter.ACTION_CREATE, null,
                Map.of("flowNo", flow.getFlowNo() == null ? "" : flow.getFlowNo()));

        flow.setEmployeeId(employee.getId());
        flow.setDeptId(deptId);
        flow.setStationId(stationId);
        hrFlowMapper.updateById(flow);

        int probationMonths = body.getProbationMonths() == null
                ? hrProperties.getDefaultProbationMonths() : body.getProbationMonths();
        LocalDate entryDate = flow.getExpectedEntryDate() == null ? LocalDate.now() : flow.getExpectedEntryDate();
        HrProfile profile = new HrProfile();
        profile.setEmployeeId(employee.getId());
        profile.setEducation(flow.getEducation());
        profile.setContractType(body.getContractType() == null ? "FIXED_TERM" : body.getContractType());
        profile.setContractStart(entryDate);
        profile.setContractEnd(entryDate.plusMonths(hrProperties.getDefaultContractMonths()));
        profile.setProbationMonths(probationMonths);
        profile.setProbationEnd(entryDate.plusMonths(probationMonths));
        profile.setRegularDate(entryDate.plusMonths(probationMonths));
        hrProfileMapper.insert(profile);

        // 定薪步骤要求「先有定薪档案再调整」（saveSalary 对无档案员工回 9305）：
        // 建档时同步落一条 0 值定薪行，否则流程会卡在「定薪」步骤永远走不到「完成」
        HrSalary salary = new HrSalary();
        salary.setEmployeeId(employee.getId());
        salary.setBasicSalary(java.math.BigDecimal.ZERO);
        salary.setPostSalary(java.math.BigDecimal.ZERO);
        salary.setPerformanceBase(java.math.BigDecimal.ZERO);
        salary.setAllowances(List.of());
        salary.setAllowancesTotal(java.math.BigDecimal.ZERO);
        salary.setTotalSalary(java.math.BigDecimal.ZERO);
        salary.setEffectiveDate(entryDate);
        hrSalaryMapper.insert(salary);
    }

    private void assignForFlow(HrFlow flow, HrStepCompleteRequest body) {
        Employee employee = flow.getEmployeeId() == null ? null : employeeMapper.selectById(flow.getEmployeeId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID, "尚未建档生成员工，无法分配驿站/岗位");
        }
        // 审计留痕（§3.3 #7b）：分配驿站/岗位为账号的「改」，变更前快照先取
        Map<String, Object> before = assignmentSnapshot(employee.getStationId(), employee.getPosition(),
                employee.getRole());
        if (body.getDeptId() != null) {
            if (departmentMapper.selectById(body.getDeptId()) == null) {
                throw new BusinessException(ErrorCode.DEPT_NOT_FOUND);
            }
            employee.setDeptId(body.getDeptId());
        }
        if (body.getStationId() != null) {
            Station station = stationMapper.selectById(body.getStationId());
            if (station == null) {
                throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
            }
            if (station.getStatus() == null || station.getStatus() != 1) {
                throw new BusinessException(ErrorCode.STATION_DISABLED);
            }
            employee.setStationId(body.getStationId());
        }
        // 岗位双写唯一入口（U-07/§11.9）：employee.position 为权威事实，hr_flow.position 为流程留痕；
        // 禁止他处单独写 employee.position（防注册侧岗位注入）。
        // 必填 + 枚举白名单（用户裁定：店员 / 站长 / 管理员）：取值非法会让出参与数据同时不可解释
        String position = trim(body.getPosition());
        if (HrValidateSupport.isBlank(position)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写岗位");
        }
        if (!PositionConstants.isValid(position)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PositionConstants.invalidMessage());
        }
        employee.setPosition(position);
        flow.setPosition(position);
        if (body.getRole() != null) {
            if (!HrConstants.ASSIGNABLE_ROLES.contains(body.getRole())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "role 仅支持 STATION_ADMIN / STAFF");
            }
            employee.setRole(body.getRole());
            flow.setRole(body.getRole());
        }
        employeeMapper.updateById(employee);
        hrFlowMapper.updateById(flow);

        // 审计留痕（§3.3 #7b）：目标名取员工姓名；变更字段由 Writer 按差异剪裁（仅列出真正变化的项）
        operationAuditWriter.record(OperationAuditWriter.TARGET_EMPLOYEE, employee.getId(), employee.getRealName(),
                OperationAuditWriter.ACTION_UPDATE, before,
                assignmentSnapshot(employee.getStationId(), employee.getPosition(), employee.getRole()));
    }

    /** 分配快照（审计白名单键：驿站 / 岗位 / 角色） */
    private Map<String, Object> assignmentSnapshot(Long stationId, String position, String role) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stationId", stationId);
        map.put("position", position);
        map.put("role", role);
        return map;
    }

    private void salaryForFlow(HrFlow flow, HrStepCompleteRequest body) {
        if (flow.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID, "尚未建档生成员工，无法定薪");
        }
        LocalDate effectiveDate = (body.getEffectiveDate() == null || body.getEffectiveDate().isBlank())
                ? flow.getExpectedEntryDate() : LocalDate.parse(body.getEffectiveDate().trim());
        // 定薪操作人取当前办理人（Mock 误用流程创建人作为操作人，服务端修正为「谁办理谁留痕」）
        hrSalaryWriter.save(flow.getEmployeeId(),
                new HrSalaryWriter.Payload(body.getBasicSalary(), body.getPostSalary(), body.getPerformanceBase(),
                        toAllowances(body.getAllowances()), effectiveDate, body.getReason()),
                UserContext.getUserId(), currentOperatorName(), HrConstants.CHANGE_TYPE_ENTRY);
    }

    /** 完成入职：员工转在职（这是「全部办完才真正落库为在职员工」的最后一环） */
    private void finishOnboarding(HrFlow flow) {
        if (flow.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.HR_ONBOARDING_STATUS_INVALID, "尚未建档生成员工，无法完成入职");
        }
        Employee employee = employeeMapper.selectById(flow.getEmployeeId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        updateEmployeeStatus(flow.getEmployeeId(), 1, employee.getRemark());
    }

    // ==================== 离职步骤副作用 ====================

    /** 薪资结算：经跨域端口创建结算单（草稿），回填引用；失败即 9306，流程不推进 */
    private void settleForFlow(HrFlow flow) {
        LocalDate base = flow.getLastWorkDate() == null ? LocalDate.now() : flow.getLastWorkDate();
        PayrollSettlementCommand command = new PayrollSettlementCommand();
        command.setEmployeeId(flow.getEmployeeId());
        command.setMonth(base.toString().substring(0, 7));
        command.setOffboardingId(flow.getId());
        command.setRemark("离职薪资结算（最后工作日 " + flow.getLastWorkDate() + "）");
        PayrollSettlementRef ref = payrollSettlementPort.createSettlement(command);
        if (ref == null || ref.getPayrollId() == null) {
            throw new BusinessException(ErrorCode.HR_SETTLEMENT_UNFINISHED, "薪资结算单创建失败");
        }
        flow.setSettlementPayrollId(ref.getPayrollId());
        flow.setSettlementPayrollNo(ref.getPayrollNo());
        flow.setSettlementAmount(ref.getAmount());
        hrFlowMapper.updateById(flow);
    }

    /**
     * 离岗：必须已有薪资结算单（9306），离岗后员工置 status=0 + 档案写 leave_date。
     * 不用 is_deleted：离职员工的历史工资单与考勤记录仍需可追溯，软删会让详情页查不到人。
     */
    private void leaveForFlow(HrFlow flow) {
        if (flow.getSettlementPayrollId() == null) {
            throw new BusinessException(ErrorCode.HR_SETTLEMENT_UNFINISHED);
        }
        Employee employee = employeeMapper.selectById(flow.getEmployeeId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        updateEmployeeStatus(flow.getEmployeeId(), 0,
                "已离职（" + nullToEmpty(HrConstants.offboardingTypeLabel(flow.getType())) + "）");
        flow.setLeaveDate(flow.getLastWorkDate());
        hrFlowMapper.updateById(flow);

        HrProfile profile = findProfile(flow.getEmployeeId());
        if (profile != null) {
            profile.setLeaveDate(flow.getLastWorkDate());
            hrProfileMapper.updateById(profile);
        }
    }

    // ==================== 私有：流程公共 ====================

    /**
     * 新建流程 + 落步骤，随后回填正式流程编号。
     * <p>
     * 为什么两步：{@code hr_flow.flow_no} 为 NOT NULL，而正式编号含自增 id（{@code ON-YYYYMMDD-0001}），
     * 插入前无法预知 id，故先以临时占位插入取 id，再在**同一事务内**更新为正式编号。
     */
    private void insertFlowWithSteps(HrFlow flow, List<HrConstants.StepDef> defs) {
        flow.setFlowNo("TMP-" + System.nanoTime());
        hrFlowMapper.insert(flow);
        String prefix = HrConstants.FLOW_TYPE_ONBOARDING.equals(flow.getFlowType()) ? "ON" : "OFF";
        flow.setFlowNo(prefix + "-" + LocalDate.now().format(FLOW_NO_DATE) + "-"
                + String.format("%04d", flow.getId()));

        for (int index = 0; index < defs.size(); index++) {
            HrConstants.StepDef def = defs.get(index);
            HrFlowStep step = new HrFlowStep();
            step.setFlowId(flow.getId());
            step.setStepKey(def.key());
            step.setStepName(def.name());
            step.setStepOrder(index + 1);
            step.setStatus(HrConstants.STEP_STATUS_PENDING);
            hrFlowStepMapper.insert(step);
        }
        flow.setCurrentStepKey(defs.get(0).key());
        hrFlowMapper.updateById(flow);
    }

    /** 标记步骤完成 + 推进游标；全办完则流程置 COMPLETED */
    private void markStepDone(HrFlow flow, List<HrFlowStep> steps, String key, String remark) {
        HrFlowStep target = null;
        for (HrFlowStep step : steps) {
            if (step.getStepKey().equals(key)) {
                target = step;
                break;
            }
        }
        if (target == null) {
            return;
        }
        target.setStatus(HrConstants.STEP_STATUS_DONE);
        target.setOperatorId(UserContext.getUserId());
        target.setOperatorName(currentOperatorName());
        target.setOperateTime(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            target.setRemark(remark.trim());
        }
        hrFlowStepMapper.updateById(target);

        HrFlowStepGuard.StepState pending = HrFlowStepGuard.firstPending(toStepStates(steps));
        flow.setCurrentStepKey(pending == null ? null : pending.key());
        if (pending == null && HrConstants.FLOW_STATUS_IN_PROGRESS.equals(flow.getStatus())) {
            flow.setStatus(HrConstants.FLOW_STATUS_COMPLETED);
        }
        hrFlowMapper.updateById(flow);
    }

    private HrFlow requireFlow(Long id, String flowType) {
        HrFlow flow = hrFlowMapper.selectById(id);
        if (flow == null || !flowType.equals(flow.getFlowType())) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    HrConstants.FLOW_TYPE_ONBOARDING.equals(flowType) ? "入职流程不存在" : "离职流程不存在");
        }
        return flow;
    }

    private List<HrFlowStep> loadSteps(Long flowId) {
        return hrFlowStepMapper.selectList(new LambdaQueryWrapper<HrFlowStep>()
                .eq(HrFlowStep::getFlowId, flowId)
                .orderByAsc(HrFlowStep::getStepOrder));
    }

    private List<HrFlowStepGuard.StepState> toStepStates(List<HrFlowStep> steps) {
        List<HrFlowStepGuard.StepState> states = new ArrayList<>(steps.size());
        for (HrFlowStep step : steps) {
            states.add(new HrFlowStepGuard.StepState(step.getStepKey(), step.getStepName(), step.getStatus()));
        }
        return states;
    }

    private PageResult<HrFlowVO> listFlows(String flowType, HrFlowQuery query) {
        if (!HrValidateSupport.isBlank(query.getStatus()) && !HrConstants.flowStatusKeys().contains(query.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }
        LambdaQueryWrapper<HrFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HrFlow::getFlowType, flowType);
        if (!HrValidateSupport.isBlank(query.getStatus())) {
            wrapper.eq(HrFlow::getStatus, query.getStatus());
        }
        if (query.getStationId() != null) {
            wrapper.eq(HrFlow::getStationId, query.getStationId());
        }
        wrapper.orderByDesc(HrFlow::getCreateTime).orderByDesc(HrFlow::getId);
        List<HrFlow> flows = hrFlowMapper.selectList(wrapper);

        Map<Long, List<HrFlowStep>> stepMap = loadStepMap(flows);
        Map<Long, Employee> employees = loadEmployees(flows);
        Map<Long, String> stationNames = loadStationNames(flows);
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();

        List<HrFlowVO> rows = new ArrayList<>();
        for (HrFlow flow : flows) {
            if (!keyword.isEmpty() && !matchesKeyword(flow, keyword, employees)) {
                continue;
            }
            rows.add(toFlowVO(flow, stepMap.getOrDefault(flow.getId(), List.of()), employees, stationNames));
        }

        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        List<HrFlowVO> slice;
        if (from >= rows.size()) {
            slice = List.of();
        } else {
            slice = new ArrayList<>(rows.subList(from, Math.min(rows.size(), from + pageSize)));
        }
        return PageResult.of(rows.size(), pageNum, pageSize, slice);
    }

    private Map<Long, List<HrFlowStep>> loadStepMap(List<HrFlow> flows) {
        Map<Long, List<HrFlowStep>> map = new HashMap<>();
        if (flows.isEmpty()) {
            return map;
        }
        Set<Long> ids = new HashSet<>();
        for (HrFlow flow : flows) {
            ids.add(flow.getId());
        }
        for (HrFlowStep step : hrFlowStepMapper.selectList(new LambdaQueryWrapper<HrFlowStep>()
                .in(HrFlowStep::getFlowId, ids)
                .orderByAsc(HrFlowStep::getStepOrder))) {
            map.computeIfAbsent(step.getFlowId(), k -> new ArrayList<>()).add(step);
        }
        return map;
    }

    private Map<Long, Employee> loadEmployees(List<HrFlow> flows) {
        Map<Long, Employee> map = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (HrFlow flow : flows) {
            if (flow.getEmployeeId() != null) {
                ids.add(flow.getEmployeeId());
            }
        }
        if (!ids.isEmpty()) {
            for (Employee employee : employeeMapper.selectBatchIds(ids)) {
                map.put(employee.getId(), employee);
            }
        }
        return map;
    }

    private Map<Long, String> loadStationNames(List<HrFlow> flows) {
        Map<Long, String> map = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (HrFlow flow : flows) {
            if (flow.getStationId() != null) {
                ids.add(flow.getStationId());
            }
        }
        if (!ids.isEmpty()) {
            for (Station station : stationMapper.selectBatchIds(ids)) {
                map.put(station.getId(), station.getStationName());
            }
        }
        return map;
    }

    /** 关键词匹配：入职看候选人姓名，离职看员工姓名（对齐 Mock），两者都含流程编号 */
    private boolean matchesKeyword(HrFlow flow, String keyword, Map<Long, Employee> employees) {
        String no = flow.getFlowNo() == null ? "" : flow.getFlowNo();
        if (no.contains(keyword)) {
            return true;
        }
        if (HrConstants.FLOW_TYPE_ONBOARDING.equals(flow.getFlowType())) {
            return flow.getCandidateName() != null && flow.getCandidateName().contains(keyword);
        }
        String name = resolveEmployeeName(flow, employees);
        return name != null && name.contains(keyword);
    }

    private String resolveEmployeeName(HrFlow flow, Map<Long, Employee> employees) {
        if (HrConstants.FLOW_TYPE_ONBOARDING.equals(flow.getFlowType())) {
            return flow.getCandidateName();
        }
        Employee employee = flow.getEmployeeId() == null ? null : employees.get(flow.getEmployeeId());
        return employee == null ? null : employee.getRealName();
    }

    // ==================== 私有：校验 ====================

    private void validateOnboardingCreate(HrOnboardingCreateRequest request) {
        if (request == null || !HrValidateSupport.textLen(request.getCandidateName(), 2, 20)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "候选人姓名长度须为 2-20");
        }
        if (!HrValidateSupport.isPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        if (request.getGender() != null && request.getGender() != 0
                && request.getGender() != 1 && request.getGender() != 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "性别取值非法");
        }
        if (!HrValidateSupport.isBlank(request.getEducation())
                && !HrConstants.educationKeys().contains(request.getEducation())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学历取值非法");
        }
        // 岗位：可选（缺省留空，待审批定岗时必填），但一旦传入须命中白名单
        if (!HrValidateSupport.isBlank(request.getPosition()) && !PositionConstants.isValid(request.getPosition())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PositionConstants.invalidMessage());
        }
        if (!HrValidateSupport.isBlank(request.getExpectedEntryDate())
                && !HrValidateSupport.isDate(request.getExpectedEntryDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "expectedEntryDate 格式须为 YYYY-MM-DD");
        }
        if (request.getDeptId() != null && departmentMapper.selectById(request.getDeptId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的部门不存在");
        }
        if (request.getStationId() != null && stationMapper.selectById(request.getStationId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的驿站不存在");
        }
        if (!HrValidateSupport.isBlank(request.getRemark())
                && !HrValidateSupport.textLen(request.getRemark(), 0, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "备注不可超过 200 字");
        }
    }

    private void validateOffboardingCreate(HrOffboardingCreateRequest request) {
        if (request == null || request.getType() == null
                || !HrConstants.offboardingTypeKeys().contains(request.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "type 仅支持 " + String.join(" / ", HrConstants.offboardingTypeKeys()));
        }
        if (!HrValidateSupport.textLen(request.getReason(), 2, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "离职原因长度须为 2-200");
        }
        if (HrValidateSupport.isBlank(request.getLastWorkDate()) || !HrValidateSupport.isDate(request.getLastWorkDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "lastWorkDate 格式须为 YYYY-MM-DD");
        }
        if (request.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
    }

    /** 步骤办理通用字段把关（步骤级参数由各副作用方法按语义校验） */
    private void validateStepRequest(String key, HrStepCompleteRequest body) {
        if (!HrValidateSupport.textLen(key, 2, 40)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "步骤标识非法");
        }
        if (!HrValidateSupport.isBlank(body.getRemark()) && !HrValidateSupport.textLen(body.getRemark(), 0, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "备注不可超过 200 字");
        }
        if (!HrValidateSupport.isBlank(body.getExpectedEntryDate())
                && !HrValidateSupport.isDate(body.getExpectedEntryDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "expectedEntryDate 格式须为 YYYY-MM-DD");
        }
        String error = HrSalaryValidator.validate(body.getBasicSalary(), body.getPostSalary(),
                body.getPerformanceBase(), body.getAllowances(), body.getEffectiveDate(), body.getReason(), false);
        if (error != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, error);
        }
    }

    private String validateReject(HrFlowRejectRequest request) {
        String reason = request == null ? null : request.getReason();
        if (!HrValidateSupport.textLen(reason, 2, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回原因长度须为 2-200");
        }
        return reason.trim();
    }

    private boolean usernameExists(String username) {
        return employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getUsername, username)) > 0;
    }

    /** 手机号活跃查重（@TableLogic 自动限定 is_deleted=0，故为「活跃」口径；命中 → 2003） */
    private boolean phoneExistsActive(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getPhone, phone)) > 0;
    }

    /** R-6 入参兜底校验（注解校验之外的语义校验：密码强度 / 角色白名单 / 薪资必填 / 用户名格式） */
    private void validateApproveRequest(HrOnboardingApproveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批入参不能为空");
        }
        // 岗位必填（主智能体裁定：审批台岗位必填）+ 枚举白名单（店员 / 站长 / 管理员）
        String position = trim(request.getPosition());
        if (HrValidateSupport.isBlank(position)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写岗位");
        }
        if (!PositionConstants.isValid(position)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, PositionConstants.invalidMessage());
        }
        if (request.getDeptId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择部门");
        }
        if (!FieldValidator.isStrongPassword(request.getInitialPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "初始密码须为 8-20 位且同时包含字母和数字");
        }
        if (request.getRole() != null && !HrConstants.ASSIGNABLE_ROLES.contains(request.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "role 仅支持 STATION_ADMIN / STAFF");
        }
        // U-10：审批定薪三项必填（防工资单静默为 0）→ isCreate=true 强制三项校验
        String salaryError = HrSalaryValidator.validate(request.getBasicSalary(), request.getPostSalary(),
                request.getPerformanceBase(), request.getAllowances(), request.getEffectiveDate(), null, true);
        if (salaryError != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, salaryError);
        }
        if (!HrValidateSupport.isBlank(request.getRemark()) && !HrValidateSupport.textLen(request.getRemark(), 0, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "备注不可超过 200 字");
        }
        String username = trim(request.getUsername());
        if (!HrValidateSupport.isBlank(username) && !FieldValidator.isValidUsername(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录账号须为字母开头、4-30 位字母数字下划线");
        }
    }

    /** R-6：把 ADMIN 审批入参映射为 CREATE_ACCOUNT 步骤体（username 缺省按 U-11 规则生成） */
    private HrStepCompleteRequest createBodyForApprove(HrOnboardingApproveRequest request, HrFlow flow) {
        HrStepCompleteRequest body = new HrStepCompleteRequest();
        String username = trim(request.getUsername());
        body.setUsername(HrValidateSupport.isBlank(username) ? defaultUsername(flow.getPhone()) : username);
        body.setPassword(request.getInitialPassword());
        body.setDeptId(request.getDeptId());
        body.setStationId(request.getStationId() != null ? request.getStationId() : flow.getStationId());
        body.setProbationMonths(request.getProbationMonths());
        body.setContractType(request.getContractType());
        body.setRemark(request.getRemark());
        return body;
    }

    /** R-6：把 ADMIN 审批入参映射为 ASSIGN_STATION 步骤体（岗位必填 → 触发员工档案双写） */
    private HrStepCompleteRequest assignBodyForApprove(HrOnboardingApproveRequest request) {
        HrStepCompleteRequest body = new HrStepCompleteRequest();
        body.setDeptId(request.getDeptId());
        body.setStationId(request.getStationId());
        body.setPosition(request.getPosition());
        body.setRole(request.getRole());
        body.setRemark(request.getRemark());
        return body;
    }

    /** R-6：把 ADMIN 审批入参映射为 SET_SALARY 步骤体 */
    private HrStepCompleteRequest salaryBodyForApprove(HrOnboardingApproveRequest request) {
        HrStepCompleteRequest body = new HrStepCompleteRequest();
        body.setBasicSalary(request.getBasicSalary());
        body.setPostSalary(request.getPostSalary());
        body.setPerformanceBase(request.getPerformanceBase());
        body.setAllowances(request.getAllowances());
        body.setEffectiveDate(request.getEffectiveDate());
        body.setRemark(request.getRemark());
        return body;
    }

    /** U-11：缺省登录账号 = {@code u} + 手机号（满足 {@code ^[a-zA-Z][a-zA-Z0-9_]{3,29}$}） */
    private String defaultUsername(String phone) {
        return "u" + (phone == null ? "" : phone.trim());
    }

    /** R-8：申请单摘要（无关联申请单返回 null，保持既有后台流程出参不变） */
    private RegistrationSummaryVO registrationSummary(Long flowId) {
        EmployeeRegistration registration = registrationMapper.selectOne(new LambdaQueryWrapper<EmployeeRegistration>()
                .eq(EmployeeRegistration::getFlowId, flowId));
        if (registration == null) {
            return null;
        }
        RegistrationSummaryVO summary = new RegistrationSummaryVO();
        summary.setApplyNo(registration.getApplyNo());
        summary.setIntentPosition(registration.getApplyPosition());
        summary.setAgreementVersion(registration.getAgreementVersion());
        summary.setSource(registration.getSource());
        summary.setCreateTime(registration.getCreateTime());
        summary.setStatus(registration.getStatus());
        return summary;
    }

    private boolean hasInProgressOffboarding(Long employeeId) {
        return hrFlowMapper.selectCount(new LambdaQueryWrapper<HrFlow>()
                .eq(HrFlow::getFlowType, HrConstants.FLOW_TYPE_OFFBOARDING)
                .eq(HrFlow::getEmployeeId, employeeId)
                .eq(HrFlow::getStatus, HrConstants.FLOW_STATUS_IN_PROGRESS)) > 0;
    }

    // ==================== 私有：员工状态 ====================

    /** 复用员工域的启停逻辑（含「禁用即强制下线」），remark 另行走 Mapper（changeStatus 不承载备注） */
    private void updateEmployeeStatus(Long employeeId, int status, String remark) {
        EmployeeStatusRequest request = new EmployeeStatusRequest();
        request.setStatus(status);
        employeeService.changeStatus(employeeId, request);
        if (remark != null) {
            Employee update = new Employee();
            update.setId(employeeId);
            update.setRemark(remark);
            employeeMapper.updateById(update);
        }
    }

    // ==================== 私有：转换 ====================

    private HrFlowVO toFlowVO(HrFlow flow) {
        return toFlowVO(flow, loadSteps(flow.getId()), loadEmployees(List.of(flow)), loadStationNames(List.of(flow)));
    }

    private HrFlowVO toFlowVO(HrFlow flow, List<HrFlowStep> steps,
                              Map<Long, Employee> employees, Map<Long, String> stationNames) {
        HrFlowVO vo = new HrFlowVO();
        vo.setId(flow.getId());
        vo.setFlowType(flow.getFlowType());
        vo.setFlowNo(flow.getFlowNo());
        // ARCH-C-6：列表与详情共用本方法，补出参 source 供审批中心识别「员工注册」
        vo.setSource(flow.getSource());
        vo.setCandidateName(flow.getCandidateName());
        vo.setEmployeeId(flow.getEmployeeId());
        vo.setEmployeeName(resolveEmployeeName(flow, employees));
        vo.setPhone(flow.getPhone() == null ? null : DesensitizeUtil.maskPhone(flow.getPhone()));
        vo.setGender(flow.getGender());
        vo.setEducation(flow.getEducation());
        vo.setEducationLabel(HrConstants.educationLabel(flow.getEducation()));
        vo.setDeptId(flow.getDeptId());
        vo.setStationId(flow.getStationId());
        vo.setStationName(flow.getStationId() == null ? null : stationNames.get(flow.getStationId()));
        vo.setPosition(flow.getPosition());
        vo.setRole(flow.getRole());
        vo.setExpectedEntryDate(flow.getExpectedEntryDate());
        vo.setType(flow.getType());
        vo.setTypeLabel(flow.getType() == null ? null : HrConstants.offboardingTypeLabel(flow.getType()));
        vo.setReason(flow.getReason());
        vo.setLastWorkDate(flow.getLastWorkDate());
        vo.setSettlementPayrollId(flow.getSettlementPayrollId());
        vo.setSettlementPayrollNo(flow.getSettlementPayrollNo());
        vo.setSettlementAmount(flow.getSettlementAmount());
        vo.setLeaveDate(flow.getLeaveDate());
        vo.setRemark(flow.getRemark());
        vo.setStatus(flow.getStatus());
        vo.setStatusLabel(HrConstants.flowStatusLabel(flow.getStatus()));
        vo.setRejectReason(flow.getRejectReason());
        vo.setRejectedBy(flow.getRejectedBy());
        vo.setRejectedTime(flow.getRejectedTime());
        vo.setCreateTime(flow.getCreateTime());
        vo.setUpdateTime(flow.getUpdateTime());
        vo.setOperatorId(flow.getOperatorId());
        vo.setOperatorName(flow.getOperatorName());

        int done = 0;
        HrFlowStepGuard.StepState pending = HrFlowStepGuard.firstPending(toStepStates(steps));
        List<HrFlowStepVO> stepVOs = new ArrayList<>(steps.size());
        for (HrFlowStep step : steps) {
            if (HrConstants.STEP_STATUS_DONE.equals(step.getStatus())) {
                done++;
            }
            stepVOs.add(toStepVO(step));
        }
        vo.setCurrentStepKey(pending == null ? null : pending.key());
        vo.setCurrentStepName(pending == null ? null : pending.name());
        vo.setProgress(new HrFlowProgressVO(done, steps.size()));
        vo.setSteps(stepVOs);
        return vo;
    }

    private HrFlowStepVO toStepVO(HrFlowStep step) {
        HrFlowStepVO vo = new HrFlowStepVO();
        vo.setKey(step.getStepKey());
        vo.setName(step.getStepName());
        vo.setOrder(step.getStepOrder());
        vo.setStatus(step.getStatus());
        vo.setStatusLabel(HrConstants.stepStatusLabel(step.getStatus()));
        vo.setOperatorId(step.getOperatorId());
        vo.setOperatorName(step.getOperatorName());
        vo.setOperateTime(step.getOperateTime());
        vo.setRemark(step.getRemark());
        return vo;
    }

    private HrProfile findProfile(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        List<HrProfile> list = hrProfileMapper.selectList(
                new LambdaQueryWrapper<HrProfile>().eq(HrProfile::getEmployeeId, employeeId));
        return list.isEmpty() ? null : list.get(0);
    }

    private List<HrAllowance> toAllowances(List<SalaryAllowanceItem> items) {
        if (items == null) {
            return null;
        }
        List<HrAllowance> list = new ArrayList<>(items.size());
        for (SalaryAllowanceItem item : items) {
            String name = item.getName() == null ? null : item.getName().trim();
            String key = item.getKey() == null ? null : item.getKey().trim();
            list.add(new HrAllowance(key, name, item.getAmount()));
        }
        return list;
    }

    private String currentOperatorName() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        Employee operator = employeeMapper.selectById(userId);
        return operator == null ? null : operator.getRealName();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return HrValidateSupport.isBlank(value) ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
