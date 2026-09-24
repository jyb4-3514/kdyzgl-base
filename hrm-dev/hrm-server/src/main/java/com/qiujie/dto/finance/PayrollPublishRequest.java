package com.qiujie.dto.finance;

import lombok.Data;

import java.util.List;

/**
 * 批量发布入参（对齐 Mock {@code publish}）：{@code ids} 与「{@code month + stationId}」二选一。
 * 后者用于「整月一键发布」——只发布已通过（APPROVED）的单据，其余计入 skipped。
 */
@Data
public class PayrollPublishRequest {

    /** 工资单 id 列表（可空；非空时优先） */
    private List<Long> ids;

    /** 账期 yyyy-MM（ids 为空时必填） */
    private String month;

    /** 驿站筛选（配合 month 使用，可空） */
    private Long stationId;
}
