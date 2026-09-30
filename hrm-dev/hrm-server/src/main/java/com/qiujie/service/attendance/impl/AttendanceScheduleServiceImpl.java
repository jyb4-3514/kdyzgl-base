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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排班服务实现（多班次版：ARCH-C-2/3/4/5 + Mock {@code attendanceStore} 排班段 + S3 算法落地）。
 * <p>
 * <b>唯一性口径（ARCH-S-1 / V22）</b>：由「{@code (employeeId, workDate)} 活跃唯一」改为
 * 「{@code (employeeId, workDate, shiftId)} 活跃唯一」，一天可并存 ≤ {@code maxShiftsPerDay} 个班次；
 * DB 侧由生成列 {@code active_shift_key} 唯一键收口，本类只按活跃行做集合运算（不写生成列）。
 * <p>
 * <b>保存语义（A-④）</b>：以「当天班次集合整体覆盖」——{@code ins = T\C} 新增、{@code del = C\T} 逻辑删、
 * {@code T∩C} no-op（集合差量，最小化写且天然幂等）。
 * <p>
 * 与 Mock 的差异（有意为之，已登记）：批量保存由「逐条应用、中途报错留下部分改动」改为 {@code @Transactional}
 * 原子提交——实时后端不应出现半成功状态；重叠 / 超上限 / {@code ordinal} 冲突均拒绝整批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceScheduleServiceImpl implements AttendanceScheduleService {

    /** 排班窗口天数（周一 → 周日） */
    private static final int WEEK_DAYS = 7;
    /** 手动批量保存单次上限（与员工导入批量口径一致） */
    private static final int BATCH_MAX_ITEMS = 200;
    /**
     * 计薪序号编码容量上限：{@code shiftUnit = epochDay×2 + ordinal} 仅容纳 0/1，即单日最多 2 个半天单元。
     * <p>
     * 这是与计薪编码绑定的结构不变式（非可调超参），故作为硬上限：即便 {@code maxShiftsPerDay} 被误配为 &gt;2
     * 也只按 2 生效，避免折算分母失真（算法 R2 缓解项）。
     */
    private static final int PAYROLL_SHIFT_UNIT_CAPACITY = 2;
    /** 重复班次处置：报错（默认 IDEMPOTENT，即集合差量天然 no-op） */
    private static final String DUPLICATE_POLICY_REJECT = "REJECT";

    /** id 升序、null 安全（单测 Mock 场景下自增主键不回填，避免排序 NPE） */
    private static final Comparator<AttendanceSchedule> BY_ID =
            Comparator.comparing(AttendanceSchedule::getId, Comparator.nullsLast(Comparator.naturalOrder()));

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
        Map<String, List<AttendanceSchedule>> index = scheduleIndex(stationId, start, end);

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
                List<AttendanceSchedule> dayRows = index.getOrDefault(key(employee.getId(), workDate), List.of());
                List<Long> shiftIds = new ArrayList<>(dayRows.size());
                for (AttendanceSchedule schedule : dayRows) {
                    shiftIds.add(schedule.getShiftId());
                }
                cell.setShiftIds(shiftIds);
                if (!dayRows.isEmpty()) {
                    // 向后兼容：首条（id 最小）仍回填 scheduleId / shiftId
                    cell.setScheduleId(dayRows.get(0).getId());
                    cell.setShiftId(dayRows.get(0).getShiftId());
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

        Map<String, List<AttendanceSchedule>> index = scheduleIndex(null, start, end, employeeId);
        Map<Long, AttendanceShift> shiftMap = loadShiftMap(index.values());
        MyScheduleVO vo = new MyScheduleVO();
        vo.setWeekStart(dates.get(0));
        vo.setWeekEnd(end);
        vo.setDates(dates);
        List<MyScheduleVO.Day> list = new ArrayList<>(dates.size());
        for (LocalDate workDate : dates) {
            MyScheduleVO.Day day = new MyScheduleVO.Day();
            day.setWorkDate(workDate);
            List<AttendanceSchedule> dayRows = index.getOrDefault(key(employeeId, workDate), List.of());
            List<MyScheduleVO.ShiftDetail> details = new ArrayList<>(dayRows.size());
            for (AttendanceSchedule schedule : dayRows) {
                MyScheduleVO.ShiftDetail detail = new MyScheduleVO.ShiftDetail();
                detail.setScheduleId(schedule.getId());
                AttendanceShift shift = schedule.getShiftId() == null ? null : shiftMap.get(schedule.getShiftId());
                if (shift != null) {
                    detail.setShiftId(shift.getId());
                    detail.setShiftName(shift.getShiftName());
                    detail.setStartTime(shift.getStartTime());
                    detail.setEndTime(shift.getEndTime());
                    detail.setColor(shift.getColor());
                    detail.setRestMinutes(shift.getRestMinutes());
                }
                details.add(detail);
            }
            day.setShifts(details);
            if (!details.isEmpty()) {
                // 向后兼容：首条（id 最小）仍回填扁平字段
                MyScheduleVO.ShiftDetail first = details.get(0);
                day.setScheduleId(first.getScheduleId());
                day.setShiftId(first.getShiftId());
                day.setShiftName(first.getShiftName());
                day.setStartTime(first.getStartTime());
                day.setEndTime(first.getEndTime());
                day.setColor(first.getColor());
                day.setRestMinutes(first.getRestMinutes());
            }
            list.add(day);
        }
        vo.setList(list);
        return vo;
    }

    // ==================== 手动批量保存（集合差量覆盖） ====================

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
        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (ScheduleBatchRequest.Item item : safe.getItems()) {
            if (item == null || item.getEmployeeId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 employeeId");
            }
            if (!AttendanceSupport.isStrictDate(item.getWorkDate())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "workDate 格式须为 YYYY-MM-DD");
            }
            LocalDate workDate = AttendanceSupport.parseDate(item.getWorkDate());
            minDate = minDate == null || workDate.isBefore(minDate) ? workDate : minDate;
            maxDate = maxDate == null || workDate.isAfter(maxDate) ? workDate : maxDate;
        }

        List<Employee> employees = activeEmployeesOfStation(stationId, false);
        Map<Long, Employee> employeeIndex = new HashMap<>();
        for (Employee employee : employees) {
            employeeIndex.put(employee.getId(), employee);
        }
        // 一次预取区间内全部活跃排班，避免逐项查询（算法 §4.1）
        Map<String, List<AttendanceSchedule>> index = scheduleIndex(stationId, minDate, maxDate);
        Map<Long, AttendanceShift> shiftCache = new HashMap<>();

        int maxPerDay = effectiveMaxShiftsPerDay();
        AlgoProperties.Attendance attendance = algoProperties.getAttendance();
        ScheduleSaveResultVO result = new ScheduleSaveResultVO();
        int saved = 0;
        int removed = 0;
        for (ScheduleBatchRequest.Item item : safe.getItems()) {
            Employee employee = employeeIndex.get(item.getEmployeeId());
            if (employee == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "员工 " + item.getEmployeeId() + " 不属于该驿站");
            }
            LocalDate workDate = AttendanceSupport.parseDate(item.getWorkDate());
            LinkedHashSet<Long> target = resolveTargetSet(item);

            // 校验①：班次存在 / 同驿站 / 启用（9106）
            List<AttendanceShift> targetShifts = new ArrayList<>(target.size());
            for (Long shiftId : target) {
                AttendanceShift shift = shiftCache.computeIfAbsent(shiftId, attendanceShiftService::findEntity);
                requireShiftAvailable(shift, stationId);
                targetShifts.add(shift);
            }
            // 校验②：单日上限（9111）
            if (target.size() > maxPerDay) {
                throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_DAILY_LIMIT_EXCEEDED,
                        "单日最多排 " + maxPerDay + " 个班次");
            }
            // 校验③：时间重叠（9110，半开区间 [s,e)）
            if (!attendance.isAllowShiftOverlap()) {
                requireNoOverlap(targetShifts, attendance.getOverlapToleranceMinutes());
            }
            // 校验④：同日 ordinal 互异（9112）
            if (attendance.isRequireDistinctOrdinalPerDay()) {
                requireDistinctOrdinal(targetShifts);
            }

            List<AttendanceSchedule> current = index.getOrDefault(key(item.getEmployeeId(), workDate), List.of());
            Set<Long> currentIds = shiftIdsOf(current);
            // 重复排同一班次（T∩C）处置：默认幂等 no-op；REJECT 则报错
            if (DUPLICATE_POLICY_REJECT.equalsIgnoreCase(attendance.getDuplicateShiftPolicy())
                    && target.stream().anyMatch(currentIds::contains)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在重复排班（同员工同天同班次）");
            }
            // 计数：removed = |C\T|；saved = |T|（覆盖后班次行数，含未变化的交集，ARCH-C-2b）
            for (AttendanceSchedule schedule : current) {
                if (!target.contains(schedule.getShiftId())) {
                    removed++;
                }
            }
            saved += target.size();
            applyDayTarget(stationId, item.getEmployeeId(), workDate, target, current, index);
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
        // 一次预取区间排班，供手动 / 智能两路径共用（避免按「员工 × 天」逐个查询）
        Map<String, List<AttendanceSchedule>> index = scheduleIndex(stationId,
                AttendanceSupport.parseDate(safe.getStartDate()), AttendanceSupport.parseDate(safe.getEndDate()));

        if (safe.getShiftId() != null) {
            return manualByStation(stationId, safe.getShiftId(), staff, dates, skipExisting, index);
        }
        return autoByStation(stationId, staff, dates, skipExisting, index);
    }

    /** 手动模式：整站铺同一班次（Mock 语义，逐位保留；多班次下按「目标集合 = {shiftId}」覆盖该天） */
    private ScheduleStationResultVO manualByStation(Long stationId, Long shiftId, List<Employee> staff,
                                                    List<LocalDate> dates, boolean skipExisting,
                                                    Map<String, List<AttendanceSchedule>> index) {
        AttendanceShift shift = attendanceShiftService.findEntity(shiftId);
        requireShiftAvailable(shift, stationId);
        ScheduleStationResultVO result = new ScheduleStationResultVO();
        int created = 0;
        int skipped = 0;
        int total = 0;
        Set<Long> target = Set.of(shiftId);
        for (Employee employee : staff) {
            for (LocalDate workDate : dates) {
                total++;
                List<AttendanceSchedule> current = index.getOrDefault(key(employee.getId(), workDate), List.of());
                if (!current.isEmpty() && skipExisting) {
                    skipped++;
                    continue;
                }
                applyDayTarget(stationId, employee.getId(), workDate, target, current, index);
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
     * <p>
     * TODO(扩展): S3 解空间仍为单班次模型（{@code shift[employee][day]} 单值），多班次完整指派登记在
     *   {@code algorithm-multi-shift-scheduling.md §4.3}；本期智能模式仍只产 1 班/天，手动模式支持多班次。
     */
    private ScheduleStationResultVO autoByStation(Long stationId, List<Employee> staff, List<LocalDate> dates,
                                                  boolean skipExisting, Map<String, List<AttendanceSchedule>> index) {
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
        Integer[][] fixed = skipExisting ? buildFixedMatrix(staff, dates, shifts, index) : null;

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
                List<AttendanceSchedule> current = index.getOrDefault(key(employee.getId(), workDate), List.of());
                if (skipExisting && !current.isEmpty()) {
                    skipped++;
                    continue;
                }
                int shiftIndex = plan.solution().shift()[e][d];
                Set<Long> target = shiftIndex < 0
                        // 轮休：清空该天排班（无排班即在岗休息，与 Mock「无排班 = 轮休」口径一致）
                        ? Set.of()
                        : Set.of(shifts.get(shiftIndex).getId());
                applyDayTarget(stationId, employee.getId(), workDate, target, current, index);
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

    // ==================== 私有：集合差量 ====================

    /**
     * 应用「当天目标班次集合」：{@code ins = T\C} 新增、{@code del = C\T} 逻辑删、{@code T∩C} no-op。
     * <p>
     * 结束后回写索引为新集合，保证「同一请求内同员工同日多次提交」按顺序生效且整体幂等。
     */
    private void applyDayTarget(Long stationId, Long employeeId, LocalDate workDate, Set<Long> target,
                                List<AttendanceSchedule> current, Map<String, List<AttendanceSchedule>> index) {
        Set<Long> currentIds = shiftIdsOf(current);
        List<AttendanceSchedule> rows = new ArrayList<>(target.size());
        for (AttendanceSchedule schedule : current) {
            if (target.contains(schedule.getShiftId())) {
                rows.add(schedule);
            } else {
                // 差量移除：逻辑删（@TableLogic → UPDATE is_deleted=1），释放活跃唯一槽位
                attendanceScheduleMapper.deleteById(schedule.getId());
            }
        }
        for (Long shiftId : target) {
            if (!currentIds.contains(shiftId)) {
                AttendanceSchedule schedule = new AttendanceSchedule();
                schedule.setStationId(stationId);
                schedule.setEmployeeId(employeeId);
                schedule.setWorkDate(workDate);
                schedule.setShiftId(shiftId);
                attendanceScheduleMapper.insert(schedule);
                rows.add(schedule);
            }
        }
        rows.sort(BY_ID);
        index.put(key(employeeId, workDate), rows);
    }

    /** 解析目标集合：{@code shiftIds} 存在即为准（含空数组）；否则回退单值 {@code shiftId}；皆缺=清空 */
    private LinkedHashSet<Long> resolveTargetSet(ScheduleBatchRequest.Item item) {
        LinkedHashSet<Long> target = new LinkedHashSet<>();
        if (item.getShiftIds() != null) {
            for (Long shiftId : item.getShiftIds()) {
                if (shiftId != null) {
                    target.add(shiftId);
                }
            }
            return target;
        }
        if (item.getShiftId() != null) {
            target.add(item.getShiftId());
        }
        return target;
    }

    private Set<Long> shiftIdsOf(List<AttendanceSchedule> rows) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceSchedule schedule : rows) {
            ids.add(schedule.getShiftId());
        }
        return ids;
    }

    /** 单日上限：以计薪编码容量为硬上限（配置 >2 时按 2 生效并告警，算法 R2） */
    private int effectiveMaxShiftsPerDay() {
        int configured = algoProperties.getAttendance().getMaxShiftsPerDay();
        if (configured > PAYROLL_SHIFT_UNIT_CAPACITY) {
            log.warn("maxShiftsPerDay 配置为 {} 超出计薪序号编码上限 {}，本次按 {} 生效（须先做计薪编码扩展）",
                    configured, PAYROLL_SHIFT_UNIT_CAPACITY, PAYROLL_SHIFT_UNIT_CAPACITY);
            return PAYROLL_SHIFT_UNIT_CAPACITY;
        }
        return Math.max(1, configured);
    }

    // ==================== 私有：校验 ====================

    private void requireShiftAvailable(AttendanceShift shift, Long stationId) {
        if (shift == null || !stationId.equals(shift.getStationId())
                || shift.getStatus() == null || shift.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_UNAVAILABLE);
        }
    }

    /** 半开区间 {@code [s,e)} 相交判定；相邻（{@code e₁==s₂}）不算重叠；跨零点按 +1440 归一 */
    private void requireNoOverlap(List<AttendanceShift> shifts, int toleranceMinutes) {
        for (int i = 0; i < shifts.size(); i++) {
            double[] a = interval(shifts.get(i));
            for (int j = i + 1; j < shifts.size(); j++) {
                double[] b = interval(shifts.get(j));
                if (a[0] + toleranceMinutes < b[1] && b[0] + toleranceMinutes < a[1]) {
                    throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_TIME_OVERLAP,
                            "班次「" + shifts.get(i).getShiftName() + "」与「" + shifts.get(j).getShiftName() + "」时间重叠");
                }
            }
        }
    }

    /** 归一化班次区间为 {@code [start, end)}（分钟）；结束不晚于开始时按跨零点顺延或判脏数据 */
    private double[] interval(AttendanceShift shift) {
        double start = AttendancePeriodResolver.minutesOfDay(shift.getStartTime());
        double end = AttendancePeriodResolver.minutesOfDay(shift.getEndTime());
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "班次「" + shift.getShiftName() + "」时间不可解析");
        }
        if (end <= start) {
            if (algoProperties.getAttendance().isCrossMidnightAsNextDay()) {
                end += 1440;
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "班次「" + shift.getShiftName() + "」结束时间须晚于开始时间");
            }
        }
        return new double[]{start, end};
    }

    /** 同日各班次 {@code ordinal} 必须互异（避免计薪应出班次去重少算，评审 M-4 / 算法 §1.3.1） */
    private void requireDistinctOrdinal(List<AttendanceShift> shifts) {
        int boundary = algoProperties.getPayroll().getMiddayBoundaryMinute();
        Set<Integer> seen = new HashSet<>();
        for (AttendanceShift shift : shifts) {
            double minutes = AttendancePeriodResolver.minutesOfDay(shift.getStartTime());
            if (!Double.isFinite(minutes)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "班次「" + shift.getShiftName() + "」开始时间不可解析");
            }
            int ordinal = minutes < boundary ? 0 : 1;
            if (!seen.add(ordinal)) {
                throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_ORDINAL_CONFLICT,
                        "同日排班须一早一晚：班次「" + shift.getShiftName() + "」与其它班次时段归属相同");
            }
        }
    }

    // ==================== 私有工具 ====================

    /** 区间活跃排班索引：key = {@code employeeId@workDate}，value 按 id 升序的当日全部活跃排班 */
    private Map<String, List<AttendanceSchedule>> scheduleIndex(Long stationId, LocalDate start, LocalDate end) {
        return scheduleIndex(stationId, start, end, null);
    }

    private Map<String, List<AttendanceSchedule>> scheduleIndex(Long stationId, LocalDate start, LocalDate end,
                                                                Long employeeId) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getId, AttendanceSchedule::getStationId, AttendanceSchedule::getEmployeeId,
                        AttendanceSchedule::getWorkDate, AttendanceSchedule::getShiftId)
                .between(AttendanceSchedule::getWorkDate, start, end)
                .orderByAsc(AttendanceSchedule::getId);
        if (stationId != null) {
            wrapper.eq(AttendanceSchedule::getStationId, stationId);
        }
        if (employeeId != null) {
            wrapper.eq(AttendanceSchedule::getEmployeeId, employeeId);
        }
        Map<String, List<AttendanceSchedule>> index = new HashMap<>();
        for (AttendanceSchedule schedule : attendanceScheduleMapper.selectList(wrapper)) {
            index.computeIfAbsent(key(schedule.getEmployeeId(), schedule.getWorkDate()), k -> new ArrayList<>())
                    .add(schedule);
        }
        return index;
    }

    /** 批量取索引内全部班次实体（我的排班出参用，避免逐条查询） */
    private Map<Long, AttendanceShift> loadShiftMap(Collection<List<AttendanceSchedule>> groups) {
        Set<Long> ids = new HashSet<>();
        for (List<AttendanceSchedule> rows : groups) {
            for (AttendanceSchedule schedule : rows) {
                if (schedule.getShiftId() != null) {
                    ids.add(schedule.getShiftId());
                }
            }
        }
        Map<Long, AttendanceShift> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (AttendanceShift shift : attendanceShiftMapper.selectBatchIds(ids)) {
                map.put(shift.getId(), shift);
            }
        }
        return map;
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
    private Integer[][] buildFixedMatrix(List<Employee> staff, List<LocalDate> dates, List<AttendanceShift> shifts,
                                         Map<String, List<AttendanceSchedule>> index) {
        Map<Long, Integer> shiftIndex = new HashMap<>();
        for (int i = 0; i < shifts.size(); i++) {
            shiftIndex.put(shifts.get(i).getId(), i);
        }
        Integer[][] fixed = new Integer[staff.size()][dates.size()];
        for (int e = 0; e < staff.size(); e++) {
            for (int d = 0; d < dates.size(); d++) {
                List<AttendanceSchedule> rows = index.getOrDefault(key(staff.get(e).getId(), dates.get(d)), List.of());
                if (!rows.isEmpty()) {
                    // 单班次解空间：取首条班次下标
                    fixed[e][d] = shiftIndex.getOrDefault(rows.get(0).getShiftId(), -1);
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
