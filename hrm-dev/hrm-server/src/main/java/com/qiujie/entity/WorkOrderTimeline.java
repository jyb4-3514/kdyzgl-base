package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单处理时间线（db.md §8.8.2，DDL: V10__work_order.sql）——追加型审计表，只增不改。
 * <p>
 * 无 {@code is_deleted} / {@code update_time}；业务时间即 {@code time}。出参按 {@code time ASC, id ASC}
 * 组装回 {@code handleLog[]}（同一秒内的多事件靠 id 保序，与 Mock 数组插入顺序一致）。
 */
@Data
@TableName("work_order_timeline")
public class WorkOrderTimeline {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单（逻辑外键 work_order.id） */
    private Long workOrderId;

    /** 动作：create/accept/resolve/close/reopen/assign/transfer/auto_dispatch */
    private String action;

    /** 操作人（系统/企微来源为空，逻辑外键 employee.id） */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 内容 */
    private String content;

    /** 事件时间（只插不改） */
    private LocalDateTime time;
}
