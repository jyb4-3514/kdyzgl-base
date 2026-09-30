package com.qiujie.dto.finance;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工资单列表查询（仅 ADMIN，对齐 Mock {@code payrollList}）。
 * 分页参数继承 {@link PageQuery}（越界 → 400）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollQuery extends PageQuery {

    /** 账期 yyyy-MM（可空） */
    private String month;

    /** 驿站（可空） */
    private Long stationId;

    /** 员工（可空） */
    private Long employeeId;

    /** 状态（可空；取值须为八态之一） */
    private String status;

    /** 单据类型（可空；MONTHLY / SETTLEMENT） */
    private String billType;

    /** 关键词（匹配员工姓名或工资单号） */
    private String keyword;
}
