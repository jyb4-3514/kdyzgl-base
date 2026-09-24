package com.qiujie.dto.attendance;

import com.qiujie.entity.CheckPeriod;
import com.qiujie.entity.WifiEntry;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保存打卡规则入参（PUT /attendance/rule）。
 * <p>
 * 白名单写入：请求体只接受下列字段，{@code id / updateTime} 一律忽略（防越权改归属/时间）；
 * 校验与归一化在 Service 内完成（文案与顺序逐条对齐 Mock {@code validateRule / periodRuleError / normalizeRule}）。
 * {@code checkPeriods} 是唯一真源；未提交时段时沿用现值，只提交 workStartTime/workEndTime 时映射到首末时段（兼容旧客户端）。
 */
@Data
public class AttendanceRuleRequest {

    /** 目标驿站（必填） */
    private Long stationId;

    private String ruleName;
    private Boolean enableWifi;
    private Boolean enableLocation;
    private Boolean enableTimeWindow;
    private String matchMode;
    private List<WifiEntry> wifiList;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer radius;
    private Integer checkFrequency;
    private List<CheckPeriod> checkPeriods;
    private Integer allowEarlyMin;
    private Integer allowLateMin;
    private String workStartTime;
    private String workEndTime;
    private Integer lateThresholdMin;
    private Integer earlyLeaveThresholdMin;
    private Integer status;
}
