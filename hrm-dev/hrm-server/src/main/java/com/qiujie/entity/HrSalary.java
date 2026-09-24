package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.handler.HrAllowanceListTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前定薪（员工 1:1，db.md §8.5，DDL: V7__hr.sql）。
 * <p>
 * 只存「当前生效」的一份；历史变更存 {@link HrSalaryLog}（只增不改）。
 * {@code allowances} 为 JSON 列，需 {@code autoResultMap = true} 才走自定义 TypeHandler。
 * <p>
 * 口径红线：{@code totalSalary = basicSalary + postSalary + performanceBase + allowancesTotal}，
 * 该口径来自 Mock {@code saveSalary}，本批不得自行引入新规则（计费口径须由用户确认）。
 */
@Data
@TableName(value = "hr_salary", autoResultMap = true)
public class HrSalary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工（逻辑外键 employee.id，1:1，活跃唯一） */
    private Long employeeId;

    /** 基本工资 */
    private BigDecimal basicSalary;

    /** 岗位工资 */
    private BigDecimal postSalary;

    /** 绩效基数 */
    private BigDecimal performanceBase;

    /** 津贴项 [{key,name,amount}] */
    @TableField(typeHandler = HrAllowanceListTypeHandler.class)
    private List<HrAllowance> allowances;

    /** 津贴合计 */
    private BigDecimal allowancesTotal;

    /** 定薪合计 */
    private BigDecimal totalSalary;

    /** 生效日期 */
    private LocalDate effectiveDate;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
