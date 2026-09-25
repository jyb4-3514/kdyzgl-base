package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.HrSalary;
import com.qiujie.entity.KpiScore;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.KpiScoreMapper;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.finance.port.ApprovedLeaveDaysPort;
import com.qiujie.service.finance.support.AttendanceStat;
import com.qiujie.service.finance.support.PayrollCalcContext;
import com.qiujie.service.finance.support.ShiftPayrollPolicy;
import com.qiujie.service.hr.impl.HrSalaryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 算薪上下文取数（财务域对上游域的<b>只读</b>依赖，架构 §2.3「服务层直调（同步只读）」）。
 * <p>
 * 依赖方向恒为 {@code finance → hr / attendance / kpi}（下游读上游），不产生环：
 * <ul>
 *   <li>定薪：经 P5 服务层 {@link HrSalaryWriter#selectByEmployeeId} 读，不复制其口径；</li>
 *   <li>考勤：读 P3 的 {@code attendance_record} / {@code attendance_schedule} / {@code attendance_shift}，统计口径对齐
 *       {@link ShiftPayrollPolicy}（有效卡判定复用 {@link AttendanceConstants#isValidCard}）；</li>
 *   <li>KPI：读 P4 的 {@code kpi_score.total_score}。</li>
 * </ul>
 * 三处取数各只调用一次（非逐项查询），复杂度不随规则项数恶化（算法 S2 §4.2）。
 * <p>
 * <b>S2b 班次制（方案 §6/§8）双保险</b>：
 * <ol>
 *   <li><b>账期开关（主保险）</b>：{@code month >= hrm.algo.payroll.shiftModelFromMonth} 走班次路径；
 *       更早账期走旧「按天」路径，行为零突变（旧账期 {@code requiredShifts = attendedShifts = 应到天数} ⇒
 *       折算比例恒为 1，{@code 全天班} 哨兵在记录级再兜一层）；</li>
 *   <li><b>记录级三态优先级（次保险）</b>：由 {@link ShiftPayrollPolicy#attendedShiftSet} 唯一实现
 *       （哨兵 {@code 全天班} → 覆盖当日全部班次；{@code period_name} 空且当日多班次 → 全班次计出勤 + 告警，<b>不克扣</b>；
 *       其余按 {@code period_index}）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollContextProvider {

    private final HrSalaryWriter hrSalaryWriter;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final AttendanceShiftMapper attendanceShiftMapper;
    private final KpiScoreMapper kpiScoreMapper;
    private final ApprovedLeaveDaysPort approvedLeaveDaysPort;
    private final AlgoProperties algoProperties;

    /** 账期上下文：定薪 / 考勤 / KPI 三处取数各一次 */
    public PayrollCalcContext contextOf(Long employeeId, String month) {
        HrSalary salary = hrSalaryWriter.selectByEmployeeId(employeeId);
        AttendanceStat attendance = attendanceStat(employeeId, month);
        BigDecimal kpiScore = kpiScore(employeeId, month);
        return new PayrollCalcContext(salary, attendance, kpiScore);
    }

    /**
     * 当月考勤统计：按账期开关分流——班次路径（S2b）或旧按天路径（历史兼容）。
     * <p>
     * 迟到 = 有效上班卡且状态 LATE（粒度由 {@code lateGranularity} 决定，默认按次）；
     * 早退 = 有效下班卡且状态 EARLY_LEAVE；异常卡 = 所有 ABNORMAL。
     */
    private AttendanceStat attendanceStat(Long employeeId, String month) {
        LocalDate start = monthStart(month);
        if (start == null) {
            // month 已过格式正则，但仍可能非真实月份；无法定位区间时按「无考勤数据」返回 0，避免整批算薪失败
            log.warn("考勤取数：月份无法解析，按无数据返回：employeeId={}, month={}", employeeId, month);
            return AttendanceStat.empty();
        }
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<AttendanceRecord> records = attendanceRecordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .select(AttendanceRecord::getWorkDate, AttendanceRecord::getPeriodIndex, AttendanceRecord::getPeriodName,
                        AttendanceRecord::getCheckType, AttendanceRecord::getStatus)
                .eq(AttendanceRecord::getEmployeeId, employeeId)
                .between(AttendanceRecord::getWorkDate, start, end));

        List<AttendanceSchedule> schedules = attendanceScheduleMapper.selectList(
                new LambdaQueryWrapper<AttendanceSchedule>()
                        .select(AttendanceSchedule::getWorkDate, AttendanceSchedule::getShiftId)
                        .eq(AttendanceSchedule::getEmployeeId, employeeId)
                        .between(AttendanceSchedule::getWorkDate, start, end));

        if (isShiftModel(month)) {
            return shiftStat(employeeId, month, start, end, records, schedules);
        }
        return legacyDayStat(employeeId, start, end, records, schedules);
    }

    // ==================== 班次路径（S2b，month >= shiftModelFromMonth） ====================

    private AttendanceStat shiftStat(Long employeeId, String month, LocalDate start, LocalDate end,
                                     List<AttendanceRecord> records, List<AttendanceSchedule> schedules) {
        Map<Long, String> startTimes = shiftStartTimes(schedules);
        List<ShiftPayrollPolicy.ScheduleRow> scheduleRows = new ArrayList<>(schedules.size());
        for (AttendanceSchedule schedule : schedules) {
            String startTime = schedule.getShiftId() == null ? null : startTimes.get(schedule.getShiftId());
            scheduleRows.add(new ShiftPayrollPolicy.ScheduleRow(schedule.getWorkDate(), startTime));
        }
        List<ShiftPayrollPolicy.RecordRow> recordRows = new ArrayList<>(records.size());
        for (AttendanceRecord record : records) {
            recordRows.add(new ShiftPayrollPolicy.RecordRow(record.getWorkDate(), record.getPeriodIndex(),
                    record.getPeriodName(), record.getCheckType(), record.getStatus()));
        }
        // 已批请假班次单元（与应出集合取交在纯逻辑内完成；请假域缺席时端口降级返回空集）
        Set<Long> leaveUnits = approvedLeaveDaysPort.approvedLeaveShiftUnits(employeeId, start, end);

        ShiftPayrollPolicy.Result result = ShiftPayrollPolicy.compute(scheduleRows, recordRows, leaveUnits,
                algoProperties.getPayroll().getMiddayBoundaryMinute(),
                algoProperties.getPayroll().getLegacyPeriodSentinel(),
                algoProperties.getPayroll().getLateGranularity());

        for (ShiftPayrollPolicy.Warning warning : result.warnings()) {
            // TODO(扩展): PERIOD_NAME_MISSING 等告警的落库/展示载体（方案 §8 T8，交后端后续实现），当前仅日志留痕
            log.warn("班次制考勤告警：employeeId={}, month={}, workDate={}, reason={}, shifts={}",
                    employeeId, month, warning.workDate(), warning.reason(), warning.shifts());
        }

        // 组合指标 absentOrLeaveCount = 旷工 + 请假（T1 全勤奖口径），见 AttendanceStat.ofShift
        return AttendanceStat.ofShift(result);
    }

    /** 排班引用到的班次开始时间（{@code attendance_shift.start_time}），供班次序号判定 */
    private Map<Long, String> shiftStartTimes(List<AttendanceSchedule> schedules) {
        Set<Long> shiftIds = new HashSet<>();
        for (AttendanceSchedule schedule : schedules) {
            if (schedule.getShiftId() != null) {
                shiftIds.add(schedule.getShiftId());
            }
        }
        if (shiftIds.isEmpty()) {
            return Map.of();
        }
        List<AttendanceShift> shifts = attendanceShiftMapper.selectList(new LambdaQueryWrapper<AttendanceShift>()
                .select(AttendanceShift::getId, AttendanceShift::getStartTime)
                .in(AttendanceShift::getId, shiftIds));
        Map<Long, String> startTimes = new HashMap<>();
        for (AttendanceShift shift : shifts) {
            startTimes.put(shift.getId(), shift.getStartTime());
        }
        return startTimes;
    }

    // ==================== 旧按天路径（month < shiftModelFromMonth，行为零突变） ====================

    /**
     * 旧口径复刻（S2b 前）：应到 = 排班日期去重数；实到 = 有效上班卡日期去重数；
     * 缺勤 = max(0, 应到 − 实到 − excludeLeaveDays)，{@code excludeLeaveDays = leaveDeductEnabled ? 0 : leaveDays}。
     * <p>
     * 班次维度字段按「等效比例 1」填充（{@code requiredShifts = attendedShifts = 应到天数}），
     * 使新 {@code PRORATED} 解析器在旧账期折算比例恒为 1（basic 不突变）；{@code absentOrLeaveCount = absentCount}
     * 使全勤奖沿用旧「仅旷工」口径。
     */
    private AttendanceStat legacyDayStat(Long employeeId, LocalDate start, LocalDate end,
                                         List<AttendanceRecord> records, List<AttendanceSchedule> schedules) {
        int lateCount = 0;
        int earlyLeaveCount = 0;
        int abnormalCount = 0;
        Set<LocalDate> attendedDates = new HashSet<>();
        for (AttendanceRecord record : records) {
            String status = record.getStatus();
            if (AttendanceConstants.STATUS_ABNORMAL.equals(status)) {
                abnormalCount++;
                continue;
            }
            if (!AttendanceConstants.isValidCard(status)) {
                continue;
            }
            if (AttendanceConstants.CHECK_TYPE_ON.equals(record.getCheckType())) {
                attendedDates.add(record.getWorkDate());
                if (AttendanceConstants.STATUS_LATE.equals(status)) {
                    lateCount++;
                }
            } else if (AttendanceConstants.CHECK_TYPE_OFF.equals(record.getCheckType())
                    && AttendanceConstants.STATUS_EARLY_LEAVE.equals(status)) {
                earlyLeaveCount++;
            }
        }

        Set<LocalDate> scheduledDates = new HashSet<>();
        for (AttendanceSchedule schedule : schedules) {
            scheduledDates.add(schedule.getWorkDate());
        }

        // 旧路径继续沿用 leave_deduct_enabled 语义（新路径为 no-op，方案 §7.2）
        BigDecimal leaveDays = approvedLeaveDaysPort.approvedLeaveDays(employeeId, start, end);
        BigDecimal excludeLeaveDays = approvedLeaveDaysPort.leaveDeductEnabled() ? BigDecimal.ZERO : leaveDays;
        BigDecimal absentCount = BigDecimal.valueOf(scheduledDates.size() - attendedDates.size())
                .subtract(excludeLeaveDays);
        if (absentCount.signum() < 0) {
            absentCount = BigDecimal.ZERO;
        }
        return new AttendanceStat(lateCount, earlyLeaveCount, absentCount, abnormalCount, leaveDays,
                scheduledDates.size(), scheduledDates.size(), 0, 0, absentCount);
    }

    // ==================== 分流判定 / KPI / 工具 ====================

    /** 是否走班次制路径：{@code month >= shiftModelFromMonth}；开关为空视为全部启用（方案 §7） */
    private boolean isShiftModel(String month) {
        String from = algoProperties.getPayroll().getShiftModelFromMonth();
        if (from == null || from.isBlank() || month == null) {
            return true;
        }
        // yyyy-MM 定长字典序比较等价于时间先后
        return month.compareTo(from) >= 0;
    }

    /** 该员工该月 KPI 总分；无评分记录 → null（解析器按 0 计并写文案） */
    private BigDecimal kpiScore(Long employeeId, String month) {
        List<KpiScore> rows = kpiScoreMapper.selectList(new LambdaQueryWrapper<KpiScore>()
                .select(KpiScore::getTotalScore)
                .eq(KpiScore::getEmployeeId, employeeId)
                .eq(KpiScore::getMonth, month));
        return rows.isEmpty() ? null : rows.get(0).getTotalScore();
    }

    /** 账期 → 当月 1 日；非法账期 → null */
    private LocalDate monthStart(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(month + "-01");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
