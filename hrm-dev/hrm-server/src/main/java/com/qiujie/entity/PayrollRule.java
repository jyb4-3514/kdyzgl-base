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
 * 计薪规则（表驱动，db.md §8.6.1，DDL: V8__payroll.sql）。
 * <p>
 * 规则本身只承载「名称/状态」，具体计薪项在 {@link PayrollRuleItem}：每个项声明「来源 + 参数」，
 * 算薪按来源分发到注册表，新增来源不改核心代码（算法 S2 注册表）。
 * 被 {@code payroll.rule_id} 引用（删除前 Service 校验是否被工资单引用）。
 */
@Data
@TableName("payroll_rule")
public class PayrollRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名（2-50 字） */
    private String ruleName;

    /** 备注（≤200 字，可空） */
    private String remark;

    /** 状态：0=停用，1=启用 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
