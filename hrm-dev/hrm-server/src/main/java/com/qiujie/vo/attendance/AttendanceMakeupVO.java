package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请出参（对齐 Mock {@code attendanceStore.toMakeupVO}）。
 * {@code approverName} 由 {@code approverId} 反查得到（非本表列）。
 */
@Data
public class AttendanceMakeupVO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long stationId;
    private String stationName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate workDate;

    private Integer periodIndex;
    private String periodName;
    /** ON / OFF */
    private String checkType;
    private String reason;
    /** PENDING / APPROVED / REJECTED */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime applyTime;

    private Long approverId;
    private String approverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime approveTime;

    private String approveRemark;
}
