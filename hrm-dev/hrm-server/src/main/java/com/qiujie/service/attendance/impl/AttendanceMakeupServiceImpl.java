package com.qiujie.service.attendance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qiujie.common.PageResult;
import com.qiujie.dto.attendance.AttendanceMakeupApproveRequest;
import com.qiujie.dto.attendance.AttendanceMakeupMineQuery;
import com.qiujie.dto.attendance.AttendanceMakeupQuery;
import com.qiujie.dto.attendance.AttendanceMakeupRequest;
import com.qiujie.entity.AttendanceMakeup;
import com.qiujie.entity.AttendanceRecord;
import com.qiujie.entity.AttendanceRule;
import com.qiujie.entity.Employee;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.AttendanceMakeupMapper;
import com.qiujie.mapper.AttendanceRecordMapper;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.attendance.AttendanceMakeupService;
import com.qiujie.service.attendance.AttendanceRecordService;
import com.qiujie.service.attendance.AttendanceRuleService;
import com.qiujie.service.attendance.support.AttendanceConstants;
import com.qiujie.service.attendance.support.AttendancePeriodResolver;
import com.qiujie.service.attendance.support.AttendanceSupport;
import com.qiujie.util.UserContext;
import com.qiujie.vo.attendance.AttendanceMakeupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 补卡服务实现（Mock {@code attendanceStore} 补卡段 + {@code routes/attendance.js}）。
 * <p>
 * 审批通过 → 补录打卡记录：打卡时间取该时段规定时间（上班卡取开始、下班卡取结束）；
 * 校验项不是设备打卡产生的，统一置 null 而非伪造命中值（前端按 {@code source=MAKEUP} 区分）。
 * 时段被改配置导致原时段不存在时<b>拒绝通过</b>（否则会落下「审批通过却无打卡记录」的矛盾数据）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceMakeupServiceImpl implements AttendanceMakeupService {

    private final AttendanceMakeupMapper attendanceMakeupMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final AttendanceRuleService attendanceRuleService;
    private final AttendanceRecordService attendanceRecordService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<AttendanceMakeupVO> mine(Long employeeId, AttendanceMakeupMineQuery query) {
        validateFilter(query.getStatus(), query.getStartDate(), query.getEndDate());
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        LambdaQueryWrapper<AttendanceMakeup> wrapper = baseFilter(null, employeeId, query.getStatus(),
                query.getStartDate(), query.getEndDate());
        return page(wrapper, pageNum, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AttendanceMakeupVO> list(AttendanceMakeupQuery query) {
        validateFilter(query.getStatus(), query.getStartDate(), query.getEndDate());
        int pageNum = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        LambdaQueryWrapper<AttendanceMakeup> wrapper = baseFilter(query.getStationId(), null, query.getStatus(),
                query.getStartDate(), query.getEndDate());
        return page(wrapper, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceMakeupVO apply(AttendanceMakeupRequest request) {
        AttendanceMakeupRequest safe = request == null ? new AttendanceMakeupRequest() : request;
        Employee employee = employeeMapper.selectById(currentUserId());
        if (employee == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Long stationId = employee.getStationId();
        if (stationId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号未归属驿站，无法提交补卡");
        }
        if (!AttendanceSupport.isStrictDate(safe.getWorkDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "workDate 格式须为 YYYY-MM-DD");
        }
        // 契约未写「不能补未来日期」，但补未来日期在业务上不成立，按常识加护栏
        if (safe.getWorkDate().compareTo(AttendanceSupport.formatDate(AttendanceSupport.today())) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "补卡日期不能晚于今天");
        }
        String checkType = safe.getCheckType();
        if (!AttendanceConstants.CHECK_TYPE_ON.equals(checkType)
                && !AttendanceConstants.CHECK_TYPE_OFF.equals(checkType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "checkType 仅支持 ON / OFF");
        }
        if (!AttendanceSupport.textLen(safe.getReason(), 2, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "补卡理由长度须为 2-200 字");
        }
        Integer periodIndex = safe.getPeriodIndex();
        if (periodIndex == null || periodIndex < 0) {
            throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND);
        }

        // 校验顺序：规则 → 时段 → 重复申请 → 已有正常打卡
        AttendanceRule rule = attendanceRuleService.findRule(stationId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED);
        }
        List<AttendancePeriodResolver.ResolvedPeriod> periods = AttendancePeriodResolver.resolve(rule);
        AttendancePeriodResolver.ResolvedPeriod period =
                periodIndex < periods.size() ? periods.get(periodIndex) : null;
        if (period == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_PERIOD_NOT_FOUND);
        }
        LocalDate workDate = AttendanceSupport.parseDate(safe.getWorkDate());

        boolean duplicated = hasPendingOrApproved(employee.getId(), workDate, periodIndex, checkType);
        if (duplicated || attendanceRecordService.hasValidCard(employee.getId(), workDate, checkType, periodIndex)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MAKEUP_DUPLICATE);
        }

        AttendanceMakeup makeup = new AttendanceMakeup();
        makeup.setEmployeeId(employee.getId());
        makeup.setStationId(stationId);
        makeup.setWorkDate(workDate);
        makeup.setPeriodIndex(periodIndex);
        makeup.setPeriodName(period.name());
        makeup.setCheckType(checkType);
        makeup.setReason(safe.getReason().trim());
        makeup.setStatus(AttendanceConstants.MAKEUP_PENDING);
        makeup.setApplyTime(LocalDateTime.now().withNano(0));
        attendanceMakeupMapper.insert(makeup);
        return toVO(makeup, employee.getRealName(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceMakeupVO approve(Long id, AttendanceMakeupApproveRequest request) {
        AttendanceMakeupApproveRequest safe = request == null ? new AttendanceMakeupApproveRequest() : request;
        if (safe.getApproved() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approved 须为布尔值");
        }
        if (safe.getApproveRemark() != null && !AttendanceSupport.textLen(safe.getApproveRemark(), 0, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批备注不可超过 200 字");
        }
        AttendanceMakeup makeup = attendanceMakeupMapper.selectById(id);
        if (makeup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "补卡申请不存在");
        }
        if (!AttendanceConstants.MAKEUP_PENDING.equals(makeup.getStatus())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MAKEUP_STATUS_INVALID);
        }
        if (safe.getApproved() && !canWriteRecord(makeup, true)) {
            // 时段被改配置导致原时段不存在：不能静默通过（会落下「审批通过却无打卡记录」的矛盾数据）
            throw new BusinessException(ErrorCode.ATTENDANCE_RULE_NOT_CONFIGURED);
        }

        Employee approver = employeeMapper.selectById(currentUserId());
        makeup.setStatus(safe.getApproved() ? AttendanceConstants.MAKEUP_APPROVED : AttendanceConstants.MAKEUP_REJECTED);
        makeup.setApproverId(approver == null ? null : approver.getId());
        makeup.setApproveTime(LocalDateTime.now().withNano(0));
        makeup.setApproveRemark(safe.getApproveRemark() == null || safe.getApproveRemark().isBlank()
                ? null : safe.getApproveRemark().trim());
        attendanceMakeupMapper.updateById(makeup);

        if (safe.getApproved()) {
            canWriteRecord(makeup, false);
        }
        String approverName = approver == null ? null : approver.getRealName();
        String employeeName = loadRealName(makeup.getEmployeeId());
        return toVO(makeup, employeeName, approverName);
    }

    // ==================== 私有方法 ====================

    /** 同一槽位是否已有未驳回的补卡申请 */
    private boolean hasPendingOrApproved(Long employeeId, LocalDate workDate, Integer periodIndex, String checkType) {
        LambdaQueryWrapper<AttendanceMakeup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttendanceMakeup::getEmployeeId, employeeId)
                .eq(AttendanceMakeup::getWorkDate, workDate)
                .eq(AttendanceMakeup::getPeriodIndex, periodIndex)
                .eq(AttendanceMakeup::getCheckType, checkType)
                .ne(AttendanceMakeup::getStatus, AttendanceConstants.MAKEUP_REJECTED);
        return attendanceMakeupMapper.selectCount(wrapper) > 0;
    }

    /**
     * 审批通过 → 补录打卡记录（对齐 Mock {@code writeMakeupRecord}）。
     *
     * @param dryRun true 只做「能不能补录」的前置判断，不落库
     * @return 可补录/已补录返回 true；员工或时段缺失返回 false
     */
    private boolean canWriteRecord(AttendanceMakeup makeup, boolean dryRun) {
        Employee employee = employeeMapper.selectById(makeup.getEmployeeId());
        AttendanceRule rule = attendanceRuleService.findRule(makeup.getStationId());
        List<AttendancePeriodResolver.ResolvedPeriod> periods = rule == null ? List.of()
                : AttendancePeriodResolver.resolve(rule);
        AttendancePeriodResolver.ResolvedPeriod period =
                makeup.getPeriodIndex() != null && makeup.getPeriodIndex() < periods.size()
                        ? periods.get(makeup.getPeriodIndex()) : null;
        if (employee == null || period == null) {
            return false;
        }
        // 申请到审批期间本人又正常打了卡：同槽位不再补录，避免一个槽位出现两条正常卡
        if (attendanceRecordService.hasValidCard(makeup.getEmployeeId(), makeup.getWorkDate(),
                makeup.getCheckType(), makeup.getPeriodIndex())) {
            return true;
        }
        if (dryRun) {
            return true;
        }
        String specTime = AttendanceConstants.CHECK_TYPE_ON.equals(makeup.getCheckType())
                ? period.startTime() : period.endTime();

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployeeId(makeup.getEmployeeId());
        record.setStationId(makeup.getStationId());
        record.setWorkDate(makeup.getWorkDate());
        record.setPeriodIndex(makeup.getPeriodIndex());
        record.setPeriodName(makeup.getPeriodName());
        record.setCheckType(makeup.getCheckType());
        record.setCheckTime(AttendancePeriodResolver.at(makeup.getWorkDate(),
                AttendancePeriodResolver.minutesOfDay(specTime)));
        record.setStatus(AttendanceConstants.STATUS_NORMAL);
        record.setSource(AttendanceConstants.SOURCE_MAKEUP);
        record.setRemark("补卡通过（系统补录）");
        // 校验项非设备打卡产生：统一置 null（不伪造命中值）
        record.setCheckMode(null);
        record.setWifiSsid(null);
        record.setWifiMatched(null);
        record.setLongitude(null);
        record.setLatitude(null);
        record.setDistance(null);
        record.setLocationMatched(null);
        attendanceRecordMapper.insert(record);
        return true;
    }

    private void validateFilter(String status, String startDate, String endDate) {
        if (status != null && !status.isBlank() && !AttendanceConstants.MAKEUP_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }
        if (!AttendanceSupport.isDate(startDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate 格式须为 YYYY-MM-DD");
        }
        if (!AttendanceSupport.isDate(endDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "endDate 格式须为 YYYY-MM-DD");
        }
    }

    private LambdaQueryWrapper<AttendanceMakeup> baseFilter(Long stationId, Long employeeId, String status,
                                                            String startDate, String endDate) {
        LambdaQueryWrapper<AttendanceMakeup> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AttendanceMakeup::getId, AttendanceMakeup::getEmployeeId, AttendanceMakeup::getStationId,
                AttendanceMakeup::getWorkDate, AttendanceMakeup::getPeriodIndex, AttendanceMakeup::getPeriodName,
                AttendanceMakeup::getCheckType, AttendanceMakeup::getReason, AttendanceMakeup::getStatus,
                AttendanceMakeup::getApplyTime, AttendanceMakeup::getApproverId, AttendanceMakeup::getApproveTime,
                AttendanceMakeup::getApproveRemark);
        if (employeeId != null) {
            wrapper.eq(AttendanceMakeup::getEmployeeId, employeeId);
        }
        if (stationId != null) {
            wrapper.eq(AttendanceMakeup::getStationId, stationId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AttendanceMakeup::getStatus, status);
        }
        if (startDate != null && !startDate.isBlank()) {
            wrapper.ge(AttendanceMakeup::getWorkDate, AttendanceSupport.parseDate(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            wrapper.le(AttendanceMakeup::getWorkDate, AttendanceSupport.parseDate(endDate));
        }
        wrapper.orderByDesc(AttendanceMakeup::getApplyTime).orderByDesc(AttendanceMakeup::getId);
        return wrapper;
    }

    private PageResult<AttendanceMakeupVO> page(LambdaQueryWrapper<AttendanceMakeup> wrapper, int pageNum, int pageSize) {
        Page<AttendanceMakeup> page = attendanceMakeupMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AttendanceMakeup> rows = page.getRecords();

        Set<Long> employeeIds = new LinkedHashSet<>();
        Set<Long> stationIds = new LinkedHashSet<>();
        for (AttendanceMakeup row : rows) {
            employeeIds.add(row.getEmployeeId());
            if (row.getApproverId() != null) {
                employeeIds.add(row.getApproverId());
            }
            if (row.getStationId() != null) {
                stationIds.add(row.getStationId());
            }
        }
        Map<Long, String> employeeNames = loadEmployeeNames(employeeIds);
        Map<Long, String> stationNames = loadStationNames(stationIds);

        List<AttendanceMakeupVO> list = new ArrayList<>(rows.size());
        for (AttendanceMakeup row : rows) {
            AttendanceMakeupVO vo = toVO(row, employeeNames.get(row.getEmployeeId()),
                    row.getApproverId() == null ? null : employeeNames.get(row.getApproverId()));
            vo.setStationName(stationNames.get(row.getStationId()));
            list.add(vo);
        }
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    private Map<Long, String> loadEmployeeNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName).in(Employee::getId, ids);
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            map.put(employee.getId(), employee.getRealName());
        }
        return map;
    }

    private Map<Long, String> loadStationNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Station::getId, Station::getStationName).in(Station::getId, ids);
        for (Station station : stationMapper.selectList(wrapper)) {
            map.put(station.getId(), station.getStationName());
        }
        return map;
    }

    private String loadRealName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        return employee == null ? null : employee.getRealName();
    }

    private AttendanceMakeupVO toVO(AttendanceMakeup makeup, String employeeName, String approverName) {
        AttendanceMakeupVO vo = new AttendanceMakeupVO();
        vo.setId(makeup.getId());
        vo.setEmployeeId(makeup.getEmployeeId());
        vo.setEmployeeName(employeeName);
        vo.setStationId(makeup.getStationId());
        vo.setStationName(stationNameOrNull(makeup.getStationId()));
        vo.setWorkDate(makeup.getWorkDate());
        vo.setPeriodIndex(makeup.getPeriodIndex());
        vo.setPeriodName(makeup.getPeriodName());
        vo.setCheckType(makeup.getCheckType());
        vo.setReason(makeup.getReason());
        vo.setStatus(makeup.getStatus());
        vo.setApplyTime(makeup.getApplyTime());
        vo.setApproverId(makeup.getApproverId());
        vo.setApproverName(approverName);
        vo.setApproveTime(makeup.getApproveTime());
        vo.setApproveRemark(makeup.getApproveRemark());
        return vo;
    }

    private String stationNameOrNull(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
