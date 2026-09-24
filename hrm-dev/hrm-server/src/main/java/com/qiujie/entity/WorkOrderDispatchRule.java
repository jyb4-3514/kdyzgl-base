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
 * 企微自动派单规则（db.md §8.8.4，DDL: V10__work_order.sql）——可热改的业务口径（ADR-05 入库）。
 * <p>
 * 关键词 → 工单类型/优先级；{@code defaultAssigneeId} 可为空（不绑定驿站），为空时由 S6 多目标
 * 排序推导候选处理人；规则未命中时走兜底类型/优先级（{@code hrm.algo.dispatch.default*}）。
 */
@Data
@TableName("work_order_dispatch_rule")
public class WorkOrderDispatchRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群消息关键词（1-20 字） */
    private String keyword;

    /** 命中后工单类型：1-4 */
    private Integer workOrderType;

    /** 命中后优先级：0-2 */
    private Integer priority;

    /** 默认处理人（可为空，逻辑外键 employee.id） */
    private Long defaultAssigneeId;

    /** 启用：0=停用，1=启用 */
    private Integer enabled;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
