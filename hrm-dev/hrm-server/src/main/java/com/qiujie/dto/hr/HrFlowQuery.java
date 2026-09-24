package com.qiujie.dto.hr;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 入职 / 离职流程列表查询（共用，对齐 Mock {@code listOnboardings/listOffboardings}）：
 * {@code status} 状态过滤、{@code stationId} 驿站过滤、{@code keyword} 匹配候选人/员工姓名或流程编号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HrFlowQuery extends PageQuery {

    /** 流程状态：IN_PROGRESS/COMPLETED/REJECTED */
    private String status;

    /** 驿站过滤 */
    private Long stationId;

    /** 关键词：候选人/员工姓名或流程编号 */
    private String keyword;
}
