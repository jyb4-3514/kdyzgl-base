package com.qiujie.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qiujie.entity.Parcel;
import com.qiujie.service.parcel.support.ParcelDailyCount;
import com.qiujie.service.parcel.support.ParcelStationStat;
import com.qiujie.service.parcel.support.ParcelSummaryRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 包裹 Mapper（大表域，db.md §8.10.1）。20 万级，查询纪律见架构 §4.3：
 * <ul>
 *   <li><b>禁止 {@code SELECT *}</b>：列表/聚合一律显式列；</li>
 *   <li>列表走 {@code idx_parcel_station_status_inbound (station_id, status, inbound_time DESC)}：
 *       固定 {@code station_id}（+ 可选 status）后按入库时间倒序，InnoDB 二级索引隐式追加主键升序，
 *       恰好匹配 {@code ORDER BY inbound_time DESC, id ASC} → <b>无 {@code Using filesort}</b>；</li>
 *   <li>仅驿站筛选走 {@code idx_parcel_station_inbound (station_id, inbound_time DESC)}；
 *       运单号精确查走 {@code idx_parcel_station_waybill}；</li>
 *   <li>逻辑删除列在自定义 SQL 中须显式 {@code is_deleted = 0}（MyBatis-Plus 仅对内置方法自动追加）；</li>
 *   <li>分页统一 {@code LIMIT ? OFFSET ?}（MySQL 8 与 PostgreSQL 双库同写出）、列名 snake_case、聚合用
 *       {@code SUM(CASE WHEN ... THEN 1 ELSE 0 END)}（避免 PG 对 boolean 求和的差异）。</li>
 * </ul>
 * <p>
 * 预期的 {@code EXPLAIN} 形态（服务器实测收敛，db.md U-3）：
 * 按驿站+状态列表 → {@code type=ref}、{@code key=idx_parcel_station_status_inbound}、{@code Extra} 无 {@code Using filesort}；
 * 仅按驿站列表 → {@code key=idx_parcel_station_inbound}；运单号精确查 → {@code key=idx_parcel_station_waybill}。
 */
public interface ParcelMapper extends BaseMapper<Parcel> {

    /**
     * 分页查询（显式列；游标优先、OFFSET 兜底）。
     * <p>
     * 游标（keyset）条件：{@code (inbound_time < cursorTime) OR (inbound_time = cursorTime AND id > cursorId)}，
     * 承接上一页末行的 {@code (inbound_time,id)}，扫描行恒定 O(log N + pageSize)（算法 S7：20 万级 38 行）。
     * 未传游标时用 {@code OFFSET}（{@code pageNum} 语义兜底）。
     */
    @Select("<script>"
            + "SELECT id, station_id, waybill_no, status, receiver_name, receiver_phone, shelf_code, "
            + "inbound_time, pickup_employee_id, pickup_time, sync_batch_no, remark, create_time, update_time "
            + "FROM parcel WHERE is_deleted = 0 "
            + "<if test='stationId != null'>AND station_id = #{stationId} </if>"
            + "<if test='status != null'>AND status = #{status} </if>"
            + "<if test='waybillNo != null'>AND waybill_no = #{waybillNo} </if>"
            + "<if test='startTime != null'>AND inbound_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND inbound_time &lt;= #{endTime} </if>"
            + "<if test='cursorTime != null'>AND (inbound_time &lt; #{cursorTime} "
            + "OR (inbound_time = #{cursorTime} AND id &gt; #{cursorId})) </if>"
            + "ORDER BY inbound_time DESC, id ASC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Parcel> selectPage(@Param("stationId") Long stationId,
                            @Param("status") Integer status,
                            @Param("waybillNo") String waybillNo,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime,
                            @Param("cursorTime") LocalDateTime cursorTime,
                            @Param("cursorId") Long cursorId,
                            @Param("offset") long offset,
                            @Param("limit") long limit);

    /** 与 {@link #selectPage} 同口径的命中总数（走同一索引的覆盖计数） */
    @Select("<script>"
            + "SELECT COUNT(*) FROM parcel WHERE is_deleted = 0 "
            + "<if test='stationId != null'>AND station_id = #{stationId} </if>"
            + "<if test='status != null'>AND status = #{status} </if>"
            + "<if test='waybillNo != null'>AND waybill_no = #{waybillNo} </if>"
            + "<if test='startTime != null'>AND inbound_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND inbound_time &lt;= #{endTime} </if>"
            + "</script>")
    long countPage(@Param("stationId") Long stationId,
                   @Param("status") Integer status,
                   @Param("waybillNo") String waybillNo,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    /**
     * 看板聚合（一次扫表出 5 个指标，口径逐条对齐 Mock {@code parcelSummary}）。
     * {@code stationId} 为空表示全站（ADMIN）。
     */
    @Select("<script>"
            + "SELECT COUNT(*) AS parcel_total, "
            + "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS pending_pickup, "
            + "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS abnormal_count, "
            + "SUM(CASE WHEN inbound_time &gt;= #{todayStart} THEN 1 ELSE 0 END) AS today_inbound, "
            + "SUM(CASE WHEN status = 2 AND pickup_time &gt;= #{todayStart} THEN 1 ELSE 0 END) AS today_pickup "
            + "FROM parcel WHERE is_deleted = 0 "
            + "<if test='stationId != null'>AND station_id = #{stationId} </if>"
            + "</script>")
    ParcelSummaryRow selectSummary(@Param("stationId") Long stationId,
                                   @Param("todayStart") LocalDateTime todayStart);

    /** 趋势：窗口内按入库日分桶计数（走 idx_parcel_station_inbound 的 range 扫描） */
    @Select("<script>"
            + "SELECT DATE(inbound_time) AS stat_date, COUNT(*) AS cnt "
            + "FROM parcel WHERE is_deleted = 0 AND inbound_time &gt;= #{windowStart} "
            + "<if test='stationId != null'>AND station_id = #{stationId} </if>"
            + "GROUP BY DATE(inbound_time)"
            + "</script>")
    List<ParcelDailyCount> countInboundByDay(@Param("stationId") Long stationId,
                                             @Param("windowStart") LocalDateTime windowStart);

    /** 趋势：窗口内按取件日分桶计数（仅 status=2 且 pickup_time 非空） */
    @Select("<script>"
            + "SELECT DATE(pickup_time) AS stat_date, COUNT(*) AS cnt "
            + "FROM parcel WHERE is_deleted = 0 AND status = 2 AND pickup_time &gt;= #{windowStart} "
            + "<if test='stationId != null'>AND station_id = #{stationId} </if>"
            + "GROUP BY DATE(pickup_time)"
            + "</script>")
    List<ParcelDailyCount> countPickupByDay(@Param("stationId") Long stationId,
                                            @Param("windowStart") LocalDateTime windowStart);

    /** 排行：按驿站聚合（含 0 包裹驿站由 Service 补全，对齐 Mock 遍历 station 全集） */
    @Select("SELECT station_id AS station_id, COUNT(*) AS parcel_total, "
            + "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS picked, "
            + "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) AS abnormal, "
            + "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS pending "
            + "FROM parcel WHERE is_deleted = 0 GROUP BY station_id")
    List<ParcelStationStat> selectStationStats();

    /**
     * 取件核销（并发安全的条件更新）。
     * <p>
     * <b>为什么用条件更新而非「先查后改」</b>：两个并发取件请求都会读到 status=1，
     * 若各自执行无条件 UPDATE 则会双写成功（后写覆盖前写）。这里把状态判定下沉到 {@code WHERE status = 1}，
     * 由 InnoDB 行锁保证同一包裹只有一个事务 {@code affectedRows = 1}，其余为 0 →
     * Service 再按「已被他人取 7003 / 其它非法 7002」归类，等价于乐观锁（以 status 为版本位）。
     *
     * @return 受影响行数（1=本次取件成功；0=状态已变或包裹不存在）
     */
    @Update("UPDATE parcel SET status = 2, pickup_employee_id = #{employeeId}, "
            + "pickup_time = #{pickupTime}, update_time = #{pickupTime} "
            + "WHERE id = #{id} AND status = 1 AND is_deleted = 0")
    int updatePickup(@Param("id") Long id,
                     @Param("employeeId") Long employeeId,
                     @Param("pickupTime") LocalDateTime pickupTime);
}
