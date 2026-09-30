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
 * 驿站级算薪配置（一驿一条；db.md §8.6.5 / DDL: V20）。
 * <p>
 * 形态对齐 {@code attendance_rule}（V5）：「一驿一条」的活跃唯一由 Service 查重保证，不建 DB 唯一索引（决策 D7）。
 * {@code enabled} 默认 0（默认不自动跑数，安全）；{@code notifyEnabled} 默认 1（生成即推管理员）。
 * {@code payrollDay} 取值 1..31，当月无该日由调度钳位到当月最后一天（U-05）。
 */
@Data
@TableName("station_payroll_setting")
public class StationPayrollSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id；一驿一条） */
    private Long stationId;

    /** 是否启用自动算薪：0=停用（默认），1=启用 */
    private Integer enabled;

    /** 算薪日=每月第几天（1-31；月末由调度钳位） */
    private Integer payrollDay;

    /** 执行时间 HH:mm（Asia/Shanghai 墙钟） */
    private String payrollTime;

    /** 生成后是否推送管理员：0=不推，1=推（默认） */
    private Integer notifyEnabled;

    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
