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
 * 班次（db.md §8.3.2，DDL: V5__attendance.sql）：早/中/晚，供排班与打卡判定引用。
 * {@code endTime} 允许 {@code 24:00} 表示跨零点收班。
 */
@Data
@TableName("attendance_shift")
public class AttendanceShift {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 班次名（1-20 字） */
    private String shiftName;

    /** 开始（HH:mm） */
    private String startTime;

    /** 结束（HH:mm，可 24:00） */
    private String endTime;

    /** 班次色值（前端展示 Token，形如 #RRGGBB） */
    private String color;

    /** 休息时长（分钟） */
    private Integer restMinutes;

    /** 状态：0=停用，1=启用 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
