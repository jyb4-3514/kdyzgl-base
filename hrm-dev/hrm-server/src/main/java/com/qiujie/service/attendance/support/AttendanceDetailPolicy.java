package com.qiujie.service.attendance.support;

import com.qiujie.service.finance.support.ShiftPayrollPolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考勤明细策略（纯逻辑）：按维度给出「人 + 当天在该维度的事实」的名单来源，以及到达态判定。
 * <p>
 * 与 Mock {@code attendanceStore.attendanceDetail} 逐条对齐：
 * <ul>
 *   <li>明细六个维度与概况六个计数字段一一对应（SHOULD/ACTUAL/NORMAL/LATE/EARLY_LEAVE/ABSENT）；</li>
 *   <li>缺卡（ABSENT）按<b>班次粒度</b>：与概况 {@code absentShifts} 同一真源
 *       （{@link ShiftPayrollPolicy#absentShiftSet}），某员工某班次无匹配有效上班卡即缺；</li>
 *   <li>到达态优先级：无有效上班卡=缺卡 &gt; 迟到 &gt; 早退 &gt; 正常。</li>
 * </ul>
 * {@link #members} 只承载「非缺卡」五个维度；缺卡名单由 {@link #absentMembers} 单独给出（班次粒度）。
 * 入参的上班卡/下班卡须为「已剔除 ABNORMAL」的有效卡（与调用方共用 {@link AttendanceSummaryPolicy#validCards}）。
 */
public final class AttendanceDetailPolicy {

    private AttendanceDetailPolicy() {
    }

    /**
     * 明细名单成员（{@code shiftName} 仅 SHOULD/ABSENT 维度有值）。
     * <p>
     * {@code shiftStartTime} 仅缺卡（ABSENT）判定需要：缺卡按班次单元
     * （{@code epochDay×2 + shiftOrdinal(startTime)}）与有效上班卡取差，故应到行须带班次开始时间；
     * 其余维度（按卡状态过滤）不依赖它，可留 {@code null}。
     */
    public record Member(Long employeeId, String shiftName, String shiftStartTime) {
        /** 兼容构造：非缺卡维度不需要班次开始时间 */
        public Member(Long employeeId, String shiftName) {
            this(employeeId, shiftName, null);
        }
    }

    /**
     * 名单来源（只决定「谁在名单里」，行内容由调用方补齐）。<b>不含 ABSENT</b>：缺卡见 {@link #absentMembers}。
     *
     * @param dim       `SHOULD` / `ACTUAL` / `NORMAL` / `LATE` / `EARLY_LEAVE`（ABSENT 由 {@link #absentMembers} 处理）
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
                // 含 ABSENT：缺卡改班次粒度，由 absentMembers 单独给出，此处返回空名单（调用方分流）
                return List.of();
            }
        }
    }

    /**
     * 缺卡名单（班次粒度，与概况 {@code absentShifts} 同源）。返回应到行中「其班次单元缺勤」的行，
     * 顺序与 {@code shouldRows} 一致。
     * <p>
     * 与概况一致地按<b>员工</b>分组：班次单元不带员工维度，跨员工聚合会互相顶缺，故必须分组后逐人求
     * {@code R \ A}（{@link ShiftPayrollPolicy#absentShiftSet}）。排班行缺班次开始时间（{@code ordinal<0}）
     * 时该行不进 R、不计缺（与概况口径一致）。
     *
     * @param workDate             明细统计日（单日）
     * @param shouldRows           应到行（须带 {@code shiftStartTime}）
     * @param validOnRecords       当天有效上班卡（{@code check_type=ON} 且非 ABNORMAL；含 periodIndex/periodName 供三态映射）
     * @param middayBoundaryMinute 早/晚序号界值（{@code hrm.algo.payroll.middayBoundaryMinute}）
     * @param legacyPeriodSentinel 单班制历史哨兵（{@code hrm.algo.payroll.legacyPeriodSentinel}）
     */
    public static List<Member> absentMembers(LocalDate workDate, List<Member> shouldRows,
                                             List<AttendanceSummaryPolicy.RecordSlot> validOnRecords,
                                             int middayBoundaryMinute, String legacyPeriodSentinel) {
        if (shouldRows == null || shouldRows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ShiftPayrollPolicy.ScheduleRow>> scheduleByEmployee = new HashMap<>();
        for (Member row : shouldRows) {
            if (row == null) {
                continue;
            }
            scheduleByEmployee.computeIfAbsent(row.employeeId(), k -> new ArrayList<>())
                    .add(new ShiftPayrollPolicy.ScheduleRow(workDate, row.shiftStartTime()));
        }
        Map<Long, List<AttendanceSummaryPolicy.RecordSlot>> recordByEmployee = new HashMap<>();
        if (validOnRecords != null) {
            for (AttendanceSummaryPolicy.RecordSlot slot : validOnRecords) {
                if (slot == null) {
                    continue;
                }
                recordByEmployee.computeIfAbsent(slot.employeeId(), k -> new ArrayList<>()).add(slot);
            }
        }

        Map<Long, Set<Long>> absentByEmployee = new HashMap<>();
        for (Map.Entry<Long, List<ShiftPayrollPolicy.ScheduleRow>> entry : scheduleByEmployee.entrySet()) {
            List<AttendanceSummaryPolicy.RecordSlot> slots = recordByEmployee.getOrDefault(entry.getKey(), List.of());
            List<ShiftPayrollPolicy.RecordRow> recordRows = new ArrayList<>(slots.size());
            for (AttendanceSummaryPolicy.RecordSlot slot : slots) {
                recordRows.add(new ShiftPayrollPolicy.RecordRow(workDate, slot.periodIndex(),
                        slot.periodName(), slot.checkType(), slot.status()));
            }
            absentByEmployee.put(entry.getKey(), ShiftPayrollPolicy.absentShiftSet(
                    entry.getValue(), recordRows, List.of(), middayBoundaryMinute, legacyPeriodSentinel));
        }

        List<Member> absent = new ArrayList<>();
        for (Member row : shouldRows) {
            if (row == null) {
                continue;
            }
            int ordinal = ShiftPayrollPolicy.shiftOrdinal(row.shiftStartTime(), middayBoundaryMinute);
            if (ordinal < 0) {
                continue;
            }
            Set<Long> units = absentByEmployee.get(row.employeeId());
            if (units != null && units.contains(ShiftPayrollPolicy.shiftUnit(workDate, ordinal))) {
                absent.add(row);
            }
        }
        return absent;
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
