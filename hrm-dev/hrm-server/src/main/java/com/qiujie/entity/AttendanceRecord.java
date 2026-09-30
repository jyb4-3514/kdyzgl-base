package com.qiujie.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录（打卡事实，db.md §8.3.4，DDL: V5__attendance.sql）。
 * <p>
 * {@code status} 是打卡事实枚举（NORMAL/LATE/EARLY_LEAVE/ABNORMAL）；出勤口径（应到=排班班次数、
 * 实到=有效上班卡映射到班次后与应到取交）在 Service 聚合，本表只存事实。
 * 补卡补录行无设备校验，校验列（checkMode/wifiSsid/wifiMatched/经纬度/distance/locationMatched）置 null。
 */
@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工（逻辑外键 employee.id） */
    private Long employeeId;

    /** 驿站（逻辑外键 station.id） */
    private Long stationId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 时段序号（从 0 起，与规则 check_periods 对齐；单班次模型恒为 0） */
    private Integer periodIndex;

    /** 时段名快照 */
    private String periodName;

    /** 打卡类型：ON=上班卡，OFF=下班卡 */
    private String checkType;

    /** 打卡时间 */
    private LocalDateTime checkTime;

    /** 打卡状态：NORMAL / LATE / EARLY_LEAVE / ABNORMAL */
    private String status;

    /** 来源：NORMAL=正常打卡，MAKEUP=补卡补录 */
    private String source;

    /** 命中校验项：WIFI / LOCATION / WIFI+LOCATION（补卡为空） */
    private String checkMode;

    /** 打卡时 WiFi SSID */
    private String wifiSsid;

    /** WiFi 是否命中：0/1（补卡为空） */
    private Integer wifiMatched;

    /** 打卡经度 */
    private BigDecimal longitude;

    /** 打卡纬度 */
    private BigDecimal latitude;

    /** 距围栏中心距离（米） */
    private BigDecimal distance;

    /** 定位是否命中：0/1（补卡为空） */
    private Integer locationMatched;

    /** 备注（迟到/早退/异常原因） */
    private String remark;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
