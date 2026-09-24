package com.qiujie.service.attendance.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 考勤明细策略（纯逻辑）：按维度给出「人 + 当天在该维度的事实」的名单来源，以及到达态判定。
 * <p>
 * 与 Mock {@code attendanceStore.attendanceDetail} 逐条对齐：
 * <ul>
 *   <li>明细六个维度与概况六个计数字段一一对应（SHOULD/ACTUAL/NORMAL/LATE/EARLY_LEAVE/ABSENT）；</li>
 *   <li>缺卡（ABSENT）= 应到差集实到（<b>不是</b>异常卡）；异常卡六个维度都不承载；</li>
 *   <li>到达态优先级：无有效上班卡=缺卡 &gt; 迟到 &gt; 早退 &gt; 正常。</li>
 * </ul>
 * 入参的上班卡/下班卡须为「已剔除 ABNORMAL」的有效卡（与调用方共用 {@link AttendanceSummaryPolicy#validCards}）。
 */
public final class AttendanceDetailPolicy {

    private AttendanceDetailPolicy() {
    }

    /** 明细名单成员（shiftName 仅 SHOULD/ABSENT 维度有值） */
    public record Member(Long employeeId, String shiftName) {
    }

    /**
     * 名单来源（只决定「谁在名单里」，行内容由调用方补齐）。
     *
     * @param dim       SHOULD / ACTUAL / NORMAL / LATE / EARLY_LEAVE / ABSENT
     * @param shouldRows 应到名单（当天有排班者，带班次名）
     * @param validOn   当天有效上班卡（非 ABNORMAL）
     * @param validOff  当天有效下班卡（非 ABNORMAL）
     */
    public static List<Member> members(String dim, List<Member> shouldRows,
                                       List<AttendanceCard> validOn, List<AttendanceCard> validOff) {
        if (dim == null) {
            return List.of();
        }
        switch (dim) {
            case "SHOULD" -> {
                return new ArrayList<>(shouldRows);
            }
            case "ABSENT" -> {
                Set<Long> actualIds = AttendanceSummaryPolicy.actualEmployeeIds(validOn);
                List<Member> absent = new ArrayList<>();
                for (Member row : shouldRows) {
                    if (!actualIds.contains(row.employeeId())) {
                        absent.add(row);
                    }
                }
                return absent;
            }
            case "ACTUAL" -> {
                return distinctIds(validOn, null);
            }
            case "NORMAL", "LATE" -> {
                return distinctIds(validOn, dim);
            }
            case "EARLY_LEAVE" -> {
                return distinctIds(validOff, AttendanceConstants.STATUS_EARLY_LEAVE);
            }
            default -> {
                return List.of();
            }
        }
    }

    /**
     * 到达态：无有效上班卡=缺卡；有卡且迟到=迟到；到达后签退=早退；否则正常。
     * 与员工端 {@code dayStatusOf} 同序（除异常）。
     */
    public static String dayState(AttendanceCard on, AttendanceCard off) {
        if (on == null) {
            return "MISS";
        }
        if (AttendanceConstants.STATUS_LATE.equals(on.status())) {
            return AttendanceConstants.STATUS_LATE;
        }
        if (off != null && AttendanceConstants.STATUS_EARLY_LEAVE.equals(off.status())) {
            return AttendanceConstants.STATUS_EARLY_LEAVE;
        }
        return AttendanceConstants.STATUS_NORMAL;
    }

    /** SHOULD 维度的风险优先级：缺卡 0 &lt; 迟到 1 &lt; 其余 2（列表按风险升序） */
    public static int riskRank(String dayState) {
        if ("MISS".equals(dayState)) {
            return 0;
        }
        if (AttendanceConstants.STATUS_LATE.equals(dayState)) {
            return 1;
        }
        return 2;
    }

    /** 该员工某类型有效卡中最早的一张（列表只展示一组上下班时间）；无卡返回 null */
    public static AttendanceCard earliest(List<AttendanceCard> cards, Long employeeId) {
        AttendanceCard earliest = null;
        for (AttendanceCard card : cards) {
            if (!java.util.Objects.equals(card.employeeId(), employeeId)) {
                continue;
            }
            if (earliest == null || (card.checkTime() != null && earliest.checkTime() != null
                    && card.checkTime().isBefore(earliest.checkTime()))) {
                earliest = card;
            }
        }
        return earliest;
    }

    /** 去重员工 id（保持首次出现顺序，对齐 JS Set 语义）；status 为 null 表示不限状态 */
    private static List<Member> distinctIds(List<AttendanceCard> cards, String status) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AttendanceCard card : cards) {
            if (status == null || status.equals(card.status())) {
                ids.add(card.employeeId());
            }
        }
        List<Member> members = new ArrayList<>(ids.size());
        for (Long id : ids) {
            members.add(new Member(id, null));
        }
        return members;
    }
}
