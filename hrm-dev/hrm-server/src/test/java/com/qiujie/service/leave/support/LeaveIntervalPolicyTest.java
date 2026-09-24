package com.qiujie.service.leave.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请假半天单元区间算法纯逻辑单测（算法 S5，algo-hrm-server.md §7；原型 {@code algo-scripts/s5-leave.mjs}）。
 * <p>
 * 边界覆盖：同日 PM→AM（非法）、同日半天、跨月、跨年、零排班、全排班、边界含端点、
 * 区间重叠（相交/不相交/长包短）、首个命中顺序、账期跨度。
 * <p>
 * <b>等价性</b>：区间法（{@code countedDaysByRange}）与逐日法（{@code countedDaysBaseline}，
 * 即 Mock {@code halfUnitsOf} 语义）在本类多组用例下断言 0 差异；本机无 JDK/Maven，测试
 * <b>未执行</b>，收敛到服务器阶段运行（并与 S5 原型 20000 次随机查询 0 差异互相印证）。
 */
class LeaveIntervalPolicyTest {

    private static final String AM = LeaveConstants.PERIOD_AM;
    private static final String PM = LeaveConstants.PERIOD_PM;

    // ==================== 半天单元换算与自然天数 ====================

    @Test
    @DisplayName("单元索引：同一天 AM < PM，且相邻日连续（无空洞）")
    void unitIndexContinuity() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        long am = LeaveIntervalPolicy.unitOf(d, AM);
        long pm = LeaveIntervalPolicy.unitOf(d, PM);
        assertEquals(am + 1, pm);
        assertEquals(pm + 1, LeaveIntervalPolicy.unitOf(d.plusDays(1), AM));
        assertEquals(d, LeaveIntervalPolicy.dateOfUnit(am));
        assertEquals(d, LeaveIntervalPolicy.dateOfUnit(pm));
    }

    @Test
    @DisplayName("自然天数：同日半天 0.5、全天 1.0、三日全天 3.0")
    void naturalDays() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertEquals(0.5, LeaveIntervalPolicy.naturalDays(d, PM, d, PM), 0.0);
        assertEquals(1.0, LeaveIntervalPolicy.naturalDays(d, AM, d, PM), 0.0);
        assertEquals(3.0, LeaveIntervalPolicy.naturalDays(d, AM, d.plusDays(2), PM), 0.0);
        assertEquals(2.0, LeaveIntervalPolicy.naturalDays(d, PM, d.plusDays(2), AM), 0.0);
    }

    @Test
    @DisplayName("非法组合：同日 PM→AM 得 0 单元、区间非法、自然天数 0（对齐 Mock 0 天 + 9604）")
    void sameDayPmToAmIsInvalid() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        LeaveUnitRange range = LeaveIntervalPolicy.unitRange(d, PM, d, AM);
        assertFalse(range.valid());
        assertEquals(0L, range.unitCount());
        assertEquals(0.0, range.naturalDays(), 0.0);
        assertTrue(LeaveIntervalPolicy.halfUnitsByDay(d, PM, d, AM).isEmpty());
    }

    // ==================== 计薪天数：区间法 vs 逐日法（0 差异） ====================

    @Test
    @DisplayName("计薪天数：零排班 → 0；全排班 → 等于自然天数")
    void countedDaysZeroAndFull() {
        LocalDate s = LocalDate.of(2026, 10, 1);
        LocalDate e = LocalDate.of(2026, 10, 3);
        long[] empty = LeaveIntervalPolicy.scheduleUnits(List.of());
        assertEquals(0.0, LeaveIntervalPolicy.countedDaysByRange(empty, unitRange(s, AM, e, PM)), 0.0);

        List<LocalDate> all = daysBetween(s, e);
        long[] full = LeaveIntervalPolicy.scheduleUnits(all);
        assertEquals(3.0, LeaveIntervalPolicy.countedDaysByRange(full, unitRange(s, AM, e, PM)), 0.0);
    }

    @Test
    @DisplayName("计薪天数：只统计有排班的日期单元（排除轮休日），半天边界按单元计")
    void countedDaysExcludesRestDays() {
        LocalDate s = LocalDate.of(2026, 10, 1); // 10-01..10-04
        LocalDate e = LocalDate.of(2026, 10, 4);
        // 排班：10-01、10-03（10-02、10-04 轮休）
        List<LocalDate> scheduled = List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        long[] units = LeaveIntervalPolicy.scheduleUnits(scheduled);
        // 全区间 AM..PM：排班 2 天 → 2.0
        assertEquals(2.0, LeaveIntervalPolicy.countedDaysByRange(units, unitRange(s, AM, e, PM)), 0.0);
        // 起始日 PM：首日只剩 PM，恰逢 10-01 有排班 → 该日计 0.5（半天），10-03 全天计 1.0 → 合计 1.5
        assertEquals(1.5, LeaveIntervalPolicy.countedDaysByRange(units, unitRange(s, PM, e, PM)), 0.0);
        // 只在轮休日 10-02 请半天 → 0
        LocalDate rest = LocalDate.of(2026, 10, 2);
        assertEquals(0.0, LeaveIntervalPolicy.countedDaysByRange(units, unitRange(rest, AM, rest, PM)), 0.0);
    }

    @Test
    @DisplayName("等价性：多组区间 × 多组排班，区间法 = 逐日法（0 差异）")
    void rangeEqualsBaselineAcrossCases() {
        List<LocalDate> scheduleA = List.of(
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 5));
        List<LocalDate> scheduleB = List.of(
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 4));

        // 跨月：9/29 PM → 10/5 AM
        assertEquivalent(scheduleA, LocalDate.of(2026, 9, 29), PM, LocalDate.of(2026, 10, 5), AM);
        // 跨年：12/31 AM → 2027/1/4 PM
        assertEquivalent(scheduleB, LocalDate.of(2026, 12, 31), AM, LocalDate.of(2027, 1, 4), PM);
        // 同日半天（命中排班日 10-02）
        assertEquivalent(scheduleA, LocalDate.of(2026, 10, 2), AM, LocalDate.of(2026, 10, 2), AM);
        // 起始 PM / 结束 AM（双侧边界）
        assertEquivalent(scheduleA, LocalDate.of(2026, 10, 1), PM, LocalDate.of(2026, 10, 5), AM);
    }

    /** 区间法与逐日法（Mock halfUnitsOf 语义）必须 0 差异 */
    private void assertEquivalent(List<LocalDate> scheduled, LocalDate s, String sp, LocalDate e, String ep) {
        double byRange = LeaveIntervalPolicy.countedDaysByRange(
                LeaveIntervalPolicy.scheduleUnits(scheduled), unitRange(s, sp, e, ep));
        double baseline = LeaveIntervalPolicy.countedDaysBaseline(new LinkedHashSet<>(scheduled), s, sp, e, ep);
        assertEquals(baseline, byRange, 0.0, "区间法与逐日法应 0 差异：" + s + " " + sp + " ~ " + e + " " + ep);
    }

    // ==================== 重叠判定 ====================

    @Test
    @DisplayName("区间相交：不相交 / 首尾相接 / 部分重叠 / 完全包含")
    void intersectMatrix() {
        LocalDate d1 = LocalDate.of(2026, 10, 1);
        LocalDate d2 = LocalDate.of(2026, 10, 2);
        LocalDate d3 = LocalDate.of(2026, 10, 3);
        LeaveUnitRange a = unitRange(d1, AM, d1, PM); // 10-01 全天
        assertFalse(LeaveIntervalPolicy.intersects(a, unitRange(d2, AM, d2, PM)));
        assertTrue(LeaveIntervalPolicy.intersects(a, unitRange(d1, PM, d1, PM)));
        assertTrue(LeaveIntervalPolicy.intersects(a, unitRange(d1, AM, d3, PM)));
        assertTrue(LeaveIntervalPolicy.intersects(unitRange(d1, PM, d3, AM), unitRange(d2, AM, d2, PM)));
    }

    @Test
    @DisplayName("相交存在性（O(log k)）：长区间包住新起点也能命中；前缀最大值法正确")
    void intersectsAnyHandlesLongInterval() {
        LocalDate base = LocalDate.of(2026, 10, 1);
        // 已占用：一条很长（10-01..10-20），一条在右侧（10-25）
        List<OccupiedLeaveInterval> sorted = List.of(
                new OccupiedLeaveInterval(1L, unitRange(base, AM, base.plusDays(19), PM)),
                new OccupiedLeaveInterval(2L, unitRange(base.plusDays(24), AM, base.plusDays(24), PM)));
        // 新单在 10-15（被长区间包住，start 远大于新起点方向）
        assertTrue(LeaveIntervalPolicy.intersectsAny(sorted, unitRange(base.plusDays(14), AM, base.plusDays(14), PM)));
        // 新单在空档 10-22 → 不相交
        assertFalse(LeaveIntervalPolicy.intersectsAny(sorted, unitRange(base.plusDays(21), AM, base.plusDays(21), PM)));
        // 空集合
        assertFalse(LeaveIntervalPolicy.intersectsAny(List.of(), unitRange(base, AM, base, PM)));
    }

    @Test
    @DisplayName("首个命中：按传入顺序返回（对齐 Mock 首个命中），无命中返回 null")
    void firstIntersectingOrder() {
        LocalDate base = LocalDate.of(2026, 10, 1);
        OccupiedLeaveInterval first = new OccupiedLeaveInterval(1L, unitRange(base, AM, base, PM));
        OccupiedLeaveInterval second = new OccupiedLeaveInterval(2L, unitRange(base, PM, base.plusDays(1), PM));
        LeaveUnitRange target = unitRange(base, AM, base, AM);
        assertEquals(1L, LeaveIntervalPolicy.firstIntersecting(List.of(first, second), target).leaveId());
        assertNull(LeaveIntervalPolicy.firstIntersecting(List.of(second), unitRange(base, AM, base, AM)));
    }

    // ==================== 账期跨度 ====================

    @Test
    @DisplayName("账期跨度：同月 1 个、跨月 2 个、跨年连续（12 → 次年 1）")
    void monthSpans() {
        assertEquals(List.of("2026-10"), LeaveIntervalPolicy.monthSpans(
                unitRange(LocalDate.of(2026, 10, 1), AM, LocalDate.of(2026, 10, 5), PM)));
        assertEquals(List.of("2026-09", "2026-10"), LeaveIntervalPolicy.monthSpans(
                unitRange(LocalDate.of(2026, 9, 29), PM, LocalDate.of(2026, 10, 2), AM)));
        assertEquals(List.of("2026-12", "2027-01"), LeaveIntervalPolicy.monthSpans(
                unitRange(LocalDate.of(2026, 12, 30), AM, LocalDate.of(2027, 1, 2), PM)));
        assertTrue(LeaveIntervalPolicy.monthSpans(
                unitRange(LocalDate.of(2026, 10, 1), PM, LocalDate.of(2026, 10, 1), AM)).isEmpty());
    }

    // ==================== 夹具 ====================

    private static LeaveUnitRange unitRange(LocalDate s, String sp, LocalDate e, String ep) {
        return LeaveIntervalPolicy.unitRange(s, sp, e, ep);
    }

    private static List<LocalDate> daysBetween(LocalDate s, LocalDate e) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }
}
