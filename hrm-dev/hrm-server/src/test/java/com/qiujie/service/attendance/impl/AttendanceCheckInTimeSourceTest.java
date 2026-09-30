package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.common.LoginUser;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceCheckInRequest;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceRuleService;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.service.support.geo.GeoService;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.AttendanceRecordVO;
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
 * 打卡「时段真源统一」单测（方案 v1.2 §4.4 / §4.5；M1 核心回归 + U-2）。
 * <p>
 * 覆盖：单班次晚班站点 {@code periodIndex=1} 打卡成功（旧实现下标取值会越界 9107）；双班次精确命中；
 * 员工当天无排班仍可打卡（U-2）；站点无启用班次 → 9113；错值 periodIndex → 9107。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceCheckInTimeSourceTest {

    private static final long EMPLOYEE = 100L;
    private static final long STATION = 3L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceRecord.class);
        TableInfoHelper.initTableInfo(assistant, AttendanceSchedule.class);
    }

    private AttendanceRecordMapper recordMapper;
    private AttendanceScheduleMapper scheduleMapper;
    private EmployeeMapper employeeMapper;
    private AttendanceRuleService ruleService;
    private AttendanceShiftService shiftService;
    private AttendanceRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        recordMapper = mock(AttendanceRecordMapper.class);
        scheduleMapper = mock(AttendanceScheduleMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        ruleService = mock(AttendanceRuleService.class);
        shiftService = mock(AttendanceShiftService.class);
        service = new AttendanceRecordServiceImpl(recordMapper, scheduleMapper, mock(AttendanceShiftMapper.class),
                employeeMapper, mock(StationMapper.class), ruleService, shiftService, mock(GeoService.class),
                new AlgoProperties());
        UserContext.set(new LoginUser(EMPLOYEE, "张三", "STAFF", "jti", String.valueOf(STATION)));
        when(employeeMapper.selectById(EMPLOYEE)).thenReturn(employee());
        when(ruleService.findRule(STATION)).thenReturn(rule());
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.insert(any(AttendanceRecord.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("单班次晚班站点：periodIndex=1 打卡成功，快照 period_index=1 / period_name=晚班（M1 核心）")
    void singleEveningShiftCheckInSucceeds() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(evening()));

        AttendanceRecordVO vo = service.checkIn(checkIn("ON", 1));

        assertEquals(1, vo.getPeriodIndex());
        assertEquals("晚班", vo.getPeriodName());
    }

    @Test
    @DisplayName("单班次晚班站点：错值 periodIndex=0 查不到 → 9107")
    void singleEveningShiftWrongIndexRejected() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(evening()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkIn(checkIn("ON", 0)));
        assertEquals(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("双班次站点：periodIndex 0/1 按值精确命中早班/晚班")
    void dualShiftExactHit() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(early(), evening()));

        assertEquals("早班", service.checkIn(checkIn("ON", 0)).getPeriodName());
        assertEquals("晚班", service.checkIn(checkIn("ON", 1)).getPeriodName());
    }

    @Test
    @DisplayName("员工当天无排班仍可打卡（U-2）：时段取站点首个启用班次")
    void noScheduleStillPunchable() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(evening()));

        AttendanceRecordVO vo = service.checkIn(checkIn("ON", null));

        assertEquals(1, vo.getPeriodIndex());
        assertEquals("晚班", vo.getPeriodName());
    }

    @Test
    @DisplayName("站点无启用班次 → 9113（打卡拒绝，文案可指导）")
    void noEnabledShiftRejected() {
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkIn(checkIn("ON", 1)));
        assertEquals(ErrorCode.ATTENDANCE_NO_ENABLED_SHIFT.getCode(), ex.getCode());
    }

    private AttendanceCheckInRequest checkIn(String checkType, Integer periodIndex) {
        AttendanceCheckInRequest request = new AttendanceCheckInRequest();
        request.setCheckType(checkType);
        request.setPeriodIndex(periodIndex);
        return request;
    }

    /** 免校验规则：时间窗/WiFi/定位全关，使单测聚焦「时段取值」而非判定链 */
    private AttendanceRule rule() {
        AttendanceRule rule = new AttendanceRule();
        rule.setStationId(STATION);
        rule.setRuleName("默认打卡规则");
        rule.setEnableTimeWindow(0);
        rule.setEnableWifi(0);
        rule.setEnableLocation(0);
        rule.setMatchMode("ALL");
        rule.setLateThresholdMin(0);
        rule.setEarlyLeaveThresholdMin(0);
        rule.setAllowEarlyMin(30);
        rule.setAllowLateMin(60);
        return rule;
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(EMPLOYEE);
        employee.setRealName("张三");
        employee.setStationId(STATION);
        return employee;
    }

    private AttendanceShift early() {
        return shift(1L, "早班", "08:00", "16:00");
    }

    private AttendanceShift evening() {
        return shift(9L, "晚班", "16:00", "24:00");
    }

    private AttendanceShift shift(Long id, String name, String start, String end) {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(id);
        shift.setStationId(STATION);
        shift.setShiftName(name);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setColor("#123456");
        shift.setStatus(1);
        return shift;
    }
}
