package com.qiujie.service.attendance.support;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 出勤口径聚合（纯逻辑）：概况与明细共用同一套口径，杜绝「明细人数与概况对不上」。
 * <p>
 * 口径（唯一真源，对齐 Mock {@code attendanceScope / attendanceSummary}）：
 * <ul>
 *   <li>应到 = 当天有排班人数；</li>
 *   <li>实到 = 当天有有效上班卡（非 ABNORMAL）的人数（按 employeeId 去重）；</li>
 *   <li>正常/迟到 = 有效上班卡按状态的<b>卡条数</b>；早退 = 有效下班卡按状态的卡条数；</li>
 *   <li>缺卡（MISS）= 应到 − 实到，且不为负；</li>
 *   <li>ABNORMAL 卡不计入实到，也不计入正常/迟到/早退。</li>
 * </ul>
 * 表达式只此一处；改口径必同时影响 {@code attendanceSummary} 与 {@code attendanceDetail}。
 */
public final class AttendanceSummaryPolicy {

    private AttendanceSummaryPolicy() {
    }

    /** 概况聚合结果（无日期/驿站字段，展示层补齐） */
    public record Summary(int shouldCount, int actualCount, int normalCount, int lateCount,
                          int earlyLeaveCount, int absentCount) {
    }

    /**
     * @param shouldCount 当天有排班人数
     * @param onCards     当天该范围内的上班卡（含 ABNORMAL，本方法内过滤）
     * @param offCards    当天该范围内的下班卡（含 ABNORMAL，本方法内过滤）
     */
    public static Summary summarize(int shouldCount, List<AttendanceCard> onCards, List<AttendanceCard> offCards) {
        List<AttendanceCard> validOn = validCards(onCards);
        List<AttendanceCard> validOff = validCards(offCards);

        Set<Long> actualIds = new HashSet<>();
        int normalCount = 0;
        int lateCount = 0;
        for (AttendanceCard card : validOn) {
            actualIds.add(card.employeeId());
            if (AttendanceConstants.STATUS_NORMAL.equals(card.status())) {
                normalCount++;
            } else if (AttendanceConstants.STATUS_LATE.equals(card.status())) {
                lateCount++;
            }
        }
        int earlyLeaveCount = 0;
        for (AttendanceCard card : validOff) {
            if (AttendanceConstants.STATUS_EARLY_LEAVE.equals(card.status())) {
                earlyLeaveCount++;
            }
        }
        int actualCount = actualIds.size();
        int absentCount = Math.max(0, shouldCount - actualCount);
        return new Summary(shouldCount, actualCount, normalCount, lateCount, earlyLeaveCount, absentCount);
    }

    /** 有效卡 = 非 ABNORMAL */
    public static List<AttendanceCard> validCards(List<AttendanceCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        return cards.stream().filter(c -> AttendanceConstants.isValidCard(c.status())).toList();
    }

    /** 有效上班卡去重后的员工 id 集合（实到/缺卡用） */
    public static Set<Long> actualEmployeeIds(List<AttendanceCard> onCards) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceCard card : validCards(onCards)) {
            ids.add(card.employeeId());
        }
        return ids;
    }
}
