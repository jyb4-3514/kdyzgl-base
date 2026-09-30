package com.qiujie.dto.finance;

import lombok.Data;

/**
 * 手工调整对账汇总入参（I-10，api.md §4.12.22）。
 * <p>
 * {@code month} 必填（yyyy-MM）；{@code stationId} 可选（按驿站过滤，员工归属口径）。
 */
@Data
public class PayrollManualAdjustmentQuery {

    /** 账期（yyyy-MM），必填 */
    private String month;

    /** 驿站 id，可选（不传 = 全驿站） */
    private Long stationId;
}
