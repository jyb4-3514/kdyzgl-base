package com.qiujie.dto.sync;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 同步任务列表查询（api.md / Mock {@code routes/syncTask.js#list}）。
 * <p>
 * {@code stationId} 继承 {@link StationScopedQuery}：非 ADMIN 由 L1 数据范围静默收敛为本人驿站（列表越权口径 = 静默）。
 * {@code status} / {@code keyword}（匹配 batchNo）为可选筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SyncTaskQuery extends StationScopedQuery {

    /** 状态筛选：0=待领取 1=执行中 2=成功 3=失败 */
    private Integer status;

    /** 批次号关键词（LIKE 包含匹配） */
    private String keyword;
}
