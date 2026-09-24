package com.qiujie.vo.leave;

import lombok.Data;

/**
 * 请假全局设置出参（api.md §7.1 #5/#6）。
 */
@Data
public class LeaveSettingVO {

    /** 请假扣款开关：true=请假按缺勤计（扣款），false=不扣（默认） */
    private Boolean leaveDeductEnabled;

    public LeaveSettingVO() {
    }

    public LeaveSettingVO(Boolean leaveDeductEnabled) {
        this.leaveDeductEnabled = leaveDeductEnabled;
    }
}
