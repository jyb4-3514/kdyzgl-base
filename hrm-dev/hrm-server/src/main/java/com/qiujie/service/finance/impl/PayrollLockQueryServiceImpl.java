package com.qiujie.service.finance.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiujie.config.AlgoProperties;
import com.qiujie.entity.Payroll;
import com.qiujie.mapper.PayrollMapper;
import com.qiujie.service.finance.PayrollLockInfo;
import com.qiujie.service.finance.PayrollLockQueryService;
import com.qiujie.service.finance.support.PayrollLockPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 账期锁查询实现（对齐 Mock {@code findLockingPayroll}）。
 * <p>
 * 说明：Mock 的判定为 {@code p.month === month && !EDITABLE_STATUS.includes(p.status)}——<b>不限单据类型</b>，
 * 本实现保持一致（月度单与结算单任意一先出账即锁）。
 */
@Service
@RequiredArgsConstructor
public class PayrollLockQueryServiceImpl implements PayrollLockQueryService {

    private final PayrollMapper payrollMapper;
    private final AlgoProperties algoProperties;

    @Override
    @Transactional(readOnly = true)
    public boolean isMonthLocked(Long employeeId, String month) {
        return findLockingPayroll(employeeId, month).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayrollLockInfo> findLockingPayroll(Long employeeId, String month) {
        if (employeeId == null || month == null || month.isBlank()) {
            return Optional.empty();
        }
        List<Payroll> rows = payrollMapper.selectList(new LambdaQueryWrapper<Payroll>()
                .select(Payroll::getId, Payroll::getPayrollNo, Payroll::getStatus)
                .eq(Payroll::getEmployeeId, employeeId)
                .eq(Payroll::getMonth, month)
                .orderByAsc(Payroll::getId));
        String policy = algoProperties.getLeave().getLockStatusPolicy();
        Payroll locking = PayrollLockPolicy.findLocking(rows, policy);
        return locking == null
                ? Optional.empty()
                : Optional.of(new PayrollLockInfo(locking.getId(), locking.getPayrollNo(), locking.getStatus()));
    }
}
