package com.qiujie.service.attendance.support;

import com.qiujie.entity.AttendanceShift;
import com.qiujie.service.finance.support.ShiftPayrollPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打卡时段「由启用班次派生」单测（真源统一方案 v1.2 §4.4 / §4.5）。
 * <p>
 * 覆盖：单班次晚班站点 {@code ordinal=1}、双班次早/晚、按 {@code ordinal} 按值查找（禁下标）、
 * {@code start_time} 不可解析排除、首末派生、{@code shiftOrdinal} 复用、存量哨兵读取零变化。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendancePeriodResolverShiftTest {

    private static final int BOUNDARY = 720;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);

    @Test
    @DisplayName("单班次晚班站点：唯一时段 periodIndex=1（ordinal，不是数组下标 0）")
    void singleEveningShift() {
        List<AttendancePeriodResolver.ResolvedPeriod> periods =
                AttendancePeriodResolver.resolveByShifts(List.of(shift(9L, "晚班", "16:00", "24:00")), BOUNDARY);
        assertEquals(1, periods.size());
        assertEquals(1, periods.get(0).periodIndex());
        assertEquals("晚班", periods.get(0).name());
        assertEquals("16:00", periods.get(0).startTime());
        assertEquals("24:00", periods.get(0).endTime());
    }

    @Test
    @DisplayName("双班次：早 0 / 晚 1，名称取班次名快照，首末派生 08:00 / 24:00")
    void dualShifts() {
        List<AttendancePeriodResolver.ResolvedPeriod> periods = AttendancePeriodResolver.resolveByShifts(
                List.of(shift(1L, "早班", "08:00", "16:00"), shift(2L, "晚班", "16:00", "24:00")), BOUNDARY);
        assertEquals(2, periods.size());
        assertEquals(0, periods.get(0).periodIndex());
        assertEquals("早班", periods.get(0).name());
        assertEquals(1, periods.get(1).periodIndex());
        assertEquals("晚班", periods.get(1).name());
        assertEquals("08:00", AttendancePeriodResolver.firstStartTime(periods));
        assertEquals("24:00", AttendancePeriodResolver.lastEndTime(periods));
    }

    @Test
    @DisplayName("按 ordinal 按值查找：晚班站点 index=1 命中、index=0 查不到（M1 核心）")
    void findByOrdinalIsValueBased() {
        List<AttendancePeriodResolver.ResolvedPeriod> evening =
                AttendancePeriodResolver.resolveByShifts(List.of(shift(9L, "晚班", "16:00", "24:00")), BOUNDARY);
        assertNotNull(AttendancePeriodResolver.findByOrdinal(evening, 1));
        assertEquals("晚班", AttendancePeriodResolver.findByOrdinal(evening, 1).name());
        assertNull(AttendancePeriodResolver.findByOrdinal(evening, 0));
        assertNull(AttendancePeriodResolver.findByOrdinal(evening, null));
        assertNull(AttendancePeriodResolver.findByOrdinal(null, 1));
    }

    @Test
    @DisplayName("边界：无启用班次 → 空集合、首末为 null；start_time 不可解析的班次被排除")
    void emptyAndUnparseable() {
        assertTrue(AttendancePeriodResolver.resolveByShifts(List.of(), BOUNDARY).isEmpty());
        assertNull(AttendancePeriodResolver.firstStartTime(List.of()));
        assertNull(AttendancePeriodResolver.lastEndTime(List.of()));

        List<AttendancePeriodResolver.ResolvedPeriod> periods = AttendancePeriodResolver.resolveByShifts(
                List.of(shift(9L, "脏班次", null, "24:00"), shift(10L, "晚班", "16:00", "24:00")), BOUNDARY);
        assertEquals(1, periods.size());
        assertEquals("晚班", periods.get(0).name());
    }

    @Test
    @DisplayName("非启用班次不入派生集合（防御层）")
    void disabledShiftExcluded() {
        AttendanceShift disabled = shift(9L, "晚班", "16:00", "24:00");
        disabled.setStatus(0);
        assertTrue(AttendancePeriodResolver.resolveByShifts(List.of(disabled), BOUNDARY).isEmpty());
    }

    @Test
    @DisplayName("shiftOrdinal 复用计薪唯一实现：16:00→1、08:00→0、缺失→-1、12:00 归晚班")
    void shiftOrdinalDelegate() {
        assertEquals(1, AttendancePeriodResolver.shiftOrdinal("16:00", BOUNDARY));
        assertEquals(0, AttendancePeriodResolver.shiftOrdinal("08:00", BOUNDARY));
        assertEquals(-1, AttendancePeriodResolver.shiftOrdinal(null, BOUNDARY));
        assertEquals(-1, AttendancePeriodResolver.shiftOrdinal("abc", BOUNDARY));
        assertEquals(1, AttendancePeriodResolver.shiftOrdinal("12:00", BOUNDARY));
    }

    @Test
    @DisplayName("存量哨兵读取零变化：period_name='全天班' 仍覆盖当日全部班次（三态分支①）")
    void legacySentinelCoversAllShifts() {
        Map<LocalDate, Set<Long>> dayKeys = Map.of(DATE,
                Set.of(ShiftPayrollPolicy.shiftUnit(DATE, 0), ShiftPayrollPolicy.shiftUnit(DATE, 1)));
        List<ShiftPayrollPolicy.RecordRow> records = List.of(
                new ShiftPayrollPolicy.RecordRow(DATE, 0, "全天班", "ON", "NORMAL"));
        Set<Long> attended = ShiftPayrollPolicy.attendedShiftSet(records, dayKeys, "全天班",
                new ArrayList<>());
        assertEquals(2, attended.size());
        assertTrue(attended.contains(ShiftPayrollPolicy.shiftUnit(DATE, 0)));
        assertTrue(attended.contains(ShiftPayrollPolicy.shiftUnit(DATE, 1)));
    }

    private AttendanceShift shift(Long id, String name, String start, String end) {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(id);
        shift.setStationId(3L);
        shift.setShiftName(name);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setColor("#0958D9");
        shift.setStatus(1);
        return shift;
    }
}
