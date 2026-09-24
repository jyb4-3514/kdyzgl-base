package com.qiujie.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤明细出参（对齐 Mock {@code attendanceStore.attendanceDetail}）：按维度返回「人 + 当天在该维度的事实」。
 * <p>
 * 口径与概况完全同源（共用出勤口径）；缺卡 = 应到差集实到，异常卡六个维度都不承载。
 */
@Data
public class AttendanceDetailVO {

    /** SHOULD / ACTUAL / NORMAL / LATE / EARLY_LEAVE / ABSENT */
    private String dim;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private Integer total;
    private List<Row> list;

    /** 明细行（六个维度结构一致） */
    @Data
    public static class Row {
        private Long employeeId;
        private String employeeName;
        private Long stationId;
        private String stationName;
        /** 仅 SHOULD/ABSENT 维度有值（来自排班班次） */
        private String shiftName;
        private String periodName;
        private CheckInfo onCheck;
        private CheckInfo offCheck;
        /** MISS / LATE / EARLY_LEAVE / NORMAL */
        private String dayState;
        private String remark;
    }

    /** 上/下班卡事实（时间 + 状态） */
    @Data
    public static class CheckInfo {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime time;
        private String status;
    }
}
