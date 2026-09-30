package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.dto.finance.PayrollSettingLogQuery;
import com.qiujie.dto.finance.StationPayrollSettingQuery;
import com.qiujie.dto.finance.StationPayrollSettingSaveRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.entity.StationPayrollSettingLog;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.StationPayrollSettingLogMapper;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.support.PayrollSettingLogAction;
import com.qiujie.vo.finance.StationPayrollSettingVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 驿站算薪配置服务单测（Mockito，无 DB）：I-1/I-2/I-3/I-9 与 9406/9407/9408、
 * 每次保存必写配置留痕（CREATE/UPDATE/ENABLE/DISABLE，ENABLE 可追溯）。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class StationPayrollSettingServiceImplTest {

    private static final Long STATION_ID = 8L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Station.class);
        TableInfoHelper.initTableInfo(assistant, StationPayrollSetting.class);
        TableInfoHelper.initTableInfo(assistant, StationPayrollSettingLog.class);
        TableInfoHelper.initTableInfo(assistant, Employee.class);
    }

    private StationPayrollSettingMapper settingMapper;
    private StationPayrollSettingLogMapper settingLogMapper;
    private StationMapper stationMapper;
    private EmployeeMapper employeeMapper;
    private StationPayrollSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        settingMapper = mock(StationPayrollSettingMapper.class);
        settingLogMapper = mock(StationPayrollSettingLogMapper.class);
        stationMapper = mock(StationMapper.class);
        employeeMapper = mock(EmployeeMapper.class);
        service = new StationPayrollSettingServiceImpl(settingMapper, settingLogMapper, stationMapper, employeeMapper);
        when(stationMapper.selectById(anyLong())).thenReturn(station());
        when(settingMapper.insert(any(StationPayrollSetting.class))).thenAnswer(inv -> {
            StationPayrollSetting s = inv.getArgument(0);
            s.setId(99L);
            return 1;
        });
        when(settingMapper.updateById(any(StationPayrollSetting.class))).thenReturn(1);
    }

    // ==================== I-2 ====================

    @Test
    @DisplayName("I-2 未配置 → 9406")
    void detail_notConfigured_throws9406() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(STATION_ID));
        assertEquals(ErrorCode.FINANCE_PAYROLL_SETTING_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("I-2 驿站不存在 → 4001")
    void detail_stationMissing_throws4001() {
        when(stationMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(STATION_ID));
        assertEquals(ErrorCode.STATION_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== I-3 ====================

    @Test
    @DisplayName("I-3 首次创建 → 写一条 CREATE 留痕（before 为 null，after 可追溯）")
    void save_new_writesCreateLog() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        StationPayrollSettingVO vo = service.save(STATION_ID, request(1, 5, "09:30", "月末结算"));

        assertEquals(1, vo.getEnabled());
        assertEquals(5, vo.getPayrollDay());
        verify(settingMapper).insert(any(StationPayrollSetting.class));
        StationPayrollSettingLog log = captureLog();
        assertEquals(PayrollSettingLogAction.CREATE, log.getAction());
        assertNull(log.getBefore());
        assertEquals(Boolean.TRUE, log.getAfter().contains("\"enabled\":1"));
    }

    @Test
    @DisplayName("I-3 启用 0→1 → action=ENABLE（可追溯），before/after 含 enabled")
    void save_enable_writesEnableAction() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(0)));
        service.save(STATION_ID, request(1, 5, "09:00", null));

        StationPayrollSettingLog log = captureLog();
        assertEquals(PayrollSettingLogAction.ENABLE, log.getAction());
        assertEquals(Boolean.TRUE, log.getBefore().contains("\"enabled\":0"));
        assertEquals(Boolean.TRUE, log.getAfter().contains("\"enabled\":1"));
    }

    @Test
    @DisplayName("I-3 停用 1→0 → action=DISABLE")
    void save_disable_writesDisableAction() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1)));
        service.save(STATION_ID, request(0, 5, "09:00", null));
        assertEquals(PayrollSettingLogAction.DISABLE, captureLog().getAction());
    }

    @Test
    @DisplayName("I-3 启停未变、仅改其它字段 → action=UPDATE")
    void save_otherChange_writesUpdateAction() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1)));
        service.save(STATION_ID, request(1, 7, "10:00", "改算薪日"));
        assertEquals(PayrollSettingLogAction.UPDATE, captureLog().getAction());
    }

    @Test
    @DisplayName("I-3 算薪日越界 → 9407，且不写留痕")
    void save_dayInvalid_throws9407() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STATION_ID, request(1, 32, "09:00", null)));
        assertEquals(ErrorCode.FINANCE_PAYROLL_SETTING_DAY_INVALID.getCode(), ex.getCode());
        verify(settingLogMapper, never()).insert(any(StationPayrollSettingLog.class));
    }

    @Test
    @DisplayName("I-3 时间格式非法 → 9408")
    void save_timeInvalid_throws9408() {
        when(settingMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(STATION_ID, request(1, 5, "9:00", null)));
        assertEquals(ErrorCode.FINANCE_PAYROLL_SETTING_TIME_INVALID.getCode(), ex.getCode());
        verify(settingLogMapper, never()).insert(any(StationPayrollSettingLog.class));
    }

    // ==================== I-9 ====================

    @Test
    @DisplayName("I-9 驿站不存在 → 4001")
    void logs_stationMissing_throws4001() {
        when(stationMapper.selectById(anyLong())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.logs(STATION_ID, new PayrollSettingLogQuery()));
        assertEquals(ErrorCode.STATION_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== I-1 ====================

    @Test
    @DisplayName("I-1 以驿站为主表合并配置：未配置站以 DDL 默认值填充；enabled 过滤按有效值")
    void list_mergesDefaultsAndFilters() {
        Station second = station();
        second.setId(9L);
        second.setStationName("二号驿站");
        when(stationMapper.selectList(any())).thenReturn(List.of(station(), second));
        StationPayrollSetting configured = setting(1);
        when(settingMapper.selectList(any())).thenReturn(List.of(configured));

        List<StationPayrollSettingVO> all = service.list(new StationPayrollSettingQuery());
        assertEquals(2, all.size());
        StationPayrollSettingVO first = all.get(0);
        assertEquals(1, first.getEnabled());
        StationPayrollSettingVO unconfigured = all.get(1);
        assertEquals(0, unconfigured.getEnabled());
        assertEquals(1, unconfigured.getPayrollDay());
        assertEquals("09:00", unconfigured.getPayrollTime());
        assertEquals(1, unconfigured.getNotifyEnabled());

        StationPayrollSettingQuery enabledOnly = new StationPayrollSettingQuery();
        enabledOnly.setEnabled(1);
        List<StationPayrollSettingVO> filtered = service.list(enabledOnly);
        assertEquals(1, filtered.size());
        assertEquals(STATION_ID, filtered.get(0).getStationId());
    }

    // ==================== helpers ====================

    private StationPayrollSettingLog captureLog() {
        ArgumentCaptor<StationPayrollSettingLog> captor = ArgumentCaptor.forClass(StationPayrollSettingLog.class);
        verify(settingLogMapper).insert(captor.capture());
        return captor.getValue();
    }

    private StationPayrollSettingSaveRequest request(int enabled, int day, String time, String remark) {
        StationPayrollSettingSaveRequest request = new StationPayrollSettingSaveRequest();
        request.setEnabled(enabled);
        request.setPayrollDay(day);
        request.setPayrollTime(time);
        request.setNotifyEnabled(1);
        request.setRemark(remark);
        return request;
    }

    private Station station() {
        Station station = new Station();
        station.setId(STATION_ID);
        station.setStationName("一号驿站");
        station.setStatus(1);
        return station;
    }

    private StationPayrollSetting setting(int enabled) {
        StationPayrollSetting setting = new StationPayrollSetting();
        setting.setId(1L);
        setting.setStationId(STATION_ID);
        setting.setEnabled(enabled);
        setting.setPayrollDay(5);
        setting.setPayrollTime("09:00");
        setting.setNotifyEnabled(1);
        return setting;
    }
}
