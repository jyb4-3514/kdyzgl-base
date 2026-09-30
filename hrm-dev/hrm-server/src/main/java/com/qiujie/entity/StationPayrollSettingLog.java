package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 驿站算薪配置变更审计（追加型：只增不改；db.md / DDL: V21 §3.6）。
 * <p>
 * 与 {@code payroll_log} 同构但独立：无 {@code is_deleted} / {@code update_time}，业务时间即 {@code time}。
 * I-3 每次保存必写一条；「启用 0→1」由 {@code action=ENABLE} 行承载、可追溯（安全 M-9 硬要求）。
 * <p>
 * {@code before}/{@code after} 存 JSON 文本（白名单键 {@code enabled/payrollDay/payrollTime/notifyEnabled/remark}，
 * 不含凭据 / 个人信息）；列名为 MySQL 近保留词，需转义（与 {@code payroll_log} 同口径）。
 */
@Data
@TableName("station_payroll_setting_log")
public class StationPayrollSettingLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id；非空定位） */
    private Long stationId;

    /** 动作：CREATE=首次创建 / UPDATE=字段变更 / ENABLE=启用(0→1) / DISABLE=停用(1→0) */
    private String action;

    /** 操作人（逻辑外键 employee.id） */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 操作人角色快照 */
    private String operatorRole;

    /** 变更前快照 JSON 文本（CREATE 时为 NULL）；{@code BEFORE} 为保留字，须转义 */
    @TableField(value = "`before`")
    private String before;

    /** 变更后快照 JSON 文本；{@code AFTER} 同族近保留词，一并转义 */
    @TableField(value = "`after`")
    private String after;

    /** 操作时间（只插不改，无 update_time） */
    private LocalDateTime time;

    private String remark;
}
