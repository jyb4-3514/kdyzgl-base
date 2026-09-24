package com.qiujie.service.leave.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.qiujie.common.LoginUser;
import com.qiujie.common.PageResult;
import com.qiujie.config.AlgoProperties;
import com.qiujie.dto.leave.LeaveApplyRequest;
import com.qiujie.dto.leave.LeaveApproveRequest;
import com.qiujie.dto.leave.LeaveMineQuery;
import com.qiujie.dto.leave.LeavePreviewRequest;
import com.qiujie.dto.leave.LeaveQuery;
import com.qiujie.dto.leave.LeaveRevokeRequest;
import com.qiujie.dto.leave.LeaveSettingRequest;
import com.qiujie.entity.Employee;
import com.qiujie.entity.LeaveLog;
import com.qiujie.entity.LeaveRequest;
import com.qiujie.entity.LeaveSetting;
import com.qiujie.entity.Station;
import com.qiujie.enums.ErrorCode;
import com.qiujie.enums.RoleEnum;
import com.qiujie.exception.BusinessException;
import com.qiujie.mapper.EmployeeMapper;
import com.qiujie.mapper.LeaveLogMapper;
import com.qiujie.mapper.LeaveRequestMapper;
import com.qiujie.mapper.LeaveSettingMapper;
import com.qiujie.mapper.StationMapper;
import com.qiujie.service.finance.PayrollLockQueryService;
import com.qiujie.service.leave.LeaveService;
import com.qiujie.service.leave.port.ScheduledDatesQuery;
import com.qiujie.service.leave.support.LeaveAccessPolicy;
import com.qiujie.service.leave.support.LeaveConstants;
import com.qiujie.service.leave.support.LeaveIntervalPolicy;
import com.qiujie.service.leave.support.LeaveUnitRange;
import com.qiujie.service.leave.support.OccupiedLeaveInterval;
import com.qiujie.service.notification.NotificationService;
import com.qiujie.util.JsonUtil;
import com.qiujie.util.UserContext;
import com.qiujie.vo.leave.LeaveLogVO;
import com.qiujie.vo.leave.LeavePreviewVO;
import com.qiujie.vo.leave.LeaveSettingVO;
import com.qiujie.vo.leave.LeaveSnapshotVO;
import com.qiujie.vo.leave.LeaveVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 请假服务实现（M7，13 接口，api.md §7.1 / Mock {@code leaveStore.js}，架构 §6.2 P7）。
 * <p>
 * 关键口径：
 * <ul>
 *   <li><b>6 态状态机</b>与迁移路径严格对齐 Mock T1–T9（判定集中在 {@link LeaveAccessPolicy} 与
 *       {@code LeaveStateMachine}）；</li>
 *   <li><b>计薪天数</b>由算法 S5 的半天单元整数区间给出（{@link LeaveIntervalPolicy}），
 *       逐日扫描被区间二分替代，结果与 Mock {@code countedDaysOf} 逐例等价；</li>
 *   <li><b>越权</b>：跨站/审自己/ADMIN 提交统一 9605，仅状态不匹配回 9602；</li>
 *   <li><b>账期锁</b>：撤回已批单前逐月问财务只读端口 {@code PayrollLockQueryService}，命中回 9606；</li>
 *   <li><b>通知</b>：申请/结果走 P2 {@code NotificationService.sendSystem}（类型 5/6、biz_type=leave）。</li>
 * </ul>
 * 依赖方向恒为 {@code leave → attendance/finance/notification}（只读端口/事件出口），不产生环。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveServiceImpl implements LeaveService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LeaveRequestMapper leaveRequestMapper;
    private final LeaveLogMapper leaveLogMapper;
    private final LeaveSettingMapper leaveSettingMapper;
    private final EmployeeMapper employeeMapper;
    private final StationMapper stationMapper;
    private final ScheduledDatesQuery scheduledDatesQuery;
    private final PayrollLockQueryService payrollLockQueryService;
    private final NotificationService notificationService;
    private final AlgoProperties algoProperties;

    // ==================== 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<LeaveVO> mine(LeaveMineQuery query) {
        validateQueryFields(query.getStatus(), query.getLeaveType(), query.getStartDate(), query.getEndDate());
        return pageLeaves(currentUserId(), null, query.getStatus(), query.getLeaveType(),
                query.getStartDate(), query.getEndDate(), pageNum(query.getPageNum()), pageSize(query.getPageSize()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LeaveVO> list(LeaveQuery query) {
        validateQueryFields(query.getStatus(), query.getLeaveType(), query.getStartDate(), query.getEndDate());
        // stationId 已由 L1 数据范围收敛（非 ADMIN 强制为本人驿站；无归属收敛为哨兵 -1 → 空结果）
        return pageLeaves(query.getEmployeeId(), query.getStationId(), query.getStatus(), query.getLeaveType(),
                query.getStartDate(), query.getEndDate(), pageNum(query.getPageNum()), pageSize(query.getPageSize()));
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveVO detail(Long id) {
        LeaveRequest leave = findLeave(id);
        LoginUser user = currentUser();
        if (!LeaveAccessPolicy.canView(user.getRole(), user.getUserId(), leave.getEmployeeId(),
                leave.getStationId(), parseStationId(user.getStationId()))) {
            throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
        }
        return toVOList(List.of(leave), user).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public LeavePreviewVO preview(LeavePreviewRequest request) {
        Employee applicant = guardApplicant();
        LeaveForm form = parseAndValidateSemantics(request);
        double naturalDays = LeaveIntervalPolicy.naturalDays(form.startDate(), form.startPeriod(),
                form.endDate(), form.endPeriod());
        double countedDays = countedDays(applicant.getId(), form);
        LeavePreviewVO vo = new LeavePreviewVO();
        vo.setNaturalDays(decimal(naturalDays));
        vo.setCountedDays(decimal(countedDays));
        // 是否排除了轮休日：计薪 < 自然（SCHEDULED 假别逐日查排班才可能小于）
        vo.setHasRestDayExcluded(countedDays < naturalDays);
        return vo;
    }

    // ==================== 扣款开关 ====================

    @Override
    @Transactional(readOnly = true)
    public LeaveSettingVO getSettings() {
        return new LeaveSettingVO(readDeductEnabled());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveSettingVO saveSettings(LeaveSettingRequest request) {
        if (request == null || request.getLeaveDeductEnabled() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "leaveDeductEnabled 须为布尔值");
        }
        int flag = request.getLeaveDeductEnabled() ? 1 : 0;
        LeaveSetting row = firstSetting();
        if (row == null) {
            // 单行表首次写入：插入一条即可（无删除语义）
            LeaveSetting created = new LeaveSetting();
            created.setLeaveDeductEnabled(flag);
            leaveSettingMapper.insert(created);
        } else {
            row.setLeaveDeductEnabled(flag);
            leaveSettingMapper.updateById(row);
        }
        return new LeaveSettingVO(flag == 1);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLeaveDeductEnabled() {
        LeaveSetting row = firstSetting();
        if (row == null) {
            // 无行时按配置默认（Q6 未裁定前默认 false，与 Mock 一致）
            return algoProperties.getLeave().isDeductEnabledDefault();
        }
        return row.getLeaveDeductEnabled() != null && row.getLeaveDeductEnabled() == 1;
    }

    // ==================== 写操作（状态机 T1–T9） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO apply(LeaveApplyRequest request) {
        Employee applicant = guardApplicant();
        validateFormShape(request);
        LeaveForm form = parseAndValidateSemantics(request);
        ensureNoOverlap(applicant.getId(), form, null);
        LeaveRequest created = createLeave(applicant, form, null, currentUser());
        return toVOList(List.of(created), currentUser()).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO update(Long id, LeaveApplyRequest request) {
        validateFormShape(request);
        LeaveRequest leave = findLeave(id);
        LoginUser user = currentUser();
        if (!LeaveAccessPolicy.canEdit(user.getUserId(), leave.getEmployeeId(), leave.getStatus())) {
            // 非本人 → 9605；本人但状态不符 → 9607（与 Mock 两个独立守卫一致）
            if (user.getUserId() == null || !user.getUserId().equals(leave.getEmployeeId())) {
                throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
            }
            throw new BusinessException(ErrorCode.LEAVE_EDIT_FORBIDDEN);
        }
        LeaveForm form = parseAndValidateSemantics(request);
        ensureNoOverlap(leave.getEmployeeId(), form, leave.getId());

        Object before = snapshotOf(leave);
        applyForm(leave, form);
        leave.setNaturalDays(decimal(naturalDays(form)));
        leave.setCountedDays(decimal(countedDays(leave.getEmployeeId(), form)));
        leave.setUpdateTime(LocalDateTime.now());
        leaveRequestMapper.updateById(leave);

        pushLog(leave, LeaveConstants.ACTION_UPDATE, user, leave.getStatus(), leave.getStatus(),
                before, snapshotOf(leave), null);
        return toVOList(List.of(leave), user).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO cancel(Long id) {
        LeaveRequest leave = findLeave(id);
        LoginUser user = currentUser();
        if (!LeaveAccessPolicy.canCancel(user.getUserId(), leave.getEmployeeId(), leave.getStatus())) {
            if (user.getUserId() == null || !user.getUserId().equals(leave.getEmployeeId())) {
                throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
            }
            throw new BusinessException(ErrorCode.LEAVE_STATUS_INVALID);
        }
        String fromStatus = leave.getStatus();
        // 撤销通知对象：待初审通知站长，待终审通知老板（对齐 Mock cancelLeave）
        Employee target = LeaveConstants.STATUS_PENDING_STATION.equals(fromStatus)
                ? findStationAdmin(leave.getStationId())
                : bossOf();

        LocalDateTime now = LocalDateTime.now();
        leave.setStatus(LeaveConstants.STATUS_CANCELLED);
        leave.setRejectStage(null);
        leave.setCancelById(user.getUserId());
        leave.setCancelTime(now);
        leave.setUpdateTime(now);
        leaveRequestMapper.updateById(leave);

        pushLog(leave, LeaveConstants.ACTION_CANCEL, user, fromStatus, LeaveConstants.STATUS_CANCELLED,
                null, null, null);
        notify(leave, target, LeaveConstants.NOTIFY_RESULT, "请假申请已撤销",
                operatorName(user.getUserId()) + " 已撤销 " + typeLabel(leave.getLeaveType()) + " "
                        + rangeText(leave) + " 的申请");
        return toVOList(List.of(leave), user).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO resubmit(Long id, LeaveApplyRequest request) {
        guardApplicant();
        validateFormShape(request);
        LeaveRequest origin = findLeave(id);
        LoginUser user = currentUser();
        if (user.getUserId() == null || !user.getUserId().equals(origin.getEmployeeId())) {
            throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
        }
        if (!LeaveConstants.STATUS_REJECTED.equals(origin.getStatus())) {
            throw new BusinessException(ErrorCode.LEAVE_STATUS_INVALID);
        }
        LeaveForm form = parseAndValidateSemantics(request);
        ensureNoOverlap(origin.getEmployeeId(), form, null);

        // 原单保持 REJECTED 只读，仅追加「修改后重新提交」留痕；新单带 originId 溯源（T9）
        pushLog(origin, LeaveConstants.ACTION_RESUBMIT, user, origin.getStatus(), null,
                null, null, "修改后重新提交，生成新单");
        LeaveRequest created = createLeave(loadEmployee(origin.getEmployeeId()), form, origin.getId(), user);
        return toVOList(List.of(created), user).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO stationApprove(Long id, LeaveApproveRequest request) {
        validateApproveRemark(request);
        LeaveRequest leave = findLeave(id);
        LoginUser user = currentUser();
        Employee operator = loadEmployee(user.getUserId());
        if (!LeaveAccessPolicy.canStationApprove(operator.getStationId(), leave.getStationId(),
                operator.getId(), leave.getEmployeeId(), leave.getStatus())) {
            if (operator.getStationId() == null || !operator.getStationId().equals(leave.getStationId())) {
                throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
            }
            if (operator.getId().equals(leave.getEmployeeId())) {
                throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION, "不能审批本人提交的请假申请");
            }
            throw new BusinessException(ErrorCode.LEAVE_STATUS_INVALID);
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String remark = trimToNull(request.getRemark());
        String fromStatus = leave.getStatus();

        LocalDateTime now = LocalDateTime.now();
        leave.setStationApproverId(operator.getId());
        leave.setStationApproveTime(now);
        leave.setStationApproveRemark(remark);
        leave.setStatus(approved ? LeaveConstants.STATUS_PENDING_BOSS : LeaveConstants.STATUS_REJECTED);
        leave.setRejectStage(approved ? null : LeaveConstants.STAGE_STATION);
        leave.setUpdateTime(now);
        leaveRequestMapper.updateById(leave);

        pushLog(leave, approved ? LeaveConstants.ACTION_STATION_APPROVE : LeaveConstants.ACTION_STATION_REJECT,
                user, fromStatus, leave.getStatus(), null, null, remark);
        if (approved) {
            notify(leave, bossOf(), LeaveConstants.NOTIFY_APPLY, "请假申请待终审",
                    employeeName(leave.getEmployeeId()) + " 的" + typeLabel(leave.getLeaveType()) + " "
                            + rangeText(leave) + " 已通过 " + stationName(leave.getStationId()) + " 站长初审");
        } else {
            notify(leave, applicantOf(leave), LeaveConstants.NOTIFY_RESULT, "请假申请未通过初审",
                    typeLabel(leave.getLeaveType()) + " " + rangeText(leave) + "，驳回原因：" + remark
                            + "。可修改后重新提交");
        }
        return toVOList(List.of(leave), user).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO finalApprove(Long id, LeaveApproveRequest request) {
        validateApproveRemark(request);
        LeaveRequest leave = findLeave(id);
        LoginUser user = currentUser();
        if (!LeaveAccessPolicy.canFinalApprove(user.getUserId(), leave.getEmployeeId(), leave.getStatus())) {
            if (user.getUserId() != null && user.getUserId().equals(leave.getEmployeeId())) {
                throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION, "不能审批本人提交的请假申请");
            }
            throw new BusinessException(ErrorCode.LEAVE_STATUS_INVALID);
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String remark = trimToNull(request.getRemark());
        String fromStatus = leave.getStatus();

        LocalDateTime now = LocalDateTime.now();
        leave.setApproverId(user.getUserId());
        leave.setApproveTime(now);
        leave.setApproveRemark(remark);
        leave.setStatus(approved ? LeaveConstants.STATUS_APPROVED : LeaveConstants.STATUS_REJECTED);
        leave.setRejectStage(approved ? null : LeaveConstants.STAGE_BOSS);
        if (approved) {
            // 终审通过落计薪天数快照：排班事后变更不影响已出账口径（设计规范 §8.5）
            LeaveSnapshotVO snapshot = new LeaveSnapshotVO();
            snapshot.setNaturalDays(leave.getNaturalDays());
            snapshot.setCountedDays(leave.getCountedDays());
            snapshot.setScheduleDigest(scheduleDigestOf(leave.getEmployeeId(),
                    toForm(leave)));
            leave.setCountedDaysSnapshot(JsonUtil.write(snapshot));
        }
        leave.setUpdateTime(now);
        leaveRequestMapper.updateById(leave);

        pushLog(leave, approved ? LeaveConstants.ACTION_FINAL_APPROVE : LeaveConstants.ACTION_FINAL_REJECT,
                user, fromStatus, leave.getStatus(), null, null, remark);
        notify(leave, applicantOf(leave), LeaveConstants.NOTIFY_RESULT,
                approved ? "请假申请已通过" : "请假申请被驳回",
                approved
                        ? typeLabel(leave.getLeaveType()) + " " + rangeText(leave) + "，计薪 "
                                + plain(leave.getCountedDays()) + " 天，已生效"
                        : typeLabel(leave.getLeaveType()) + " " + rangeText(leave) + "，驳回原因：" + remark
                                + "。可修改后重新提交");
        return toVOList(List.of(leave), user).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO revoke(Long id, LeaveRevokeRequest request) {
        if (request == null || !textLen(request.getReason(), 2, 100)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "撤回原因须为 2-100 字");
        }
        LeaveRequest leave = findLeave(id);
        LoginUser user = currentUser();
        if (!LeaveAccessPolicy.canRevoke(user.getRole(), leave.getStatus())) {
            throw new BusinessException(ErrorCode.LEAVE_STATUS_INVALID);
        }
        String reason = request.getReason().trim();

        // 逐月检查账期锁：任一覆盖月份存在非 DRAFT/REJECTED 工资单 → 9606
        String lockedMonth = LeaveIntervalPolicy.monthSpans(toForm(leave).unitRange()).stream()
                .filter(month -> payrollLockQueryService.isMonthLocked(leave.getEmployeeId(), month))
                .findFirst()
                .orElse(null);
        if (lockedMonth != null) {
            throw new BusinessException(ErrorCode.LEAVE_PAYROLL_LOCKED,
                    lockedMonth + " 工资单已生成，撤回会导致工资数据不一致。请先在财务管理中作废该单据。");
        }

        String fromStatus = leave.getStatus();
        LocalDateTime now = LocalDateTime.now();
        leave.setStatus(LeaveConstants.STATUS_REVOKED);
        leave.setRejectStage(null);
        leave.setRevokerId(user.getUserId());
        leave.setRevokeTime(now);
        leave.setRevokeReason(reason);
        leave.setUpdateTime(now);
        leaveRequestMapper.updateById(leave);

        pushLog(leave, LeaveConstants.ACTION_REVOKE, user, fromStatus, LeaveConstants.STATUS_REVOKED,
                null, null, reason);
        notify(leave, applicantOf(leave), LeaveConstants.NOTIFY_RESULT, "已批准的请假被撤回",
                typeLabel(leave.getLeaveType()) + " " + rangeText(leave) + " 已被 " + operatorName(user.getUserId())
                        + " 撤回，原因：" + reason + "。如有疑问请联系站长");
        return toVOList(List.of(leave), user).get(0);
    }

    // ==================== 内部：分页 / 持久化 ====================

    private PageResult<LeaveVO> pageLeaves(Long employeeId, Long stationId, String status, String leaveType,
                                           String startDate, String endDate, int pageNum, int pageSize) {
        // 查询日期在 validateQueryFields 已确认为合法 yyyy-MM-dd，这里解析为 LocalDate 交由驱动按日期类型绑定
        LocalDate rangeStart = isBlank(startDate) ? null : parseStrict(startDate);
        LocalDate rangeEnd = isBlank(endDate) ? null : parseStrict(endDate);
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(employeeId != null, LeaveRequest::getEmployeeId, employeeId)
                .eq(stationId != null, LeaveRequest::getStationId, stationId)
                .eq(leaveType != null && !leaveType.isBlank(), LeaveRequest::getLeaveType, leaveType)
                // 区间有交集：已有单的 end_date ≥ 查询起，且已有单的 start_date ≤ 查询止
                .ge(rangeStart != null, LeaveRequest::getEndDate, rangeStart)
                .le(rangeEnd != null, LeaveRequest::getStartDate, rangeEnd)
                .orderByDesc(LeaveRequest::getApplyTime)
                .orderByAsc(LeaveRequest::getId);
        List<String> statuses = expandStatus(status);
        if (statuses != null) {
            wrapper.in(LeaveRequest::getStatus, statuses);
        }
        Page<LeaveRequest> page = leaveRequestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords(), currentUser()));
    }

    /** 建单 + 定初始态 + 留痕 + 通知（提交与驳回重提共用；T9 生成新单带 originId） */
    private LeaveRequest createLeave(Employee applicant, LeaveForm form, Long originId, LoginUser operator) {
        // 站长本人提交跳过初审；无可用站长同样直进终审（共用同一判定，避免两条跳级逻辑）
        Employee stationApprover = RoleEnum.STATION_ADMIN.name().equals(applicant.getRole())
                ? null
                : findStationAdmin(applicant.getStationId());
        String status = stationApprover != null
                ? LeaveConstants.STATUS_PENDING_STATION
                : LeaveConstants.STATUS_PENDING_BOSS;

        LocalDateTime now = LocalDateTime.now();
        LeaveRequest leave = new LeaveRequest();
        leave.setEmployeeId(applicant.getId());
        leave.setStationId(applicant.getStationId());
        applyForm(leave, form);
        leave.setStatus(status);
        leave.setRejectStage(null);
        leave.setNaturalDays(decimal(naturalDays(form)));
        leave.setCountedDays(decimal(countedDays(applicant.getId(), form)));
        leave.setOriginId(originId);
        leave.setApplyTime(now);
        leave.setUpdateTime(now);
        leaveRequestMapper.insert(leave);

        pushLog(leave, LeaveConstants.ACTION_SUBMIT, operator, null, status, null, null, null);
        notifyApply(leave, applicant, stationApprover, status);
        return leave;
    }

    // ==================== 内部：校验 ====================

    /** 提交/重提前置：ADMIN 与无归属驿站拦截（对齐 Mock applicantGuard） */
    private Employee guardApplicant() {
        LoginUser user = currentUser();
        Employee employee = loadEmployee(user.getUserId());
        if (LeaveAccessPolicy.isAdmin(user.getRole())) {
            throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION, "超级管理员无需提交请假申请");
        }
        if (employee.getStationId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号未归属驿站，无法提交请假");
        }
        return employee;
    }

    /** 表单「形状」校验（格式与枚举，回 400；对齐 Mock 路由 formError） */
    private void validateFormShape(LeaveApplyRequest request) {
        LeaveApplyRequest r = request == null ? new LeaveApplyRequest() : request;
        if (!LeaveConstants.LEAVE_TYPES.contains(r.getLeaveType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "leaveType 取值非法");
        }
        requireDatePattern(r.getStartDate(), "startDate");
        requireDatePattern(r.getEndDate(), "endDate");
        requirePeriod(r.getStartPeriod(), "startPeriod");
        requirePeriod(r.getEndPeriod(), "endPeriod");
        if (!textLen(r.getReason(), 2, 200)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请假事由须为 2-200 字");
        }
    }

    /**
     * 日期与枚举语义校验（提交/编辑/重提/试算共用，回 9604；对齐 Mock {@code dateError}）。
     * 只判「这一段区间本身合不合法」，不含重叠（重叠要排除本单，单独一步）。
     */
    private LeaveForm parseAndValidateSemantics(LeaveApplyRequest request) {
        LeaveApplyRequest r = request == null ? new LeaveApplyRequest() : request;
        if (!LeaveConstants.LEAVE_TYPES.contains(r.getLeaveType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "leaveType 取值非法");
        }
        if (!LeaveConstants.PERIOD_AM.equals(r.getStartPeriod())
                && !LeaveConstants.PERIOD_PM.equals(r.getStartPeriod())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "半天粒度仅支持 AM / PM");
        }
        if (!LeaveConstants.PERIOD_AM.equals(r.getEndPeriod())
                && !LeaveConstants.PERIOD_PM.equals(r.getEndPeriod())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "半天粒度仅支持 AM / PM");
        }
        LocalDate startDate = parseStrict(r.getStartDate());
        LocalDate endDate = parseStrict(r.getEndDate());
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_INVALID, "请假日期不合法");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_INVALID,
                    "请假不能选择过去的日期。已发生缺勤的情况请联系站长线下确认处理。");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_INVALID, "结束日期不能早于开始日期");
        }
        if (startDate.equals(endDate)
                && LeaveConstants.PERIOD_PM.equals(r.getStartPeriod())
                && LeaveConstants.PERIOD_AM.equals(r.getEndPeriod())) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_INVALID, "同一天内，结束时段不能早于开始时段");
        }
        double naturalDays = LeaveIntervalPolicy.naturalDays(startDate, r.getStartPeriod(), endDate, r.getEndPeriod());
        int maxLeaveDays = algoProperties.getLeave().getMaxLeaveDays();
        if (naturalDays > maxLeaveDays) {
            throw new BusinessException(ErrorCode.LEAVE_DATE_INVALID,
                    "单次请假最长 " + maxLeaveDays + " 天，如需更长请分次申请或联系老板");
        }
        return new LeaveForm(r.getLeaveType(), startDate, r.getStartPeriod(), endDate, r.getEndPeriod(),
                r.getReason() == null ? null : r.getReason().trim());
    }

    /** 审批意见校验：通过选填 0-100 字；驳回必填 2-100 字（对齐 Mock approveRemarkError） */
    private void validateApproveRemark(LeaveApproveRequest request) {
        LeaveApproveRequest r = request == null ? new LeaveApproveRequest() : request;
        if (!(r.getApproved() instanceof Boolean)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approved 须为布尔值");
        }
        String remark = trimToNull(r.getRemark());
        if (!r.getApproved() && !textLen(remark, 2, 100)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "驳回原因须为 2-100 字");
        }
        if (r.getApproved() && remark != null && !textLen(remark, 0, 100)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批意见不可超过 100 字");
        }
    }

    /** 列表/我的共用筛选校验（对齐 Mock queryError；pageSize 由 PageQuery @Valid 处理） */
    private void validateQueryFields(String status, String leaveType, String startDate, String endDate) {
        if (!isBlank(status)
                && !LeaveConstants.AGGREGATE_PENDING.equals(status)
                && !LeaveConstants.QUERY_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "status 取值非法");
        }
        if (!isBlank(leaveType) && !LeaveConstants.LEAVE_TYPES.contains(leaveType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "leaveType 取值非法");
        }
        if (!isBlank(startDate) && parseStrict(startDate) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "startDate 格式须为 YYYY-MM-DD");
        }
        if (!isBlank(endDate) && parseStrict(endDate) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "endDate 格式须为 YYYY-MM-DD");
        }
    }

    /** 与本人已有申请的重叠校验（区间相交；T1/T8/T9 共用，编辑排除本单） */
    private void ensureNoOverlap(Long employeeId, LeaveForm form, Long excludeId) {
        List<String> occupiedStatus = algoProperties.getLeave().getOccupiedStatus();
        if (occupiedStatus == null || occupiedStatus.isEmpty()) {
            return;
        }
        LeaveUnitRange target = form.unitRange();
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(LeaveRequest::getId, LeaveRequest::getLeaveType, LeaveRequest::getStartDate,
                        LeaveRequest::getStartPeriod, LeaveRequest::getEndDate, LeaveRequest::getEndPeriod,
                        LeaveRequest::getStatus)
                .eq(LeaveRequest::getEmployeeId, employeeId)
                .in(LeaveRequest::getStatus, occupiedStatus)
                // 日级粗筛（走 idx_leave_request_date）：区间相交者必满足 end_date ≥ 起 且 start_date ≤ 止
                .ge(LeaveRequest::getEndDate, form.startDate())
                .le(LeaveRequest::getStartDate, form.endDate())
                .orderByAsc(LeaveRequest::getId);
        if (excludeId != null) {
            wrapper.ne(LeaveRequest::getId, excludeId);
        }
        List<LeaveRequest> candidates = leaveRequestMapper.selectList(wrapper);
        List<OccupiedLeaveInterval> intervals = new ArrayList<>(candidates.size());
        Map<Long, LeaveRequest> byId = new LinkedHashMap<>();
        for (LeaveRequest candidate : candidates) {
            intervals.add(new OccupiedLeaveInterval(candidate.getId(), rangeOf(candidate)));
            byId.put(candidate.getId(), candidate);
        }
        // 逐条精确判定半天单元相交（日级粗筛后候选量很小）；顺序即 id 升序，与 Mock 首个命中一致
        OccupiedLeaveInterval hit = LeaveIntervalPolicy.firstIntersecting(intervals, target);
        if (hit != null) {
            throw new BusinessException(ErrorCode.LEAVE_OVERLAP, overlapMessage(byId.get(hit.leaveId())));
        }
    }

    // ==================== 内部：算法 S5 计薪口径 ====================

    /** SCHEDULED 逐日查排班（区间算法）；NATURAL 与排班无关 */
    private double countedDays(Long employeeId, LeaveForm form) {
        if (LeaveConstants.COUNT_MODE_NATURAL.equals(countMode(form.leaveType()))) {
            return naturalDays(form);
        }
        List<LocalDate> scheduled = scheduledDatesQuery.scheduledDates(employeeId, form.startDate(), form.endDate());
        return LeaveIntervalPolicy.countedDaysByRange(LeaveIntervalPolicy.scheduleUnits(scheduled), form.unitRange());
    }

    /** 排班摘要（进快照留证）：NATURAL 假别为空串 */
    private String scheduleDigestOf(Long employeeId, LeaveForm form) {
        if (LeaveConstants.COUNT_MODE_NATURAL.equals(countMode(form.leaveType()))) {
            return "";
        }
        List<LocalDate> scheduled = scheduledDatesQuery.scheduledDates(employeeId, form.startDate(), form.endDate());
        List<String> days = new ArrayList<>(scheduled.size());
        for (LocalDate day : scheduled) {
            days.add(day.format(DATE_FMT));
        }
        return String.join(",", days);
    }

    private double naturalDays(LeaveForm form) {
        return LeaveIntervalPolicy.naturalDays(form.startDate(), form.startPeriod(), form.endDate(), form.endPeriod());
    }

    /** 假别 → 计薪口径（唯一真源：{@code hrm.algo.leave.countModeMap}；未知假别按 SCHEDULED） */
    private String countMode(String leaveType) {
        Map<String, String> map = algoProperties.getLeave().getCountModeMap();
        String mode = map == null ? null : map.get(leaveType);
        return mode == null ? LeaveConstants.COUNT_MODE_SCHEDULED : mode;
    }

    // ==================== 内部：通知 ====================

    /** 提交通知（对齐 Mock notifyApply）：待初审发站长；待终审发老板；无站长降级补发给申请人 */
    private void notifyApply(LeaveRequest leave, Employee applicant, Employee stationApprover, String status) {
        String summary = typeLabel(leave.getLeaveType()) + " " + rangeText(leave) + " 共 "
                + plain(leave.getNaturalDays()) + " 天";
        if (LeaveConstants.STATUS_PENDING_STATION.equals(status)) {
            notify(leave, stationApprover, LeaveConstants.NOTIFY_APPLY,
                    applicant.getRealName() + " 提交了请假申请", summary + "，待您初审");
            return;
        }
        boolean applicantIsStationAdmin = RoleEnum.STATION_ADMIN.name().equals(applicant.getRole());
        notify(leave, bossOf(), LeaveConstants.NOTIFY_APPLY,
                applicant.getRealName() + (applicantIsStationAdmin ? "（站长）" : "") + "提交了请假申请",
                summary + "，待您终审");
        if (stationApprover == null && !applicantIsStationAdmin) {
            notify(leave, applicant, LeaveConstants.NOTIFY_RESULT, "请假申请已直接提交终审",
                    "本站暂无在职站长，您的申请已直接提交老板终审");
        }
    }

    /** 站内信投递：目标缺失留 NOTIFY_SKIP 排障痕迹；通知开关关闭则不投递（对齐 Mock notify + D5） */
    private void notify(LeaveRequest leave, Employee target, int type, String title, String content) {
        if (!algoProperties.getLeave().isNotifyEnabled()) {
            return;
        }
        if (target == null || target.getId() == null) {
            pushLog(leave, LeaveConstants.ACTION_NOTIFY_SKIP, null, null, null, null, null,
                    "通知目标缺失，未写入通知：" + title);
            return;
        }
        notificationService.sendSystem(target.getId(), type, title, content, LeaveConstants.BIZ_TYPE, leave.getId());
    }

    // ==================== 内部：留痕 ====================

    private void pushLog(LeaveRequest leave, String action, LoginUser operator, String fromStatus, String toStatus,
                         Object before, Object after, String remark) {
        LeaveLog row = new LeaveLog();
        row.setLeaveId(leave.getId());
        row.setAction(action);
        if (operator != null) {
            row.setOperatorId(operator.getUserId());
            row.setOperatorName(operatorName(operator.getUserId()));
            row.setOperatorRole(operator.getRole());
        }
        row.setTime(LocalDateTime.now());
        row.setFromStatus(fromStatus);
        row.setToStatus(toStatus);
        row.setBefore(JsonUtil.write(before));
        row.setAfter(JsonUtil.write(after));
        row.setRemark(remark);
        leaveLogMapper.insert(row);
    }

    // ==================== 内部：出参组装 ====================

    /** 单条/批量统一组装：批量补齐姓名、驿站名、留痕，避免列表 N+1 查询 */
    private List<LeaveVO> toVOList(List<LeaveRequest> rows, LoginUser user) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = new LinkedHashSet<>();
        Set<Long> stationIds = new LinkedHashSet<>();
        Set<Long> leaveIds = new LinkedHashSet<>();
        for (LeaveRequest row : rows) {
            addIfNotNull(employeeIds, row.getEmployeeId());
            addIfNotNull(employeeIds, row.getStationApproverId());
            addIfNotNull(employeeIds, row.getApproverId());
            addIfNotNull(employeeIds, row.getRevokerId());
            addIfNotNull(stationIds, row.getStationId());
            if (row.getId() != null) {
                leaveIds.add(row.getId());
            }
        }
        Map<Long, String> employeeNames = employeeNames(employeeIds);
        Map<Long, String> stationNames = stationNames(stationIds);
        Map<Long, List<LeaveLog>> logs = logsByLeave(leaveIds);

        List<LeaveVO> list = new ArrayList<>(rows.size());
        for (LeaveRequest row : rows) {
            list.add(toVO(row, user, employeeNames, stationNames, logs));
        }
        return list;
    }

    private LeaveVO toVO(LeaveRequest leave, LoginUser user, Map<Long, String> employeeNames,
                         Map<Long, String> stationNames, Map<Long, List<LeaveLog>> logs) {
        LeaveVO vo = new LeaveVO();
        vo.setId(leave.getId());
        vo.setEmployeeId(leave.getEmployeeId());
        vo.setEmployeeName(employeeNames.get(leave.getEmployeeId()));
        vo.setStationId(leave.getStationId());
        vo.setStationName(stationNames.get(leave.getStationId()));
        vo.setLeaveType(leave.getLeaveType());
        vo.setStartDate(leave.getStartDate());
        vo.setStartPeriod(leave.getStartPeriod());
        vo.setEndDate(leave.getEndDate());
        vo.setEndPeriod(leave.getEndPeriod());
        vo.setReason(leave.getReason());
        vo.setNaturalDays(leave.getNaturalDays());
        vo.setCountedDays(leave.getCountedDays());
        vo.setCountedDaysSnapshot(parseSnapshot(leave.getCountedDaysSnapshot()));
        vo.setStatus(leave.getStatus());
        vo.setRejectStage(leave.getRejectStage());
        vo.setApproverId(leave.getApproverId());
        vo.setApproverName(employeeNames.get(leave.getApproverId()));
        vo.setApproveTime(leave.getApproveTime());
        vo.setApproveRemark(leave.getApproveRemark());
        vo.setStationApproverId(leave.getStationApproverId());
        vo.setStationApproverName(employeeNames.get(leave.getStationApproverId()));
        vo.setStationApproveTime(leave.getStationApproveTime());
        vo.setStationApproveRemark(leave.getStationApproveRemark());
        vo.setCancelById(leave.getCancelById());
        vo.setCancelTime(leave.getCancelTime());
        vo.setRevokerId(leave.getRevokerId());
        vo.setRevokerName(employeeNames.get(leave.getRevokerId()));
        vo.setRevokeTime(leave.getRevokeTime());
        vo.setRevokeReason(leave.getRevokeReason());
        vo.setOriginId(leave.getOriginId());
        vo.setApplyTime(leave.getApplyTime());
        vo.setUpdateTime(leave.getUpdateTime());

        List<LeaveLog> logRows = logs.getOrDefault(leave.getId(), List.of());
        List<LeaveLogVO> handleLog = new ArrayList<>(logRows.size());
        for (LeaveLog logRow : logRows) {
            handleLog.add(toLogVO(logRow));
        }
        vo.setHandleLog(handleLog);

        // 派生动作标志（前端按角色直接渲染按钮）
        Long userId = user == null ? null : user.getUserId();
        String role = user == null ? null : user.getRole();
        vo.setCanEdit(LeaveAccessPolicy.canEdit(userId, leave.getEmployeeId(), leave.getStatus()));
        vo.setCanCancel(LeaveAccessPolicy.canCancel(userId, leave.getEmployeeId(), leave.getStatus()));
        vo.setCanRevoke(LeaveAccessPolicy.canRevoke(role, leave.getStatus()));
        return vo;
    }

    private LeaveLogVO toLogVO(LeaveLog row) {
        LeaveLogVO vo = new LeaveLogVO();
        vo.setId(row.getId());
        vo.setLeaveId(row.getLeaveId());
        vo.setAction(row.getAction());
        vo.setOperatorId(row.getOperatorId());
        vo.setOperatorName(row.getOperatorName());
        vo.setOperatorRole(row.getOperatorRole());
        vo.setTime(row.getTime());
        vo.setFromStatus(row.getFromStatus());
        vo.setToStatus(row.getToStatus());
        vo.setBefore(parseJsonObject(row.getBefore()));
        vo.setAfter(parseJsonObject(row.getAfter()));
        vo.setRemark(row.getRemark());
        return vo;
    }

    private Map<Long, String> employeeNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Employee::getId, Employee::getRealName).in(Employee::getId, ids);
        Map<Long, String> map = new LinkedHashMap<>();
        for (Employee employee : employeeMapper.selectList(wrapper)) {
            map.put(employee.getId(), employee.getRealName());
        }
        return map;
    }

    private Map<Long, String> stationNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Station::getId, Station::getStationName).in(Station::getId, ids);
        Map<Long, String> map = new LinkedHashMap<>();
        for (Station station : stationMapper.selectList(wrapper)) {
            map.put(station.getId(), station.getStationName());
        }
        return map;
    }

    private Map<Long, List<LeaveLog>> logsByLeave(Collection<Long> leaveIds) {
        if (leaveIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<LeaveLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LeaveLog::getLeaveId, leaveIds)
                .orderByAsc(LeaveLog::getLeaveId)
                .orderByAsc(LeaveLog::getId);
        Map<Long, List<LeaveLog>> map = new LinkedHashMap<>();
        for (LeaveLog row : leaveLogMapper.selectList(wrapper)) {
            map.computeIfAbsent(row.getLeaveId(), key -> new ArrayList<>()).add(row);
        }
        return map;
    }

    // ==================== 内部：只读取数 ====================

    private LeaveRequest findLeave(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_EXISTS);
        }
        LeaveRequest leave = leaveRequestMapper.selectById(id);
        if (leave == null) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_EXISTS);
        }
        return leave;
    }

    private Employee loadEmployee(Long employeeId) {
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BusinessException(ErrorCode.LEAVE_NO_PERMISSION);
        }
        return employee;
    }

    private String employeeName(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        return employee == null ? null : employee.getRealName();
    }

    private String operatorName(Long employeeId) {
        return employeeName(employeeId);
    }

    /** 本站可用站长（在职 + 启用），无则 null（对齐 Mock stationAdminOf） */
    private Employee findStationAdmin(Long stationId) {
        if (stationId == null) {
            return null;
        }
        List<Employee> admins = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .select(Employee::getId, Employee::getRealName, Employee::getStationId, Employee::getRole)
                .eq(Employee::getRole, RoleEnum.STATION_ADMIN.name())
                .eq(Employee::getStationId, stationId)
                .eq(Employee::getStatus, 1)
                .orderByAsc(Employee::getId));
        return admins.isEmpty() ? null : admins.get(0);
    }

    /** 老板（在职 + 启用 ADMIN），无则 null */
    private Employee bossOf() {
        List<Employee> admins = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .select(Employee::getId, Employee::getRealName, Employee::getRole)
                .eq(Employee::getRole, RoleEnum.ADMIN.name())
                .eq(Employee::getStatus, 1)
                .orderByAsc(Employee::getId));
        return admins.isEmpty() ? null : admins.get(0);
    }

    /** 申请人（用于通知接收人；已删除员工由逻辑删除过滤掉 → 返回 null → NOTIFY_SKIP） */
    private Employee applicantOf(LeaveRequest leave) {
        return leave.getEmployeeId() == null ? null : employeeMapper.selectById(leave.getEmployeeId());
    }

    private String stationName(Long stationId) {
        if (stationId == null) {
            return null;
        }
        Station station = stationMapper.selectById(stationId);
        return station == null ? null : station.getStationName();
    }

    private LeaveSetting firstSetting() {
        List<LeaveSetting> rows = leaveSettingMapper.selectList(
                new LambdaQueryWrapper<LeaveSetting>().orderByAsc(LeaveSetting::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Boolean readDeductEnabled() {
        LeaveSetting row = firstSetting();
        if (row == null) {
            return algoProperties.getLeave().isDeductEnabledDefault();
        }
        return row.getLeaveDeductEnabled() != null && row.getLeaveDeductEnabled() == 1;
    }

    // ==================== 内部：小工具 ====================

    private LoginUser currentUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private Long currentUserId() {
        return currentUser().getUserId();
    }

    private int pageNum(Integer pageNum) {
        return pageNum == null ? 1 : pageNum;
    }

    private int pageSize(Integer pageSize) {
        return pageSize == null ? 10 : pageSize;
    }

    /** 'PENDING' 聚合虚拟值展开为两个待审态；空 → null（不过滤） */
    private List<String> expandStatus(String status) {
        if (isBlank(status)) {
            return null;
        }
        if (LeaveConstants.AGGREGATE_PENDING.equals(status)) {
            return List.of(LeaveConstants.STATUS_PENDING_STATION, LeaveConstants.STATUS_PENDING_BOSS);
        }
        return List.of(status);
    }

    private LeaveForm toForm(LeaveRequest leave) {
        return new LeaveForm(leave.getLeaveType(), leave.getStartDate(), leave.getStartPeriod(),
                leave.getEndDate(), leave.getEndPeriod(), leave.getReason());
    }

    private void applyForm(LeaveRequest leave, LeaveForm form) {
        leave.setLeaveType(form.leaveType());
        leave.setStartDate(form.startDate());
        leave.setStartPeriod(form.startPeriod());
        leave.setEndDate(form.endDate());
        leave.setEndPeriod(form.endPeriod());
        leave.setReason(form.reason());
    }

    private LeaveUnitRange rangeOf(LeaveRequest leave) {
        return LeaveIntervalPolicy.unitRange(leave.getStartDate(), leave.getStartPeriod(),
                leave.getEndDate(), leave.getEndPeriod());
    }

    /** 变更前后快照（编辑留痕用；字段与 Mock snapshotOf 一致，日期输出为 yyyy-MM-dd 字符串） */
    private Object snapshotOf(LeaveRequest leave) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("leaveType", leave.getLeaveType());
        snapshot.put("startDate", leave.getStartDate() == null ? null : leave.getStartDate().format(DATE_FMT));
        snapshot.put("startPeriod", leave.getStartPeriod());
        snapshot.put("endDate", leave.getEndDate() == null ? null : leave.getEndDate().format(DATE_FMT));
        snapshot.put("endPeriod", leave.getEndPeriod());
        snapshot.put("reason", leave.getReason());
        return snapshot;
    }

    private String overlapMessage(LeaveRequest other) {
        return "该时间段与已有申请重叠：" + typeLabel(other.getLeaveType()) + " " + rangeText(other)
                + "（" + statusLabel(other.getStatus()) + "）。请调整时间，或先撤销原申请再提交。";
    }

    /** 区间展示串：{起} ~ {止}；同日压缩为「2026-10-01 上午 ~ 下午」 */
    private String rangeText(LeaveRequest leave) {
        String head = leave.getStartDate().format(DATE_FMT) + " " + halfDayLabel(leave.getStartPeriod());
        String tail = leave.getEndDate().equals(leave.getStartDate())
                ? halfDayLabel(leave.getEndPeriod())
                : leave.getEndDate().format(DATE_FMT) + " " + halfDayLabel(leave.getEndPeriod());
        return head + " ~ " + tail;
    }

    private String typeLabel(String leaveType) {
        return LeaveConstants.typeLabel(leaveType);
    }

    private String statusLabel(String status) {
        return LeaveConstants.statusLabel(status);
    }

    private String halfDayLabel(String period) {
        String label = LeaveConstants.HALF_DAY_LABEL.get(period);
        return label == null ? period : label;
    }

    /** LocalDate → 严格解析（格式不合法或非真实日期 → null） */
    private LocalDate parseStrict(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FMT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 站内信数值内插（JS 风格：整数不带小数、去尾零） */
    private String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP);
    }

    private LeaveSnapshotVO parseSnapshot(String json) {
        return JsonUtil.read(json, new TypeReference<LeaveSnapshotVO>() {
        });
    }

    private Object parseJsonObject(String json) {
        return JsonUtil.read(json, new TypeReference<Object>() {
        });
    }

    private void addIfNotNull(Set<Long> target, Long value) {
        if (value != null) {
            target.add(value);
        }
    }

    private Long parseStationId(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(stationId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void requireDatePattern(String value, String field) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 格式须为 YYYY-MM-DD");
        }
    }

    private void requirePeriod(String value, String field) {
        if (!LeaveConstants.PERIOD_AM.equals(value) && !LeaveConstants.PERIOD_PM.equals(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 仅支持 AM / PM");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean textLen(String value, int min, int max) {
        int length = value == null ? 0 : value.trim().length();
        return length >= min && length <= max;
    }

    /** 请假表单值对象（校验通过后的内部载体；避免在方法间反复解析字符串日期） */
    private record LeaveForm(String leaveType, LocalDate startDate, String startPeriod,
                             LocalDate endDate, String endPeriod, String reason) {

        LeaveUnitRange unitRange() {
            return LeaveIntervalPolicy.unitRange(startDate, startPeriod, endDate, endPeriod);
        }
    }
}
