package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单转单留痕（db.md §8.8.3，DDL: V10__work_order.sql）——追加型审计表，只增不改。
 * <p>
 * 无 {@code is_deleted} / {@code update_time}；业务时间即 {@code transferTime}。
 * 出参按 {@code transfer_time DESC} 返回（详情页「就近在上」，对齐 Mock {@code listWorkOrderTransfers}）。
 */
@Data
@TableName("work_order_transfer")
public class WorkOrderTransfer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单（逻辑外键 work_order.id） */
    private Long workOrderId;

    /** 原处理人（逻辑外键 employee.id） */
    private Long fromEmployeeId;

    /** 原处理人姓名快照 */
    private String fromEmployeeName;

    /** 新处理人（逻辑外键 employee.id） */
    private Long toEmployeeId;

    /** 新处理人姓名快照 */
    private String toEmployeeName;

    /** 转单理由（2-100 字） */
    private String reason;

    /** 转单操作人（逻辑外键 employee.id） */
    private Long operatorId;

    /** 转单操作人姓名快照 */
    private String operatorName;

    /** 转单时间（只插不改） */
    private LocalDateTime transferTime;
}
