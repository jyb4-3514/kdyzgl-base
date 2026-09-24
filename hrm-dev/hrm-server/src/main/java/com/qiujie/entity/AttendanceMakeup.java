package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请（db.md §8.3.5，DDL: V5__attendance.sql）。
 * <p>
 * 审批通过后回写 {@code attendance_record}（{@code source=MAKEUP}）；驳回仅改状态，不物理删除（可重新申请）。
 * {@code approverName} 非本表列，出参时按 {@code approverId} 反查员工姓名快照。
 */
@Data
@TableName("attendance_makeup")
public class AttendanceMakeup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请人（逻辑外键 employee.id） */
    private Long employeeId;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 补卡日期 */
    private LocalDate workDate;

    /** 时段序号 */
    private Integer periodIndex;

    /** 时段名快照 */
    private String periodName;

    /** 打卡类型：ON / OFF */
    private String checkType;

    /** 补卡理由（2-200 字） */
    private String reason;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 审批人（ADMIN，逻辑外键 employee.id） */
    private Long approverId;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 审批意见（≤200 字） */
    private String approveRemark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
