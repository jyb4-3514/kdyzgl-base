package com.qiujie.service.finance.port;

import com.qiujie.service.finance.PayrollService;
import com.qiujie.service.hr.port.PayrollSettlementCommand;
import com.qiujie.service.hr.port.PayrollSettlementPort;
import com.qiujie.service.hr.port.PayrollSettlementRef;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 人事域跨域端口 {@link PayrollSettlementPort} 的财务域实现（ADR-03 断环，P6 接入点）。
 * <p>
 * 为什么这样接：人事域只依赖端口接口，财务域提供实现 Bean → 依赖方向恒为 {@code finance → hr}（实现方依赖接口方），
 * {@code hr ↔ finance} 环被断开。本 Bean 注册后，人事域 {@code HrPortConfig} 的
 * {@code @ConditionalOnMissingBean} 降级实现（{@code UnavailablePayrollSettlementPort}）自动失效。
 * <p>
 * 语义（与 Mock {@code routes/hr.js} 的 SETTLEMENT 编排一致）：返回的 {@code amount} = 结算单净额（netAmount），
 * 由人事域回填 {@code hr_flow.settlement_payroll_id/no/amount}。
 */
@Component
@RequiredArgsConstructor
public class PayrollSettlementAdapter implements PayrollSettlementPort {

    private final PayrollService payrollService;

    @Override
    public PayrollSettlementRef createSettlement(PayrollSettlementCommand command) {
        return payrollService.createSettlement(command);
    }
}
