package com.qiujie.service.attendance.support;

import com.qiujie.service.finance.support.ShiftPayrollPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 出勤口径聚合单测。
 * <p>
 * 覆盖两类口径：① B7b 班次粒度（{@code summarizeByShift}，用户已裁定：应到/缺卡按班次统计）；
 * ② 旧「按人/天去重」回落口径（{@code summarize}，{@code absentGranularity=PER_DAY} 时启用）。
 * 另含「存量单班次零变化」对照与计薪折算算例（{@code 1500÷30÷2=25/班}）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceSummaryPolicyTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 9, 24);
    private static final int MIDDAY_BOUNDARY_MINUTE = 720;
    private static final String SENTINEL = "全天班";

    // ==================== ① B7b 班次粒度口径 ====================

    @Test
    @DisplayName("两班全勤不再被判缺卡：应到 2 / 实到 2 / 缺卡 0")
    void twoShiftsFullAttendanceNoAbsent() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(1L, 1, "晚班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, records);

        assertEquals(2, s.shouldCount(), "应到按班次计数：一天两班 = 2");
        assertEquals(2, s.actualCount(), "实到 = |A∩R|：两班各一张有效 ON 卡 = 2");
        assertEquals(0, s.absentCount(), "两班全勤不缺卡（旧口径会误判为 1）");
        assertEquals(2, s.normalCount());
    }

    @Test
    @DisplayName("一天两班其中一班缺卡 → 缺卡数 = 1（不是当天算缺勤）")
    void oneShiftMissingCountsOneAbsent() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, records);

        assertEquals(2, s.shouldCount());
        assertEquals(1, s.actualCount());
        assertEquals(1, s.absentCount(), "仅缺晚班 1 个班次，可定位到班次");
    }

    @Test
    @DisplayName("应到按班次计数：一天两班无打卡 → 应到 2 / 实到 0 / 缺卡 2")
    void shouldCountsPerShift() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"));

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, List.of());

        assertEquals(2, s.shouldCount(), "应到是班次数而非天数");
        assertEquals(0, s.actualCount());
        assertEquals(2, s.absentCount());
    }

    @Test
    @DisplayName("多打卡不超额：A∩R 取交封顶，实到不超过应到")
    void extraPunchNotOverCount() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(1L, 1, "晚班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL), // 重复卡
                record(1L, 5, "异常时段", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL)); // R 之外

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, records);

        assertEquals(2, s.shouldCount());
        assertEquals(2, s.actualCount(), "A∩R 取交 ⇒ 多打卡不放大实到（封顶 = 满额）");
        assertEquals(0, s.absentCount());
    }

    @Test
    @DisplayName("跨员工不互相顶缺：E1 两班全勤、E2 缺晚班 → 应到 4 / 实到 3 / 缺卡 1")
    void multiEmployeeNoCrossFill() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"),
                slot(2L, "08:00"), slot(2L, "16:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(1L, 1, "晚班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(2L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, records);

        assertEquals(4, s.shouldCount());
        assertEquals(3, s.actualCount(), "员工维度分组后 E1 的卡不得填补 E2 的应到");
        assertEquals(1, s.absentCount());
    }

    @Test
    @DisplayName("迟到/早退逐班次各自判定：早班迟到 1 次 + 晚班早退 1 次")
    void lateAndEarlyLeavePerShift() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE),
                record(1L, 1, "晚班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(1L, 1, "晚班", AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_EARLY_LEAVE));

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, records);

        assertEquals(1, s.lateCount());
        assertEquals(1, s.normalCount());
        assertEquals(1, s.earlyLeaveCount());
        assertEquals(2, s.actualCount());
        assertEquals(0, s.absentCount());
    }

    @Test
    @DisplayName("ABNORMAL 卡不计实到/正常/迟到/早退（沿用旧过滤）")
    void abnormalCardExcluded() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(
                slot(1L, "08:00"), slot(1L, "16:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(1L, 1, "晚班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_ABNORMAL));

        AttendanceSummaryPolicy.Summary s = summarizeByShift(schedules, records);

        assertEquals(2, s.shouldCount());
        assertEquals(1, s.actualCount(), "异常卡不算有效出勤");
        assertEquals(1, s.absentCount());
        assertEquals(0, s.lateCount());
    }

    // ==================== ② 存量单班次零变化 ====================

    @Test
    @DisplayName("存量单班次零变化：早晚口径逐项一致（应到/实到/缺卡）")
    void singleShiftLegacyUnchanged() {
        List<AttendanceSummaryPolicy.ScheduleSlot> schedules = List.of(slot(1L, "08:00"), slot(2L, "08:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                record(1L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                record(2L, 0, "早班", AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));

        AttendanceSummaryPolicy.Summary byShift = summarizeByShift(schedules, records);

        // 旧口径：应到 = 排班人数；实到 = 有效上班卡员工去重；缺卡 = 应到 − 实到
        AttendanceSummaryPolicy.Summary byDay = AttendanceSummaryPolicy.summarize(2,
                List.of(card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                        card(2L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL)),
                List.of());

        assertEquals(byDay.shouldCount(), byShift.shouldCount());
        assertEquals(byDay.actualCount(), byShift.actualCount());
        assertEquals(byDay.absentCount(), byShift.absentCount());
        assertEquals(2, byShift.shouldCount());
        assertEquals(2, byShift.actualCount());
        assertEquals(0, byShift.absentCount());
    }

    // ==================== ③ 计薪折算算例（1500 ÷ 30 ÷ 2 = 25/班） ====================

    @Test
    @DisplayName("计薪折算核验：1500 × 1 ÷ 60 = 25/班；一天两班满额 = 1500")
    void payrollProratedExample() {
        BigDecimal basic = new BigDecimal("1500");
        // 30 天 × 2 班 = 60 个应出班次；单班 = 1500 ÷ 60 = 25（等价用户算例 1500÷30÷2）
        assertEquals(0, ShiftPayrollPolicy.prorated(basic, 1, 60, ShiftPayrollPolicy.ZERO_SCHEDULE_FULL_BASIC)
                .compareTo(new BigDecimal("25.00")));
        // 一天两班全出勤：1500 × 2 ÷ 2 = 1500
        assertEquals(0, ShiftPayrollPolicy.prorated(basic, 2, 2, ShiftPayrollPolicy.ZERO_SCHEDULE_FULL_BASIC)
                .compareTo(new BigDecimal("1500.00")));
        // 满月全勤：1500 × 60 ÷ 60 = 1500
        assertEquals(0, ShiftPayrollPolicy.prorated(basic, 60, 60, ShiftPayrollPolicy.ZERO_SCHEDULE_FULL_BASIC)
                .compareTo(new BigDecimal("1500.00")));
    }

    // ==================== ④ 旧口径回落（absentGranularity=PER_DAY） ====================

    @Test
    @DisplayName("回落口径一致：实到按员工去重、缺卡不为负、ABNORMAL 全不计")
    void summarize() {
        List<AttendanceCard> onCards = List.of(
                card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                card(2L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE),
                card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE),
                // 异常卡：不计入实到，也不计入正常/迟到
                card(4L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_ABNORMAL));
        List<AttendanceCard> offCards = List.of(
                card(1L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_EARLY_LEAVE),
                card(2L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_NORMAL),
                card(5L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_ABNORMAL));

        AttendanceSummaryPolicy.Summary summary = AttendanceSummaryPolicy.summarize(5, onCards, offCards);

        assertEquals(5, summary.shouldCount());
        assertEquals(2, summary.actualCount(), "实到只认员工 1、2（去重，且排除异常卡员工 4）");
        assertEquals(1, summary.normalCount());
        assertEquals(2, summary.lateCount());
        assertEquals(1, summary.earlyLeaveCount());
        assertEquals(3, summary.absentCount());
    }

    @Test
    @DisplayName("回落口径：实到多于应到时缺卡为 0，不出现负数")
    void absentNeverNegative() {
        List<AttendanceCard> onCards = List.of(
                card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                card(2L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));
        AttendanceSummaryPolicy.Summary summary =
                AttendanceSummaryPolicy.summarize(1, onCards, List.of());
        assertEquals(0, summary.absentCount());
    }

    // ==================== 夹具 ====================

    private AttendanceSummaryPolicy.Summary summarizeByShift(
            List<AttendanceSummaryPolicy.ScheduleSlot> schedules,
            List<AttendanceSummaryPolicy.RecordSlot> records) {
        return AttendanceSummaryPolicy.summarizeByShift(WORK_DATE, schedules, records,
                MIDDAY_BOUNDARY_MINUTE, SENTINEL, ShiftPayrollPolicy.LATE_PER_CARD);
    }

    private AttendanceSummaryPolicy.ScheduleSlot slot(long employeeId, String startTime) {
        return new AttendanceSummaryPolicy.ScheduleSlot(employeeId, startTime);
    }

    private AttendanceSummaryPolicy.RecordSlot record(long employeeId, Integer periodIndex, String periodName,
                                                      String checkType, String status) {
        return new AttendanceSummaryPolicy.RecordSlot(employeeId, periodIndex, periodName, checkType, status);
    }

    private AttendanceCard card(Long employeeId, String checkType, String status) {
        return new AttendanceCard(employeeId, checkType, status,
                LocalDateTime.of(2026, 9, 24, 8, 0), "全天班", null);
    }
}
