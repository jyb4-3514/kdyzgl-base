package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.PageResult;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.attendance.AttendanceCheckInRequest;
import com.qiujie.dto.attendance.AttendanceDetailQuery;
import com.qiujie.dto.attendance.AttendanceRecordQuery;
import com.qiujie.dto.attendance.AttendanceSummaryQuery;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.AttendanceSchedule;
import com.qiujie.entity.AttendanceShift;
import com.qiujie.entity.Employee;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.AttendanceScheduleMapper;
import com.qiujie.mapper.AttendanceShiftMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceRecordService;
import com.qiujie.service.attendance.AttendanceRuleService;
import com.qiujie.service.attendance.AttendanceShiftService;
import com.qiujie.service.attendance.support.AttendanceCard;
import com.qiujie.service.attendance.support.AttendanceCheckPolicy;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.attendance.support.AttendanceDetailPolicy;
import com.qiujie.service.attendance.support.AttendancePeriodResolver;
import com.qiujie.service.attendance.support.AttendanceSummaryPolicy;
import com.qiujie.service.attendance.support.AttendanceSupport;
import com.qiujie.service.attendance.support.CsvSupport;
import com.qiujie.service.support.geo.GeoService;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.AttendanceDetailVO;
import com.qiujie.vo.attendance.AttendanceRecordVO;
import com.qiujie.vo.attendance.AttendanceRuleVO;
import com.qiujie.vo.attendance.AttendanceStatusVO;
import com.qiujie.vo.attendance.AttendanceSummaryVO;
import com.qiujie.vo.attendance.MyAttendanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 打卡记录服务实现（Mock {@code attendanceStore} 记录/打卡段 + {@code routes/attendance.js}）。
 * <p>
 * 打卡判定链顺序不得变更：规则 → 时段/班次 → 时间窗 → 重复 → 校验项（WiFi/定位，ALL/ANY）→ 迟到/早退。
 * 其中「规则未配 9101 / 时段不存在 9107 / 班次停用 9106 / 重复 9105」在 Service 前置判定，
 * 「时间窗 9102 / WiFi 9103 / 定位 9104 / 迟到早退」由 {@link AttendanceCheckPolicy} 纯计算。
 * <p>
 * {@code checkIn} 刻意<b>不加事务</b>：校验未通过的尝试必须落一条 ABNORMAL 留痕（Mock 口径），
 * 若包在 {@code @Transactional(rollbackFor=Exception.class)} 里会随异常回滚而丢失留痕。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceRecordServiceImpl implements AttendanceRecordService {

    /** 记录导出列数（员工姓名…备注共 13 列，与 Mock 表头一致） */
    private static final int EXPORT_COLUMN_COUNT = 13;

    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceScheduleMapper attendanceScheduleMapper;
    private final AttendanceShiftMapper attendanceShiftMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final AttendanceRuleService attendanceRuleService;
    private final AttendanceShiftService attendanceShiftService;
    /** 地理端口（M2 接入）：围栏距离计算走端口，默认/降级实现为 Haversine，行为与改造前等价 */
    private final GeoService geoService;
    /** 算法参数：班次序号界值 / 历史哨兵 / 迟到粒度 / 应到-缺卡粒度（B7b 班次口径） */
    private final AlgoProperties algoProperties;

    // ==================== 记录列表 / 导出 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<AttendanceRecordVO> records(AttendanceRecordQuery query) {
        validateRecordFilter(query.getStatus(), query.getStartDate(), query.getEndDate());
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();

        LambdaQueryWrapper<AttendanceRecord> wrapper = recordFilter(query.getStationId(), query.getEmployeeId(),
                query.getStatus(), query.getStartDate(), query.getEndDate());
        wrapper.orderByDesc(AttendanceRecord::getCheckTime).orderByDesc(AttendanceRecord::getId);

        Page<AttendanceRecord> page = attendanceRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    @Override
    @Transactional(readOnly = true)
    public CsvExport export(AttendanceRecordQuery query) {
        // 导出与列表共用同一筛选口径，但不分页（全量）
        validateRecordFilter(query.getStatus(), query.getStartDate(), query.getEndDate());
        LambdaQueryWrapper<AttendanceRecord> wrapper = recordFilter(query.getStationId(), query.getEmployeeId(),
                query.getStatus(), query.getStartDate(), query.getEndDate());
        wrapper.orderByDesc(AttendanceRecord::getCheckTime).orderByDesc(AttendanceRecord::getId);
        List<AttendanceRecord> rows = attendanceRecordMapper.selectList(wrapper);

        List<List<String>> csv = new ArrayList<>();
        csv.add(new ArrayList<>(List.of("员工姓名", "登录账号", "所属驿站", "日期", "时段名称", "卡类型", "打卡时间",
                "打卡方式", "WiFi", "距离(米)", "状态", "来源", "备注")));
        for (AttendanceRecord r : rows) {
            Employee employee = employeeMapper.selectById(r.getEmployeeId());
            List<String> line = new ArrayList<>(EXPORT_COLUMN_COUNT);
            line.add(employee == null || employee.getRealName() == null ? "" : employee.getRealName());
            line.add(employee == null || employee.getUsername() == null ? "" : employee.getUsername());
            line.add(stationNameOrEmpty(r.getStationId()));
            line.add(AttendanceSupport.formatDate(r.getWorkDate()));
            line.add(r.getPeriodName() == null ? "" : r.getPeriodName());
            line.add(exportCheckType(r.getCheckType()));
            line.add(AttendanceSupport.formatDateTime(r.getCheckTime()));
            line.add(exportCheckMode(r.getCheckMode()));
            line.add(r.getWifiSsid() == null ? "" : r.getWifiSsid());
            line.add(r.getDistance() == null ? "" : r.getDistance().stripTrailingZeros().toPlainString());
            line.add(exportStatus(r.getStatus()));
            line.add(exportSource(r.getSource()));
            line.add(r.getRemark() == null ? "" : r.getRemark());
            csv.add(line);
        }
        String filename = "考勤记录_" + AttendanceSupport.formatDate(AttendanceSupport.today()).replace("-", "") + ".csv";
        return new CsvExport(CsvSupport.toCsvBytes(csv), filename);
    }

    // ==================== 概况 / 明细 ====================

    @Override
    @Transactional(readOnly = true)
    public AttendanceSummaryVO summary(AttendanceSummaryQuery query) {
        if (!AttendanceSupport.isDate(query.getDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "date 格式须为 YYYY-MM-DD");
        }
        LocalDate workDate = (query.getDate() == null || query.getDate().isBlank())
                ? AttendanceSupport.today()
                : AttendanceSupport.parseDate(query.getDate());
        Long stationId = query.getStationId();

        List<AttendanceSchedule> schedules = scheduleRows(stationId, workDate);
        List<AttendanceRecord> rows = recordRows(stationId, workDate, null);

        AttendanceSummaryPolicy.Summary s;
        if (AttendanceConstants.ABSENT_GRANULARITY_PER_DAY.equals(
                algoProperties.getAttendance().getAbsentGranularity())) {
            // 回落开关：旧「按人/天去重」口径（应到 = 排班人数、实到 = 有效上班卡员工去重）
            s = AttendanceSummaryPolicy.summarize(schedules.size(),
                    cardsOf(rows, AttendanceConstants.CHECK_TYPE_ON),
                    cardsOf(rows, AttendanceConstants.CHECK_TYPE_OFF));
        } else {
            // B7b 默认：应到/实到/缺卡按班次粒度；班次单元与记录→班次映射复用计薪唯一真源
            Map<Long, String> shiftStartTimes = shiftStartTimes(schedules);
            List<AttendanceSummaryPolicy.ScheduleSlot> slots = new ArrayList<>(schedules.size());
            for (AttendanceSchedule schedule : schedules) {
                if (schedule.getEmployeeId() != null) {
                    slots.add(new AttendanceSummaryPolicy.ScheduleSlot(schedule.getEmployeeId(),
                            shiftStartTimes.get(schedule.getShiftId())));
                }
            }
            List<AttendanceSummaryPolicy.RecordSlot> recordSlots = new ArrayList<>(rows.size());
            for (AttendanceRecord row : rows) {
                if (row.getEmployeeId() != null) {
                    recordSlots.add(new AttendanceSummaryPolicy.RecordSlot(row.getEmployeeId(),
                            row.getPeriodIndex(), row.getPeriodName(), row.getCheckType(), row.getStatus()));
                }
            }
            s = AttendanceSummaryPolicy.summarizeByShift(workDate, slots, recordSlots,
                    algoProperties.getPayroll().getMiddayBoundaryMinute(),
                    algoProperties.getPayroll().getLegacyPeriodSentinel(),
                    algoProperties.getPayroll().getLateGranularity());
        }

        AttendanceSummaryVO vo = new AttendanceSummaryVO();
        vo.setDate(workDate);
        vo.setShouldCount(s.shouldCount());
        vo.setActualCount(s.actualCount());
        vo.setNormalCount(s.normalCount());
        vo.setLateCount(s.lateCount());
        vo.setEarlyLeaveCount(s.earlyLeaveCount());
        vo.setAbsentCount(s.absentCount());
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceDetailVO detail(AttendanceDetailQuery query) {
        if (!AttendanceConstants.DETAIL_DIMS.contains(query.getDim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "dim 取值非法");
        }
        if (!AttendanceSupport.isDate(query.getDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "date 格式须为 YYYY-MM-DD");
        }
        LocalDate workDate = (query.getDate() == null || query.getDate().isBlank())
                ? AttendanceSupport.today()
                : AttendanceSupport.parseDate(query.getDate());
        Long stationId = query.getStationId();

        List<AttendanceSchedule> schedules = scheduleRows(stationId, workDate);
        Map<Long, String> shiftNames = shiftNames(schedules);
        Map<Long, String> shiftStartTimes = shiftStartTimes(schedules);
        List<AttendanceDetailPolicy.Member> shouldRows = new ArrayList<>(schedules.size());
        for (AttendanceSchedule schedule : schedules) {
            // 缺卡按班次粒度判定，应到行须带班次开始时间（派生班次单元）
            shouldRows.add(new AttendanceDetailPolicy.Member(schedule.getEmployeeId(),
                    shiftNames.get(schedule.getShiftId()), shiftStartTimes.get(schedule.getShiftId())));
        }

        List<AttendanceRecord> rows = recordRows(stationId, workDate, null);
        List<AttendanceCard> validOn = AttendanceSummaryPolicy.validCards(cardsOf(rows, AttendanceConstants.CHECK_TYPE_ON));
        List<AttendanceCard> validOff = AttendanceSummaryPolicy.validCards(cardsOf(rows, AttendanceConstants.CHECK_TYPE_OFF));

        // 缺卡（ABSENT）按班次粒度、与概况同源；其余维度仍按卡状态过滤去重（members 不含 ABSENT）
        List<AttendanceDetailPolicy.Member> members = "ABSENT".equals(query.getDim())
                ? AttendanceDetailPolicy.absentMembers(workDate, shouldRows, validOnRecords(rows),
                        algoProperties.getPayroll().getMiddayBoundaryMinute(),
                        algoProperties.getPayroll().getLegacyPeriodSentinel())
                : AttendanceDetailPolicy.members(query.getDim(), shouldRows, validOn, validOff);

        Set<Long> employeeIds = new LinkedHashSet<>();
        for (AttendanceDetailPolicy.Member member : members) {
            employeeIds.add(member.employeeId());
        }
        Map<Long, Employee> employees = loadEmployees(employeeIds);

        List<AttendanceDetailVO.Row> list = new ArrayList<>(members.size());
        for (AttendanceDetailPolicy.Member member : members) {
            list.add(buildDetailRow(member, employees, validOn, validOff));
        }
        sortDetailRows(query.getDim(), list);

        AttendanceDetailVO vo = new AttendanceDetailVO();
        vo.setDim(query.getDim());
        vo.setDate(workDate);
        vo.setTotal(list.size());
        vo.setList(list);
        return vo;
    }

    // ==================== 我的 / 今日状态 ====================

    @Override
    @Transactional(readOnly = true)
    public MyAttendanceVO mine(Long employeeId, String month) {
        if (!AttendanceSupport.isMonth(month)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式须为 YYYY-MM");
        }
        String prefix = (month == null || month.isBlank()) ? AttendanceSupport.currentMonth() : month;
        LocalDate start = AttendanceSupport.parseDate(prefix + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceRecord::getEmployeeId, employeeId)
                .between(AttendanceRecord::getWorkDate, start, end)
                .orderByDesc(AttendanceRecord::getCheckTime).orderByDesc(AttendanceRecord::getId);
        List<AttendanceRecord> rows = attendanceRecordMapper.selectList(wrapper);

        MyAttendanceVO vo = new MyAttendanceVO();
        vo.setMonth(prefix);
        vo.setList(toVOList(rows));
        Employee employee = employeeId == null ? null : employeeMapper.selectById(employeeId);
        vo.setTodayStatus(employee == null ? null : todayStatus(employee, employee.getStationId()));
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceStatusVO status() {
        Long userId = currentUserId();
        Employee employee = employeeMapper.selectById(userId);
        if (employee == null) {
            // 已认证端点必有员工；防御性兜底避免空归属造成规则误配
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return todayStatus(employee, employee.getStationId());
    }

    // ==================== 打卡 ====================

    @Override
    public AttendanceRecordVO checkIn(AttendanceCheckInRequest request) {
        AttendanceCheckInRequest safe = request == null ? new AttendanceCheckInRequest() : request;
        Long employeeId = currentUserId();
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String checkType = safe.getCheckType();
        if (!AttendanceConstants.CHECK_TYPE_ON.equals(checkType)
                && !AttendanceConstants.CHECK_TYPE_OFF.equals(checkType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "checkType 仅支持 ON / OFF");
        }
        // periodIndex 非整数与越界同处理（9107）：前端只可能是索引对不上，不再分码
        Integer periodIndex = safe.getPeriodIndex();
        if (periodIndex != null && periodIndex < 0) {
            throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND);
        }

        Long stationId = scopedStationId(safe.getStationId());
        AttendanceRule rule = attendanceRuleService.findRule(stationId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED);
        }

        LocalDate workDate = AttendanceSupport.today();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int nowMinutes = now.getHour() * 60 + now.getMinute();

        // 时段真源 = 该驿站启用班次（不再读 rule.checkPeriods，方案 §4 / U-1 站点级）
        List<AttendancePeriodResolver.ResolvedPeriod> periods = derivedPeriods(stationId);
        boolean usePeriod = periodIndex != null;
        AttendancePeriodResolver.ResolvedPeriod period;
        if (usePeriod) {
            // M1：按 ordinal 按值查找，禁止 `periods.get(periodIndex)` 下标取值（单班次晚班站点会越界）
            if (periods.isEmpty()) {
                throw new BusinessException(ErrorCode.ATTENDANCE_NO_ENABLED_SHIFT);
            }
            period = AttendancePeriodResolver.findByOrdinal(periods, periodIndex);
            if (period == null) {
                throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND);
            }
        } else {
            // 单班次模型：取排班班次；无排班 → 首个启用班次（U-2：员工无排班仍可打卡）；无启用班次 → 9113
            AttendanceSchedule schedule = findSchedule(employeeId, workDate);
            if (schedule != null) {
                AttendanceShift shift = attendanceShiftMapper.selectById(schedule.getShiftId());
                if (shift == null || shift.getStatus() == null || shift.getStatus() != 1) {
                    throw new BusinessException(ErrorCode.ATTENDANCE_SHIFT_UNAVAILABLE);
                }
                period = new AttendancePeriodResolver.ResolvedPeriod(
                        AttendancePeriodResolver.shiftOrdinal(shift.getStartTime(), middayBoundaryMinute()),
                        shift.getShiftName(), shift.getStartTime(), shift.getEndTime());
            } else {
                if (periods.isEmpty()) {
                    throw new BusinessException(ErrorCode.ATTENDANCE_NO_ENABLED_SHIFT);
                }
                period = periods.get(0);
            }
        }
        double startMin = AttendancePeriodResolver.minutesOfDay(period.startTime());
        double endMin = AttendancePeriodResolver.minutesOfDay(period.endTime());

        AttendanceCheckPolicy.ClockFacts facts = new AttendanceCheckPolicy.ClockFacts(
                isEnabled(rule.getEnableTimeWindow()),
                usePeriod,
                startMin,
                endMin,
                nz(rule.getAllowEarlyMin()),
                nz(rule.getAllowLateMin()),
                checkType,
                nowMinutes,
                isEnabled(rule.getEnableWifi()),
                isEnabled(rule.getEnableLocation()),
                rule.getMatchMode(),
                rule.getWifiList() == null ? List.of()
                        : rule.getWifiList().stream().filter(java.util.Objects::nonNull)
                        .map(w -> w.getSsid()).filter(java.util.Objects::nonNull).toList(),
                safe.getWifiSsid(),
                toDouble(rule.getLongitude()),
                toDouble(rule.getLatitude()),
                toDouble(safe.getLongitude()),
                toDouble(safe.getLatitude()),
                rule.getRadius(),
                nz(rule.getLateThresholdMin()),
                nz(rule.getEarlyLeaveThresholdMin()));

        // 判定链顺序不得变更：时间窗（越窗直接短路、不留痕）→ 重复 → 校验项 → 迟到/早退
        if (AttendanceCheckPolicy.windowCode(facts) == 9102) {
            throw new BusinessException(ErrorCode.ATTENDANCE_OUT_OF_TIME_WINDOW);
        }
        // 去重粒度：时段模型按「员工 + 日期 + 时段 + 类型」；单班次模型不限定时段
        Integer duplicatePeriod = usePeriod ? periodIndex : null;
        if (validCard(employeeId, workDate, checkType, duplicatePeriod) != null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_DUPLICATE_CHECK);
        }

        // 围栏距离走地理端口（M2）：默认/降级为 Haversine，与改造前逐位等价；高德仅补逆地理编码（展示用）
        AttendanceCheckPolicy.ClockDecision decision = AttendanceCheckPolicy.evaluate(facts, geoService);

        // 记录快照：period_index = 命中班次序号（单班次模型取所用班次的 ordinal，避免与计薪班次单元错位）
        int recordPeriodIndex = usePeriod ? periodIndex : Math.max(period.periodIndex(), 0);
        String recordPeriodName = period.name();

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployeeId(employeeId);
        record.setStationId(stationId);
        record.setWorkDate(workDate);
        record.setPeriodIndex(recordPeriodIndex);
        record.setPeriodName(recordPeriodName);
        record.setCheckType(checkType);
        record.setCheckTime(now);
        record.setStatus(decision.status());
        record.setSource(AttendanceConstants.SOURCE_NORMAL);
        record.setCheckMode(decision.checkMode());
        record.setWifiSsid(safe.getWifiSsid());
        record.setWifiMatched(boolToInt(decision.wifiMatched()));
        record.setLongitude(safe.getLongitude());
        record.setLatitude(safe.getLatitude());
        record.setDistance(decision.distance() == null ? null : BigDecimal.valueOf(decision.distance()));
        record.setLocationMatched(boolToInt(decision.locationMatched()));
        record.setRemark(decision.remark());
        attendanceRecordMapper.insert(record);

        if (!decision.passed()) {
            // 校验未通过：留痕后按「首个未通过项」回码，便于前端给出针对性提示（9103 / 9104）
            throw decision.code() == 9103
                    ? new BusinessException(ErrorCode.ATTENDANCE_WIFI_MISMATCH)
                    : new BusinessException(ErrorCode.ATTENDANCE_LOCATION_MISMATCH);
        }
        return toVO(record, employee.getRealName());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasValidCard(Long employeeId, LocalDate workDate, String checkType, Integer periodIndex) {
        return validCard(employeeId, workDate, checkType, periodIndex) != null;
    }

    // ==================== 今日状态组装 ====================

    /** 今日打卡状态（对齐 Mock {@code todayStatus}）：今日班次 + 规则要求摘要 + 按时段展开的打卡项 */
    private AttendanceStatusVO todayStatus(Employee employee, Long stationId) {
        LocalDate workDate = AttendanceSupport.today();
        AttendanceRule rule = attendanceRuleService.findRule(stationId);
        // 时段真源 = 该驿站启用班次（站点级，U-1）；无启用班次 → periods 空、shiftConfigured=false
        List<AttendanceShift> enabledShifts = attendanceShiftService.enabledShifts(stationId);
        List<AttendancePeriodResolver.ResolvedPeriod> shiftPeriods = AttendancePeriodResolver.resolveByShifts(
                enabledShifts, middayBoundaryMinute());
        AttendanceSchedule schedule = findSchedule(employee.getId(), workDate);
        AttendanceShift scheduledShift = null;
        if (schedule != null && schedule.getShiftId() != null) {
            scheduledShift = attendanceShiftMapper.selectById(schedule.getShiftId());
        }
        AttendanceRecord onRecord = validCard(employee.getId(), workDate, AttendanceConstants.CHECK_TYPE_ON, null);
        AttendanceRecord offRecord = validCard(employee.getId(), workDate, AttendanceConstants.CHECK_TYPE_OFF, null);

        AttendanceStatusVO vo = new AttendanceStatusVO();
        vo.setWorkDate(workDate);
        vo.setHasSchedule(schedule != null);
        vo.setOnChecked(onRecord != null);
        vo.setOffChecked(offRecord != null);
        vo.setOnRecord(onRecord == null ? null : toVO(onRecord, employee.getRealName()));
        vo.setOffRecord(offRecord == null ? null : toVO(offRecord, employee.getRealName()));

        List<AttendanceStatusVO.PeriodStatus> periods = new ArrayList<>();
        if (rule != null) {
            for (AttendancePeriodResolver.ResolvedPeriod p : shiftPeriods) {
                AttendanceRecord periodOn = validCard(employee.getId(), workDate, AttendanceConstants.CHECK_TYPE_ON,
                        p.periodIndex());
                AttendanceRecord periodOff = validCard(employee.getId(), workDate, AttendanceConstants.CHECK_TYPE_OFF,
                        p.periodIndex());
                AttendanceStatusVO.PeriodStatus status = new AttendanceStatusVO.PeriodStatus();
                status.setPeriodIndex(p.periodIndex());
                status.setName(p.name());
                status.setStartTime(p.startTime());
                status.setEndTime(p.endTime());
                status.setWindowStart(AttendancePeriodResolver.clockOfMinutes(
                        AttendancePeriodResolver.minutesOfDay(p.startTime()) - nz(rule.getAllowEarlyMin())));
                status.setWindowEnd(AttendancePeriodResolver.clockOfMinutes(
                        AttendancePeriodResolver.minutesOfDay(p.endTime()) + nz(rule.getAllowLateMin())));
                status.setOnChecked(periodOn != null);
                status.setOffChecked(periodOff != null);
                status.setOnTime(periodOn == null ? null : periodOn.getCheckTime());
                status.setOffTime(periodOff == null ? null : periodOff.getCheckTime());
                periods.add(status);
            }
        }
        vo.setPeriods(periods);
        vo.setShiftConfigured(!enabledShifts.isEmpty());
        // 频次只读派生 = 启用班次数 × 2（无启用班次 → 0）；无规则时不返回频次（对齐既有）
        Integer frequency = shiftPeriods.size() * 2;
        vo.setCheckFrequency(rule == null ? null : frequency);
        vo.setRequireSummary(rule == null ? null : rule.getRuleName() + "｜每日 " + frequency
                + " 次打卡（" + String.join("、", periods.stream().map(AttendanceStatusVO.PeriodStatus::getName).toList())
                + "）");
        // 未排班时返回「首个启用班次」派生的兜底班次；无启用班次 → null（前端置空态）
        if (scheduledShift != null && scheduledShift.getStatus() != null && scheduledShift.getStatus() == 1) {
            vo.setShift(attendanceShiftService.toVO(scheduledShift));
        } else {
            vo.setShift(rule == null ? null : attendanceShiftService.defaultShift(rule));
        }
        AttendanceRuleVO ruleVO = rule == null ? null : attendanceRuleService.toVO(rule);
        vo.setRule(ruleVO);
        return vo;
    }

    // ==================== 私有：查询与组装 ====================

    private void validateRecordFilter(String status, String startDate, String endDate) {
        if (status != null && !status.isBlank() && !AttendanceConstants.RECORD_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }
        if (!AttendanceSupport.isDate(startDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate 格式须为 YYYY-MM-DD");
        }
        if (!AttendanceSupport.isDate(endDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "endDate 格式须为 YYYY-MM-DD");
        }
    }

    /** 记录筛选条件（列表与导出共用一套口径） */
    private LambdaQueryWrapper<AttendanceRecord> recordFilter(Long stationId, Long employeeId, String status,
                                                              String startDate, String endDate) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceRecord::getId, AttendanceRecord::getEmployeeId, AttendanceRecord::getStationId,
                AttendanceRecord::getWorkDate, AttendanceRecord::getPeriodIndex, AttendanceRecord::getPeriodName,
                AttendanceRecord::getCheckType, AttendanceRecord::getCheckTime, AttendanceRecord::getStatus,
                AttendanceRecord::getSource, AttendanceRecord::getCheckMode, AttendanceRecord::getWifiSsid,
                AttendanceRecord::getWifiMatched, AttendanceRecord::getLongitude, AttendanceRecord::getLatitude,
                AttendanceRecord::getDistance, AttendanceRecord::getLocationMatched, AttendanceRecord::getRemark);
        if (stationId != null) {
            wrapper.eq(AttendanceRecord::getStationId, stationId);
        }
        if (employeeId != null) {
            wrapper.eq(AttendanceRecord::getEmployeeId, employeeId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AttendanceRecord::getStatus, status);
        }
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge(AttendanceRecord::getWorkDate, AttendanceSupport.parseDate(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le(AttendanceRecord::getWorkDate, AttendanceSupport.parseDate(endDate));
        }
        return wrapper;
    }

    private List<AttendanceSchedule> scheduleRows(Long stationId, LocalDate workDate) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getId, AttendanceSchedule::getEmployeeId, AttendanceSchedule::getShiftId)
                .eq(AttendanceSchedule::getWorkDate, workDate);
        if (stationId != null) {
            wrapper.eq(AttendanceSchedule::getStationId, stationId);
        }
        return attendanceScheduleMapper.selectList(wrapper);
    }

    private List<AttendanceRecord> recordRows(Long stationId, LocalDate workDate, String checkType) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceRecord::getId, AttendanceRecord::getEmployeeId, AttendanceRecord::getCheckType,
                        AttendanceRecord::getStatus, AttendanceRecord::getCheckTime, AttendanceRecord::getPeriodName,
                        AttendanceRecord::getPeriodIndex, AttendanceRecord::getRemark)
                .eq(AttendanceRecord::getWorkDate, workDate);
        if (stationId != null) {
            wrapper.eq(AttendanceRecord::getStationId, stationId);
        }
        if (checkType != null) {
            wrapper.eq(AttendanceRecord::getCheckType, checkType);
        }
        return attendanceRecordMapper.selectList(wrapper);
    }

    private List<AttendanceCard> cardsOf(List<AttendanceRecord> rows, String checkType) {
        List<AttendanceCard> cards = new ArrayList<>();
        for (AttendanceRecord row : rows) {
            if (checkType.equals(row.getCheckType())) {
                cards.add(new AttendanceCard(row.getEmployeeId(), row.getCheckType(), row.getStatus(),
                        row.getCheckTime(), row.getPeriodName(), row.getRemark()));
            }
        }
        return cards;
    }

    /**
     * 有效上班卡槽（{@code check_type=ON} 且非 ABNORMAL）：缺卡班次粒度判定用。
     * <p>
     * 与概况共用 {@link AttendanceSummaryPolicy.RecordSlot}（含 periodIndex/periodName/status），
     * 供「记录 → 班次」三态映射，避免明细另建一套映射载体。
     */
    private List<AttendanceSummaryPolicy.RecordSlot> validOnRecords(List<AttendanceRecord> rows) {
        List<AttendanceSummaryPolicy.RecordSlot> slots = new ArrayList<>();
        for (AttendanceRecord row : rows) {
            if (row.getEmployeeId() == null
                    || !AttendanceConstants.CHECK_TYPE_ON.equals(row.getCheckType())
                    || !AttendanceConstants.isValidCard(row.getStatus())) {
                continue;
            }
            slots.add(new AttendanceSummaryPolicy.RecordSlot(row.getEmployeeId(), row.getPeriodIndex(),
                    row.getPeriodName(), row.getCheckType(), row.getStatus()));
        }
        return slots;
    }

    private AttendanceSchedule findSchedule(Long employeeId, LocalDate workDate) {
        LambdaQueryWrapper<AttendanceSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceSchedule::getId, AttendanceSchedule::getShiftId)
                .eq(AttendanceSchedule::getEmployeeId, employeeId)
                .eq(AttendanceSchedule::getWorkDate, workDate)
                .orderByAsc(AttendanceSchedule::getId);
        List<AttendanceSchedule> list = attendanceScheduleMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 有效卡（非 ABNORMAL）：periodIndex 为 null 表示不限定时段 */
    private AttendanceRecord validCard(Long employeeId, LocalDate workDate, String checkType, Integer periodIndex) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceRecord::getEmployeeId, employeeId)
                .eq(AttendanceRecord::getWorkDate, workDate)
                .eq(AttendanceRecord::getCheckType, checkType)
                .ne(AttendanceRecord::getStatus, AttendanceConstants.STATUS_ABNORMAL)
                .orderByAsc(AttendanceRecord::getId);
        if (periodIndex != null) {
            wrapper.eq(AttendanceRecord::getPeriodIndex, periodIndex);
        }
        List<AttendanceRecord> list = attendanceRecordMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<Long, String> shiftNames(List<AttendanceSchedule> schedules) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceSchedule schedule : schedules) {
            if (schedule.getShiftId() != null) {
                ids.add(schedule.getShiftId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceShift::getId, AttendanceShift::getShiftName).in(AttendanceShift::getId, ids);
        for (AttendanceShift shift : attendanceShiftMapper.selectList(wrapper)) {
            names.put(shift.getId(), shift.getShiftName());
        }
        return names;
    }

    /** 班次开始时间（shiftId → start_time）：班次粒度「应到」的 shiftOrdinal 输入 */
    private Map<Long, String> shiftStartTimes(List<AttendanceSchedule> schedules) {
        Set<Long> ids = new HashSet<>();
        for (AttendanceSchedule schedule : schedules) {
            if (schedule.getShiftId() != null) {
                ids.add(schedule.getShiftId());
            }
        }
        Map<Long, String> startTimes = new HashMap<>();
        if (ids.isEmpty()) {
            return startTimes;
        }
        LambdaQueryWrapper<AttendanceShift> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceShift::getId, AttendanceShift::getStartTime).in(AttendanceShift::getId, ids);
        for (AttendanceShift shift : attendanceShiftMapper.selectList(wrapper)) {
            startTimes.put(shift.getId(), shift.getStartTime());
        }
        return startTimes;
    }

    private Map<Long, Employee> loadEmployees(Set<Long> ids) {
        Map<Long, Employee> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName, Employee::getStationId).in(Employee::getId, ids);
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            map.put(employee.getId(), employee);
        }
        return map;
    }

    private AttendanceDetailVO.Row buildDetailRow(AttendanceDetailPolicy.Member member,
                                                   Map<Long, Employee> employees,
                                                   List<AttendanceCard> validOn, List<AttendanceCard> validOff) {
        Employee employee = employees.get(member.employeeId());
        AttendanceCard on = AttendanceDetailPolicy.earliest(validOn, member.employeeId());
        AttendanceCard off = AttendanceDetailPolicy.earliest(validOff, member.employeeId());
        AttendanceCard representative = on != null ? on : off;

        AttendanceDetailVO.Row row = new AttendanceDetailVO.Row();
        row.setEmployeeId(member.employeeId());
        row.setEmployeeName(employee == null
                ? "员工 #" + member.employeeId()
                : (employee.getRealName() == null ? "" : employee.getRealName()));
        row.setStationId(employee == null ? null : employee.getStationId());
        row.setStationName(employee == null ? "" : stationNameOrEmpty(employee.getStationId()));
        row.setShiftName(member.shiftName());
        row.setPeriodName(representative == null ? null : representative.periodName());
        row.setOnCheck(toCheckInfo(on));
        row.setOffCheck(toCheckInfo(off));
        row.setDayState(AttendanceDetailPolicy.dayState(on, off));
        row.setRemark(representative == null ? null : representative.remark());
        return row;
    }

    private AttendanceDetailVO.CheckInfo toCheckInfo(AttendanceCard card) {
        if (card == null) {
            return null;
        }
        AttendanceDetailVO.CheckInfo info = new AttendanceDetailVO.CheckInfo();
        info.setTime(card.checkTime());
        info.setStatus(card.status());
        return info;
    }

    /**
     * 明细排序（对齐 Mock §14.6.2）：迟到/早退按命中卡时间倒序，缺卡按姓名，应到按风险优先，实到/正常按上班卡时间倒序。
     */
    private void sortDetailRows(String dim, List<AttendanceDetailVO.Row> rows) {
        Comparator<AttendanceDetailVO.Row> byName = (a, b) -> AttendanceSupport.chineseCollator()
                .compare(a.getEmployeeName(), b.getEmployeeName());
        if ("ABSENT".equals(dim)) {
            rows.sort(byName);
        } else if ("SHOULD".equals(dim)) {
            rows.sort(Comparator.comparingInt((AttendanceDetailVO.Row r) -> AttendanceDetailPolicy.riskRank(r.getDayState()))
                    .thenComparing(byName));
        } else {
            rows.sort((a, b) -> hitTime(b).compareTo(hitTime(a)));
        }
    }

    private String hitTime(AttendanceDetailVO.Row row) {
        if (row.getOnCheck() != null) {
            return AttendanceSupport.formatDateTime(row.getOnCheck().getTime());
        }
        if (row.getOffCheck() != null) {
            return AttendanceSupport.formatDateTime(row.getOffCheck().getTime());
        }
        return "";
    }

    private List<AttendanceRecordVO> toVOList(List<AttendanceRecord> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> employeeIds = new LinkedHashSet<>();
        for (AttendanceRecord row : rows) {
            employeeIds.add(row.getEmployeeId());
        }
        Map<Long, Employee> employees = loadEmployees(employeeIds);
        List<AttendanceRecordVO> list = new ArrayList<>(rows.size());
        for (AttendanceRecord row : rows) {
            Employee employee = employees.get(row.getEmployeeId());
            list.add(toVO(row, employee == null ? null : employee.getRealName()));
        }
        return list;
    }

    private AttendanceRecordVO toVO(AttendanceRecord record, String employeeName) {
        AttendanceRecordVO vo = new AttendanceRecordVO();
        vo.setId(record.getId());
        vo.setEmployeeId(record.getEmployeeId());
        vo.setEmployeeName(employeeName);
        vo.setStationId(record.getStationId());
        vo.setWorkDate(record.getWorkDate());
        vo.setPeriodIndex(record.getPeriodIndex());
        vo.setPeriodName(record.getPeriodName());
        vo.setCheckType(record.getCheckType());
        vo.setCheckTime(record.getCheckTime());
        vo.setStatus(record.getStatus());
        vo.setSource(record.getSource());
        vo.setCheckMode(record.getCheckMode());
        vo.setWifiSsid(record.getWifiSsid());
        vo.setWifiMatched(record.getWifiMatched() == null ? null : record.getWifiMatched() == 1);
        vo.setLongitude(record.getLongitude());
        vo.setLatitude(record.getLatitude());
        vo.setDistance(record.getDistance());
        vo.setLocationMatched(record.getLocationMatched() == null ? null : record.getLocationMatched() == 1);
        vo.setRemark(record.getRemark());
        return vo;
    }

    /** 打卡驿站的归属收敛：ADMIN 取入参；非 ADMIN 强制本人驿站（防代他人向别的驿站打卡） */
    private Long scopedStationId(Long rawStationId) {
        if ("ADMIN".equals(UserContext.getRole())) {
            return rawStationId;
        }
        String stationId = UserContext.getStationId();
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            log.error("会话中的 stationId 非法，打卡驿站收敛为无归属：stationId={}", stationId);
            return null;
        }
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String stationNameOrEmpty(Long stationId) {
        if (stationId == null) {
            return "";
        }
        var station = stationMapper.selectById(stationId);
        return station == null || station.getStationName() == null ? "" : station.getStationName();
    }

    private boolean isEnabled(Integer flag) {
        return flag != null && flag == 1;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    /** 班次序号界值（{@code hrm.algo.payroll.middayBoundaryMinute}，默认 720） */
    private int middayBoundaryMinute() {
        return algoProperties.getPayroll().getMiddayBoundaryMinute();
    }

    /** 该驿站启用班次派生的打卡时段（时段真源；站点级，U-1） */
    private List<AttendancePeriodResolver.ResolvedPeriod> derivedPeriods(Long stationId) {
        return AttendancePeriodResolver.resolveByShifts(
                attendanceShiftService.enabledShifts(stationId), middayBoundaryMinute());
    }

    private Integer boolToInt(boolean value) {
        return value ? 1 : 0;
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String exportCheckType(String value) {
        return AttendanceConstants.CHECK_TYPE_ON.equals(value) ? "上班卡"
                : AttendanceConstants.CHECK_TYPE_OFF.equals(value) ? "下班卡" : (value == null ? "" : value);
    }

    private String exportStatus(String value) {
        if (AttendanceConstants.STATUS_NORMAL.equals(value)) {
            return "正常";
        }
        if (AttendanceConstants.STATUS_LATE.equals(value)) {
            return "迟到";
        }
        if (AttendanceConstants.STATUS_EARLY_LEAVE.equals(value)) {
            return "早退";
        }
        if (AttendanceConstants.STATUS_ABNORMAL.equals(value)) {
            return "异常";
        }
        return value == null ? "" : value;
    }

    private String exportCheckMode(String value) {
        if (AttendanceConstants.CHECK_MODE_WIFI.equals(value)) {
            return "WiFi";
        }
        if (AttendanceConstants.CHECK_MODE_LOCATION.equals(value)) {
            return "定位";
        }
        if (AttendanceConstants.CHECK_MODE_BOTH.equals(value)) {
            return "WiFi+定位";
        }
        return "";
    }

    private String exportSource(String value) {
        if (AttendanceConstants.SOURCE_NORMAL.equals(value)) {
            return "正常打卡";
        }
        if (AttendanceConstants.SOURCE_MAKEUP.equals(value)) {
            return "补卡";
        }
        return value == null ? "" : value;
    }
}
