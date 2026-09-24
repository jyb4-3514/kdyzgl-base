package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.dto.kpi.KpiMetricDetailItem;
import com.qiujie.handler.KpiMetricDetailListTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * KPI 月度评分（一员工一账期一行，含算分快照，db.md §8.4.2，DDL: V6__kpi.sql）。
 * <p>
 * 与 Mock {@code kpiStore} 的差异：Mock 为「一员工一账期一指标」逐指标行；本表按架构 §4.2 落
 * 「一员工一账期」聚合行，逐指标明细以 {@code metricDetail}（JSON 快照）承载（低频读取、结构多变）。
 * 总分/达成率/等级/指标数/权重合计显式列，列表与排名直接读列，不必解析 JSON。
 * <p>
 * {@code autoResultMap = true}：JSON 列 {@code metric_detail} 走自定义类型处理器，必须开启结果映射。
 * <p>
 * 幂等策略（对齐 Mock {@code calculate}）：同月同员工**覆盖重建**（重算即覆盖旧快照），重复算分结果恒定。
 */
@Data
@TableName(value = "kpi_score", autoResultMap = true)
public class KpiScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 统计时归属驿站（数据范围收敛/驿站排行用，逻辑外键 station.id） */
    private Long stationId;

    /** 考核月份 yyyy-MM */
    private String month;

    /** 加权总分：Σ(单项得分×权重)/Σ(适用指标权重)，保留 1 位小数 */
    private BigDecimal totalScore;

    /** 平均达成率：逐指标达成率的算术平均，保留 4 位小数 */
    private BigDecimal achievementRate;

    /** 等级：EXCELLENT / GOOD / PASS / IMPROVE */
    private String level;

    /** 参与指标数 */
    private Integer metricCount;

    /** 参与权重合计 */
    private Integer weightSum;

    /** 逐指标算分快照（目标/实际/达成率/单项分/加权分/评分规则副本） */
    @TableField(typeHandler = KpiMetricDetailListTypeHandler.class)
    private List<KpiMetricDetailItem> metricDetail;

    /** 算分时间 */
    private LocalDateTime calculateTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
