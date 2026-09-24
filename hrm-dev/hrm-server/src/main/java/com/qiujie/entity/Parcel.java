package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 包裹表（db.md §8.10.1，DDL: V13__parcel.sql），20 万级大表。
 * <p>
 * 字段真源：Mock {@code parcelStore.js}（{@code shelf_code} / {@code sync_batch_no} 命名以 Mock 为准，
 * 见 db.md §9 命名纠正）。列名一律 snake_case，MyBatis-Plus 自动映射为驼峰。
 * <p>
 * 大表纪律（架构 §4.3）：禁止 {@code SELECT *}（列表查询走 {@code ParcelMapper} 显式列 + 覆盖索引）；
 * 分页默认游标（ADR-06）；结构变更须数据库 + 算法联评（本类为只读映射，无自定义 DDL）。
 */
@Data
@TableName("parcel")
public class Parcel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id，数据可见范围核心维度） */
    private Long stationId;

    /** 运单号（与 station_id 组合活跃唯一，Service 查重） */
    private String waybillNo;

    /** 状态：0=待入库 1=在库待取 2=已取件 3=异常 4=已退回 */
    private Integer status;

    /** 收件人姓名（出参脱敏 maskName） */
    private String receiverName;

    /** 收件人手机号（出参脱敏 maskPhone） */
    private String receiverPhone;

    /** 货架位编码 */
    private String shelfCode;

    /** 入库时间（列表默认排序键，游标键首列） */
    private LocalDateTime inboundTime;

    /** 取件员工（逻辑外键 employee.id） */
    private Long pickupEmployeeId;

    /** 取件时间（状态 2 时非空） */
    private LocalDateTime pickupTime;

    /** 来源同步批次号（逻辑外键 sync_task.batch_no） */
    private String syncBatchNo;

    /** 备注（异常件说明） */
    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
