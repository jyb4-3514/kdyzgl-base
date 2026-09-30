package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceMakeupApproveRequest;
import com.qiujie.dto.attendance.AttendanceMakeupRequest;
import com.qiujie.entity.AttendanceMakeup;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceMakeupMapper;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceRecordService;
import com.qiujie.service.attendance.AttendanceRuleService;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.attendance.support.AttendanceSupport;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.AttendanceMakeupVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 补卡「时段真源统一」单测（方案 v1.2 §4.4 / §7.6；M1 核心回归 + 9113）。
 * <p>
 * 覆盖：单班次晚班站点 {@code periodIndex=1} 申请补卡成功（旧实现下标取值会越界 9107）；
 * 站点无启用班次申请补卡 → 9113。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceMakeupTimeSourceTest {

    private static final long EMPLOYEE = 100L;
    private static final long STATION = 3L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceMakeup.class);
    }

    private AttendanceMakeupMapper makeupMapper;
    private AttendanceRecordService recordService;
    private AttendanceRuleService ruleService;
    private AttendanceShiftService shiftService;
    private AttendanceMakeupServiceImpl service;

    @BeforeEach
    void setUp() {
        makeupMapper = mock(AttendanceMakeupMapper.class);
        EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
        recordService = mock(AttendanceRecordService.class);
        ruleService = mock(AttendanceRuleService.class);
        shiftService = mock(AttendanceShiftService.class);
        service = new AttendanceMakeupServiceImpl(makeupMapper, mock(AttendanceRecordMapper.class), employeeMapper,
                mock(StationMapper.class), ruleService, recordService, shiftService, new AlgoProperties());

        UserContext.set(new LoginUser(EMPLOYEE, "张三", "STAFF", "jti", String.valueOf(STATION)));
        Employee employee = new Employee();
        employee.setId(EMPLOYEE);
        employee.setRealName("张三");
        employee.setStationId(STATION);
        when(employeeMapper.selectById(EMPLOYEE)).thenReturn(employee);

        AttendanceRule rule = new AttendanceRule();
        rule.setStationId(STATION);
        when(ruleService.findRule(STATION)).thenReturn(rule);
        when(makeupMapper.selectCount(any())).thenReturn(0L);
        when(recordService.hasValidCard(any(), any(), any(), any())).thenReturn(false);
        when(makeupMapper.insert(any(AttendanceMakeup.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("单班次晚班站点：periodIndex=1 申请补卡成功，快照 period_name=晚班（M1 核心）")
    void singleEveningShiftMakeupSucceeds() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(evening()));

        AttendanceMakeupVO vo = service.apply(makeup(1, "OFF"));

        assertEquals(1, vo.getPeriodIndex());
        assertEquals("晚班", vo.getPeriodName());
    }

    @Test
    @DisplayName("站点无启用班次申请补卡 → 9113")
    void noEnabledShiftMakeupRejected() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(makeup(1, "OFF")));
        assertEquals(ErrorCode.ATTENDANCE_NO_ENABLED_SHIFT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("审批补卡：站点无启用班次 → 9113（与申请路径同码，L-2）")
    void noEnabledShiftApproveRejected() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of());
        when(makeupMapper.selectById(77L)).thenReturn(pendingMakeup(1, "OFF"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(77L, approveRequest()));
        assertEquals(ErrorCode.ATTENDANCE_NO_ENABLED_SHIFT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("审批补卡：有启用班次但 periodIndex 无匹配 → 维持 9101（不扩大影响面，L-2）")
    void approvePeriodMismatchKeepsRuleCode() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(evening()));
        // 站点唯一启用班次为晚班（ordinal=1），请求 periodIndex=0 → findByOrdinal 无匹配
        when(makeupMapper.selectById(77L)).thenReturn(pendingMakeup(0, "OFF"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.approve(77L, approveRequest()));
        assertEquals(ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED.getCode(), ex.getCode());
    }

    private AttendanceMakeup pendingMakeup(Integer periodIndex, String checkType) {
        AttendanceMakeup makeup = new AttendanceMakeup();
        makeup.setId(77L);
        makeup.setEmployeeId(EMPLOYEE);
        makeup.setStationId(STATION);
        makeup.setWorkDate(AttendanceSupport.today());
        makeup.setPeriodIndex(periodIndex);
        makeup.setPeriodName("晚班");
        makeup.setCheckType(checkType);
        makeup.setReason("漏打卡补卡");
        makeup.setStatus(AttendanceConstants.MAKEUP_PENDING);
        return makeup;
    }

    private AttendanceMakeupApproveRequest approveRequest() {
        AttendanceMakeupApproveRequest request = new AttendanceMakeupApproveRequest();
        request.setApproved(true);
        return request;
    }

    private AttendanceMakeupRequest makeup(Integer periodIndex, String checkType) {
        AttendanceMakeupRequest request = new AttendanceMakeupRequest();
        request.setWorkDate(AttendanceSupport.formatDate(AttendanceSupport.today()));
        request.setPeriodIndex(periodIndex);
        request.setCheckType(checkType);
        request.setReason("漏打卡补卡");
        return request;
    }

    private AttendanceShift evening() {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(9L);
        shift.setStationId(STATION);
        shift.setShiftName("晚班");
        shift.setStartTime("16:00");
        shift.setEndTime("24:00");
        shift.setColor("#123456");
        shift.setStatus(1);
        return shift;
    }
}
