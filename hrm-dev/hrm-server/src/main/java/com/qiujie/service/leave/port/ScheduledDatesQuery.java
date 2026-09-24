package com.qiujie.service.leave.port;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班查询只读端口（P7 请假域消费 P3 考勤域的「逐日排班」能力）。
 * <p>
 * 语义对齐 Mock {@code attendanceStore.scheduledDatesOf}：返回某员工在 [start, end] 内「有排班」的日期集合
 * （升序、去重）。计薪天数（SCHEDULED 假别）必须复用本端口，不得另写一份排班查询（口径漂移会导致
 * 计薪天数与考勤/算薪对不上）。
 * <p>
 * <b>实现说明（事实性纠正）</b>：P3 当前<b>未</b>对外暴露 {@code scheduledDatesOf} 公共服务方法
 * （{@code AttendanceScheduleService} 无该方法，P3 域内 {@code AttendanceAnomalyServiceImpl} /
 * {@code PayrollContextProvider} / {@code KpiAttendanceMetricSource} 均直连 {@code AttendanceScheduleMapper}）。
 * 为不改动 P3 域代码，本批次按架构 §2.2「跨域只读端口」模式，在请假域建本端口并由请假域实现类
 * 复用既有 {@code AttendanceScheduleMapper} 查询（不新建他域 Mapper、不写他域表）。
 * TODO(扩展): P3 若后续对外暴露排班只读服务，本端口实现改为委托其公共方法即可，调用方零改动。
 */
public interface ScheduledDatesQuery {

    /**
     * 某员工在 [start, end] 内「有排班」的日期集合（升序、去重）。
     *
     * @param employeeId 员工 id
     * @param start      区间起（含）
     * @param end        区间止（含）
     * @return 排班日期升序列表；入参缺失或 start &gt; end 时返回空列表
     */
    List<LocalDate> scheduledDates(Long employeeId, LocalDate start, LocalDate end);
}
