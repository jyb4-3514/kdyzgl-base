package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.Employee;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.service.attendance.AttendanceAnomalyService;
import com.qiujie.service.attendance.support.AttendanceAnomalyDetector;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.attendance.support.AttendanceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考勤异常检测服务实现（S4 落地）。
 * <p>
 * 特征来源与出勤口径<b>同源</b>：迟到频次只数「有效上班卡（非 ABNORMAL）且状态 LATE」；
 * 连续缺卡按「有排班但无有效上班卡」的连续天数游程（异常卡不计入实到，与 {@code attendanceSummary} 一致）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceAnomalyServiceImpl implements AttendanceAnomalyService {

    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final EmployeeMapper employeeMapper;
    private final AlgoProperties algoProperties;

    @Override
    @Transactional(readOnly = true)
    public AttendanceAnomalyDetector.Report detect(Long stationId, Integer windowDays) {
        AlgoProperties.Anomaly cfg = algoProperties.getAttendance().getAnomaly();
        int days = (windowDays == null || windowDays <= 0) ? cfg.getWindowDays() : windowDays;
        LocalDate end = AttendanceSupport.today();
        LocalDate start = end.minusDays(days - 1L);

        List<Long> employeeIds = stationEmployeeIds(stationId);
        if (employeeIds.isEmpty()) {
            return AttendanceAnomalyDetector.detect(List.of(), toDetectorConfig(cfg));
        }

        Map<Long, Set<LocalDate>> scheduled = scheduledDatesByEmployee(stationId, start, end);
        Map<Long, Set<LocalDate>> attended = attendedDatesByEmployee(stationId, start, end);
        Map<Long, Integer> lateCounts = lateCountsByEmployee(stationId, start, end);

        List<AttendanceAnomalyDetector.EmployeeData> data = new ArrayList<>(employeeIds.size());
        for (Long employeeId : employeeIds) {
            data.add(new AttendanceAnomalyDetector.EmployeeData(
                    employeeId,
                    lateCounts.getOrDefault(employeeId, 0),
                    maxAbsentRun(scheduled.getOrDefault(employeeId, Set.of()),
                            attended.getOrDefault(employeeId, Set.of()))));
        }

        AttendanceAnomalyDetector.Report report = AttendanceAnomalyDetector.detect(data, toDetectorConfig(cfg));
        if (!report.anomalies().isEmpty()) {
            log.info("考勤异常检测命中：stationId={}, 窗口 {}~{}天, 样本 {}, 异常 {} 条",
                    stationId, start, end, report.sampleSize(), report.anomalies().size());
        }
        return report;
    }

    // ==================== 取数 ====================

    private List<Long> stationEmployeeIds(Long stationId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId).eq(Employee::getStatus, 1);
        if (stationId != null) {
            wrapper.eq(Employee::getStationId, stationId);
        }
        List<Long> ids = new ArrayList<>();
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            ids.add(employee.getId());
        }
        return ids;
    }

    /** 员工 → 窗口内有排班的日期集合 */
    private Map<Long, Set<LocalDate>> scheduledDatesByEmployee(Long stationId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getEmployeeId, AttendanceSchedule::getWorkDate)
                .between(AttendanceSchedule::getWorkDate, start, end);
        if (stationId != null) {
            wrapper.eq(AttendanceSchedule::getStationId, stationId);
        }
        Map<Long, Set<LocalDate>> map = new HashMap<>();
        for (AttendanceSchedule schedule : attendanceScheduleMapper.selectList(wrapper)) {
            map.computeIfAbsent(schedule.getEmployeeId(), k -> new HashSet<>()).add(schedule.getWorkDate());
        }
        return map;
    }

    /** 员工 → 窗口内有有效上班卡（非 ABNORMAL）的日期集合 */
    private Map<Long, Set<LocalDate>> attendedDatesByEmployee(Long stationId, LocalDate start, LocalDate end) {
        Map<Long, Set<LocalDate>> map = new HashMap<>();
        for (AttendanceRecord record : validOnRecords(stationId, start, end)) {
            map.computeIfAbsent(record.getEmployeeId(), k -> new HashSet<>()).add(record.getWorkDate());
        }
        return map;
    }

    /** 员工 → 窗口内有效上班卡的迟到条数 */
    private Map<Long, Integer> lateCountsByEmployee(Long stationId, LocalDate start, LocalDate end) {
        Map<Long, Integer> map = new HashMap<>();
        for (AttendanceRecord record : validOnRecords(stationId, start, end)) {
            if (AttendanceConstants.STATUS_LATE.equals(record.getStatus())) {
                map.merge(record.getEmployeeId(), 1, Integer::sum);
            }
        }
        return map;
    }

    /** 窗口内有效上班卡（status != ABNORMAL 且 check_type = ON） */
    private List<AttendanceRecord> validOnRecords(Long stationId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceRecord::getEmployeeId, AttendanceRecord::getWorkDate, AttendanceRecord::getStatus)
                .eq(AttendanceRecord::getCheckType, AttendanceConstants.CHECK_TYPE_ON)
                .ne(AttendanceRecord::getStatus, AttendanceConstants.STATUS_ABNORMAL)
                .between(AttendanceRecord::getWorkDate, start, end);
        if (stationId != null) {
            wrapper.eq(AttendanceRecord::getStationId, stationId);
        }
        return attendanceRecordMapper.selectList(wrapper);
    }

    /** 连续缺卡游程：按有排班的日期升序，逐日检查是否有有效上班卡，取最长连续缺失天数 */
    private int maxAbsentRun(Set<LocalDate> scheduledDates, Set<LocalDate> attendedDates) {
        if (scheduledDates.isEmpty()) {
            return 0;
        }
        List<LocalDate> ordered = new ArrayList<>(scheduledDates);
        ordered.sort(LocalDate::compareTo);
        int run = 0;
        int max = 0;
        for (LocalDate date : ordered) {
            if (attendedDates.contains(date)) {
                run = 0;
            } else {
                run++;
                max = Math.max(max, run);
            }
        }
        return max;
    }

    private AttendanceAnomalyDetector.Config toDetectorConfig(AlgoProperties.Anomaly cfg) {
        return new AttendanceAnomalyDetector.Config(cfg.isUseRobust(), cfg.getLateWarn(), cfg.getLateCritical(),
                cfg.getConsecutiveAbsent(), cfg.getMinSamples());
    }
}
