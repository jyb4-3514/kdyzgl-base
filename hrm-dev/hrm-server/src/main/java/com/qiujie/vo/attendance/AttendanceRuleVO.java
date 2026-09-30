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
    /** 2 / 4（只读派生：= 该驿站启用班次数 × 2，U-4） */
    private Integer checkFrequency;
    /** 由该驿站启用班次派生（只读；值域为班次名与班次起止） */
    private List<CheckPeriod> checkPeriods;
    /** 时段是否只读（真源统一后恒为 true，供前端渲染「由班次决定」） */
    private Boolean checkPeriodsReadonly;
    private Integer allowEarlyMin;
    private Integer allowLateMin;
    /** 派生：首班开始（无启用班次时为 null） */
    private String workStartTime;
    /** 派生：末班结束（无启用班次时为 null） */
    private String workEndTime;
    private Integer lateThresholdMin;
    private Integer earlyLeaveThresholdMin;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
