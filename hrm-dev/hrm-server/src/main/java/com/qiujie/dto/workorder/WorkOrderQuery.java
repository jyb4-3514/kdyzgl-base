package com.qiujie.dto.workorder;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工单列表查询（对齐 Mock {@code workOrder.list} 的 params）。
 * <p>
 * {@code stationId} 由 L1 数据范围静默收敛（非 ADMIN 强制为本人驿站，见 {@code StationScopedQuery}）；
 * {@code pageSize} 越界由 {@code PageQuery} 的 Bean Validation 统一回 400（文案「每页条数须为 1-100」）。
 * <p>
 * 超时筛选：新参数 {@code overdueUnhandled} 为准，旧参数 {@code overSla} 作为别名继续可用
 * （Mock 两者并存，值判定为 {@code "1" / "true"}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderQuery extends StationScopedQuery {

    /** 状态筛选：0/1/2/3（可空） */
    private Integer status;

    /** 类型筛选：1-4（可空） */
    private Integer type;

    /** 优先级筛选：0-2（可空） */
    private Integer priority;

    /** 处理人筛选（可空） */
    private Long assigneeId;

    /** 关键词：匹配工单号或标题（可空） */
    private String keyword;

    /** 只看超时未处理（新参数名） */
    private String overdueUnhandled;

    /** 只看超时未处理（旧参数名别名，兼容存量页面） */
    private String overSla;
}
