package com.qiujie.service.finance.schedule;

import com.qiujie.config.PayrollScheduleProperties;
import com.qiujie.entity.Station;
import com.qiujie.entity.StationPayrollSetting;
import com.qiujie.mapper.StationMapper;
import com.qiujie.mapper.StationPayrollSettingMapper;
import com.qiujie.service.finance.PayrollRunService;
import com.qiujie.service.finance.support.PayrollRunStatus;
import com.qiujie.vo.finance.PayrollRunVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 调度器 ticker 单测（Mockito，无 DB）：总开关短路、僵死回收先行、逐驿站失败隔离、单轮上限。
 * <p>本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class PayrollScheduleTickerTest {

    private PayrollScheduleProperties scheduleProperties;
    private StationPayrollSettingMapper settingMapper;
    private StationMapper stationMapper;
    private PayrollRunService payrollRunService;
    private PayrollScheduleTicker ticker;

    @BeforeEach
    void setUp() {
        scheduleProperties = new PayrollScheduleProperties();
        settingMapper = mock(StationPayrollSettingMapper.class);
        stationMapper = mock(StationMapper.class);
        payrollRunService = mock(PayrollRunService.class);
        ticker = new PayrollScheduleTicker(scheduleProperties, settingMapper, stationMapper, payrollRunService);
        scheduleProperties.setZone("Asia/Shanghai");
        scheduleProperties.setInterStationDelayMs(0);
        when(payrollRunService.reclaimStale(any())).thenReturn(0);
    }

    @Test
    @DisplayName("总开关关闭 → 不做任何扫描与执行")
    void tick_disabled_shortCircuits() {
        scheduleProperties.setEnabled(false);
        ticker.tick();
        verifyNoInteractions(settingMapper, stationMapper, payrollRunService);
    }

    @Test
    @DisplayName("僵死回收先行：开关开启时每轮先扫描回收")
    void tick_reclaimsBeforeScan() {
        scheduleProperties.setEnabled(true);
        when(settingMapper.selectList(any())).thenReturn(List.of());
        ticker.tick();
        verify(payrollRunService).reclaimStale(any());
    }

    @Test
    @DisplayName("逐驿站失败隔离：单站抛异常不影响其它站")
    void tick_isolatesStationFailure() {
        scheduleProperties.setEnabled(true);
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1L), setting(2L)));
        when(stationMapper.selectById(anyLong())).thenAnswer(inv -> station(inv.getArgument(0)));
        when(payrollRunService.executeIfDue(any(), any())).thenAnswer(inv -> {
            StationPayrollSetting s = inv.getArgument(0);
            if (Long.valueOf(1L).equals(s.getStationId())) {
                throw new IllegalStateException("station-1 boom");
            }
            return successVo();
        });

        assertDoesNotThrow(() -> ticker.tick());

        verify(payrollRunService, times(2)).executeIfDue(any(), any());
    }

    @Test
    @DisplayName("单轮驿站上限：达上限即顺延，不再执行后续站")
    void tick_respectsMaxStationsPerTick() {
        scheduleProperties.setEnabled(true);
        scheduleProperties.setMaxStationsPerTick(1);
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1L), setting(2L)));
        when(stationMapper.selectById(anyLong())).thenAnswer(inv -> station(inv.getArgument(0)));
        when(payrollRunService.executeIfDue(any(), any())).thenReturn(successVo());

        ticker.tick();

        verify(payrollRunService, times(1)).executeIfDue(any(), any());
    }

    @Test
    @DisplayName("station.status=0 的驿站不参与执行")
    void tick_skipsDisabledStation() {
        scheduleProperties.setEnabled(true);
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(1L)));
        Station disabled = station(1L);
        disabled.setStatus(0);
        when(stationMapper.selectById(anyLong())).thenReturn(disabled);

        ticker.tick();

        verify(payrollRunService, never()).executeIfDue(any(), any());
    }

    private StationPayrollSetting setting(Long stationId) {
        StationPayrollSetting setting = new StationPayrollSetting();
        setting.setId(stationId);
        setting.setStationId(stationId);
        setting.setEnabled(1);
        setting.setPayrollDay(1);
        setting.setPayrollTime("00:00");
        return setting;
    }

    private Station station(Long id) {
        Station station = new Station();
        station.setId(id);
        station.setStationName("驿站" + id);
        station.setStatus(1);
        return station;
    }

    private PayrollRunVO successVo() {
        PayrollRunVO vo = new PayrollRunVO();
        vo.setId(1L);
        vo.setStatus(PayrollRunStatus.SUCCESS.name());
        return vo;
    }
}
