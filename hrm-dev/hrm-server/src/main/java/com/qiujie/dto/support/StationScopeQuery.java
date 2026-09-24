package com.qiujie.dto.support;

import com.qiujie.util.DataScopeContext;
import lombok.Data;

/**
 * 非分页的驿站归属查询基类（L1 数据范围收敛的消费端，见 {@link StationScopedQuery}）。
 * <p>
 * 为什么单列一个非分页基类：打卡规则 / 概况 / 明细 / 班次等端点按契约<b>不分页</b>，
 * 若继承 {@code PageQuery} 会把分页参数带入并在入参校验阶段产生无关的 400 风险。
 * 收敛口径与 {@code StationScopedQuery} 完全一致：非 ADMIN 读 {@link DataScopeContext} 强制值。
 */
@Data
public class StationScopeQuery {

    /** 归属驿站筛选；非 ADMIN 读取时被强制收敛为本人驿站 */
    private Long stationId;

    /** 读取时叠加 L1 数据范围收敛（Lombok 检测到本方法已存在，不再生成同名 getter） */
    public Long getStationId() {
        Long converged = DataScopeContext.get();
        return converged != null ? converged : stationId;
    }
}
