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
 * 排班（db.md §8.3.3，DDL: V5__attendance.sql；多班次活跃唯一见 V22）：员工 × 日期 × 班次。
 * <p>
 * 唯一性 = {@code (employeeId, workDate, shiftId)} 活跃唯一（V22 生成列式部分唯一，软删行不占键）。
 * <b>本实体刻意不声明 {@code active_shift_key} 生成列</b>——该列由 MySQL 生成（STORED），应用层写入会触发
 * 「生成列不可插入」错误；对齐 {@code Employee} 不声明 {@code phone_active} 的既有做法。
 * 索引 {@code (station_id, work_date)} 供驿站周矩阵、{@code (employee_id, work_date)} 供我的排班与逐日统计。
 */
@Data
@TableName("attendance_schedule")
public class AttendanceSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 班次（逻辑外键 attendance_shift.id） */
    private Long shiftId;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
