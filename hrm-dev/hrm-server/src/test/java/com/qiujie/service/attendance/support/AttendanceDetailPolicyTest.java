package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 考勤明细策略单测（边界：六维度名单来源、到达态优先级、最早卡选取、风险排序）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceDetailPolicyTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 9, 24);
    private static final int MIDDAY_BOUNDARY_MINUTE = 720;
    private static final String SENTINEL = "全天班";

    private final List<AttendanceDetailPolicy.Member> shouldRows = List.of(
            new AttendanceDetailPolicy.Member(1L, "早班", "08:00"),
            new AttendanceDetailPolicy.Member(2L, "晚班", "16:00"));

    private final List<AttendanceCard> validOn = List.of(
            card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE, 10),
            card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 5),
            card(3L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 6));

    private final List<AttendanceCard> validOff = List.of(
            card(1L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_EARLY_LEAVE, 20));

    @Test
    @DisplayName("SHOULD = 应到全部（带班次名）；ABSENT = 应到行按班次粒度取差（与概况同源）")
    void shouldAndAbsent() {
        List<AttendanceDetailPolicy.Member> should =
                AttendanceDetailPolicy.members("SHOULD", shouldRows, validOn, validOff);
        assertEquals(2, should.size());
        assertEquals("早班", should.get(0).shiftName());

        List<AttendanceDetailPolicy.Member> absent = AttendanceDetailPolicy.absentMembers(
                WORK_DATE, shouldRows, onRecords(), MIDDAY_BOUNDARY_MINUTE, SENTINEL);
        assertEquals(1, absent.size());
        assertEquals(2L, absent.get(0).employeeId(),
                "员工 1 命中哨兵覆盖全部班次、员工 3 无对应排班 → 仅员工 2 缺卡");
    }

    @Test
    @DisplayName("多班次：仅打早班卡的员工，其晚班行计入缺卡（班次粒度，与概况 absentCount 一致）")
    void multiShiftAbsentPerShift() {
        List<AttendanceDetailPolicy.Member> rows = List.of(
                new AttendanceDetailPolicy.Member(1L, "早班", "08:00"),
                new AttendanceDetailPolicy.Member(1L, "晚班", "16:00"),
                new AttendanceDetailPolicy.Member(2L, "早班", "08:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                new AttendanceSummaryPolicy.RecordSlot(1L, 0, "早班",
                        AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));

        List<AttendanceDetailPolicy.Member> absent = AttendanceDetailPolicy.absentMembers(
                WORK_DATE, rows, records, MIDDAY_BOUNDARY_MINUTE, SENTINEL);
        assertEquals(2, absent.size(), "员工 1 缺晚班、员工 2 缺早班");
        assertEquals(1L, absent.get(0).employeeId());
        assertEquals("晚班", absent.get(0).shiftName(), "顺序与应到行一致：员工 1 的晚班行在前");
        assertEquals(2L, absent.get(1).employeeId());

        // 与概况同源：明细缺卡行数 == 概况 absentCount
        List<AttendanceSummaryPolicy.ScheduleSlot> scheduleSlots = List.of(
                new AttendanceSummaryPolicy.ScheduleSlot(1L, "08:00"),
                new AttendanceSummaryPolicy.ScheduleSlot(1L, "16:00"),
                new AttendanceSummaryPolicy.ScheduleSlot(2L, "08:00"));
        AttendanceSummaryPolicy.Summary summary = AttendanceSummaryPolicy.summarizeByShift(
                WORK_DATE, scheduleSlots, records, MIDDAY_BOUNDARY_MINUTE, SENTINEL, "PER_CARD");
        assertEquals(absent.size(), summary.absentCount());
    }

    @Test
    @DisplayName("单班次站点存量零变化：哨兵卡/空名卡均判为已到，仅无卡者缺（与改造前逐项一致）")
    void singleShiftLegacyUnchanged() {
        List<AttendanceDetailPolicy.Member> single = List.of(
                new AttendanceDetailPolicy.Member(1L, "早班", "08:00"),
                new AttendanceDetailPolicy.Member(2L, "早班", "08:00"),
                new AttendanceDetailPolicy.Member(3L, "早班", "08:00"));
        List<AttendanceSummaryPolicy.RecordSlot> records = List.of(
                // 存量哨兵名（覆盖全部班次）
                new AttendanceSummaryPolicy.RecordSlot(1L, 0, SENTINEL,
                        AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                // 存量空名（单班次下归属唯一班次）
                new AttendanceSummaryPolicy.RecordSlot(2L, 0, null,
                        AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));

        List<AttendanceDetailPolicy.Member> absent = AttendanceDetailPolicy.absentMembers(
                WORK_DATE, single, records, MIDDAY_BOUNDARY_MINUTE, SENTINEL);
        assertEquals(1, absent.size());
        assertEquals(3L, absent.get(0).employeeId(), "仅无卡员工 3 缺卡，与改造前按人去重口径一致");
    }

    @Test
    @DisplayName("排班行缺班次开始时间（ordinal<0）不计缺，与概况口径一致")
    void missingShiftStartTimeNotAbsent() {
        List<AttendanceDetailPolicy.Member> rows = List.of(
                new AttendanceDetailPolicy.Member(1L, "早班", null));
        List<AttendanceDetailPolicy.Member> absent = AttendanceDetailPolicy.absentMembers(
                WORK_DATE, rows, List.of(), MIDDAY_BOUNDARY_MINUTE, SENTINEL);
        assertEquals(0, absent.size(), "缺班次时间 → 该行不进 R、不计缺（与概况口径一致）");
    }

    private List<AttendanceSummaryPolicy.RecordSlot> onRecords() {
        return List.of(
                new AttendanceSummaryPolicy.RecordSlot(1L, 0, SENTINEL,
                        AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE),
                new AttendanceSummaryPolicy.RecordSlot(1L, 0, SENTINEL,
                        AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL),
                new AttendanceSummaryPolicy.RecordSlot(3L, 0, SENTINEL,
                        AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL));
    }

    @Test
    @DisplayName("ACTUAL/NORMAL/LATE/EARLY_LEAVE 名单按维度过滤并去重")
    void otherDimensions() {
        assertEquals(2, AttendanceDetailPolicy.members("ACTUAL", shouldRows, validOn, validOff).size());
        // 员工 1 同时存在 NORMAL(08:05) 与 LATE(08:10) 两张有效上班卡（多时段）；按 Mock
        // attendanceStore.detailMembers 的「按卡状态过滤 + 去重」口径，NORMAL 命中 {1,3}，故为 2
        assertEquals(2, AttendanceDetailPolicy.members("NORMAL", shouldRows, validOn, validOff).size());
        assertEquals(1, AttendanceDetailPolicy.members("LATE", shouldRows, validOn, validOff).size());
        assertEquals(1, AttendanceDetailPolicy.members("EARLY_LEAVE", shouldRows, validOn, validOff).size());
    }

    @Test
    @DisplayName("未知维度 → 空名单（服务端已白名单拦截，纯函数此处防御）")
    void unknownDimension() {
        assertEquals(0, AttendanceDetailPolicy.members("UNKNOWN", shouldRows, validOn, validOff).size());
    }

    @Test
    @DisplayName("到达态优先级：无上班卡=缺卡 > 迟到 > 早退 > 正常")
    void dayStatePriority() {
        assertEquals("MISS", AttendanceDetailPolicy.dayState(null, null));
        assertEquals(AttendanceConstants.STATUS_LATE, AttendanceDetailPolicy.dayState(
                card(1L, "ON", AttendanceConstants.STATUS_LATE, 1),
                card(1L, "OFF", AttendanceConstants.STATUS_EARLY_LEAVE, 2)));
        assertEquals(AttendanceConstants.STATUS_EARLY_LEAVE, AttendanceDetailPolicy.dayState(
                card(1L, "ON", AttendanceConstants.STATUS_NORMAL, 1),
                card(1L, "OFF", AttendanceConstants.STATUS_EARLY_LEAVE, 2)));
        assertEquals(AttendanceConstants.STATUS_NORMAL, AttendanceDetailPolicy.dayState(
                card(1L, "ON", AttendanceConstants.STATUS_NORMAL, 1),
                card(1L, "OFF", AttendanceConstants.STATUS_NORMAL, 2)));
    }

    @Test
    @DisplayName("代表卡取最早一张（多时段时列表只展示一组时间）")
    void earliestCard() {
        AttendanceCard earliest = AttendanceDetailPolicy.earliest(validOn, 1L);
        assertEquals(5, earliest.checkTime().getMinute(), "应取 08:05 而非 08:10");
        assertNull(AttendanceDetailPolicy.earliest(validOn, 99L));
    }

    @Test
    @DisplayName("SHOULD 风险优先级：缺卡 0 < 迟到 1 < 其余 2")
    void riskRank() {
        assertEquals(0, AttendanceDetailPolicy.riskRank("MISS"));
        assertEquals(1, AttendanceDetailPolicy.riskRank(AttendanceConstants.STATUS_LATE));
        assertEquals(2, AttendanceDetailPolicy.riskRank(AttendanceConstants.STATUS_NORMAL));
    }

    private AttendanceCard card(Long employeeId, String checkType, String status, int minute) {
        return new AttendanceCard(employeeId, checkType, status,
                LocalDateTime.of(2026, 9, 24, 8, minute), "全天班", null);
    }
}
