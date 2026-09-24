package com.qiujie.dto.parcel;

import com.qiujie.dto.support.StationScopedQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 包裹列表查询（GET /parcels，对齐 Mock {@code routes/parcel.js#list} 入参）。
 * <p>
 * 入参字段集：{@code stationId / status / waybillNo / startTime / endTime / pageNum / pageSize}。
 * {@code stationId} 经 L1 数据范围收敛（非 ADMIN 强制本人驿站；无归属收敛为哨兵 -1 → 空结果）。
 * <p>
 * {@code cursor} 为**可选契约扩展**（opt-in 游标翻页）：不传时行为与 Mock 逐位一致（按 pageNum OFFSET）；
 * 传入时启用 keyset 分页，响应附加 {@code nextCursor}。
 * TODO(扩展): {@code cursor/nextCursor} 属 P10 新增，待主智能体回填 api.md 与架构文档后转正式契约。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ParcelQuery extends StationScopedQuery {

    /** 状态筛选：0=待入库 1=在库待取 2=已取件 3=异常 4=已退回；缺省=不限（对齐 Mock，不额外限值域） */
    private Integer status;

    /** 运单号精确查（走 idx_parcel_station_waybill） */
    private String waybillNo;

    /** 入库时间下界（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd；非法值忽略该筛选） */
    private String startTime;

    /** 入库时间上界（同上） */
    private String endTime;

    /** 游标（上一页返回的 nextCursor；可选，opt-in） */
    private String cursor;
}
