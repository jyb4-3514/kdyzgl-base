package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qiujie.entity.CheckPeriod;
import com.qiujie.entity.WifiEntry;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打卡规则出参（对齐 Mock {@code attendanceStore.toRuleVO}）。
 * 字段集合与顺序按 Mock：含派生的 {@code workStartTime / workEndTime} 与 {@code stationName}。
 */
@Data
public class AttendanceRuleVO {

    private Long id;
    private Long stationId;
    private String stationName;
    private String ruleName;
    private Boolean enableWifi;
    private Boolean enableLocation;
    private Boolean enableTimeWindow;
    /** ALL / ANY */
    private String matchMode;
    private List<WifiEntry> wifiList;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer radius;
    /** 2 / 4 */
    private Integer checkFrequency;
    private List<CheckPeriod> checkPeriods;
    private Integer allowEarlyMin;
    private Integer allowLateMin;
    private String workStartTime;
    private String workEndTime;
    private Integer lateThresholdMin;
    private Integer earlyLeaveThresholdMin;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
