package com.qiujie.service.leave.port.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.service.leave.port.ScheduledDatesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班查询只读端口实现（复用 P3 既有 {@code AttendanceScheduleMapper}，见端口类头说明）。
 * <p>
 * 只读、不修改 P3 域任何代码与数据；查询走 {@code attendance_schedule} 的 (employee_id, work_date) 维度。
 */
@Service
@RequiredArgsConstructor
public class LeaveScheduledDatesQueryImpl implements ScheduledDatesQuery {

    private final AttendanceScheduleMapper attendanceScheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> scheduledDates(Long employeeId, LocalDate start, LocalDate end) {
        if (employeeId == null || start == null || end == null || start.isAfter(end)) {
            return List.of();
        }
        List<AttendanceSchedule> rows = attendanceScheduleMapper.selectList(
                new LambdaQueryWrapper<AttendanceSchedule>()
                        .select(AttendanceSchedule::getWorkDate)
                        .eq(AttendanceSchedule::getEmployeeId, employeeId)
                        .between(AttendanceSchedule::getWorkDate, start, end));
        return rows.stream()
                .map(AttendanceSchedule::getWorkDate)
                .distinct()
                .sorted()
                .toList();
    }
}
