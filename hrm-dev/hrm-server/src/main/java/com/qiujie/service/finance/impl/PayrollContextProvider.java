package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.HrSalary;
import com.qiujie.entity.KpiScore;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.KpiScoreMapper;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.finance.port.ApprovedLeaveDaysPort;
import com.qiujie.service.finance.support.AttendanceStat;
import com.qiujie.service.finance.support.PayrollCalcContext;
import com.qiujie.service.hr.impl.HrSalaryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 算薪上下文取数（财务域对上游域的<b>只读</b>依赖，架构 §2.3「服务层直调（同步只读）」）。
 * <p>
 * 依赖方向恒为 {@code finance → hr / attendance / kpi}（下游读上游），不产生环：
 * <ul>
 *   <li>定薪：经 P5 服务层 {@link HrSalaryWriter#selectByEmployeeId} 读，不复制其口径；</li>
 *   <li>考勤：读 P3 的 {@code attendance_record} / {@code attendance_schedule}，统计口径对齐 Mock
 *       {@code employeeAttendanceStat}（有效卡判定复用 {@link AttendanceConstants#isValidCard}）；</li>
 *   <li>KPI：读 P4 的 {@code kpi_score.total_score}。</li>
 * </ul>
 * 三处取数各只调用一次（非逐项查询），复杂度不随规则项数恶化（算法 S2 §4.2）。
 * <p>
 * <b>请假天数（leaveCount）</b>：经只读端口 {@link ApprovedLeaveDaysPort} 取「已批请假计薪天数」（P7 落地接入）；
 * 缺勤口径 {@code absentCount = max(0, 应到 − 实到 − excludeLeaveDays)}，其中
 * {@code excludeLeaveDays = leaveDeductEnabled ? 0 : leaveDays}（对齐 Mock {@code employeeAttendanceStat}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollContextProvider {

    private final HrSalaryWriter hrSalaryWriter;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final KpiScoreMapper kpiScoreMapper;
    private final ApprovedLeaveDaysPort approvedLeaveDaysPort;

    /** 账期上下文：定薪 / 考勤 / KPI 三处取数各一次 */
    public PayrollCalcContext contextOf(Long employeeId, String month) {
        HrSalary salary = hrSalaryWriter.selectByEmployeeId(employeeId);
        AttendanceStat attendance = attendanceStat(employeeId, month);
        BigDecimal kpiScore = kpiScore(employeeId, month);
        return new PayrollCalcContext(salary, attendance, kpiScore);
    }

    /**
     * 当月考勤统计（对齐 Mock {@code employeeAttendanceStat}）。
     * <p>
     * 迟到 = 有效上班卡且状态 LATE；早退 = 有效下班卡且状态 EARLY_LEAVE；异常卡 = 所有 ABNORMAL；
     * 应到 = 排班日期去重数；实到 = 有效上班卡日期去重数；缺勤 = max(0, 应到 − 实到 − 请假天数)。
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
                .select(AttendanceRecord::getWorkDate, AttendanceRecord::getCheckType, AttendanceRecord::getStatus)
                .eq(AttendanceRecord::getEmployeeId, employeeId)
                .between(AttendanceRecord::getWorkDate, start, end));

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
        List<AttendanceSchedule> schedules = attendanceScheduleMapper.selectList(
                new LambdaQueryWrapper<AttendanceSchedule>()
                        .select(AttendanceSchedule::getWorkDate)
                        .eq(AttendanceSchedule::getEmployeeId, employeeId)
                        .between(AttendanceSchedule::getWorkDate, start, end));
        for (AttendanceSchedule schedule : schedules) {
            scheduledDates.add(schedule.getWorkDate());
        }

        // P7 已落地：接入「已批请假天数」并按 leaveDeductEnabled 修正缺勤口径（对齐 Mock excludeLeaveDays）
        BigDecimal leaveDays = approvedLeaveDaysPort.approvedLeaveDays(employeeId, start, end);
        BigDecimal excludeLeaveDays = approvedLeaveDaysPort.leaveDeductEnabled() ? BigDecimal.ZERO : leaveDays;
        BigDecimal absentCount = BigDecimal.valueOf(scheduledDates.size() - attendedDates.size())
                .subtract(excludeLeaveDays);
        if (absentCount.signum() < 0) {
            absentCount = BigDecimal.ZERO;
        }
        return new AttendanceStat(lateCount, earlyLeaveCount, absentCount, abnormalCount, leaveDays);
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
