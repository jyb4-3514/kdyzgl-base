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
 * 工资单明细（子表，db.md §8.6.4，DDL: V8__payroll.sql）。
 * <p>
 * 逐项金额与「取数解释」文案，替代 Mock 内嵌 {@code items[]}，Service 组装回 {@code items[]}。
 * 金额只存正数（增/扣语义由 {@code itemType} 承载）；{@code detail} 与 Mock 计算说明文案逐字一致。
 */
@Data
@TableName("payroll_item")
public class PayrollItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工资单（逻辑外键 payroll.id） */
    private Long payrollId;

    /** 规则项键 */
    private String itemKey;

    /** 规则项名 */
    private String itemName;

    /** 类型：ADDITION=增项，DEDUCTION=扣项 */
    private String itemType;

    /** 来源：FIXED/ATTENDANCE/KPI/MANUAL */
    private String source;

    /** 金额（正数，增/扣由 itemType 承载） */
    private BigDecimal amount;

    /** 取数解释文案 */
    private String detail;

    /** 排序（升序） */
    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
