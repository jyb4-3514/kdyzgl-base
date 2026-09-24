package com.qiujie.dto.attendance;

import lombok.Data;

/**
 * 班次新增/编辑入参（POST /shifts、PUT /shifts/{id}）。
 * 校验在 Service 内完成（文案对齐 Mock {@code validateShift}）；{@code color} 归一为大写。
 */
@Data
public class AttendanceShiftRequest {

    /** 新增必填；编辑时忽略（归属不可改） */
    private Long stationId;

    private String shiftName;
    private String startTime;
    private String endTime;
    private String color;
    private Integer restMinutes;
    private Integer status;
}
