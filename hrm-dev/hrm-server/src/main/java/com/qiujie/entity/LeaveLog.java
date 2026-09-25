package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请假操作留痕（db.md §7.2 / DDL: V9__leave.sql）——追加型审计表，只增不改。
 * <p>
 * 无 {@code is_deleted} / {@code update_time}（DDL 未定义），业务时间即 {@code time}；
 * {@code before}/{@code after} 为变更前后快照 JSON 文本（低频读取、结构多变，出参时解析成对象）。
 * {@code NOTIFY_SKIP} 是「通知目标缺失」的排障留痕，不属业务动作但同样可检索。
 */
@Data
@TableName("leave_log")
public class LeaveLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请假单（逻辑外键 leave_request.id） */
    private Long leaveId;

    /** 动作：SUBMIT/UPDATE/RESUBMIT/CANCEL/STATION_APPROVE/STATION_REJECT/FINAL_APPROVE/FINAL_REJECT/REVOKE/NOTIFY_SKIP */
    private String action;

    /** 操作人（逻辑外键 employee.id；系统留痕可为空） */
    private Long operatorId;

    /** 操作人姓名快照 */
    private String operatorName;

    /** 操作人角色快照 */
    private String operatorRole;

    /** 操作时间（只插不改，无 update_time） */
    private LocalDateTime time;

    /** 变更前状态 */
    private String fromStatus;

    /** 变更后状态 */
    private String toStatus;

    /**
     * 变更前快照 JSON 文本。
     * <p>为什么显式转义：{@code BEFORE} 是 MySQL 8.0 保留字（BEFORE MySQL 8.0 Keywords: BEFORE (R)），
     * MyBatis-Plus 生成 SQL / LambdaQueryWrapper 解析列名时不会自动加引号，不转义将报 1064 语法错误。
     * 出参字段名仍为 {@code before}（{@link com.qiujie.vo.leave.LeaveLogVO} 不受影响）。
     */
    @TableField(value = "`before`")
    private String before;

    /**
     * 变更后快照 JSON 文本。
     * <p>为什么显式转义：{@code AFTER} 在 MySQL 8.0 虽为非保留关键字（可裸用），但与 {@code before}
     * 同属保留/近保留词族，且未来切换 PostgreSQL 时习惯保持一致；一并转义可消除歧义、便于统一维护。
     */
    @TableField(value = "`after`")
    private String after;

    /** 备注（驳回原因 / 撤回原因 / 排障说明） */
    private String remark;
}
