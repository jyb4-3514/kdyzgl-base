package com.qiujie.service.attendance.support;

import com.qiujie.service.finance.support.ShiftPayrollPolicy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出勤口径聚合（纯逻辑）：概况与明细共用同一套口径，杜绝「明细人数与概况对不上」。
 * <p>
 * <b>默认口径（B7b，用户已裁定：应到/缺卡按班次统计）</b>：
 * <ul>
 *   <li>应到 = 该范围内<b>排班班次数</b>（一天两班 = 2，不再是按人去重后的天数）；</li>
 *   <li>实到 = 有效上班卡映射到班次后与应到集合取交（{@code |A ∩ R|}，只减不增、多打卡不超额）；</li>
 *   <li>缺卡 = {@code |R 去掉 A|}（某班次无匹配有效卡即缺；考勤概况不承载请假抵扣）；</li>
 *   <li>正常/迟到 = 有效上班卡<b>卡条数</b>（逐班次各自判定，不按天合并）；早退 = 有效下班卡卡条数；</li>
 *   <li>ABNORMAL 卡不计入实到，也不计入正常/迟到/早退。</li>
 * </ul>
 * 班次单元（{@code epochDay×2 + ordinal}）与「记录 → 班次」的三态映射<b>复用计薪唯一真源</b>
 * {@link ShiftPayrollPolicy}（算法 §1.3.1 / §1.5 的唯一定义处），避免考勤与计薪出现双口径漂移。
 * <p>
 * <b>回落口径</b>：{@link #summarize(int, List, List)} 为旧「按人/天去重」口径，
 * 仅当 {@code hrm.algo.attendance.absentGranularity=PER_DAY} 时启用（兼容 / 回退）。
 */
public final class AttendanceSummaryPolicy {

    private AttendanceSummaryPolicy() {
    }

    /** 概况聚合结果（无日期/驿站字段，展示层补齐） */
    public record Summary(int shouldCount, int actualCount, int normalCount, int lateCount,
                          int earlyLeaveCount, int absentCount) {
    }

    /** 排班班次槽（班次粒度「应到」的构造输入）：{@code startTime} 取 {@code attendance_shift.start_time} */
    public record ScheduleSlot(long employeeId, String startTime) {
    }

    /** 打卡记录槽（班次粒度「实到」的构造输入）：含时段序号与快照名，供「记录 → 班次」三态映射 */
    public record RecordSlot(long employeeId, Integer periodIndex, String periodName,
                             String checkType, String status) {
    }

    /**
     * 班次粒度聚合（B7b 默认口径，唯一实现）。
     * <p>
     * 按员工分组后复用 {@link ShiftPayrollPolicy#compute}：应到 = 排班班次数；实到 = {@code |A ∩ R|}；
     * 缺卡 = {@code |R 去掉 A|}（不传请假单元，L 为空集）；正常/迟到/早退按卡条数统计。
     *
     * @param workDate             该批次的工作日（概况为单日）
     * @param schedules            当日全部排班班次（同一员工可多条）
     * @param records              当日全部打卡记录
     * @param middayBoundaryMinute 早/晚序号界值（{@code hrm.algo.payroll.middayBoundaryMinute}）
     * @param legacyPeriodSentinel 单班制历史哨兵（{@code hrm.algo.payroll.legacyPeriodSentinel}）
     * @param lateGranularity      迟到粒度（{@code hrm.algo.payroll.lateGranularity}；本方法迟到按卡条数）
     */
    public static Summary summarizeByShift(LocalDate workDate,
                                           List<ScheduleSlot> schedules,
                                           List<RecordSlot> records,
                                           int middayBoundaryMinute,
                                           String legacyPeriodSentinel,
                                           String lateGranularity) {
        // 按员工分组：班次单元不带员工维度，跨员工聚合会互相「顶缺」，故必须分组后逐人算
        Map<Long, List<ShiftPayrollPolicy.ScheduleRow>> scheduleByEmployee = new HashMap<>();
        if (schedules != null) {
            for (ScheduleSlot slot : schedules) {
                if (slot == null) {
                    continue;
                }
                scheduleByEmployee.computeIfAbsent(slot.employeeId(), k -> new ArrayList<>())
                        .add(new ShiftPayrollPolicy.ScheduleRow(workDate, slot.startTime()));
            }
        }
        Map<Long, List<ShiftPayrollPolicy.RecordRow>> recordByEmployee = new HashMap<>();
        Set<Long> employeeIds = new HashSet<>(scheduleByEmployee.keySet());
        if (records != null) {
            for (RecordSlot slot : records) {
                if (slot == null) {
                    continue;
                }
                recordByEmployee.computeIfAbsent(slot.employeeId(), k -> new ArrayList<>())
                        .add(new ShiftPayrollPolicy.RecordRow(workDate, slot.periodIndex(),
                                slot.periodName(), slot.checkType(), slot.status()));
                employeeIds.add(slot.employeeId());
            }
        }

        int shouldCount = 0;
        int actualCount = 0;
        int absentCount = 0;
        for (Long employeeId : employeeIds) {
            ShiftPayrollPolicy.Result result = ShiftPayrollPolicy.compute(
                    scheduleByEmployee.getOrDefault(employeeId, List.of()),
                    recordByEmployee.getOrDefault(employeeId, List.of()),
                    List.of(), // 考勤概况不计请假抵扣：缺卡 = |R 去掉 A|
                    middayBoundaryMinute, legacyPeriodSentinel, lateGranularity);
            shouldCount += result.requiredShifts();
            actualCount += result.attendedShifts();
            absentCount += result.absentShifts();
        }

        // 正常/迟到/早退：按有效卡条数（迟到按次，用户已裁定），与班次粒度天然一致
        int normalCount = 0;
        int lateCount = 0;
        int earlyLeaveCount = 0;
        if (records != null) {
            for (RecordSlot slot : records) {
                if (slot == null || !AttendanceConstants.isValidCard(slot.status())) {
                    continue;
                }
                if (AttendanceConstants.CHECK_TYPE_ON.equals(slot.checkType())) {
                    if (AttendanceConstants.STATUS_NORMAL.equals(slot.status())) {
                        normalCount++;
                    } else if (AttendanceConstants.STATUS_LATE.equals(slot.status())) {
                        lateCount++;
                    }
                } else if (AttendanceConstants.CHECK_TYPE_OFF.equals(slot.checkType())
                        && AttendanceConstants.STATUS_EARLY_LEAVE.equals(slot.status())) {
                    earlyLeaveCount++;
                }
            }
        }
        return new Summary(shouldCount, actualCount, normalCount, lateCount, earlyLeaveCount, absentCount);
    }

    /**
     * 旧口径（{@code absentGranularity=PER_DAY} 回落）：应到 = 有排班人数；实到 = 有效上班卡员工去重；
     * 缺卡 = {@code max(0, 应到 − 实到)}。
     *
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
}
