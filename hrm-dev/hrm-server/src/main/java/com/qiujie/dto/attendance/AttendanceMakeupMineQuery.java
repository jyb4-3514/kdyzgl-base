package com.qiujie.dto.attendance;

import com.qiujie.dto.support.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的补卡查询（GET /attendance/makeup/my）。
 * <p>
 * 数据范围一律以登录身份收口（employeeId 取登录人），<b>不接收</b>前端传入的 employeeId/stationId。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceMakeupMineQuery extends PageQuery {

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private String startDate;

    private String endDate;
}
