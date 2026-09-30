package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计留痕（ARCH-S-2 / DDL: V23__operation_audit_log.sql）——追加型审计表，只增不改。
 * <p>
 * <b>为什么独立成表</b>：{@code payroll_log.payroll_id} 为 NOT NULL，结构上无法承载「无工资单」的
 * 驿站/账号类事件；对齐既有追加型留痕形态（{@code payroll_log} / {@code station_payroll_setting_log}）。
 * <p>
 * <b>不可变</b>：无 {@code update_time} / {@code is_deleted}（DDL 未定义），业务时间列即 {@code time}；
 * 应用层禁止 UPDATE / DELETE。
 * <p>
 * <b>口令脱敏硬约束</b>（V23 §3.4）：{@code before} / {@code after} / {@code changed_fields} 一律白名单化，
 * 涉及口令只允许布尔标记（如 {@code {"password":"RESET"}}），绝不落明文或散列。
 * <p>
 * {@code before} / {@code after} 为 MySQL 近保留词，须转义（与 {@code payroll_log} / {@code station_payroll_setting_log} 同口径）。
 */
@Data
@TableName("operation_audit_log")
public class OperationAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人（逻辑外键 employee.id；SYSTEM 触发为空） */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 操作人角色快照（ADMIN/STATION_ADMIN/STAFF） */
    private String operatorRole;

    /** 操作主体：USER=人工，SYSTEM=系统自动 */
    private String operatorType;

    /** 目标类型：EMPLOYEE / STATION（扩展新对象只增取值，不改表结构） */
    private String targetType;

    /** 目标主键（逻辑外键，按 targetType 指向 employee.id 或 station.id） */
    private Long targetId;

    /** 目标名称快照（员工姓名 / 驿站名；目标逻辑删后仍可知「改的是谁」） */
    private String targetName;

    /** 动作：CREATE / UPDATE / CHANGE_STATUS / DELETE / RESET_PASSWORD */
    private String action;

    /** 变更前快照 JSON 文本（白名单键；口令只允许布尔标记）；BEFORE 为保留字，须转义 */
    @TableField(value = "`before`")
    private String before;

    /** 变更后快照 JSON 文本；AFTER 同族近保留词，一并转义 */
    @TableField(value = "`after`")
    private String after;

    /** 发生变化的字段名白名单（JSON 数组）；口令变更只记 "password" 字段名 */
    private String changedFields;

    /** 客户端 IP（Nginx 透传 X-Forwarded-For 首个） */
    private String clientIp;

    /** 结果：SUCCESS=成功，FAIL=失败 */
    private String result;

    /** 失败原因（截断，不落敏感信息 / 口令；result=FAIL 时可选填） */
    private String failReason;

    /** 操作时间（只插不改，无 update_time；命名对齐 leave_log/payroll_log 的 time） */
    private LocalDateTime time;
}
