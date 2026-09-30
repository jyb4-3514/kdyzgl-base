package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工资单操作留痕（db.md §8.6.6 / DDL: V20 + V21）——追加型审计表，只增不改。
 * <p>
 * 无 {@code is_deleted} / {@code update_time}（DDL 未定义），业务时间即 {@code time}；
 * {@code before}/{@code after} 为变更前后快照 JSON 文本（低频读取、结构多变，出参时解析成对象）。
 * <p>
 * {@code employeeId} / {@code month} 为 V21 补的<b>冗余定位列</b>：{@code generate} 覆盖重建会物理删除
 * DRAFT/REJECTED 单，{@code payrollId} 可能指向已删单（孤儿）；冗余「员工 + 账期」使留痕可脱离
 * {@code payroll_id} 独立检索。
 */
@Data
@TableName("payroll_log")
public class PayrollLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工资单（逻辑外键 payroll.id；覆盖重建后可能指向已删单，孤儿风险） */
    private Long payrollId;

    /** 冗余定位列：留痕所属员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 冗余定位列：账期 yyyy-MM */
    private String month;

    /** 动作：GENERATE_AUTO/GENERATE_MANUAL/ITEM_ADD/ITEM_UPDATE/SUBMIT/APPROVE/REJECT/PUBLISH/REPUBLISH/CONFIRM/OBJECTION/PAY/NOTIFY/NOTIFY_SKIP */
    private String action;

    /** 操作人（逻辑外键 employee.id；SYSTEM 为空） */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 操作人角色快照 */
    private String operatorRole;

    /** 操作主体：USER=人工，SYSTEM=自动调度 */
    private String operatorType;

    /** 操作时间（只插不改，无 update_time） */
    private LocalDateTime time;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /** 事由：手工加扣款必填 / 异议原因 / 驳回意见 / 再发布处理说明 */
    private String reason;

    /**
     * 变更前快照 JSON 文本。
     * <p>为什么显式转义：{@code BEFORE} 是 MySQL 8.0 保留字，MyBatis-Plus 生成 SQL / LambdaQueryWrapper
     * 解析列名时不会自动加引号，不转义将报 1064 语法错误。出参字段名仍为 {@code before}。
     */
    @TableField(value = "`before`")
    private String before;

    /** 变更后快照 JSON 文本；{@code AFTER} 同族近保留词，一并转义以消歧（与 {@code leave_log} 同口径） */
    @TableField(value = "`after`")
    private String after;

    /** 备注 / 排障说明 */
    private String remark;
}
