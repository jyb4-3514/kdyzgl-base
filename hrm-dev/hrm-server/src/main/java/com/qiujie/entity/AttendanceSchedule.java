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
 * 排班（db.md §8.3.3，DDL: V5__attendance.sql）：员工 × 日期 × 班次。
 * <p>
 * 唯一性 = {@code employeeId + workDate}（活跃唯一），由 Service 查重保证（与一期「不依赖数据库唯一索引」一致）；
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
