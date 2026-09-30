package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceRuleRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.CheckPeriod;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceRuleMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.vo.attendance.AttendanceRuleVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 规则出参/保存「时段真源统一」单测（方案 v1.2 §5 / §7.1 / §7.2，U-4 / U-5）。
 * <p>
 * 覆盖：{@code GET /rule} 时段/频次/上下班时间由启用班次派生、{@code checkPeriodsReadonly=true}；
 * 无启用班次时派生为空；{@code PUT /rule} 带 {@code checkPeriods} 回通用 400；入参 {@code workStartTime/EndTime} 被忽略。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceRuleTimeSourceTest {

    private static final long STATION = 3L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceRule.class);
    }

    private AttendanceRuleMapper ruleMapper;
    private StationMapper stationMapper;
    private AttendanceShiftService shiftService;
    private AttendanceRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(AttendanceRuleMapper.class);
        stationMapper = mock(StationMapper.class);
        shiftService = mock(AttendanceShiftService.class);
        service = new AttendanceRuleServiceImpl(ruleMapper, stationMapper, shiftService, new AlgoProperties());
    }

    @Test
    @DisplayName("GET /rule：时段/频次/上下班时间由启用班次派生，checkPeriodsReadonly=true")
    void toVoDerivesFromShifts() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(
                shift(1L, "早班", "08:00", "16:00"), shift(2L, "晚班", "16:00", "24:00")));

        AttendanceRuleVO vo = service.toVO(rule(2, checkPeriod("全天班", "08:00", "18:00")));

        assertEquals(4, vo.getCheckFrequency());
        assertEquals(2, vo.getCheckPeriods().size());
        assertEquals("早班", vo.getCheckPeriods().get(0).getName());
        assertEquals("晚班", vo.getCheckPeriods().get(1).getName());
        assertEquals(Boolean.TRUE, vo.getCheckPeriodsReadonly());
        assertEquals("08:00", vo.getWorkStartTime());
        assertEquals("24:00", vo.getWorkEndTime());
    }

    @Test
    @DisplayName("无启用班次：派生时段为空、频次 0、上下班时间为 null")
    void toVoEmptyWhenNoEnabledShift() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of());

        AttendanceRuleVO vo = service.toVO(rule(2, checkPeriod("全天班", "08:00", "18:00")));

        assertEquals(0, vo.getCheckFrequency());
        assertTrue(vo.getCheckPeriods().isEmpty());
        assertEquals(Boolean.TRUE, vo.getCheckPeriodsReadonly());
        assertNull(vo.getWorkStartTime());
        assertNull(vo.getWorkEndTime());
    }

    @Test
    @DisplayName("PUT /rule 带非空 checkPeriods → 通用 400（U-5，不新增 91xx）")
    void saveRuleRejectsCheckPeriods() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        AttendanceRuleRequest req = new AttendanceRuleRequest();
        req.setStationId(STATION);
        req.setCheckPeriods(List.of(checkPeriod("全天班", "08:00", "18:00")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveRule(req));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("PUT /rule 忽略 workStartTime/EndTime：不写入列，出参改派生")
    void saveRuleIgnoresDeprecatedTimeFields() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        AttendanceRule current = rule(2, checkPeriod("全天班", "08:00", "18:00"));
        current.setId(7L);
        current.setRuleName("旧名");
        current.setWorkStartTime("08:00");
        current.setWorkEndTime("18:00");
        when(ruleMapper.selectList(any())).thenReturn(List.of(current));
        when(shiftService.enabledShifts(STATION)).thenReturn(List.of(
                shift(1L, "早班", "08:00", "16:00"), shift(2L, "晚班", "16:00", "24:00")));

        AttendanceRuleRequest req = new AttendanceRuleRequest();
        req.setStationId(STATION);
        req.setRuleName("新名");
        req.setWorkStartTime("09:00");
        req.setWorkEndTime("17:00");

        AttendanceRuleVO vo = service.saveRule(req);

        assertEquals("新名", current.getRuleName());
        // 入参上下班时间被忽略：列保持原值不被覆盖
        assertEquals("08:00", current.getWorkStartTime());
        assertEquals("18:00", current.getWorkEndTime());
        // 出参为派生值
        assertEquals("08:00", vo.getWorkStartTime());
        assertEquals("24:00", vo.getWorkEndTime());
    }

    private AttendanceRule rule(Integer storedFrequency, CheckPeriod... periods) {
        AttendanceRule rule = new AttendanceRule();
        rule.setStationId(STATION);
        rule.setRuleName("默认打卡规则");
        rule.setCheckFrequency(storedFrequency);
        rule.setCheckPeriods(List.of(periods));
        rule.setAllowEarlyMin(30);
        rule.setAllowLateMin(60);
        rule.setStatus(1);
        return rule;
    }

    private CheckPeriod checkPeriod(String name, String start, String end) {
        CheckPeriod period = new CheckPeriod();
        period.setName(name);
        period.setStartTime(start);
        period.setEndTime(end);
        return period;
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
