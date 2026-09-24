package com.qiujie.dto.attendance;

import lombok.Data;

/**
 * 我的打卡查询（GET /attendance/my）：{@code month} 可空（默认当前月 yyyy-MM）。非分页。
 */
@Data
public class MyAttendanceQuery {

    /** 账期 yyyy-MM，可空 */
    private String month;
}
