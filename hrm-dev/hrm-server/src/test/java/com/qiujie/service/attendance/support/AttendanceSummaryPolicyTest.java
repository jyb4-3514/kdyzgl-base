package com.qiujie.service.attendance.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 出勤口径聚合单测（边界：ABNORMAL 不计入实到/正常/迟到/早退；缺卡 = 应到−实到，不为负）。
 * <p>注：本机无 JDK/Maven，本测试<b>未执行</b>，收敛到服务器阶段运行。</p>
 */
class AttendanceSummaryPolicyTest {

    @Test
    @DisplayName("口径一致：应到=排班数，实到=有效上班卡去重人数，缺卡不为负，ABNORMAL 全不计")
    void summarize() {
        List<AttendanceCard> onCards = List.of(
                card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 0),
                card(2L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE, 1),
                card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_LATE, 2),
                // 异常卡：不计入实到，也不计入正常/迟到
                card(4L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_ABNORMAL, 3));
        List<AttendanceCard> offCards = List.of(
                card(1L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_EARLY_LEAVE, 4),
                card(2L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_NORMAL, 5),
                card(5L, AttendanceConstants.CHECK_TYPE_OFF, AttendanceConstants.STATUS_ABNORMAL, 6));

        AttendanceSummaryPolicy.Summary summary = AttendanceSummaryPolicy.summarize(5, onCards, offCards);

        assertEquals(5, summary.shouldCount());
        assertEquals(2, summary.actualCount(), "实到只认员工 1、2（去重，且排除异常卡员工 4）");
        assertEquals(1, summary.normalCount());
        assertEquals(2, summary.lateCount());
        assertEquals(1, summary.earlyLeaveCount());
        assertEquals(3, summary.absentCount());
    }

    @Test
    @DisplayName("实到多于应到（排班缺失）时缺卡为 0，不出现负数")
    void absentNeverNegative() {
        List<AttendanceCard> onCards = List.of(
                card(1L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 0),
                card(2L, AttendanceConstants.CHECK_TYPE_ON, AttendanceConstants.STATUS_NORMAL, 1));
        AttendanceSummaryPolicy.Summary summary =
                AttendanceSummaryPolicy.summarize(1, onCards, List.of());
        assertEquals(0, summary.absentCount());
    }

    private AttendanceCard card(Long employeeId, String checkType, String status, int minute) {
        return new AttendanceCard(employeeId, checkType, status,
                LocalDateTime.of(2026, 9, 24, 8, minute), "全天班", null);
    }
}
