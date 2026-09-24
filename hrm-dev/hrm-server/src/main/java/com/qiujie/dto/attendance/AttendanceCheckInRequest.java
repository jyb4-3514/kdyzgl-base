package com.qiujie.dto.attendance;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 打卡入参（POST /attendance/check-in）。
 * <p>
 * {@code stationId} 在 Service 内按登录人收敛（非 ADMIN 强制本人驿站），防代他人向别的驿站打卡；
 * {@code periodIndex} 可缺省：缺省走单班次模型（排班班次为时间基准），传入则按时段模型判定。
 */
@Data
public class AttendanceCheckInRequest {

    private Long stationId;

    /** ON / OFF */
    private String checkType;

    private String wifiSsid;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer periodIndex;
}
