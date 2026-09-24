package com.qiujie.service.hr.port;

import lombok.Data;

/**
 * 创建离职结算单的命令（人事域 → 财务域的跨域请求）。
 * <p>
 * 字段与 Mock {@code routes/hr.js} 调 {@code createSettlementPayroll} 的入参一致：
 * 员工、结算月份、来源离职流程 id、备注。结算金额口径由财务域计算，人事域不参与。
 */
@Data
public class PayrollSettlementCommand {

    /** 员工 id */
    private Long employeeId;

    /** 结算月份（yyyy-MM，取最后工作日的月份） */
    private String month;

    /** 来源离职流程 id（供财务侧回链） */
    private Long offboardingId;

    /** 备注（含最后工作日说明） */
    private String remark;
}
