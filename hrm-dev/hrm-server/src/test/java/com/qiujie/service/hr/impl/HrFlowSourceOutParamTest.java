package com.qiujie.service.hr.impl;

import com.qiujie.common.PageResult;
import com.qiujie.config.HrProperties;
import com.qiujie.dto.hr.HrFlowQuery;
import com.qiujie.entity.HrFlow;
import com.qiujie.mapper.DepartmentMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.EmployeeRegistrationMapper;
import com.qiujie.mapper.HrFlowMapper;
import com.qiujie.mapper.HrFlowStepMapper;
import com.qiujie.mapper.HrProfileMapper;
import com.qiujie.mapper.HrSalaryMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.audit.OperationAuditWriter;
import com.qiujie.service.employee.EmployeeService;
import com.qiujie.service.hr.port.PayrollSettlementPort;
import com.qiujie.service.hr.support.HrConstants;
import com.qiujie.vo.hr.HrFlowVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ARCH-C-6 单测：{@code HrFlowVO} 列表与详情均出参 {@code source}（审批中心识别「员工注册」所需）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class HrFlowSourceOutParamTest {

    private HrFlowMapper hrFlowMapper;
    private HrFlowStepMapper hrFlowStepMapper;
    private EmployeeRegistrationMapper registrationMapper;
    private HrFlowServiceImpl service;

    @BeforeEach
    void setUp() {
        hrFlowMapper = mock(HrFlowMapper.class);
        hrFlowStepMapper = mock(HrFlowStepMapper.class);
        registrationMapper = mock(EmployeeRegistrationMapper.class);
        service = new HrFlowServiceImpl(hrFlowMapper, hrFlowStepMapper, registrationMapper,
                mock(HrProfileMapper.class), mock(HrSalaryMapper.class), mock(EmployeeMapper.class),
                mock(DepartmentMapper.class), mock(StationMapper.class), mock(EmployeeService.class),
                mock(HrSalaryWriter.class), mock(PayrollSettlementPort.class), new HrProperties(),
                mock(OperationAuditWriter.class));
        when(hrFlowStepMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("详情出参含 source=SELF_REGISTER")
    void detailExposesSource() {
        when(hrFlowMapper.selectById(1L)).thenReturn(flow());
        when(registrationMapper.selectOne(any())).thenReturn(null);

        HrFlowVO vo = service.onboardingDetail(1L);

        assertEquals(HrConstants.FLOW_SOURCE_SELF_REGISTER, vo.getSource());
    }

    @Test
    @DisplayName("列表出参含 source（原列表不附 registration，故须补 source 标识来源）")
    void listExposesSource() {
        when(hrFlowMapper.selectList(any())).thenReturn(List.of(flow()));

        PageResult<HrFlowVO> page = service.listOnboardings(new HrFlowQuery());

        assertEquals(1, page.getList().size());
        assertEquals(HrConstants.FLOW_SOURCE_SELF_REGISTER, page.getList().get(0).getSource());
    }

    private HrFlow flow() {
        HrFlow flow = new HrFlow();
        flow.setId(1L);
        flow.setFlowType(HrConstants.FLOW_TYPE_ONBOARDING);
        flow.setFlowNo("ON-20260927-0001");
        flow.setCandidateName("王五");
        flow.setPhone("13812345678");
        flow.setStatus(HrConstants.FLOW_STATUS_IN_PROGRESS);
        flow.setSource(HrConstants.FLOW_SOURCE_SELF_REGISTER);
        return flow;
    }
}
