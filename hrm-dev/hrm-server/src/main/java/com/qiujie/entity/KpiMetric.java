package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * KPI 指标配置（业务口径可热改，db.md §8.4.1，DDL: V6__kpi.sql）。
 * <p>
 * {@code scoreMode} + {@code fullScore} 是 Mock {@code scoreRule{mode,fullScore}} 的拆列：
 * 它们结构固定且需可读与约束，按架构 §4.6(3) 禁止进 JSON。
 * {@code roleScope} 为逗号分隔角色串（NULL/空 = 全员适用），由 Service 与数组互转。
 * <p>
 * 权重守卫只在「新增/更新/删除/批量保存」时校验启用指标合计 = 100（与 Mock {@code weightError} 时机一致）；
 * 算分按「员工实际适用指标权重」归一，故临时偏离 100 不会算错分。
 */
@Data
@TableName("kpi_metric")
public class KpiMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指标键（活跃唯一；评分快照按此可读） */
    private String metricKey;

    /** 指标名（1-50 字） */
    private String metricName;

    /** 类型：PARCEL/PICKUP/COMPLAINT/ATTENDANCE/SERVICE/WORK_ORDER/TRAINING/OTHER */
    private String metricType;

    /** 权重（0-100 整数；启用指标合计须 100） */
    private Integer weight;

    /** 目标值（可为 0 / NULL） */
    private BigDecimal targetValue;

    /** 单位（件 / % / 分 …） */
    private String unit;

    /** 方向：UP=越高越好，DOWN=越低越好 */
    private String direction;

    /** 评分规则：LINEAR / TIERED / BINARY */
    private String scoreMode;

    /** 单项满分（(0,100]） */
    private BigDecimal fullScore;

    /** 适用角色，逗号分隔（ADMIN/STATION_ADMIN/STAFF），空=全员 */
    private String roleScope;

    /** 启用：0=停用，1=启用 */
    private Integer enabled;

    /** 排序（升序） */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
