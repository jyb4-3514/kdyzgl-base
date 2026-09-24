package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡记录出参（对齐 Mock 打卡记录行 + {@code verifyFields}）。
 * <p>
 * 补卡补录行（{@code source=MAKEUP}）的设备校验字段为 null，用 {@link JsonInclude} 保留 null 字段
 * （契约要求字段存在且为 null，前端按 {@code source} 区分展示）。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AttendanceRecordVO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long stationId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate workDate;

    private Integer periodIndex;
    private String periodName;
    /** ON / OFF */
    private String checkType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime checkTime;

    /** NORMAL / LATE / EARLY_LEAVE / ABNORMAL */
    private String status;
    /** NORMAL / MAKEUP */
    private String source;
    /** WIFI / LOCATION / WIFI+LOCATION（补卡为空） */
    private String checkMode;
    private String wifiSsid;
    private Boolean wifiMatched;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal distance;
    private Boolean locationMatched;
    private String remark;
}
