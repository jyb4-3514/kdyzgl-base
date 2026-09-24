package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiujie.handler.CheckPeriodListTypeHandler;
import com.qiujie.handler.WifiEntryListTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打卡规则（一驿一条，db.md §8.3.1，DDL: V5__attendance.sql）。
 * <p>
 * {@code checkPeriods} 是打卡时间判定的唯一真源；{@code checkFrequency} 只决定每日几段；
 * {@code workStartTime / workEndTime} 是「首段开始 / 末段结束」的派生值（Service 保存时重算，与 Mock 一致）。
 * <p>
 * {@code autoResultMap = true}：JSON 列（wifi_list / check_periods）走自定义类型处理器，必须开启结果映射。
 */
@Data
@TableName(value = "attendance_rule", autoResultMap = true)
public class AttendanceRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 驿站（逻辑外键 station.id，一驿一条，活跃唯一由 Service 保证） */
    private Long stationId;

    /** 规则名称（1-50 字） */
    private String ruleName;

    /** 启用 WiFi 校验：0/1 */
    private Integer enableWifi;

    /** 启用定位校验：0/1 */
    private Integer enableLocation;

    /** 启用时间窗校验：0/1 */
    private Integer enableTimeWindow;

    /** 校验项组合：ALL=全部满足，ANY=任一满足 */
    private String matchMode;

    /** WiFi 白名单 JSON（[{ssid,bssid}]） */
    @TableField(typeHandler = WifiEntryListTypeHandler.class)
    private List<WifiEntry> wifiList;

    /** 电子围栏中心经度 */
    private BigDecimal longitude;

    /** 电子围栏中心纬度 */
    private BigDecimal latitude;

    /** 围栏半径（米） */
    private Integer radius;

    /** 每日打卡次数：2=单时段，4=双时段 */
    private Integer checkFrequency;

    /** 时段明细 JSON（[{name,startTime,endTime}]，时间判定唯一真源） */
    @TableField(typeHandler = CheckPeriodListTypeHandler.class)
    private List<CheckPeriod> checkPeriods;

    /** 时间窗提前量（分钟） */
    private Integer allowEarlyMin;

    /** 时间窗延后量（分钟） */
    private Integer allowLateMin;

    /** 派生：首时段开始（HH:mm） */
    private String workStartTime;

    /** 派生：末时段结束（HH:mm，可 24:00） */
    private String workEndTime;

    /** 迟到判定阈值（分钟） */
    private Integer lateThresholdMin;

    /** 早退判定阈值（分钟） */
    private Integer earlyLeaveThresholdMin;

    /** 状态：0=停用，1=启用 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
