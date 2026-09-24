package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.ScheduleBatchByStationRequest;
import com.qiujie.dto.attendance.ScheduleBatchRequest;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceScheduleService;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.service.attendance.support.AttendancePeriodResolver;
import com.qiujie.service.attendance.support.AttendanceSupport;
import com.qiujie.service.attendance.support.SchedulePlanner;
import com.qiujie.vo.attendance.MyScheduleVO;
import com.qiujie.vo.attendance.ScheduleMatrixVO;
import com.qiujie.vo.attendance.ScheduleSaveResultVO;
import com.qiujie.vo.attendance.ScheduleStationResultVO;
import com.qiujie.vo.attendance.ScheduleViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排班服务实现（Mock {@code attendanceStore} 排班段 + S3 算法落地）。
 * <p>
 * 与 Mock 的差异（有意为之，已登记）：手动批量保存由「逐条应用、中途报错留下部分改动」改为
 * {@code @Transactional} 原子提交——实时后端不应出现半成功状态；错误码与文案保持逐条一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceScheduleServiceImpl implements AttendanceScheduleService {

    /** 排班窗口天数（周一 → 周日） */
    private static final int WEEK_DAYS = 7;
    /** 手动批量保存单次上限（与员工导入批量口径一致） */
    private static final int BATCH_MAX_ITEMS = 200;

    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final AttendanceShiftMapper attendanceShiftMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final AttendanceShiftService attendanceShiftService;
    private final AlgoProperties algoProperties;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public ScheduleMatrixVO matrix(Long stationId, String weekStart) {
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        LocalDate start = resolveWeekStart(weekStart);
        List<LocalDate> dates = weekDates(start);
        LocalDate end = dates.get(dates.size() - 1);

        List<Employee> staff = activeEmployeesOfStation(stationId, false);
        Map<String, AttendanceSchedule> index = scheduleIndex(stationId, start, end);

        ScheduleMatrixVO vo = new ScheduleMatrixVO();
        vo.setWeekStart(dates.get(0));
        vo.setWeekEnd(end);
        vo.setDates(dates);
        vo.setShifts(attendanceShiftService.list(stationId));
        List<ScheduleMatrixVO.EmployeeRow> rows = new ArrayList<>(staff.size());
        for (Employee employee : staff) {
            ScheduleMatrixVO.EmployeeRow row = new ScheduleMatrixVO.EmployeeRow();
            row.setEmployeeId(employee.getId());
            row.setEmployeeName(employee.getRealName());
            List<ScheduleMatrixVO.DayCell> cells = new ArrayList<>(dates.size());
            for (LocalDate workDate : dates) {
                ScheduleMatrixVO.DayCell cell = new ScheduleMatrixVO.DayCell();
                cell.setWorkDate(workDate);
                AttendanceSchedule schedule = index.get(key(employee.getId(), workDate));
                if (schedule != null) {
                    cell.setScheduleId(schedule.getId());
                    cell.setShiftId(schedule.getShiftId());
                }
                cells.add(cell);
            }
            row.setDays(cells);
            rows.add(row);
        }
        vo.setEmployees(rows);
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public MyScheduleVO mine(Long employeeId, String weekStart) {
        LocalDate start = resolveWeekStart(weekStart);
        List<LocalDate> dates = weekDates(start);
        LocalDate end = dates.get(dates.size() - 1);

        Map<String, AttendanceSchedule> index = scheduleIndex(null, start, end, employeeId);
        MyScheduleVO vo = new MyScheduleVO();
        vo.setWeekStart(dates.get(0));
        vo.setWeekEnd(end);
        vo.setDates(dates);
        List<MyScheduleVO.Day> list = new ArrayList<>(dates.size());
        for (LocalDate workDate : dates) {
            MyScheduleVO.Day day = new MyScheduleVO.Day();
            day.setWorkDate(workDate);
            AttendanceSchedule schedule = index.get(key(employeeId, workDate));
            if (schedule != null) {
                day.setScheduleId(schedule.getId());
                AttendanceShift shift = schedule.getShiftId() == null ? null
                        : attendanceShiftMapper.selectById(schedule.getShiftId());
                if (shift != null) {
                    day.setShiftId(shift.getId());
                    day.setShiftName(shift.getShiftName());
                    day.setStartTime(shift.getStartTime());
                    day.setEndTime(shift.getEndTime());
                    day.setColor(shift.getColor());
                    day.setRestMinutes(shift.getRestMinutes());
                }
            }
            list.add(day);
        }
        vo.setList(list);
        return vo;
    }

    // ==================== 手动批量保存 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleSaveResultVO saveBatch(ScheduleBatchRequest request) {
        ScheduleBatchRequest safe = request == null ? new ScheduleBatchRequest() : request;
        Long stationId = safe.getStationId();
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        if (safe.getItems() == null || safe.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "items 须为非空数组");
        }
        if (safe.getItems().size() > BATCH_MAX_ITEMS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次保存不超过 " + BATCH_MAX_ITEMS + " 条");
        }
        for (ScheduleBatchRequest.Item item : safe.getItems()) {
            if (item == null || item.getEmployeeId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 employeeId");
            }
            if (!AttendanceSupport.isStrictDate(item.getWorkDate())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "workDate 格式须为 YYYY-MM-DD");
            }
        }

        List<Employee> employees = activeEmployeesOfStation(stationId, false);
        Map<Long, Employee> employeeIndex = new HashMap<>();
        for (Employee employee : employees) {
            employeeIndex.put(employee.getId(), employee);
        }

        ScheduleSaveResultVO result = new ScheduleSaveResultVO();
        int saved = 0;
        int removed = 0;
        for (ScheduleBatchRequest.Item item : safe.getItems()) {
            Employee employee = employeeIndex.get(item.getEmployeeId());
            if (employee == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "员工 " + item.getEmployeeId() + " 不属于该驿站");
            }
            LocalDate workDate = AttendanceSupport.parseDate(item.getWorkDate());
            AttendanceSchedule existing = findOne(item.getEmployeeId(), workDate);
            if (item.getShiftId() == null) {
                if (existing != null) {
                    attendanceScheduleMapper.deleteById(existing.getId());
                    removed++;
                }
                continue;
            }
            AttendanceShift shift = attendanceShiftService.findEntity(item.getShiftId());
            if (shift == null || !stationId.equals(shift.getStationId())
                    || shift.getStatus() == null || shift.getStatus() != 1) {
                throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_UNAVAILABLE);
            }
            if (existing != null) {
                existing.setShiftId(shift.getId());
                attendanceScheduleMapper.updateById(existing);
            } else {
                AttendanceSchedule schedule = new AttendanceSchedule();
                schedule.setStationId(stationId);
                schedule.setEmployeeId(item.getEmployeeId());
                schedule.setWorkDate(workDate);
                schedule.setShiftId(shift.getId());
                attendanceScheduleMapper.insert(schedule);
            }
            saved++;
        }
        result.setSaved(saved);
        result.setRemoved(removed);
        return result;
    }

    // ==================== 整站排班（手动 / S3 智能） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleStationResultVO saveByStation(ScheduleBatchByStationRequest request) {
        ScheduleBatchByStationRequest safe = request == null ? new ScheduleBatchByStationRequest() : request;
        Long stationId = safe.getStationId();
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 stationId");
        }
        if (stationMapper.selectById(stationId) == null) {
            throw new BusinessException(ErrorCode.STATION_NOT_FOUND);
        }
        if (!AttendanceSupport.isStrictDate(safe.getStartDate()) || !AttendanceSupport.isStrictDate(safe.getEndDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate / endDate 格式须为 YYYY-MM-DD");
        }
        if (safe.getStartDate().compareTo(safe.getEndDate()) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "endDate 不能早于 startDate");
        }
        if (safe.getWeekdays() != null && safe.getWeekdays().stream()
                .anyMatch(d -> d == null || d < 0 || d > 6)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "weekdays 须为 0-6 的整数数组");
        }
        boolean skipExisting = safe.getSkipExisting() == null || safe.getSkipExisting();

        List<LocalDate> dates = resolveDates(safe.getStartDate(), safe.getEndDate(), safe.getWeekdays());
        List<Employee> staff = activeEmployeesOfStation(stationId, true, safe.getEmployeeIds());

        if (safe.getShiftId() != null) {
            return manualByStation(stationId, safe.getShiftId(), staff, dates, skipExisting);
        }
        return autoByStation(stationId, staff, dates, skipExisting);
    }

    /** 手动模式：整站铺同一班次（Mock 语义，逐位保留） */
    private ScheduleStationResultVO manualByStation(Long stationId, Long shiftId, List<Employee> staff,
                                                    List<LocalDate> dates, boolean skipExisting) {
        AttendanceShift shift = attendanceShiftService.findEntity(shiftId);
        if (shift == null || !stationId.equals(shift.getStationId())
                || shift.getStatus() == null || shift.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_UNAVAILABLE);
        }
        ScheduleStationResultVO result = new ScheduleStationResultVO();
        int created = 0;
        int skipped = 0;
        int total = 0;
        for (Employee employee : staff) {
            for (LocalDate workDate : dates) {
                total++;
                AttendanceSchedule existing = findOne(employee.getId(), workDate);
                if (existing != null && skipExisting) {
                    skipped++;
                    continue;
                }
                if (existing != null) {
                    existing.setShiftId(shift.getId());
                    attendanceScheduleMapper.updateById(existing);
                } else {
                    insert(employee.getId(), stationId, workDate, shift.getId());
                }
                created++;
            }
        }
        result.setCreated(created);
        result.setSkipped(skipped);
        result.setTotal(total);
        return result;
    }

    /**
     * 智能模式（S3 算法）：逐格生成班次，尊重「每日每班最少在岗 / 连续工作上限 / 轮休均衡 / 班次均衡」。
     * 失败降级由 {@link SchedulePlanner} 内部保证（返回贪心解 + 违规清单，不抛异常）。
     */
    private ScheduleStationResultVO autoByStation(Long stationId, List<Employee> staff, List<LocalDate> dates,
                                                  boolean skipExisting) {
        List<AttendanceShift> shifts = activeShiftsOfStation(stationId);
        if (shifts.isEmpty()) {
            // 无可用班次则时间基准缺失，智能排班无意义：与手动模式同码（9106）快速失败
            throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_UNAVAILABLE);
        }
        ScheduleStationResultVO result = new ScheduleStationResultVO();
        if (staff.isEmpty() || dates.isEmpty()) {
            result.setCreated(0);
            result.setSkipped(0);
            result.setTotal(0);
            result.setViolations(new ArrayList<>());
            result.setFallback(false);
            return result;
        }

        // 已存在排班 → 固定格（skipExisting 时不被重排）；未知班次按「固定轮休」处理以保持既有状态
        Integer[][] fixed = skipExisting ? buildFixedMatrix(stationId, staff, dates, shifts) : null;

        AlgoProperties.Schedule s = algoProperties.getSchedule();
        SchedulePlanner.Config cfg = new SchedulePlanner.Config(
                s.getMinPerShift(), s.getMaxConsecutiveWork(), s.getRestCycleDays(),
                s.getWeights().getMinStaff(), s.getWeights().getConsecutive(), s.getWeights().getCoverageDeficit(),
                s.getWeights().getShiftBalance(), s.getWeights().getRestSpread(),
                s.getSa().getIterations(), s.getSa().getInitialTemp(), s.getSa().getCooling());
        long seed = planSeed(stationId, dates);
        SchedulePlanner.Plan plan = SchedulePlanner.plan(cfg, staff.size(), dates.size(), shifts.size(), fixed, seed);

        int created = 0;
        int skipped = 0;
        int total = 0;
        for (int e = 0; e < staff.size(); e++) {
            Employee employee = staff.get(e);
            for (int d = 0; d < dates.size(); d++) {
                total++;
                LocalDate workDate = dates.get(d);
                AttendanceSchedule existing = findOne(employee.getId(), workDate);
                if (skipExisting && existing != null) {
                    skipped++;
                    continue;
                }
                int shiftIndex = plan.solution().shift()[e][d];
                if (shiftIndex < 0) {
                    // 轮休：清空该天排班（无排班即在岗休息，与 Mock「无排班 = 轮休」口径一致）
                    if (existing != null) {
                        attendanceScheduleMapper.deleteById(existing.getId());
                    }
                } else {
                    Long targetShiftId = shifts.get(shiftIndex).getId();
                    if (existing != null) {
                        existing.setShiftId(targetShiftId);
                        attendanceScheduleMapper.updateById(existing);
                    } else {
                        insert(employee.getId(), stationId, workDate, targetShiftId);
                    }
                }
                created++;
            }
        }
        result.setCreated(created);
        result.setSkipped(skipped);
        result.setTotal(total);
        result.setViolations(toViolationVOs(plan.violations()));
        result.setFallback(plan.fallback());
        if (plan.fallback()) {
            // 算法降级属于「可恢复」事件：记 warn 便于线上观察（不阻塞主流程）
            log.warn("整站智能排班发生降级：stationId={}, 日期={}~{}, 违规 {} 条",
                    stationId, dates.get(0), dates.get(dates.size() - 1), plan.violations().size());
        }
        return result;
    }

    // ==================== 私有工具 ====================

    private void insert(Long employeeId, Long stationId, LocalDate workDate, Long shiftId) {
        AttendanceSchedule schedule = new AttendanceSchedule();
        schedule.setStationId(stationId);
        schedule.setEmployeeId(employeeId);
        schedule.setWorkDate(workDate);
        schedule.setShiftId(shiftId);
        attendanceScheduleMapper.insert(schedule);
    }

    private AttendanceSchedule findOne(Long employeeId, LocalDate workDate) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getId, AttendanceSchedule::getStationId, AttendanceSchedule::getEmployeeId,
                        AttendanceSchedule::getWorkDate, AttendanceSchedule::getShiftId)
                .eq(AttendanceSchedule::getEmployeeId, employeeId)
                .eq(AttendanceSchedule::getWorkDate, workDate)
                .orderByAsc(AttendanceSchedule::getId);
        List<AttendanceSchedule> list = attendanceScheduleMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 驿站内某日期区间的排班索引：key = employeeId + workDate */
    private Map<String, AttendanceSchedule> scheduleIndex(Long stationId, LocalDate start, LocalDate end) {
        return scheduleIndex(stationId, start, end, null);
    }

    private Map<String, AttendanceSchedule> scheduleIndex(Long stationId, LocalDate start, LocalDate end, Long employeeId) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getId, AttendanceSchedule::getStationId, AttendanceSchedule::getEmployeeId,
                        AttendanceSchedule::getWorkDate, AttendanceSchedule::getShiftId)
                .between(AttendanceSchedule::getWorkDate, start, end);
        if (stationId != null) {
            wrapper.eq(AttendanceSchedule::getStationId, stationId);
        }
        if (employeeId != null) {
            wrapper.eq(AttendanceSchedule::getEmployeeId, employeeId);
        }
        Map<String, AttendanceSchedule> index = new HashMap<>();
        for (AttendanceSchedule schedule : attendanceScheduleMapper.selectList(wrapper)) {
            index.put(key(schedule.getEmployeeId(), schedule.getWorkDate()), schedule);
        }
        return index;
    }

    /** 在职员工（{@code statusOnly=true} 时额外过滤 status=1）；{@code employeeIds} 非空时只取指定员工 */
    private List<Employee> activeEmployeesOfStation(Long stationId, boolean statusOnly) {
        return activeEmployeesOfStation(stationId, statusOnly, null);
    }

    private List<Employee> activeEmployeesOfStation(Long stationId, boolean statusOnly, List<Long> employeeIds) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName, Employee::getStationId)
                .eq(Employee::getStationId, stationId)
                .orderByAsc(Employee::getId);
        if (statusOnly) {
            wrapper.eq(Employee::getStatus, 1);
        }
        if (employeeIds != null && !employeeIds.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            for (Long id : employeeIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
            if (!ids.isEmpty()) {
                wrapper.in(Employee::getId, ids);
            }
        }
        return employeeMapper.selectList(wrapper);
    }

    /** 驿站内启用班次（按开始时间升序，其顺序即算法中的班次下标） */
    private List<AttendanceShift> activeShiftsOfStation(Long stationId) {
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceShift::getStationId, stationId).eq(AttendanceShift::getStatus, 1);
        List<AttendanceShift> rows = attendanceShiftMapper.selectList(wrapper);
        rows.sort(Comparator.comparingDouble(s -> AttendancePeriodResolver.minutesOfDay(s.getStartTime())));
        return rows;
    }

    /** 由既有排班构造固定格矩阵（skipExisting）：班次命中 → 其下标；未命中启用班次 → 固定轮休（-1） */
    private Integer[][] buildFixedMatrix(Long stationId, List<Employee> staff, List<LocalDate> dates,
                                         List<AttendanceShift> shifts) {
        Map<Long, Integer> shiftIndex = new HashMap<>();
        for (int i = 0; i < shifts.size(); i++) {
            shiftIndex.put(shifts.get(i).getId(), i);
        }
        // 一次取全区间排班，避免按「员工 × 天」逐个查询（N+1）
        Map<String, AttendanceSchedule> index =
                scheduleIndex(stationId, dates.get(0), dates.get(dates.size() - 1));
        Integer[][] fixed = new Integer[staff.size()][dates.size()];
        for (int e = 0; e < staff.size(); e++) {
            for (int d = 0; d < dates.size(); d++) {
                AttendanceSchedule schedule = index.get(key(staff.get(e).getId(), dates.get(d)));
                if (schedule != null) {
                    fixed[e][d] = shiftIndex.getOrDefault(schedule.getShiftId(), -1);
                }
            }
        }
        return fixed;
    }

    /** 固定种子 + 输入指纹：同一（驿站, 日期区间）可复现，不同输入产生不同排布 */
    private long planSeed(Long stationId, List<LocalDate> dates) {
        long seed = SchedulePlanner.DEFAULT_SEED;
        seed = seed * 31 + stationId;
        seed = seed * 31 + dates.get(0).toEpochDay();
        seed = seed * 31 + dates.get(dates.size() - 1).toEpochDay();
        return seed;
    }

    private List<ScheduleViolationVO> toViolationVOs(List<SchedulePlanner.Violation> violations) {
        List<ScheduleViolationVO> list = new ArrayList<>(violations.size());
        for (SchedulePlanner.Violation v : violations) {
            ScheduleViolationVO vo = new ScheduleViolationVO();
            vo.setRule(v.rule());
            vo.setDayIndex(v.dayIndex());
            vo.setShiftIndex(v.shiftIndex());
            vo.setEmployeeIndex(v.employeeId());
            vo.setDetail(v.detail());
            list.add(vo);
        }
        return list;
    }

    private List<LocalDate> resolveDates(String startDate, String endDate, List<Integer> weekdays) {
        Set<Integer> dayFilter = null;
        if (weekdays != null && !weekdays.isEmpty()) {
            dayFilter = new HashSet<>(weekdays);
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate start = AttendanceSupport.parseDate(startDate);
        LocalDate end = AttendanceSupport.parseDate(endDate);
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (dayFilter != null && !dayFilter.contains(day.getDayOfWeek().getValue() % 7)) {
                continue;
            }
            dates.add(day);
        }
        return dates;
    }

    private LocalDate resolveWeekStart(String weekStart) {
        if (weekStart != null && !weekStart.isBlank() && !AttendanceSupport.isDate(weekStart)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "weekStart 格式须为 YYYY-MM-DD");
        }
        LocalDate anchor = (weekStart == null || weekStart.isBlank())
                ? AttendanceSupport.today()
                : AttendanceSupport.parseDate(weekStart);
        return AttendanceSupport.mondayOf(anchor);
    }

    private List<LocalDate> weekDates(LocalDate monday) {
        List<LocalDate> dates = new ArrayList<>(WEEK_DAYS);
        for (int i = 0; i < WEEK_DAYS; i++) {
            dates.add(monday.plusDays(i));
        }
        return dates;
    }

    private String key(Long employeeId, LocalDate workDate) {
        return employeeId + "@" + AttendanceSupport.formatDate(workDate);
    }
}
