package com.qiujie.service.finance.port.impl;

import com.qiujie.service.finance.port.ApprovedLeaveDaysPort;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 已批请假天数端口降级实现（请假域未装配时的兜底）。
 * <p>
 * 行为口径与 P6 当前实现一致：请假天数按 0、扣款开关按 false（与 Mock 默认一致），
 * 保证「请假域缺席」时算薪结果不因端口缺失而失败；真实实现由请假域提供 Bean 后本兜底自动失效。
 */
public class UnavailableApprovedLeaveDaysPort implements ApprovedLeaveDaysPort {

    @Override
    public BigDecimal approvedLeaveDays(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return BigDecimal.ZERO;
    }

    @Override
    public boolean leaveDeductEnabled() {
        return false;
    }
}
