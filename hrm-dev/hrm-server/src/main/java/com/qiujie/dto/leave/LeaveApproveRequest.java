package com.qiujie.dto.leave;

import lombok.Data;

/**
 * 审批入参（站长初审 / 老板终审共用，api.md §7.1 #11/#12）。
 * <p>
 * 通过时意见选填 0-100 字、驳回时必填 2-100 字（与补卡的「选填」刻意不同）。
 */
@Data
public class LeaveApproveRequest {

    /** 是否通过 */
    private Boolean approved;

    /** 审批意见（通过选填 / 驳回必填） */
    private String remark;
}
