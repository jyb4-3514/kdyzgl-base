package com.qiujie.vo.attendance;

import lombok.Data;

import java.util.List;

/**
 * 我的打卡出参（对齐 Mock {@code attendanceStore.myAttendance}）：按月返回本人记录 + 今日状态。
 */
@Data
public class MyAttendanceVO {

    /** 账期 yyyy-MM */
    private String month;

    /** 本人当月打卡记录（按打卡时间倒序） */
    private List<AttendanceRecordVO> list;

    /** 今日打卡状态（账号归属员工不存在时为 null） */
    private AttendanceStatusVO todayStatus;
}
