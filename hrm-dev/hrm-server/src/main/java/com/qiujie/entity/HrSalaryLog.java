package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.handler.HrAllowanceListTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调薪留痕（追加型审计表，db.md §8.5，DDL: V7__hr.sql）。
 * <p>
 * 只增不改：含入职定薪与每次调整各一条，无 {@code is_deleted} 与 {@code update_time}（历史不可模糊）。
 * 「当前定薪 = 最新生效日期的一条」，故查询历史按 {@code effective_date} 降序。
 */
@Data
@TableName(value = "hr_salary_log", autoResultMap = true)
public class HrSalaryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 变更类型：ENTRY=入职定薪，ADJUST=调薪 */
    private String changeType;

    /** 基本工资（变更后） */
    private BigDecimal basicSalary;

    /** 岗位工资（变更后） */
    private BigDecimal postSalary;

    /** 绩效基数（变更后） */
    private BigDecimal performanceBase;

    /** 津贴项快照 */
    @TableField(typeHandler = HrAllowanceListTypeHandler.class)
    private List<HrAllowance> allowances;

    /** 津贴合计 */
    private BigDecimal allowancesTotal;

    /** 定薪合计 */
    private BigDecimal totalSalary;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 变更原因 */
    private String reason;

    /** 操作人（逻辑外键 employee.id） */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
