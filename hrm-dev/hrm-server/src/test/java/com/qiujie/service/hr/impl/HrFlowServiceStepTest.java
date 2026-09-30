package com.qiujie.service.hr.impl;

import com.qiujie.config.HrProperties;
import com.qiujie.entity.HrFlow;
import com.qiujie.entity.HrFlowStep;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程步骤办理的守卫与前置校验单测：覆盖「跳步拒绝 / 无结算单不可离岗 / 结算端口未就绪」边界。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrFlowServiceStepTest {

    private HrFlowMapper hrFlowMapper;
    private HrFlowStepMapper hrFlowStepMapper;
    private PayrollSettlementPort payrollSettlementPort;
    private HrFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        hrFlowMapper = mock(HrFlowMapper.class);
        hrFlowStepMapper = mock(HrFlowStepMapper.class);
        payrollSettlementPort = mock(PayrollSettlementPort.class);
        service = new HrFlowServiceImpl(hrFlowMapper, hrFlowStepMapper, mock(EmployeeRegistrationMapper.class),
                mock(HrProfileMapper.class),
                mock(HrSalaryMapper.class), mock(EmployeeMapper.class), mock(DepartmentMapper.class),
                mock(StationMapper.class), mock(EmployeeService.class), mock(HrSalaryWriter.class),
                payrollSettlementPort, new HrProperties(), mock(OperationAuditWriter.class));
    }

    private HrFlow flow(String flowType) {
        HrFlow flow = new HrFlow();
        flow.setId(1L);
        flow.setFlowType(flowType);
        flow.setFlowNo("ON-20260924-0001");
        flow.setStatus(HrConstants.FLOW_STATUS_IN_PROGRESS);
        return flow;
    }

    private List<HrFlowStep> steps(List<HrConstants.StepDef> defs, int done) {
        List<HrFlowStep> list = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) {
            HrConstants.StepDef def = defs.get(i);
            HrFlowStep step = new HrFlowStep();
            step.setId((long) (i + 1));
            step.setFlowId(1L);
            step.setStepKey(def.key());
            step.setStepName(def.name());
            step.setStepOrder(i + 1);
            step.setStatus(i < done ? HrConstants.STEP_STATUS_DONE : HrConstants.STEP_STATUS_PENDING);
            list.add(step);
        }
        return list;
    }

    @Test
    @DisplayName("入职跳步（未办首步直接建档）→ 9303")
    void onboardingSkipRejected() {
        when(hrFlowMapper.selectById(1L)).thenReturn(flow(HrConstants.FLOW_TYPE_ONBOARDING));
        when(hrFlowStepMapper.selectList(any())).thenReturn(steps(HrConstants.ONBOARDING_STEPS, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeOnboardingStep(1L, HrConstants.ONBOARD_CREATE_ACCOUNT, null));
        assertEquals(ErrorCode.HR_ONBOARDING_STATUS_INVALID.getCode(), ex.getCode());
        assertEquals("请先办理「提交资料」", ex.getMessage());
    }

    @Test
    @DisplayName("离职跳步（未办审批直接离岗）→ 9304")
    void offboardingSkipRejected() {
        when(hrFlowMapper.selectById(1L)).thenReturn(flow(HrConstants.FLOW_TYPE_OFFBOARDING));
        when(hrFlowStepMapper.selectList(any())).thenReturn(steps(HrConstants.OFFBOARDING_STEPS, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeOffboardingStep(1L, HrConstants.OFFBOARD_LEAVE, null));
        assertEquals(ErrorCode.HR_OFFBOARDING_STATUS_INVALID.getCode(), ex.getCode());
        assertEquals("请先办理「主管审批」", ex.getMessage());
    }

    @Test
    @DisplayName("无结算单直接离岗 → 9306")
    void leaveWithoutSettlementRejected() {
        HrFlow flow = flow(HrConstants.FLOW_TYPE_OFFBOARDING);
        when(hrFlowMapper.selectById(1L)).thenReturn(flow);
        when(hrFlowStepMapper.selectList(any())).thenReturn(steps(HrConstants.OFFBOARDING_STEPS, 5));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeOffboardingStep(1L, HrConstants.OFFBOARD_LEAVE, null));
        assertEquals(ErrorCode.HR_SETTLEMENT_UNFINISHED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("结算端口未就绪（返回空引用）→ 9306，流程不推进")
    void settlementPortUnavailable() {
        HrFlow flow = flow(HrConstants.FLOW_TYPE_OFFBOARDING);
        when(hrFlowMapper.selectById(1L)).thenReturn(flow);
        when(hrFlowStepMapper.selectList(any())).thenReturn(steps(HrConstants.OFFBOARDING_STEPS, 4));
        when(payrollSettlementPort.createSettlement(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.completeOffboardingStep(1L, HrConstants.OFFBOARD_SETTLEMENT, null));
        assertEquals(ErrorCode.HR_SETTLEMENT_UNFINISHED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("流程不存在 → 404 文案区分入职/离职")
    void missingFlow() {
        when(hrFlowMapper.selectById(2L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.onboardingDetail(2L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("入职流程不存在", ex.getMessage());

        BusinessException ex2 = assertThrows(BusinessException.class, () -> service.offboardingDetail(2L));
        assertEquals("离职流程不存在", ex2.getMessage());
    }
}
