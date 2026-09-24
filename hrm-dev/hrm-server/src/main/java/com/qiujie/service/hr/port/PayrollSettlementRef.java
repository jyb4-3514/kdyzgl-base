package com.qiujie.service.hr.port;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 离职结算单的跨域返回引用（财务域 → 人事域）。
 * <p>
 * 对齐 Mock {@code settlementRef = {payrollId, payrollNo, amount}}：人事域只落引用与快照，
 * 不持有工资单实体（数据所有权仍归财务域）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSettlementRef {

    /** 结算单 id（写入 hr_flow.settlement_payroll_id） */
    private Long payrollId;

    /** 结算单号快照 */
    private String payrollNo;

    /** 结算金额快照（净额，口径归财务域） */
    private BigDecimal amount;
}
