package com.qiujie.service.kpi.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.service.attendance.support.AttendanceConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 考勤类 KPI 指标（{@code metricType=ATTENDANCE}）的实际值来源：**复用 P3 考勤能力**，不重写考勤统计。
 * <p>
 * <b>取数边界（唯一真源口径在此）</b>：
 * <ul>
 *   <li>应到天数 = 该员工当月 {@code attendance_schedule} 的排班日期数（去重）；</li>
 *   <li>实到天数 = 该员工当月有「有效上班卡」的日期数（去重）；有效卡判定复用 P3 的
 *       {@link AttendanceConstants#isValidCard(String)}（非 ABNORMAL），与出勤口径同源，避免两处各写一份判定；</li>
 *   <li>合格率 = 实到天数 / 应到天数 × 100（1 位小数）。</li>
 * </ul>
 * 无排班（应到 = 0）时无法判定基准，退化为「有卡记 100、无卡记 0」（与 S1 {@code target≤0} 退化同思路）。
 * <p>
 * <b>与 Mock 的有意差异</b>：Mock 的 ATTENDANCE 实际值走哈希模拟（同其它类型）；本实现按主智能体 P4 指令
 * 改由考勤真实明细统计，故 ATTENDANCE 指标得分与 Mock 演示值**不逐位相等**（属应用户裁定/指令的有意差异，非缺陷）。
 * <p>
 * TODO(扩展): 「合格率」是否应进一步排除迟到/早退（口径待用户裁定，见返回摘要遗留项），
 *   裁定后仅改本方法的分母/分子定义即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiAttendanceMetricSource {

    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;

    /**
     * 考勤合格率（%，1 位小数）。
     *
     * @param employeeId 员工 id
     * @param month      考核月份 yyyy-MM
     */
    public BigDecimal qualificationRate(Long employeeId, String month) {
        LocalDate start;
        try {
            start = LocalDate.parse(month + "-01");
        } catch (RuntimeException e) {
            // month 已过格式正则（yyyy-MM），但仍可能非真实月份（如 2026-13，Mock isMonth 同样放行）；
            // 无法定位区间时按「无考勤数据」返回 0，避免算分整体失败
            log.warn("考勤指标取数：月份无法解析，按无数据返回：employeeId={}, month={}", employeeId, month);
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        Set<LocalDate> shouldDays = scheduleDays(employeeId, start, end);
        Set<LocalDate> actualDays = validOnCardDays(employeeId, start, end);

        if (shouldDays.isEmpty()) {
            return (actualDays.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(actualDays.size() * 100L)
                .divide(BigDecimal.valueOf(shouldDays.size()), 1, RoundingMode.HALF_UP);
    }

    /** 当月排班日期集合（去重） */
    private Set<LocalDate> scheduleDays(Long employeeId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getWorkDate)
                .eq(AttendanceSchedule::getEmployeeId, employeeId)
                .between(AttendanceSchedule::getWorkDate, start, end);
        Set<LocalDate> days = new HashSet<>();
        for (AttendanceSchedule schedule : attendanceScheduleMapper.selectList(wrapper)) {
            days.add(schedule.getWorkDate());
        }
        return days;
    }

    /** 当月有有效上班卡（非 ABNORMAL）的日期集合（去重） */
    private Set<LocalDate> validOnCardDays(Long employeeId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceRecord::getWorkDate, AttendanceRecord::getStatus)
                .eq(AttendanceRecord::getEmployeeId, employeeId)
                .eq(AttendanceRecord::getCheckType, AttendanceConstants.CHECK_TYPE_ON)
                .between(AttendanceRecord::getWorkDate, start, end);
        List<AttendanceRecord> rows = attendanceRecordMapper.selectList(wrapper);
        Set<LocalDate> days = new HashSet<>();
        for (AttendanceRecord row : rows) {
            if (AttendanceConstants.isValidCard(row.getStatus())) {
                days.add(row.getWorkDate());
            }
        }
        return days;
    }
}
