package com.qiujie.dto.leave;

import lombok.Data;

/**
 * 请假扣款开关入参（api.md §7.1 #6）。仅 ADMIN 可写。
 */
@Data
public class LeaveSettingRequest {

    /** 请假扣款开关：true=请假按缺勤计（扣款），false=不扣 */
    private Boolean leaveDeductEnabled;
}
