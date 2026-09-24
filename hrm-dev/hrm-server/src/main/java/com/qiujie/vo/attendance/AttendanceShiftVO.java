package com.qiujie.vo.attendance;

import lombok.Data;

/**
 * 班次出参（对齐 Mock {@code attendanceStore.toShiftVO}）。
 * 未排班时的「兜底班次」{@code id=null}、{@code shiftName=默认班次}（见 {@code defaultShiftOf}）。
 */
@Data
public class AttendanceShiftVO {

    private Long id;
    private Long stationId;
    private String stationName;
    private String shiftName;
    private String startTime;
    private String endTime;
    private String color;
    private Integer restMinutes;
    private Integer status;
}
