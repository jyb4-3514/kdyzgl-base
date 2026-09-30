package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceShiftRequest;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.vo.attendance.AttendanceShiftVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 班次定义侧校验单测（真源统一方案 v1.2 §3.2 / §6.4 / §7.7，U-6）。
 * <p>
 * 覆盖：保留名（含前后空白）拒绝 9114、启用班次数上限 9114、{@code ordinal} 冲突 9114、
 * {@code defaultShift} 由首个启用班次派生 / 无启用班次返回 null。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceShiftDefinitionTest {

    private static final long STATION = 3L;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AttendanceShift.class);
        TableInfoHelper.initTableInfo(assistant, AttendanceSchedule.class);
    }

    private AttendanceShiftMapper shiftMapper;
    private StationMapper stationMapper;
    private AttendanceShiftServiceImpl service;

    @BeforeEach
    void setUp() {
        shiftMapper = mock(AttendanceShiftMapper.class);
        stationMapper = mock(StationMapper.class);
        service = new AttendanceShiftServiceImpl(shiftMapper, mock(AttendanceScheduleMapper.class),
                stationMapper, new AlgoProperties());
    }

    @Test
    @DisplayName("保留名（含前后空白）归一化后拒绝：回 9114，不做全角/大小写折叠")
    void reservedNameRejected() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        for (String name : List.of("全天班", " 全天班", "全天班 ")) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(shiftReq(name, "08:00", "16:00")));
            assertEquals(ErrorCode.ATTENDANCE_SHIFT_DEFINITION_INVALID.getCode(), ex.getCode(), "名称=" + name);
        }
    }

    @Test
    @DisplayName("非保留名（如「早班」）可创建")
    void normalNameAccepted() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        when(shiftMapper.selectList(any())).thenReturn(List.of());
        when(shiftMapper.insert(any(AttendanceShift.class))).thenReturn(1);
        AttendanceShiftVO vo = service.create(shiftReq("早班", "08:00", "16:00"));
        assertEquals("早班", vo.getShiftName());
    }

    @Test
    @DisplayName("启用班次 ordinal 冲突（同落午前）→ 9114")
    void ordinalConflictRejected() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        when(shiftMapper.selectList(any())).thenReturn(List.of(shift(1L, "早班", "08:00", "16:00", 1)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(shiftReq("上午班", "09:00", "12:00")));
        assertEquals(ErrorCode.ATTENDANCE_SHIFT_DEFINITION_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("启用班次数超上限（>2）→ 9114")
    void countLimitRejected() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        // 现存 2 个启用班次（其一 start_time 缺失不计 ordinal），新增第 3 个 → 超上限
        when(shiftMapper.selectList(any())).thenReturn(List.of(
                shift(1L, "脏班次", null, "24:00", 1),
                shift(2L, "早班", "08:00", "16:00", 1)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(shiftReq("晚班", "16:00", "24:00")));
        assertEquals(ErrorCode.ATTENDANCE_SHIFT_DEFINITION_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("defaultShift：有启用班次取首个（id=null）；无启用班次返回 null")
    void defaultShiftFromFirstEnabled() {
        when(stationMapper.selectById(STATION)).thenReturn(new Station());
        AttendanceRule rule = new AttendanceRule();
        rule.setStationId(STATION);

        when(shiftMapper.selectList(any())).thenReturn(List.of(shift(9L, "晚班", "16:00", "24:00", 1)));
        AttendanceShiftVO vo = service.defaultShift(rule);
        assertEquals("晚班", vo.getShiftName());
        assertEquals("16:00", vo.getStartTime());
        assertNull(vo.getId());

        when(shiftMapper.selectList(any())).thenReturn(List.of());
        assertNull(service.defaultShift(rule));
        assertNull(service.defaultShift(null));
    }

    private AttendanceShiftRequest shiftReq(String name, String start, String end) {
        AttendanceShiftRequest req = new AttendanceShiftRequest();
        req.setStationId(STATION);
        req.setShiftName(name);
        req.setStartTime(start);
        req.setEndTime(end);
        req.setColor("#123456");
        req.setStatus(1);
        return req;
    }

    private AttendanceShift shift(Long id, String name, String start, String end, int status) {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(id);
        shift.setStationId(STATION);
        shift.setShiftName(name);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setColor("#123456");
        shift.setStatus(status);
        return shift;
    }
}
