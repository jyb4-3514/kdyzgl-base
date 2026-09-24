package com.qiujie.dto.support;

import com.qiujie.util.DataScopeContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带驿站归属筛选的分页查询基类（L1 数据范围收敛的消费端）。
 * <p>
 * 非 ADMIN 的 {@code stationId} 由 {@code QueryDataScopeInterceptor} 在 {@link DataScopeContext} 中强制写入，
 * 本类 {@link #getStationId()} 读取时叠加该收敛值，从而「一处收敛、各 Service 不重复写」（对齐 Mock {@code applyDataScope}）。
 * <ul>
 *   <li>ADMIN / 公开端点 / 未认证：不收敛，返回入参原值；</li>
 *   <li>非 ADMIN：一律返回本人驿站 id（无归属时为 {@link DataScopeContext#NO_DATA_STATION_ID} 哨兵，收敛到无数据）。</li>
 * </ul>
 * 注意：只收敛 {@code stationId}，<b>不收敛 {@code employeeId}</b>；「只看本人」端点直接以登录身份为准。
 * 用法：所有支持按驿站筛选的列表/统计端点入参 DTO 继承本类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StationScopedQuery extends PageQuery {

    /** 归属驿站筛选；非 ADMIN 读取时被强制收敛为本人驿站 */
    private Long stationId;

    /** 读取时叠加 L1 数据范围收敛（Lombok 检测到本方法已存在，不再生成同名 getter） */
    public Long getStationId() {
        Long converged = DataScopeContext.get();
        return converged != null ? converged : stationId;
    }
}
