package com.qiujie.service.leave.port.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.LeaveRequest;
import com.qiujie.mapper.LeaveRequestMapper;
import com.qiujie.service.finance.port.ApprovedLeaveDaysPort;
import com.qiujie.service.leave.LeaveService;
import com.qiujie.service.leave.port.ScheduledDatesQuery;
import com.qiujie.service.leave.support.LeaveConstants;
import com.qiujie.service.leave.support.LeaveIntervalPolicy;
import com.qiujie.service.leave.support.LeaveUnitRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 已批请假天数端口实现（请假域 → 财务域只读，架构 §2.2；口语名 {@code approvedLeaveDays}）。
 * <p>
 * 口径与 Mock {@code leaveStore.approvedLeaveDays} 逐位一致：
 * <ul>
 *   <li>只统计 {@code APPROVED} 单据；</li>
 *   <li>先把每单的半天单元区间与调用方区间取交（区间算法，O(1)）；</li>
 *   <li>NATURAL 假别按交区间自然天数；SCHEDULED 假别在排班单元有序数组上两次二分（O(log m)）计数；</li>
 *   <li>跨月单由调用方按月区间调用天然切分，不在本方法内做整单归属判断（避免「整单落起始月」错法）。</li>
 * </ul>
 * 依赖方向恒为 {@code leave → finance}（实现财务域的端口接口），不产生环。
 */
@Service
@RequiredArgsConstructor
public class ApprovedLeaveDaysPortImpl implements ApprovedLeaveDaysPort {

    private final LeaveRequestMapper leaveRequestMapper;
    private final ScheduledDatesQuery scheduledDatesQuery;
    private final LeaveService leaveService;
    private final AlgoProperties algoProperties;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal approvedLeaveDays(Long employeeId, LocalDate startDate, LocalDate endDate) {
        if (employeeId == null) {
            return BigDecimal.ZERO;
        }
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(LeaveRequest::getId, LeaveRequest::getLeaveType, LeaveRequest::getStartDate,
                        LeaveRequest::getStartPeriod, LeaveRequest::getEndDate, LeaveRequest::getEndPeriod)
                .eq(LeaveRequest::getEmployeeId, employeeId)
                .eq(LeaveRequest::getStatus, LeaveConstants.STATUS_APPROVED)
                // 与调用方区间相交者必有 end_date ≥ 起、start_date ≤ 止（走 idx_leave_request_date）
                .ge(startDate != null, LeaveRequest::getEndDate, startDate)
                .le(endDate != null, LeaveRequest::getStartDate, endDate)
                .orderByAsc(LeaveRequest::getId);
        List<LeaveRequest> rows = leaveRequestMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long lowUnit = startDate == null ? Long.MIN_VALUE : LeaveIntervalPolicy.unitOf(startDate, LeaveConstants.PERIOD_AM);
        long highUnit = endDate == null ? Long.MAX_VALUE : LeaveIntervalPolicy.unitOf(endDate, LeaveConstants.PERIOD_PM);

        // 排班单元数组懒加载：调用方给定完整区间时只需查一次（财务按月调用即此路径）
        long[] sharedSchedUnits = null;
        double sum = 0;
        for (LeaveRequest row : rows) {
            LeaveUnitRange range = LeaveIntervalPolicy.unitRange(row.getStartDate(), row.getStartPeriod(),
                    row.getEndDate(), row.getEndPeriod());
            long clipStart = Math.max(range.startUnit(), lowUnit);
            long clipEnd = Math.min(range.endUnit(), highUnit);
            if (clipStart > clipEnd) {
                continue;
            }
            LeaveUnitRange clipped = new LeaveUnitRange(clipStart, clipEnd);
            if (LeaveConstants.COUNT_MODE_NATURAL.equals(countMode(row.getLeaveType()))) {
                sum += clipped.naturalDays();
                continue;
            }
            if (startDate != null && endDate != null) {
                if (sharedSchedUnits == null) {
                    sharedSchedUnits = scheduleUnits(employeeId, startDate, endDate);
                }
                sum += LeaveIntervalPolicy.countedDaysByRange(sharedSchedUnits, clipped);
            } else {
                // 调用方区间不完整时按 Mock 口径退化为「本单区间」查排班（逐单查询）
                LocalDate from = startDate != null ? startDate : row.getStartDate();
                LocalDate to = endDate != null ? endDate : row.getEndDate();
                sum += LeaveIntervalPolicy.countedDaysByRange(scheduleUnits(employeeId, from, to), clipped);
            }
        }
        return BigDecimal.valueOf(sum).setScale(1, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean leaveDeductEnabled() {
        return leaveService.isLeaveDeductEnabled();
    }

    private long[] scheduleUnits(Long employeeId, LocalDate from, LocalDate to) {
        List<LocalDate> scheduled = scheduledDatesQuery.scheduledDates(employeeId, from, to);
        return LeaveIntervalPolicy.scheduleUnits(scheduled);
    }

    /** 假别 → 计薪口径（唯一真源：{@code hrm.algo.leave.countModeMap}；未知假别按 SCHEDULED） */
    private String countMode(String leaveType) {
        java.util.Map<String, String> map = algoProperties.getLeave().getCountModeMap();
        String mode = map == null ? null : map.get(leaveType);
        return mode == null ? LeaveConstants.COUNT_MODE_SCHEDULED : mode;
    }
}
