package com.qiujie.service.hr.impl;

import com.qiujie.config.HrProperties;
import com.qiujie.dto.employee.EmployeeStatusRequest;
import com.qiujie.dto.hr.HrFlowRejectRequest;
import com.qiujie.dto.hr.HrOnboardingApproveRequest;
import com.qiujie.dto.hr.HrStepCompleteRequest;
import com.qiujie.entity.Department;
import com.qiujie.entity.Employee;
import com.qiujie.entity.EmployeeRegistration;
import com.qiujie.entity.HrFlow;
import com.qiujie.entity.HrFlowStep;
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
import com.qiujie.service.audit.OperationAuditWriter;
import com.qiujie.service.hr.port.PayrollSettlementPort;
import com.qiujie.service.hr.support.HrConstants;
import com.qiujie.service.registration.support.RegistrationConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-6 聚合审批 / R-9 驳回联动 单测（B4，registration-design §4.2.1 / §4.3 / §11.7）。
 * <p>覆盖：5 步推进（不含 DONE）+ {@code current_step_key=='DONE'} + 不激活、
 * 岗位双写、{@code DONE} 二次确认后 {@code status==1}、行锁调用、全回滚、终态清凭据、{@code registration} 联动、phone 2003。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrFlowServiceApproveTest {

    private HrFlowMapper hrFlowMapper;
    private HrFlowStepMapper hrFlowStepMapper;
    private EmployeeRegistrationMapper registrationMapper;
    private EmployeeMapper employeeMapper;
    private StationMapper stationMapper;
    private EmployeeService employeeService;

    private HrFlowServiceImpl service;

    private HrFlow flow;
    private List<HrFlowStep> steps;
    private EmployeeRegistration registration;
    private final Employee[] created = new Employee[1];

    @BeforeEach
    void setUp() {
        hrFlowMapper = mock(HrFlowMapper.class);
        hrFlowStepMapper = mock(HrFlowStepMapper.class);
        registrationMapper = mock(EmployeeRegistrationMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        stationMapper = mock(StationMapper.class);
        employeeService = mock(EmployeeService.class);
        service = new HrFlowServiceImpl(hrFlowMapper, hrFlowStepMapper, registrationMapper,
                mock(HrProfileMapper.class), mock(HrSalaryMapper.class), employeeMapper, mock(DepartmentMapper.class),
                stationMapper, employeeService, mock(HrSalaryWriter.class), mock(PayrollSettlementPort.class),
                new HrProperties(), mock(OperationAuditWriter.class));

        flow = new HrFlow();
        flow.setId(1L);
        flow.setFlowType(HrConstants.FLOW_TYPE_ONBOARDING);
        flow.setFlowNo("ON-20260926-0001");
        flow.setStatus(HrConstants.FLOW_STATUS_IN_PROGRESS);
        flow.setCandidateName("李四");
        flow.setPhone("13812345678");
        flow.setGender(0);
        flow.setStationId(3L);
        flow.setRole("STAFF");
        flow.setExpectedEntryDate(LocalDate.now());
        flow.setSource(HrConstants.FLOW_SOURCE_SELF_REGISTER);

        steps = buildSteps();

        registration = new EmployeeRegistration();
        registration.setId(50L);
        registration.setFlowId(1L);
        registration.setStatus(RegistrationConstants.STATUS_SUBMITTED);
        registration.setPasswordHash("$2a$10$hash");

        when(hrFlowMapper.selectByIdForUpdate(1L)).thenReturn(flow);
        when(hrFlowMapper.selectById(1L)).thenReturn(flow);
        when(hrFlowStepMapper.selectList(any())).thenReturn(steps);
        when(registrationMapper.selectByFlowIdForUpdate(1L)).thenReturn(registration);
    }

    private List<HrFlowStep> buildSteps() {
        List<HrFlowStep> list = new ArrayList<>();
        List<HrConstants.StepDef> defs = HrConstants.ONBOARDING_STEPS;
        for (int i = 0; i < defs.size(); i++) {
            HrFlowStep step = new HrFlowStep();
            step.setId((long) (i + 1));
            step.setFlowId(1L);
            step.setStepKey(defs.get(i).key());
            step.setStepName(defs.get(i).name());
            step.setStepOrder(i + 1);
            step.setStatus(HrConstants.STEP_STATUS_PENDING);
            list.add(step);
        }
        return list;
    }

    private Station enabledStation() {
        Station station = new Station();
        station.setId(3L);
        station.setStatus(1);
        return station;
    }

    private HrOnboardingApproveRequest approveRequest() {
        HrOnboardingApproveRequest request = new HrOnboardingApproveRequest();
        request.setInitialPassword("Init1234");
        request.setDeptId(2L);
        request.setStationId(3L);
        request.setPosition("店员");
        request.setBasicSalary(new BigDecimal("5000"));
        request.setPostSalary(new BigDecimal("2000"));
        request.setPerformanceBase(new BigDecimal("1000"));
        return request;
    }

    private void stubCommonHappyPath() {
        DepartmentMapper departmentMapper = mock(DepartmentMapper.class);
        when(departmentMapper.selectById(anyLong())).thenReturn(new Department());
        // 重新构造以注入 departmentMapper（与 setUp 中的服务隔离，避免多态桩覆盖）
        service = new HrFlowServiceImpl(hrFlowMapper, hrFlowStepMapper, registrationMapper,
                mock(HrProfileMapper.class), mock(HrSalaryMapper.class), employeeMapper, departmentMapper,
                stationMapper, employeeService, mock(HrSalaryWriter.class), mock(PayrollSettlementPort.class),
                new HrProperties(), mock(OperationAuditWriter.class));
        when(stationMapper.selectById(anyLong())).thenReturn(enabledStation());
        when(employeeMapper.selectCount(any())).thenReturn(0L);
        when(employeeMapper.insert(any(Employee.class))).thenAnswer(inv -> {
            Employee employee = inv.getArgument(0);
            employee.setId(100L);
            created[0] = employee;
            return 1;
        });
        when(employeeMapper.selectById(100L)).thenAnswer(inv -> created[0]);
    }

    @Test
    @DisplayName("R-6：5 步推进（不含 DONE）+ current_step_key=='DONE' + 不激活（employee.status=0）")
    void approveAdvancesFiveStepsWithoutActivation() {
        stubCommonHappyPath();

        service.approveOnboarding(1L, approveRequest());

        assertEquals(5, countDone(steps), "应推进前 5 步");
        assertEquals("DONE", flow.getCurrentStepKey(), "游标停在 DONE（激活待二次确认）");
        assertEquals(HrConstants.FLOW_STATUS_IN_PROGRESS, flow.getStatus(), "hr_flow 保持 IN_PROGRESS");
        assertTrue(created[0] != null, "应完成建档");
        assertEquals(0, created[0].getStatus(), "审批不得自动激活（status 恒 0）");
        assertEquals("STAFF", created[0].getRole(), "role 恒 STAFF（防注册注入）");
        verify(hrFlowMapper).selectByIdForUpdate(1L);
    }

    @Test
    @DisplayName("R-6：岗位双写（employee.position 权威 + hr_flow.position 留痕，须一致）")
    void approveDoubleWritesPosition() {
        stubCommonHappyPath();

        service.approveOnboarding(1L, approveRequest());

        assertEquals("店员", flow.getPosition());
        assertEquals("店员", created[0].getPosition());
        assertEquals(flow.getPosition(), created[0].getPosition(), "双写点须一致");
    }

    @Test
    @DisplayName("岗位非法值（不在 店员/站长/管理员 白名单）→ 400")
    void approveRejectsInvalidPosition() {
        HrOnboardingApproveRequest request = approveRequest();
        request.setPosition("分拣员");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approveOnboarding(1L, request));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("岗位"), "文案须点明岗位取值");
    }

    @Test
    @DisplayName("岗位缺失（空白）→ 400 必填")
    void approveRejectsBlankPosition() {
        HrOnboardingApproveRequest request = approveRequest();
        request.setPosition("   ");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approveOnboarding(1L, request));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("合法三值（店员/站长/管理员）均可通过审批并双写一致")
    void approveAcceptsAllThreePositions() {
        for (String position : List.of("店员", "站长", "管理员")) {
            setUp();
            stubCommonHappyPath();
            HrOnboardingApproveRequest request = approveRequest();
            request.setPosition(position);

            service.approveOnboarding(1L, request);

            assertEquals(position, flow.getPosition(), "hr_flow.position 应写入 " + position);
            assertEquals(position, created[0].getPosition(), "employee.position 应写入 " + position);
        }
    }

    @Test
    @DisplayName("R-6：registration 置 APPROVED + 回填员工 id + 清凭据")
    void approveFinalizesRegistrationAndClearsCredentials() {
        stubCommonHappyPath();

        service.approveOnboarding(1L, approveRequest());

        assertEquals(RegistrationConstants.STATUS_APPROVED, registration.getStatus());
        assertEquals(100L, registration.getApprovedEmployeeId());
        assertNull(registration.getPasswordHash(), "终态清密码散列");
        assertNull(registration.getQueryTokenHash());
    }

    @Test
    @DisplayName("U-01：审批后 DONE 二次确认 → employee.status=1（激活）")
    void doneAfterApproveActivatesEmployee() {
        stubCommonHappyPath();
        service.approveOnboarding(1L, approveRequest());

        service.completeOnboardingStep(1L, HrConstants.ONBOARD_DONE, null);

        ArgumentCaptor<EmployeeStatusRequest> captor = ArgumentCaptor.forClass(EmployeeStatusRequest.class);
        verify(employeeService).changeStatus(eq(100L), captor.capture());
        assertEquals(1, captor.getValue().getStatus(), "DONE 才置在职");
        assertEquals(HrConstants.FLOW_STATUS_COMPLETED, flow.getStatus());
    }

    @Test
    @DisplayName("R-6 全回滚：中途失败（驿站停用）→ 抛 4004，registration 不得被置 APPROVED")
    void midStepFailureDoesNotFinalizeRegistration() {
        stubCommonHappyPath();
        Station disabled = enabledStation();
        disabled.setStatus(0);
        when(stationMapper.selectById(anyLong())).thenReturn(disabled);

        assertThrows(BusinessException.class, () -> service.approveOnboarding(1L, approveRequest()));

        verify(registrationMapper, never()).updateById(any(EmployeeRegistration.class));
        assertEquals(RegistrationConstants.STATUS_SUBMITTED, registration.getStatus());
    }

    @Test
    @DisplayName("重复审批：registration 非 SUBMITTED → 9309")
    void repeatedApprovalRejected() {
        registration.setStatus(RegistrationConstants.STATUS_APPROVED);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approveOnboarding(1L, approveRequest()));
        assertEquals(ErrorCode.REGISTRATION_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("R-9 驳回：同事务联动 registration → REJECTED + 清凭据")
    void rejectSyncsRegistration() {
        HrFlowRejectRequest request = new HrFlowRejectRequest();
        request.setReason("资料不全");

        service.rejectOnboarding(1L, request);

        assertEquals(HrConstants.FLOW_STATUS_REJECTED, flow.getStatus());
        assertEquals(RegistrationConstants.STATUS_REJECTED, registration.getStatus());
        assertNull(registration.getPasswordHash());
        verify(hrFlowMapper).selectByIdForUpdate(1L);
        verify(registrationMapper).updateById(registration);
    }

    @Test
    @DisplayName("R-9 驳回：申请单非 SUBMITTED（已通过）→ 9309")
    void rejectWhenRegistrationFinalizedRejected() {
        registration.setStatus(RegistrationConstants.STATUS_APPROVED);
        HrFlowRejectRequest request = new HrFlowRejectRequest();
        request.setReason("资料不全");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.rejectOnboarding(1L, request));
        assertEquals(ErrorCode.REGISTRATION_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("M-5：建档手机号活跃查重命中 → 2003")
    void createAccountRejectsDuplicatePhone() {
        // 前两步置 DONE，使 CREATE_ACCOUNT 成为首个待办
        steps.get(0).setStatus(HrConstants.STEP_STATUS_DONE);
        steps.get(1).setStatus(HrConstants.STEP_STATUS_DONE);
        // 第 1 次 selectCount（username）→ 0；第 2 次（phone）→ 1（命中）
        when(employeeMapper.selectCount(any())).thenReturn(0L, 1L);

        HrStepCompleteRequest body = new HrStepCompleteRequest();
        body.setUsername("zhangsan");
        body.setPassword("Init1234");
        body.setDeptId(2L);
        body.setStationId(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeOnboardingStep(1L, HrConstants.ONBOARD_CREATE_ACCOUNT, body));
        assertEquals(ErrorCode.PHONE_EXISTS.getCode(), ex.getCode());
    }

    private int countDone(List<HrFlowStep> steps) {
        int done = 0;
        for (HrFlowStep step : steps) {
            if (HrConstants.STEP_STATUS_DONE.equals(step.getStatus())) {
                done++;
            }
        }
        return done;
    }
}
