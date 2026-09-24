package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 考勤明细策略单测（边界：六维度名单来源、到达态优先级、最早卡选取、风险排序）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceDetailPolicyTest {

    private final List<AttendanceDetailPolicy.Member> shouldRows = List.of(
            new AttendanceDetailPolicy.Member(1L, "早班"),
            new AttendanceDetailPolicy.Member(2L, "晚班"));

    private final List<AttendanceCard> validOn = List.of(
            card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE, 10),
            card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 5),
            card(3L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 6));

    private final List<AttendanceCard> validOff = List.of(
            card(1L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_EARLY_LEAVE, 20));

    @Test
    @DisplayName("SHOULD = 应到全部（带班次名）；ABSENT = 应到差集实到")
    void shouldAndAbsent() {
        List<AttendanceDetailPolicy.Member> should =
                AttendanceDetailPolicy.members("SHOULD", shouldRows, validOn, validOff);
        assertEquals(2, should.size());
        assertEquals("早班", should.get(0).shiftName());

        List<AttendanceDetailPolicy.Member> absent =
                AttendanceDetailPolicy.members("ABSENT", shouldRows, validOn, validOff);
        assertEquals(1, absent.size());
        assertEquals(2L, absent.get(0).employeeId(), "员工 1、3 有有效上班卡 → 仅员工 2 缺卡");
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
