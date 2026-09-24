package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.handler.PayrollItemParamsTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 计薪规则项（db.md §8.6.2，DDL: V8__payroll.sql）。
 * <p>
 * {@code params} 为 JSON 列，结构随来源而变（FIXED: field/allowanceKey；ATTENDANCE: metric/mode/amount/cap；
 * KPI: baseField/capRatio；MANUAL: defaultValue），故以 {@code Map<String,Object>} 承载并走自定义 TypeHandler。
 * 需要 {@code autoResultMap = true} 才能让 TypeHandler 生效。
 */
@Data
@TableName(value = "payroll_rule_item", autoResultMap = true)
public class PayrollRuleItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则（逻辑外键 payroll_rule.id） */
    private Long ruleId;

    /** 规则项键（大写字母/数字/下划线，1-30） */
    private String itemKey;

    /** 规则项名（1-20 字） */
    private String itemName;

    /** 类型：ADDITION=增项，DEDUCTION=扣项 */
    private String itemType;

    /** 来源：FIXED / ATTENDANCE / KPI / MANUAL */
    private String source;

    /** 来源解析参数（JSON 对象） */
    @TableField(typeHandler = PayrollItemParamsTypeHandler.class)
    private Map<String, Object> params;

    /** 启用：0=停用，1=启用 */
    private Integer enabled;

    /** 排序（升序） */
    private Integer sortOrder;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
