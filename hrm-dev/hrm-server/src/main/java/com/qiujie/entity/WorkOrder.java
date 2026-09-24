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
 * 工单主表（db.md §8.8.1，DDL: V10__work_order.sql）。
 * <p>
 * 处理时间线拆到 {@link WorkOrderTimeline}（替代 Mock {@code handle_log} 内嵌 JSON），
 * 出参由 Service 组装回 {@code handleLog[]}（契约不变）。
 * 单号 {@code orderNo} 由「自增主键回填」生成（Mock 用全局序号拼号）——见 {@code WorkOrderServiceImpl} 说明。
 */
@Data
@TableName("work_order")
public class WorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号 WO-yyyyMMdd-####（活跃唯一） */
    private String orderNo;

    /** 类型：1=包裹异常 2=设备故障 3=客户投诉 4=其他 */
    private Integer type;

    /** 状态：0=待处理 1=处理中 2=已解决 3=已关闭 */
    private Integer status;

    /** 优先级：0=低 1=中 2=高 */
    private Integer priority;

    /** 标题（1-100 字） */
    private String title;

    /** 描述（≤500 字） */
    private String content;

    /** 来源：MANUAL=手工，AUTO_WECHAT=企微自动派发 */
    private String source;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 关联包裹（逻辑外键 parcel.id） */
    private Long parcelId;

    /** 关联运单号 */
    private String waybillNo;

    /** 上报人（企微自动派发为空，逻辑外键 employee.id） */
    private Long reporterId;

    /** 处理人（无候选时为空 = 转人工，逻辑外键 employee.id） */
    private Long assigneeId;

    /** SLA 截止时间 */
    private LocalDateTime slaDeadline;

    /** 解决时间（仅状态 2/3 有值） */
    private LocalDateTime resolvedTime;

    /** 关闭时间（仅状态 3 有值） */
    private LocalDateTime closedTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
